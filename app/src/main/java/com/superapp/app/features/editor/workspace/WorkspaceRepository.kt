package com.superapp.app.features.editor.workspace

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import com.superapp.app.features.editor.model.OpenDocument
import com.superapp.app.features.editor.model.WorkspaceNode
import com.superapp.app.features.editor.syntax.LanguageRegistry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object WorkspaceRepository {

    fun listChildren(context: Context, uri: Uri): List<WorkspaceNode> {
        val dir = DocumentFile.fromTreeUri(context, uri)
            ?: DocumentFile.fromSingleUri(context, uri)
            ?: return emptyList()
        return dir.listFiles()
            .filter { it.name != null }
            .sortedWith(compareBy({ !it.isDirectory }, { it.name!!.lowercase() }))
            .map { f ->
                WorkspaceNode(
                    uri = f.uri,
                    name = f.name ?: "?",
                    isDirectory = f.isDirectory,
                    children = if (f.isDirectory) null else emptyList(),
                    lastModified = f.lastModified(),
                    size = f.length()
                )
            }
    }

    fun loadNode(context: Context, uri: Uri, name: String, isDir: Boolean): WorkspaceNode {
        return WorkspaceNode(
            uri = uri,
            name = name,
            isDirectory = isDir,
            children = if (isDir) listChildren(context, uri) else emptyList()
        )
    }

    suspend fun readDocument(
        context: Context,
        uri: Uri,
        name: String,
        largeFileLimit: Int
    ): OpenDocument = withContext(Dispatchers.IO) {
        val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
            ?: return@withContext OpenDocument(uri, name, "plain", "", "", readOnly = true)
        val truncated = bytes.size > largeFileLimit
        val contentBytes = if (truncated) bytes.copyOf(largeFileLimit) else bytes
        val text = Encoding.decode(contentBytes)
        val lang = LanguageRegistry.forFile(name)?.id ?: "plain"
        OpenDocument(
            uri = uri,
            name = name,
            languageId = lang,
            content = text,
            savedContent = text,
            truncated = truncated
        )
    }

    suspend fun writeDocument(context: Context, uri: Uri, content: String): Boolean =
        withContext(Dispatchers.IO) {
            runCatching {
                context.contentResolver.openOutputStream(uri, "wt")?.use { out ->
                    out.write(content.toByteArray(Charsets.UTF_8))
                } != null
            }.getOrDefault(false)
        }

    fun createFile(context: Context, parentUri: Uri, name: String, mime: String = "text/plain"): Uri? {
        val parent = DocumentFile.fromTreeUri(context, parentUri)
            ?: DocumentFile.fromSingleUri(context, parentUri)
            ?: return null
        return parent.createFile(mime, name)?.uri
    }

    fun createDirectory(context: Context, parentUri: Uri, name: String): Uri? {
        val parent = DocumentFile.fromTreeUri(context, parentUri)
            ?: DocumentFile.fromSingleUri(context, parentUri)
            ?: return null
        return parent.createDirectory(name)?.uri
    }

    fun delete(context: Context, uri: Uri): Boolean {
        val f = DocumentFile.fromSingleUri(context, uri) ?: return false
        return f.delete()
    }

    fun rename(context: Context, uri: Uri, newName: String): Boolean {
        val f = DocumentFile.fromSingleUri(context, uri) ?: return false
        return f.renameTo(newName)
    }
}
