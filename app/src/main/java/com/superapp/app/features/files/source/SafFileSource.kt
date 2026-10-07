package com.superapp.app.features.files.source

import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.provider.DocumentsContract
import com.superapp.app.features.files.model.FileItem
import com.superapp.app.features.files.model.FilesException
import com.superapp.app.features.files.model.SourceKind
import com.superapp.app.features.files.model.mimeFor
import java.io.InputStream
import java.io.OutputStream

/**
 * A folder tree the user granted through the Storage Access Framework (ACTION_OPEN_DOCUMENT_TREE).
 * Works without any storage permission and respects scoped storage. Uses DocumentsContract directly (no extra library).
 */
class SafFileSource(context: Context, val treeUri: Uri) : FileSource {
    private val resolver = context.applicationContext.contentResolver
    override val id = "saf:$treeUri"
    override val kind = SourceKind.SAF
    // Cheap (no I/O): safe to read on the main thread.
    override val label: String = "Folder: " + DocumentsContract.getTreeDocumentId(treeUri)
        .substringAfterLast(':').substringAfterLast('/').ifEmpty { "storage" }

    private val cols = arrayOf(
        DocumentsContract.Document.COLUMN_DOCUMENT_ID,
        DocumentsContract.Document.COLUMN_DISPLAY_NAME,
        DocumentsContract.Document.COLUMN_MIME_TYPE,
        DocumentsContract.Document.COLUMN_SIZE,
        DocumentsContract.Document.COLUMN_LAST_MODIFIED,
    )

    private fun docUri(docId: String): Uri = DocumentsContract.buildDocumentUriUsingTree(treeUri, docId)

    private fun fromCursor(c: Cursor, parentId: String?): FileItem {
        val docId = c.getString(0)
        val name = c.getString(1) ?: "?"
        val mime = c.getString(2)
        val dir = mime == DocumentsContract.Document.MIME_TYPE_DIR
        return FileItem(
            id = docUri(docId).toString(),
            name = name,
            isDirectory = dir,
            size = if (c.isNull(3)) -1L else c.getLong(3),
            modified = if (c.isNull(4)) 0L else c.getLong(4),
            mime = if (dir) null else (mime?.takeIf { it != "application/octet-stream" } ?: mimeFor(name)),
            sourceId = id,
            parentId = parentId,
        )
    }

    override fun roots(): List<FileItem> {
        val rootId = DocumentsContract.getTreeDocumentId(treeUri)
        return listOfNotNull(stat(docUri(rootId).toString()))
    }

    override fun stat(id: String): FileItem? = try {
        resolver.query(Uri.parse(id), cols, null, null, null)?.use {
            if (it.moveToFirst()) fromCursor(it, null) else null
        }
    } catch (_: Exception) { null }

    override fun list(dir: FileItem): List<FileItem> {
        val parentDocId = DocumentsContract.getDocumentId(Uri.parse(dir.id))
        val children = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, parentDocId)
        val out = mutableListOf<FileItem>()
        try {
            resolver.query(children, cols, null, null, null)?.use { c ->
                while (c.moveToNext()) out += fromCursor(c, dir.id)
            } ?: throw FilesException("Cannot read this folder.")
        } catch (e: SecurityException) {
            throw FilesException("Access to this folder was revoked. Add it again.", e)
        }
        return out
    }

    override fun createFolder(parent: FileItem, name: String): FileItem {
        validateName(name)
        if (child(parent, name) != null) throw FilesException("\"$name\" already exists.")
        val uri = DocumentsContract.createDocument(resolver, Uri.parse(parent.id), DocumentsContract.Document.MIME_TYPE_DIR, name)
            ?: throw FilesException("Could not create the folder.")
        return stat(uri.toString()) ?: throw FilesException("Could not create the folder.")
    }

    override fun createFile(parent: FileItem, name: String): FileItem {
        validateName(name)
        if (child(parent, name) != null) throw FilesException("\"$name\" already exists.")
        val uri = DocumentsContract.createDocument(resolver, Uri.parse(parent.id), mimeFor(name) ?: "application/octet-stream", name)
            ?: throw FilesException("Could not create the file.")
        return stat(uri.toString()) ?: throw FilesException("Could not create the file.")
    }

    override fun rename(item: FileItem, newName: String): FileItem {
        validateName(newName)
        val uri = DocumentsContract.renameDocument(resolver, Uri.parse(item.id), newName)
            ?: throw FilesException("Could not rename.")
        return stat(uri.toString()) ?: item.copy(name = newName, id = uri.toString())
    }

    override fun deleteOne(item: FileItem) {
        if (!DocumentsContract.deleteDocument(resolver, Uri.parse(item.id))) {
            throw FilesException("Could not delete \"${item.name}\".")
        }
    }

    override fun openInput(item: FileItem): InputStream =
        resolver.openInputStream(Uri.parse(item.id)) ?: throw FilesException("Cannot open \"${item.name}\".")

    override fun openOutput(parent: FileItem, name: String): OutputStream {
        validateName(name)
        val existing = child(parent, name)
        val uri = existing?.id?.let { Uri.parse(it) }
            ?: DocumentsContract.createDocument(resolver, Uri.parse(parent.id), mimeFor(name) ?: "application/octet-stream", name)
            ?: throw FilesException("Could not create \"$name\".")
        return resolver.openOutputStream(uri, "wt") ?: throw FilesException("Cannot write \"$name\".")
    }

    override fun moveWithin(item: FileItem, newParent: FileItem): Boolean {
        val parent = item.parentId ?: return false
        return try {
            DocumentsContract.moveDocument(resolver, Uri.parse(item.id), Uri.parse(parent), Uri.parse(newParent.id)) != null
        } catch (_: Exception) { false }
    }
}

