package com.superapp.app.features.editor.data

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * Persists:
 *   - The workspace tree URI (with takePersistableUriPermission).
 *   - The list of open file URIs (for tab restore).
 *   - Active tab index.
 * Used by EditorRoot to restore state across app restarts.
 */
object WorkspaceStore {
    private const val PREFS = "editor_workspace"
    private const val K_TREE = "tree_uri"
    private const val K_TABS = "open_tabs"
    private const val K_ACTIVE = "active_tab"

    private var prefs: SharedPreferences? = null

    var workspaceUri: Uri? by mutableStateOf(null)
        private set

    fun init(context: Context) {
        if (prefs != null) return
        prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val saved = prefs!!.getString(K_TREE, null)?.let(Uri::parse)
        if (saved != null) {
            try {
                context.contentResolver.takePersistableUriPermission(
                    saved,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                )
                workspaceUri = saved
            } catch (_: SecurityException) {
                prefs!!.edit().remove(K_TREE).apply()
            }
        }
    }

    fun setWorkspace(context: Context, uri: Uri) {
        try {
            context.contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            )
        } catch (_: SecurityException) { /* permission not persistable — still use it this session */ }
        workspaceUri = uri
        prefs?.edit()?.putString(K_TREE, uri.toString())?.apply()
    }

    fun clearWorkspace() {
        workspaceUri = null
        prefs?.edit()?.remove(K_TREE)?.apply()
    }

    fun saveOpenTabs(uris: List<Uri>, activeIndex: Int) {
        val joined = uris.joinToString("\n") { it.toString() }
        prefs?.edit()?.putString(K_TABS, joined)?.putInt(K_ACTIVE, activeIndex)?.apply()
    }

    fun loadOpenTabs(): Pair<List<Uri>, Int> {
        val p = prefs ?: return emptyList<Uri>() to 0
        val raw = p.getString(K_TABS, null) ?: return emptyList<Uri>() to 0
        val list = raw.split("\n").filter { it.isNotBlank() }.map(Uri::parse)
        return list to p.getInt(K_ACTIVE, 0)
    }
}
