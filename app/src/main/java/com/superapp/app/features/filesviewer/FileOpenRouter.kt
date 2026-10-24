package com.superapp.app.features.filesviewer

import com.superapp.app.features.files.model.FileItem
import com.superapp.app.features.files.source.FileSource

/**
 * The bridge between Files and the Files Viewer.
 *
 *  Files  -> open(item, source) -> hands the file over and navigates to the viewer node.
 *  Viewer -> take()             -> picks the file up once when its screen starts.
 *
 * MainActivity connects [navigate] (goes through the normal route entry, so Node Lock
 * still applies) and [goBack] (direct, used to return to the node you came from).
 * If the viewer is opened without a file (circle / side menu), take() is null and the
 * viewer shows the file picker instead.
 */
object FileOpenRouter {
    const val ROUTE = "filesviewer"
    private const val TTL_MS = 60_000L

    class Request(
        val item: FileItem,
        val source: FileSource,
        /** true = came from a tap in Files, so Back returns to Files. */
        val fromFiles: Boolean,
        internal val createdAt: Long = System.currentTimeMillis(),
    )

    @Volatile private var pending: Request? = null

    /** Set by MainActivity. */
    @Volatile var navigate: ((String) -> Unit)? = null
    @Volatile var goBack: ((String) -> Unit)? = null

    /** @return true if the file was handed to the viewer. */
    fun open(item: FileItem, source: FileSource): Boolean {
        val nav = navigate ?: return false
        pending = Request(item, source, fromFiles = true)
        nav(ROUTE)
        return true
    }

    /** One-shot. A request that was never picked up (e.g. Node Lock refused) expires. */
    fun take(): Request? {
        val r = pending
        pending = null
        return r?.takeIf { System.currentTimeMillis() - it.createdAt < TTL_MS }
    }
}
