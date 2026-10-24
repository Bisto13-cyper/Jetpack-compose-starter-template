package com.superapp.app.features.files

import com.superapp.app.features.files.model.FileItem

/** Where the user was in Files: which source and which folder path. */
class FilesSnapshot(val sourceId: String, val stack: List<FileItem>)

/**
 * Remembers the last Files location in memory, so leaving Files (to the Files Viewer or any
 * other node) and coming back does not reset to Roots. Not saved to disk on purpose.
 */
object FilesLocationMemory {
    @Volatile private var last: FilesSnapshot? = null

    fun save(c: FilesController) { last = c.snapshot() }

    fun restoreInto(c: FilesController) { last?.let { c.restore(it) } }
}
