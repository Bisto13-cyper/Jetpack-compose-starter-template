package com.superapp.app.core.settings

import android.content.Context
import com.superapp.app.core.images.ImageStore
import com.superapp.app.core.theme.LineStyle
import com.superapp.app.core.theme.ThemeSettings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * SETTINGS FILE 8 - Saves "favorite looks" so one tap restores a whole style.
 *
 * A favorite look remembers:
 *  - the four theme colors (background, panels, accent, text)
 *  - circle size, glow (strength, size, color) and connection lines (style, color, width)
 *  - the color of every home circle
 *  - the background PHOTO (its own private copy, so changing or removing
 *    the current background never breaks a favorite)
 *
 * Everything stays on the phone: SharedPreferences for the data and the
 * app's private folder for the photos. Nothing is sent anywhere.
 *
 * Saving and applying copy a photo file, so call them from a background
 * thread (withContext(Dispatchers.IO)).
 *
 * HOW TO ADD SOMETHING TO A FAVORITE (a new ThemeSettings field):
 *  1. Add the field to ThemeSettings
 *  2. Add it in toJson() and fromJson() below
 */
data class FavoriteLook(
    val id: String,
    val name: String,
    val theme: ThemeSettings,
    /** Circle colors: feature id -> color. Missing id = default color. */
    val accents: Map<String, Int>
)

class FavoritesStore(context: Context) {

    private val appContext = context.applicationContext
    private val prefs = appContext.getSharedPreferences(FILE_NAME, Context.MODE_PRIVATE)

    private val _items = MutableStateFlow(load())

    /** All saved favorites, oldest first. */
    val items: StateFlow<List<FavoriteLook>> = _items.asStateFlow()

    /** Saves the app's current look as a new favorite. */
    fun saveCurrent(name: String, repository: SettingsRepository): FavoriteLook {
        val id = "fav_${System.currentTimeMillis()}"
        val theme = repository.theme.value

        val photoCopy = theme.backgroundImagePath?.let { copyInto(it, "favorites", "$id.jpg") }

        val accents = repository.featureStyles.value
            .mapNotNull { (featureId, style) -> style.accent?.let { featureId to it } }
            .toMap()

        val look = FavoriteLook(
            id = id,
            name = name.trim().ifEmpty { "Favorite ${_items.value.size + 1}" },
            theme = theme.copy(backgroundImagePath = photoCopy),
            accents = accents
        )
        persist(_items.value + look)
        return look
    }

    /** Applies a favorite: colors, circle colors and the photo, all at once. */
    fun apply(look: FavoriteLook, repository: SettingsRepository) {
        val oldBackground = repository.theme.value.backgroundImagePath

        // The app gets its own fresh copy, so deleting or replacing the
        // background later can never damage the favorite.
        val newPath = look.theme.backgroundImagePath?.let {
            copyInto(it, "backgrounds", "img_${System.currentTimeMillis()}.jpg")
        }

        repository.updateTheme { look.theme.copy(backgroundImagePath = newPath) }

        val knownIds = repository.featureStyles.value.keys
        (knownIds + look.accents.keys).forEach { featureId ->
            repository.updateFeatureStyle(featureId) { it.copy(accent = look.accents[featureId]) }
        }

        ImageStore.delete(appContext, oldBackground)
    }

    /** Deletes a favorite and its photo copy. */
    fun delete(id: String) {
        val look = _items.value.firstOrNull { it.id == id } ?: return
        ImageStore.delete(appContext, look.theme.backgroundImagePath)
        persist(_items.value.filterNot { it.id == id })
    }

    // ---------- storage ----------

    private fun persist(list: List<FavoriteLook>) {
        val array = JSONArray()
        list.forEach { array.put(toJson(it)) }
        prefs.edit().putString(KEY_ITEMS, array.toString()).apply()
        _items.value = list
    }

    private fun load(): List<FavoriteLook> {
        return runCatching {
            val array = JSONArray(prefs.getString(KEY_ITEMS, "[]"))
            List(array.length()) { fromJson(array.getJSONObject(it)) }
        }.getOrDefault(emptyList())
    }

    private fun copyInto(sourcePath: String, folder: String, fileName: String): String? {
        return runCatching {
            val source = File(sourcePath)
            if (!source.exists()) {
                null
            } else {
                val dir = File(appContext.filesDir, folder).apply { mkdirs() }
                val target = File(dir, fileName)
                source.copyTo(target, overwrite = true)
                target.absolutePath
            }
        }.getOrNull()
    }

    private fun toJson(look: FavoriteLook): JSONObject {
        val accents = JSONObject()
        look.accents.forEach { (featureId, color) -> accents.put(featureId, color) }

        return JSONObject()
            .put("id", look.id)
            .put("name", look.name)
            .put("background", look.theme.background)
            .put("surface", look.theme.surface)
            .put("primary", look.theme.primary)
            .put("text", look.theme.onBackground)
            .put("image", look.theme.backgroundImagePath ?: JSONObject.NULL)
            .put("scale", look.theme.circleScale.toDouble())
            .put("glowStrength", look.theme.glowStrength.toDouble())
            .put("glowSize", look.theme.glowSize.toDouble())
            .put("glowColor", look.theme.glowColor ?: JSONObject.NULL)
            .put("lineStyle", look.theme.lineStyle.name)
            .put("lineColor", look.theme.lineColor ?: JSONObject.NULL)
            .put("lineWidth", look.theme.lineWidth.toDouble())
            .put("lineAlpha", look.theme.lineAlpha.toDouble())
            .put("accents", accents)
    }

    private fun fromJson(o: JSONObject): FavoriteLook {
        val accents = mutableMapOf<String, Int>()
        o.optJSONObject("accents")?.let { json ->
            json.keys().forEach { key -> accents[key] = json.getInt(key) }
        }

        val defaults = ThemeSettings()

        return FavoriteLook(
            id = o.getString("id"),
            name = o.getString("name"),
            theme = ThemeSettings(
                background = o.getInt("background"),
                surface = o.getInt("surface"),
                primary = o.getInt("primary"),
                onBackground = o.getInt("text"),
                backgroundImagePath = if (o.isNull("image")) null else o.getString("image"),
                circleScale = o.optDouble("scale", 1.0).toFloat(),
                // Favorites saved before glow and lines existed use the defaults.
                glowStrength = o.optDouble("glowStrength", defaults.glowStrength.toDouble()).toFloat(),
                glowSize = o.optDouble("glowSize", defaults.glowSize.toDouble()).toFloat(),
                glowColor = if (o.isNull("glowColor")) null else o.getInt("glowColor"),
                lineStyle = runCatching {
                    LineStyle.valueOf(o.optString("lineStyle", defaults.lineStyle.name))
                }.getOrDefault(defaults.lineStyle),
                lineColor = if (o.isNull("lineColor")) null else o.getInt("lineColor"),
                lineWidth = o.optDouble("lineWidth", defaults.lineWidth.toDouble()).toFloat(),
                lineAlpha = o.optDouble("lineAlpha", defaults.lineAlpha.toDouble()).toFloat()
            ),
            accents = accents
        )
    }

    private companion object {
        const val FILE_NAME = "super_app_favorites"
        const val KEY_ITEMS = "items"
    }
}
