package com.superapp.app.features.filesviewer

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Divider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.superapp.app.features.files.FilesScreen
import com.superapp.app.features.files.model.formatSize
import com.superapp.app.features.files.model.userMessage
import com.superapp.app.features.filesviewer.viewers.CsvViewer
import com.superapp.app.features.filesviewer.viewers.EnvViewer
import com.superapp.app.features.filesviewer.viewers.JsonViewer
import com.superapp.app.features.filesviewer.viewers.ShViewer
import com.superapp.app.features.filesviewer.viewers.TextViewer
import com.superapp.app.features.filesviewer.viewers.TomlViewer
import com.superapp.app.features.filesviewer.viewers.XmlViewer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream

/** Big files are cut here: laying out megabytes of text would freeze the screen. */
private const val MAX_BYTES = 512 * 1024

private sealed interface LoadState {
    object Loading : LoadState
    object Binary : LoadState
    class Ready(val text: String, val truncated: Boolean) : LoadState
    class Failed(val message: String) : LoadState
}

/**
 * The whole Files Viewer node.
 *
 *  no file yet -> the Files screen in "pick" mode (a tap on a file chooses it)
 *  file chosen -> the matching viewer with a small top bar
 *
 * Back: from a file opened via Files -> returns to Files (same folder);
 *       from a file chosen here -> returns to the picker; picker back -> Home.
 */
@Composable
fun FilesViewerScreen(onClose: () -> Unit) {
    var current by remember { mutableStateOf(FileOpenRouter.take()) }
    val req = current

    if (req == null) {
        FilesScreen(
            onClose = onClose,
            pickMode = true,
            onPick = { item, src ->
                current = FileOpenRouter.Request(item, src, fromFiles = false)
            },
        )
    } else {
        FileContent(
            req = req,
            onBack = {
                if (req.fromFiles) {
                    val back = FileOpenRouter.goBack
                    if (back != null) back("files") else onClose()
                } else {
                    current = null
                }
            },
            onPickAnother = { current = null },
        )
    }
}

@Composable
private fun FileContent(
    req: FileOpenRouter.Request,
    onBack: () -> Unit,
    onPickAnother: () -> Unit,
) {
    val item = req.item
    val kind = remember(item.name) { FileKinds.kindOf(item.name) ?: ViewerKind.TEXT }
    var state by remember(req) { mutableStateOf<LoadState>(LoadState.Loading) }
    var raw by remember(req) { mutableStateOf(false) }
    val clipboard = LocalClipboardManager.current

    BackHandler(onBack = onBack)

    LaunchedEffect(req) {
        state = withContext(Dispatchers.IO) { readFile(req) }
    }

    val ready = state as? LoadState.Ready

    Surface(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(onClick = onBack) { Text("←") }
                Column(Modifier.weight(1f)) {
                    Text(
                        item.name,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        "${kind.label} · ${formatSize(item.size)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                    )
                }
                if (ready != null && kind != ViewerKind.TEXT) {
                    TextButton(onClick = { raw = !raw }) { Text(if (raw) "Pretty" else "Raw") }
                }
                if (ready != null) {
                    TextButton(onClick = { clipboard.setText(AnnotatedString(ready.text)) }) { Text("Copy") }
                }
                TextButton(onClick = onPickAnother) { Text("📂") }
            }
            Divider()

            when (val s = state) {
                LoadState.Loading -> Centered { CircularProgressIndicator() }
                LoadState.Binary -> Centered {
                    Text("This looks like a binary file, so it can't be shown as text.", modifier = Modifier.padding(24.dp))
                }
                is LoadState.Failed -> Centered {
                    Text(s.message, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(24.dp))
                }
                is LoadState.Ready -> Column(Modifier.fillMaxSize()) {
                    if (s.truncated) {
                        Text(
                            "Showing only the first ${MAX_BYTES / 1024} KB of this file.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.tertiary,
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
                        )
                    }
                    Box(Modifier.weight(1f).fillMaxWidth()) {
                        if (s.text.isBlank()) {
                            Text("Empty file.", modifier = Modifier.padding(16.dp))
                        } else {
                            ShowViewer(kind, s.text, raw, s.truncated)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ShowViewer(kind: ViewerKind, text: String, raw: Boolean, truncated: Boolean) {
    // A cut-off JSON/XML file can never parse, so show it as plain text instead of "Invalid JSON".
    val cutStructured = truncated && (kind == ViewerKind.JSON || kind == ViewerKind.XML)
    if (raw || cutStructured) {
        TextViewer(text)
        return
    }
    when (kind) {
        ViewerKind.JSON -> JsonViewer(text)
        ViewerKind.XML -> XmlViewer(text)
        ViewerKind.TOML -> TomlViewer(text)
        ViewerKind.SHELL -> ShViewer(text)
        ViewerKind.ENV -> EnvViewer(text)
        ViewerKind.CSV -> CsvViewer(text = text)
        ViewerKind.TSV -> CsvViewer(text = text, delimiter = '\t')
        ViewerKind.TEXT -> TextViewer(text)
    }
}

@Composable
private fun Centered(content: @Composable () -> Unit) {
    Column(
        Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) { content() }
}

/** Blocking: call on Dispatchers.IO. Works for local, SAF and SSH files alike. */
private fun readFile(req: FileOpenRouter.Request): LoadState = try {
    req.source.openInput(req.item).use { input ->
        val out = ByteArrayOutputStream()
        val buf = ByteArray(64 * 1024)
        var truncated = false
        while (true) {
            val n = input.read(buf)
            if (n < 0) break
            val room = MAX_BYTES - out.size()
            if (n > room) {
                out.write(buf, 0, room)
                truncated = true
                break
            }
            out.write(buf, 0, n)
        }
        val bytes = out.toByteArray()
        var binary = false
        for (i in 0 until minOf(bytes.size, 8192)) {
            if (bytes[i] == 0.toByte()) { binary = true; break }
        }
        if (binary) LoadState.Binary
        else LoadState.Ready(String(bytes, Charsets.UTF_8).removePrefix("\uFEFF"), truncated)
    }
} catch (e: Throwable) {
    LoadState.Failed(e.userMessage())
}
