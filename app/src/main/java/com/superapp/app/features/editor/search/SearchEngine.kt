package com.superapp.app.features.editor.search

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import com.superapp.app.features.editor.model.WorkspaceNode
import com.superapp.app.features.editor.workspace.Encoding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.regex.Pattern

data class TextMatch(val start: Int, val end: Int)

data class FileHit(
    val uri: Uri,
    val fileName: String,
    val relativePath: String,
    val lineNumber: Int,
    val lineText: String,
    val matchStart: Int,
    val matchEnd: Int
)

object SearchEngine {

    fun findInText(
        text: String,
        query: String,
        caseSensitive: Boolean,
        wholeWord: Boolean
    ): List<TextMatch> {
        if (query.isEmpty()) return emptyList()
        val flags = if (caseSensitive) 0 else Pattern.CASE_INSENSITIVE
        val pattern = if (wholeWord) {
            Pattern.compile("\\b${Pattern.quote(query)}\\b", flags)
        } else {
            Pattern.compile(Pattern.quote(query), flags)
        }
        val matcher = pattern.matcher(text)
        val results = mutableListOf<TextMatch>()
        while (matcher.find()) {
            results.add(TextMatch(matcher.start(), matcher.end()))
        }
        return results
    }

    suspend fun searchWorkspace(
        context: Context,
        root: WorkspaceNode,
        query: String,
        caseSensitive: Boolean,
        wholeWord: Boolean,
        onFileProgress: (Int) -> Unit = {}
    ): List<FileHit> = withContext(Dispatchers.IO) {
        if (query.isBlank()) return@withContext emptyList()
        val hits = mutableListOf<FileHit>()
        var scanned = 0
        fun walk(node: WorkspaceNode, path: String) {
            if (node.isDirectory) {
                val children = node.children
                    ?: DocumentFile.fromTreeUri(context, node.uri)?.listFiles()?.map {
                        WorkspaceNode(it.uri, it.name ?: "?", it.isDirectory)
                    }.orEmpty()
                children.forEach { child ->
                    walk(child, if (path.isEmpty()) child.name else "$path/${child.name}")
                }
            } else {
                scanned++
                onFileProgress(scanned)
                // skip very large / binary-looking files
                val doc = DocumentFile.fromSingleUri(context, node.uri) ?: return
                if (doc.length() > 2_000_000) return
                val name = node.name.lowercase()
                val textExt = setOf(
                    "kt", "kts", "java", "js", "ts", "jsx", "tsx", "py", "c", "h", "cpp", "hpp",
                    "html", "htm", "css", "scss", "xml", "json", "md", "txt", "gradle", "toml",
                    "yaml", "yml", "properties", "sql", "sh", "bash"
                )
                val ext = name.substringAfterLast('.', "")
                if (ext.isNotEmpty() && ext !in textExt) return

                runCatching {
                    val bytes = context.contentResolver.openInputStream(node.uri)?.use { it.readBytes() }
                        ?: return@runCatching
                    val text = Encoding.decode(bytes)
                    val matches = findInText(text, query, caseSensitive, wholeWord)
                    if (matches.isEmpty()) return@runCatching
                    val lines = text.lines()
                    for (m in matches) {
                        var pos = 0
                        var lineNo = 1
                        for (line in lines) {
                            val end = pos + line.length
                            if (m.start in pos..end) {
                                hits.add(
                                    FileHit(
                                        uri = node.uri,
                                        fileName = node.name,
                                        relativePath = path,
                                        lineNumber = lineNo,
                                        lineText = line,
                                        matchStart = m.start - pos,
                                        matchEnd = m.end - pos
                                    )
                                )
                                break
                            }
                            pos = end + 1 // +1 for newline
                            lineNo++
                        }
                    }
                }
            }
        }
        walk(root, "")
        hits
    }
}
