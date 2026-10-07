-----------------------------------------------------------------------
package com.superapp.app.features.files.store

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.LruCache
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.superapp.app.features.files.index.FileIndex
import com.superapp.app.features.files.model.FilesException
import com.superapp.app.features.files.ssh.SshConnectionManager
import com.superapp.app.features.files.ssh.SshProfileStore
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

enum class ViewMode { LIST, GRID, COMPACT }
enum class SortBy { NAME, SIZE, DATE, TYPE }

class PrefState<T>(initial: T, private val write: (T) -> Unit) {
    var value by mutableStateOf(initial)
        private set

    fun set(v: T) { value = v; write(v) }
}

/**
 * Files-specific display options (layout only). Colours come from the app's existing theme (MaterialTheme /
 * AppTheme); this class does NOT define a second theme system. folderColor = 0 means "use the theme".
 */
class FilesPrefs(context: Context) {
    private val sp = context.applicationContext.getSharedPreferences("files_prefs", Context.MODE_PRIVATE)

    val viewMode = PrefState(runCatching { ViewMode.valueOf(sp.getString("view", "LIST")!!) }.getOrDefault(ViewMode.LIST)) { sp.edit().putString("view", it.name).apply() }
    val sortBy = PrefState(runCatching { SortBy.valueOf(sp.getString("sort", "NAME")!!) }.getOrDefault(SortBy.NAME)) { sp.edit().putString("sort", it.name).apply() }
    val sortAsc = PrefState(sp.getBoolean("asc", true)) { sp.edit().putBoolean("asc", it).apply() }
    val showHidden = PrefState(sp.getBoolean("hidden", false)) { sp.edit().putBoolean("hidden", it).apply() }
    val showSize = PrefState(sp.getBoolean("size", true)) { sp.edit().putBoolean("size", it).apply() }
    val showDate = PrefState(sp.getBoolean("date", true)) { sp.edit().putBoolean("date", it).apply() }
    val showType = PrefState(sp.getBoolean("type", true)) { sp.edit().putBoolean("type", it).apply() }
    val cornerDp = PrefState(sp.getInt("corner", 12)) { sp.edit().putInt("corner", it).apply() }
    val spacingDp = PrefState(sp.getInt("spacing", 6)) { sp.edit().putInt("spacing", it).apply() }
    val folderColor = PrefState(sp.getInt("folderColor", 0)) { sp.edit().putInt("folderColor", it).apply() }

    var lastIndexed: Long
        get() = sp.getLong("lastIndexed", 0L)
        set(v) { sp.edit().putLong("lastIndexed", v).apply() }

    fun safTrees(): List<String> = sp.getStringSet("saf", emptySet())!!.toList().sorted()
    fun addSafTree(uri: String) { sp.edit().putStringSet("saf", sp.getStringSet("saf", emptySet())!! + uri).apply() }
    fun removeSafTree(uri: String) { sp.edit().putStringSet("saf", sp.getStringSet("saf", emptySet())!! - uri).apply() }
}

data class FavoriteRef(val sourceId: String, val id: String, val name: String, val isDirectory: Boolean)

/** Favorites (files and folders), persisted. Availability is checked by the controller when shown. */
class FileFavorites(context: Context) {
    private val sp = context.applicationContext.getSharedPreferences("files_favorites", Context.MODE_PRIVATE)
    val items = mutableStateListOf<FavoriteRef>().apply {
        try {
            val a = JSONArray(sp.getString("list", "[]"))
            for (i in 0 until a.length()) a.getJSONObject(i).let {
                add(FavoriteRef(it.getString("s"), it.getString("id"), it.getString("n"), it.getBoolean("d")))
            }
        } catch (_: Exception) {}
    }

    private fun save() {
        val a = JSONArray()
        items.forEach { a.put(JSONObject().put("s", it.sourceId).put("id", it.id).put("n", it.name).put("d", it.isDirectory)) }
        sp.edit().putString("list", a.toString()).apply()
    }

    fun isFavorite(sourceId: String, id: String) = items.any { it.sourceId == sourceId && it.id == id }

    fun toggle(ref: FavoriteRef) {
        val ex = items.firstOrNull { it.sourceId == ref.sourceId && it.id == ref.id }
        if (ex != null) items.remove(ex) else items.add(ref)
        save()
    }

    fun remove(ref: FavoriteRef) { items.remove(ref); save() }
}

data class RecentFolder(val sourceId: String, val id: String, val name: String, val lastUsed: Long, val count: Int)

/** Recently / frequently opened folders (bounded list; no background scanning). */
class RecentStore(context: Context) {
    private val sp = context.applicationContext.getSharedPreferences("files_recent", Context.MODE_PRIVATE)
    var folders by mutableStateOf(load())
        private set

