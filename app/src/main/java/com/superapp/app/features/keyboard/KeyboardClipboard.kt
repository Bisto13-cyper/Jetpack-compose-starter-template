package com.superapp.app.features.keyboard

import android.content.ClipboardManager
import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.snapshots.SnapshotStateList
import org.json.JSONArray
import org.json.JSONObject

data class PinnedClip(
    val id: String,
    val text: String,
    val createdAt: Long
)

/**
 * Clipboard handling for the keyboard.
 *
 * Android only exposes the *current* clip to apps. There is no public API
 * that returns clipboard history. We therefore:
 *   1. Show the currently-exposed clip (and clips observed while this
 *      keyboard was open) in "Recent".
 *   2. Provide a separate "Pinned" list that is fully persisted inside the
 *      app, so pinned items survive clipboard clears and reboots.
 */
object KeyboardClipboard {

    private const val PREFS = "keyboard_clipboard"
    private const val KEY_PINNED = "pinned"
    private const val MAX_RECENT = 30

    private var prefs: SharedPreferences? = null

    /** Recent clips observed while this keyboard was active (session-only). */
    val recent: SnapshotStateList<String> = mutableStateListOf()

    /** Items explicitly pinned by the user. Persisted in app storage. */
    val pinned: SnapshotStateList<PinnedClip> = mutableStateListOf()

    fun init(context: Context) {
        if (prefs != null) return
        prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        loadPinned()
    }

    /**
     * Reads the current primary clip from the system and, if it differs from
     * the most recent entry, appends it to the session recent list.
     */
    fun refreshFromSystem(context: Context) {
        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager ?: return
        if (!cm.hasPrimaryClip()) return
        val clip = cm.primaryClip ?: return
        if (clip.itemCount == 0) return
        val text = clip.getItemAt(0).coerceToText(context).toString()
        if (text.isBlank()) return
        addRecent(text)
    }

    fun addRecent(text: String) {
        if (recent.firstOrNull() == text) return
        recent.remove(text)
        recent.add(0, text)
        while (recent.size > MAX_RECENT) recent.removeAt(recent.size - 1)
    }

    fun pin(text: String) {
        if (text.isBlank()) return
        if (pinned.any { it.text == text }) return
        val now = System.currentTimeMillis()
        pinned.add(0, PinnedClip(id = "pin_$now", text = text, createdAt = now))
        savePinned()
    }

    fun unpin(id: String) {
        pinned.removeAll { it.id == id }
        savePinned()
    }

    fun clearRecent() {
        recent.clear()
    }

    private fun loadPinned() {
        val raw = prefs?.getString(KEY_PINNED, null) ?: return
        runCatching {
            val arr = JSONArray(raw)
            pinned.clear()
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                pinned.add(
                    PinnedClip(
                        id = o.getString("id"),
                        text = o.getString("text"),
                        createdAt = o.optLong("createdAt")
                    )
                )
            }
        }
    }

    private fun savePinned() {
        val arr = JSONArray()
        pinned.forEach { p ->
            arr.put(JSONObject().apply {
                put("id", p.id)
                put("text", p.text)
                put("createdAt", p.createdAt)
            })
        }
        prefs?.edit()?.putString(KEY_PINNED, arr.toString())?.apply()
    }
}