    private fun load(): List<RecentFolder> = try {
        val a = JSONArray(sp.getString("list", "[]"))
        (0 until a.length()).map { a.getJSONObject(it) }.map {
            RecentFolder(it.getString("s"), it.getString("id"), it.getString("n"), it.getLong("t"), it.getInt("c"))
        }
    } catch (_: Exception) { emptyList() }

    fun record(sourceId: String, id: String, name: String) {
        val old = folders.firstOrNull { it.sourceId == sourceId && it.id == id }
        val updated = (folders.filterNot { it === old } + RecentFolder(sourceId, id, name, System.currentTimeMillis(), (old?.count ?: 0) + 1))
            .sortedByDescending { it.lastUsed }.take(40)
        folders = updated
        val a = JSONArray()
        updated.forEach { a.put(JSONObject().put("s", it.sourceId).put("id", it.id).put("n", it.name).put("t", it.lastUsed).put("c", it.count)) }
        sp.edit().putString("list", a.toString()).apply()
    }

    fun recent(sourceId: String, n: Int = 5) = folders.filter { it.sourceId == sourceId }.sortedByDescending { it.lastUsed }.take(n)
    fun frequent(sourceId: String, n: Int = 5) = folders.filter { it.sourceId == sourceId && it.count > 1 }.sortedByDescending { it.count }.take(n)
}

/**
 * User-assigned icons per file extension (any extension) plus the special key "folder".
 * Mapping value: "emoji:<text>" or "img:<file>" (image copied + downscaled into app storage, so no URI permission is needed later).
 */
class FileIconStore(context: Context) {
    private val dir = File(context.applicationContext.filesDir, "file_icons").apply { mkdirs() }
    private val sp = context.applicationContext.getSharedPreferences("files_icons", Context.MODE_PRIVATE)
    private val resolver = context.applicationContext.contentResolver
    private val cache = LruCache<String, Bitmap>(64)
    val mappings = mutableStateMapOf<String, String>().apply {
        try {
            val o = JSONObject(sp.getString("map", "{}"))
            o.keys().forEach { put(it, o.getString(it)) }
        } catch (_: Exception) {}
    }

    companion object {
        const val FOLDER = "folder"
        fun normalizeKey(raw: String): String? {
            val k = raw.trim().lowercase().removePrefix(".").removePrefix("*.")
            return if (k == FOLDER || Regex("[a-z0-9_+\\-]{1,16}").matches(k)) k else null
        }
    }

    private fun save() {
        val o = JSONObject()
        mappings.forEach { (k, v) -> o.put(k, v) }
        sp.edit().putString("map", o.toString()).apply()
    }

    fun setEmoji(key: String, emoji: String) {
        removeFile(key)
        mappings[key] = "emoji:" + emoji.trim().take(8)
        save()
    }

    /** Blocking (decodes/scales the image). Call off the main thread. */
    fun setImage(key: String, uri: Uri) {
        val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, opts) }
        if (opts.outWidth <= 0) throw FilesException("This is not a readable image.")
        var sample = 1
        while (opts.outWidth / sample > 384 || opts.outHeight / sample > 384) sample *= 2
        val bmp = resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample }) }
            ?: throw FilesException("This is not a readable image.")
        val scaled = Bitmap.createScaledBitmap(bmp, 128, 128, true)
        val file = File(dir, "$key.png")
        file.outputStream().use { scaled.compress(Bitmap.CompressFormat.PNG, 100, it) }
        cache.remove(key)
        mappings[key] = "img:${file.name}"
        save()
    }

    fun remove(key: String) { removeFile(key); mappings.remove(key); save() }

    private fun removeFile(key: String) {
        cache.remove(key)
        if (mappings[key]?.startsWith("img:") == true) File(dir, "$key.png").delete()
    }

    fun emoji(key: String): String? = mappings[key]?.takeIf { it.startsWith("emoji:") }?.removePrefix("emoji:")

    fun bitmap(key: String): Bitmap? {
        val m = mappings[key] ?: return null
        if (!m.startsWith("img:")) return null
        cache.get(key)?.let { return it }
        return BitmapFactory.decodeFile(File(dir, m.removePrefix("img:")).path)?.also { cache.put(key, it) }
    }
}

/** Process-wide holder so the index and SSH sessions survive screen rotation. */
class FilesServices private constructor(context: Context) {
    val prefs = FilesPrefs(context)
    val icons = FileIconStore(context)
    val favorites = FileFavorites(context)
    val recents = RecentStore(context)
    val index = FileIndex(context)
    val profiles = SshProfileStore(context)
    val ssh = SshConnectionManager(context, profiles)

    companion object {
        @Volatile private var instance: FilesServices? = null
        fun get(context: Context): FilesServices = instance ?: synchronized(this) {
            instance ?: FilesServices(context.applicationContext).also { instance = it }
        }
    }
}

-----------------------------------------------------------------------
