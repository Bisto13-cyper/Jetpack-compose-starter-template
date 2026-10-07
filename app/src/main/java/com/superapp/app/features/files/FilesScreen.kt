package com.superapp.app.features.files

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import com.superapp.app.features.files.archive.ArchiveEngine
import com.superapp.app.features.files.model.ConflictPolicy
import com.superapp.app.features.files.model.FileItem
import com.superapp.app.features.files.model.HostCheck
import com.superapp.app.features.files.model.SourceKind
import com.superapp.app.features.files.model.formatDate
import com.superapp.app.features.files.model.formatSize
import com.superapp.app.features.files.source.LocalFileSource
import com.superapp.app.features.files.ssh.SshProfile
import com.superapp.app.features.files.store.FileIconStore
import com.superapp.app.features.files.store.SortBy
import com.superapp.app.features.files.store.ViewMode
import kotlinx.coroutines.delay

internal sealed interface FilesDialog {
    object NewFolder : FilesDialog
    object NewFile : FilesDialog
    class Rename(val item: FileItem) : FilesDialog
    object ConfirmDelete : FilesDialog
    object Conflict : FilesDialog
    object Compress : FilesDialog
    class Details(val item: FileItem) : FilesDialog
    object Display : FilesDialog
    object Icons : FilesDialog
    object SshList : FilesDialog
    class SshEdit(val profile: SshProfile?) : FilesDialog
}

/** Entry point of the Files feature. Host it from navigation (see integration notes). */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun FilesScreen(onClose: () -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val c = remember { FilesController(context) }
    var dialog by remember { mutableStateOf<FilesDialog?>(null) }
    var menu by remember { mutableStateOf(false) }
    var sourceMenu by remember { mutableStateOf(false) }

    DisposableEffect(c) { onDispose { c.dispose() } }
    DisposableEffect(context) {
        val owner = context.findActivity() as? LifecycleOwner
        val obs = LifecycleEventObserver { _, e -> if (e == Lifecycle.Event.ON_RESUME) c.onResume() }
        owner?.lifecycle?.addObserver(obs)
        onDispose { owner?.lifecycle?.removeObserver(obs) }
    }
    BackHandler { if (!c.back()) onClose() }

    val treeLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) c.addSafTree(uri)
    }
    val uploadLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        if (uris.isNotEmpty()) c.importUris(uris)
    }
    val permLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { c.onResume() }

    val shape = RoundedCornerShape(c.prefs.cornerDp.value.dp)

    Surface(modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            // ---- top bar
            Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = { if (!c.back()) onClose() }) { Text("←") }
                Text("Files", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                TextButton(onClick = { if (c.searchOpen) c.closeSearch() else c.openSearch() }) { Text("🔍") }
                Box {
                    TextButton(onClick = { menu = true }) { Text("⋮") }
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        @Composable fun item(t: String, a: () -> Unit) = DropdownMenuItem(text = { Text(t) }, onClick = { menu = false; a() })
                        item("New folder") { dialog = FilesDialog.NewFolder }
                        item("New file") { dialog = FilesDialog.NewFile }
                        item(if (c.source.kind == SourceKind.SSH) "Upload files here…" else "Add files here…") {
                            uploadLauncher.launch(arrayOf("*/*"))
                        }
                        item("Select all") { c.selectAll() }
                        item("View: List") { c.prefs.viewMode.set(ViewMode.LIST) }
                        item("View: Grid") { c.prefs.viewMode.set(ViewMode.GRID) }
                        item("View: Compact") { c.prefs.viewMode.set(ViewMode.COMPACT) }
                        item("Sort by: ${c.prefs.sortBy.value.name.lowercase()} (tap to change)") {
                            val all = SortBy.values()
                            c.prefs.sortBy.set(all[(c.prefs.sortBy.value.ordinal + 1) % all.size]); c.resort()
                        }
                        item(if (c.prefs.sortAsc.value) "Order: ascending" else "Order: descending") {
                            c.prefs.sortAsc.set(!c.prefs.sortAsc.value); c.resort()
                        }
                        item("Display options…") { dialog = FilesDialog.Display }
                        item("File type icons…") { dialog = FilesDialog.Icons }
                        item("SSH connections…") { dialog = FilesDialog.SshList }
                        if (c.source.kind == SourceKind.SSH) item("Disconnect SSH") {
                            (c.source as? com.superapp.app.features.files.source.SshFileSource)?.let { c.ssh.disconnect(it.profile.id) }
                            c.openSource(c.sources().first())
                        }
                    }
                }
            }

            // ---- source banner: LOCAL vs TERMUX/SSH must always be obvious
            val isSsh = c.source.kind == SourceKind.SSH
            Surface(color = if (isSsh) MaterialTheme.colorScheme.tertiaryContainer else MaterialTheme.colorScheme.primaryContainer) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            when (c.source.kind) { SourceKind.LOCAL -> "LOCAL FILES"; SourceKind.SAF -> "LOCAL FOLDER (SAF)"; SourceKind.SSH -> "TERMUX / SSH FILES (remote)" },
                            fontWeight = FontWeight.Bold, fontSize = 13.sp
                        )
                        Text(c.source.label, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    Box {
                        TextButton(onClick = { sourceMenu = true }) { Text("Source ▾") }
                        DropdownMenu(expanded = sourceMenu, onDismissRequest = { sourceMenu = false }) {
                            for (s in c.sources()) DropdownMenuItem(text = { Text(s.label) }, onClick = { sourceMenu = false; c.openSource(s) })
                            DropdownMenuItem(text = { Text("+ Add folder (SAF)…") }, onClick = { sourceMenu = false; treeLauncher.launch(null) })
                            DropdownMenuItem(text = { Text("+ New SSH / Termux connection…") }, onClick = { sourceMenu = false; dialog = FilesDialog.SshEdit(null) })
                        }
                    }
                }
            }

            if (c.searchOpen) {
                SearchPanel(c, shape)
            } else {
                Row {
                    TextButton(onClick = { c.tab = FilesTab.BROWSE }) { Text(if (c.tab == FilesTab.BROWSE) "[Browse]" else "Browse") }
                    TextButton(onClick = { c.tab = FilesTab.FAVORITES }) { Text(if (c.tab == FilesTab.FAVORITES) "[Favorites]" else "Favorites") }
                }
                if (c.tab == FilesTab.FAVORITES) FavoritesPanel(c, shape) else BrowsePanel(c, shape, onNeedPermission = {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                        context.startActivity(Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION, Uri.parse("package:${context.packageName}")))
                    } else permLauncher.launch(arrayOf(android.Manifest.permission.READ_EXTERNAL_STORAGE, android.Manifest.permission.WRITE_EXTERNAL_STORAGE))
                }, onDialog = { dialog = it })
            }
        }
    }

    // ---- progress overlay (cancellable)
    c.progress?.let { p ->
        AlertDialog(
            onDismissRequest = {},
            title = { Text(p.label) },
            text = {
                Column {
                    if (p.total > 0) LinearProgressIndicator(progress = (p.done.toFloat() / p.total).coerceIn(0f, 1f), modifier = Modifier.fillMaxWidth())
                    else LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    Spacer(Modifier.height(6.dp))
                    Text(if (p.total > 0) "${formatSize(p.done)} / ${formatSize(p.total)}" else formatSize(p.done), fontSize = 12.sp)
                }
            },
            confirmButton = { TextButton(onClick = { c.cancelOperation() }) { Text("Cancel") } },
        )
    }

    FilesDialogs(c, dialog, onDismiss = { dialog = null }, onOpen = { dialog = it })

    c.hostPrompt?.let { HostPromptDialog(it, onTrust = { c.trustHostAndRetry() }, onCancel = { c.hostPrompt = null }) }

    c.message?.let {
        AlertDialog(onDismissRequest = { c.message = null }, text = { Text(it) },
            confirmButton = { TextButton(onClick = { c.message = null }) { Text("OK") } })
    }
}

// =================================================================== browse

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ColumnScope.BrowsePanel(
    c: FilesController, shape: RoundedCornerShape, onNeedPermission: () -> Unit, onDialog: (FilesDialog) -> Unit,
) {
    val mode = c.prefs.viewMode.value
    val gap = c.prefs.spacingDp.value.dp
    LaunchedEffect(c.source) { if (c.items.isEmpty() && !c.loading && c.error == null) c.reload() }

    // breadcrumbs
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Text("Roots", modifier = Modifier.clickable { c.goRoots() }.padding(6.dp), color = MaterialTheme.colorScheme.primary)
        c.stack.forEachIndexed { i, it ->
            Text("›")
            Text(it.name, modifier = Modifier.clickable { c.goTo(i) }.padding(6.dp), color = MaterialTheme.colorScheme.primary)
        }
    }

    if (c.source.kind == SourceKind.LOCAL && c.stack.isEmpty() && !c.sharedAccess) {
        Card(Modifier.fillMaxWidth().padding(8.dp)) {
            Column(Modifier.padding(12.dp)) {
                Text("Shared storage is not accessible yet.", fontWeight = FontWeight.Bold)
                Text("Grant \"All files access\" to browse the whole device, or add a folder (Source ▾ → Add folder). App folders work without any permission.", fontSize = 13.sp)
                Button(onClick = onNeedPermission) { Text("Grant access") }
            }
        }
    }
    c.error?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(12.dp)) }
    if (c.loading) LinearProgressIndicator(modifier = Modifier.fillMaxWidth())

    Box(Modifier.weight(1f)) {
        LazyVerticalGrid(
            columns = if (mode == ViewMode.GRID) GridCells.Adaptive(112.dp) else GridCells.Fixed(1),
            modifier = Modifier.fillMaxSize(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(8.dp),
            verticalArrangement = Arrangement.spacedBy(gap),
            horizontalArrangement = Arrangement.spacedBy(gap),
        ) {
            if (c.stack.isEmpty()) {
                val rec = c.recents.recent(c.source.id)
                val freq = c.recents.frequent(c.source.id)
                if (rec.isNotEmpty() || freq.isNotEmpty()) item(span = { GridItemSpan(maxLineSpan) }) {
                    Column {
                        if (rec.isNotEmpty()) {
                            Text("Recent folders", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            Row(Modifier.horizontalScroll(rememberScrollState())) {
                                rec.forEach { TextButton(onClick = { c.openRecent(it.sourceId, it.id, it.name) }) { Text("🕘 ${it.name}") } }
                            }
                        }
                        if (freq.isNotEmpty()) {
                            Text("Frequently used", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            Row(Modifier.horizontalScroll(rememberScrollState())) {
                                freq.forEach { TextButton(onClick = { c.openRecent(it.sourceId, it.id, it.name) }) { Text("⭐ ${it.name}") } }
                            }
                        }
                    }
                }
            }
            if (!c.loading && c.items.isEmpty() && c.error == null) item(span = { GridItemSpan(maxLineSpan) }) {
                Text("Empty.", modifier = Modifier.padding(16.dp))
            }
            items(c.items, key = { it.id }) { f ->
                val selected = f.id in c.selection
                val container = if (selected) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceVariant
                Card(
                    shape = shape,
                    colors = CardDefaults.cardColors(containerColor = container),
                    modifier = Modifier.fillMaxWidth().combinedClickable(
                        onClick = {
                            if (c.selection.isNotEmpty()) c.toggleSelect(f.id)
                            else if (f.isDirectory) c.openDir(f) else c.open(f)
                        },
                        onLongClick = { c.toggleSelect(f.id) },
                    ),
                ) {
                    val fav = c.isFavorite(f)
                    when (mode) {
                        ViewMode.LIST -> Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                            FileIcon(c, f, 40.dp)
                            Spacer(Modifier.size(10.dp))
                            Column(Modifier.weight(1f)) {
                                Text(f.name, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.Medium)
                                val sub = subtitle(c, f)
                                if (sub.isNotEmpty()) Text(sub, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                            if (fav) Text("★")
                        }
                        ViewMode.COMPACT -> Row(Modifier.padding(horizontal = 8.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                            FileIcon(c, f, 24.dp)
                            Spacer(Modifier.size(8.dp))
                            Text(f.name, Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis, fontSize = 14.sp)
                            if (c.prefs.showSize.value && !f.isDirectory) Text(formatSize(f.size), fontSize = 11.sp)
                            if (fav) Text(" ★")
                        }
                        ViewMode.GRID -> Column(Modifier.padding(8.dp).fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                            FileIcon(c, f, 56.dp)
                            Text(f.name, maxLines = 2, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center, fontSize = 12.sp)
                            if (c.prefs.showSize.value && !f.isDirectory) Text(formatSize(f.size), fontSize = 10.sp)
                            if (fav) Text("★", fontSize = 11.sp)
                        }
                    }
                }
            }
        }
    }

    // ---- bottom bars
    val sel = c.selectedItems()
    if (sel.isNotEmpty()) {
        Surface(tonalElevation = 3.dp) {
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(4.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("${sel.size} ", fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 8.dp))
                TextButton(onClick = { c.copySelection(false) }) { Text("Copy") }
                TextButton(onClick = { c.copySelection(true) }) { Text("Cut") }
                TextButton(onClick = { onDialog(FilesDialog.ConfirmDelete) }) { Text("Delete") }
                TextButton(onClick = { c.share(sel) }) { Text("Share") }
                TextButton(onClick = { onDialog(FilesDialog.Compress) }) { Text("Compress") }
                TextButton(onClick = { sel.forEach { c.toggleFavorite(it) }; c.clearSelection() }) { Text("★ Fav") }
                if (sel.size == 1) {
                    TextButton(onClick = { onDialog(FilesDialog.Rename(sel[0])) }) { Text("Rename") }
                    TextButton(onClick = { onDialog(FilesDialog.Details(sel[0])) }) { Text("Details") }
                    if (!sel[0].isDirectory && ArchiveEngine.isArchive(sel[0].name)) TextButton(onClick = { c.extract(sel[0]) }) { Text("Extract") }
                }
                TextButton(onClick = { c.clearSelection() }) { Text("✕") }
            }
        }
    } else c.clipboard?.let { cb ->
        Surface(tonalElevation = 3.dp) {
            Row(Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                val verb = when {
                    cb.source.kind != SourceKind.SSH && c.source.kind == SourceKind.SSH -> "Upload here"
                    cb.source.kind == SourceKind.SSH && c.source.kind != SourceKind.SSH -> "Download here"
                    cb.move -> "Move here" else -> "Paste here"
                }
                Column(Modifier.weight(1f)) {
                    Text("${cb.items.size} item(s) · ${if (cb.move) "cut" else "copied"}", fontSize = 13.sp)
                    Text("from ${cb.source.label}", fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Button(enabled = c.stack.isNotEmpty(), onClick = {
                    if (c.pasteHasConflicts()) onDialog(FilesDialog.Conflict) else c.paste(ConflictPolicy.KEEP_BOTH)
                }) { Text(verb) }
                TextButton(onClick = { c.clearClipboard() }) { Text("✕") }
            }
        }
    }
}

private fun subtitle(c: FilesController, f: FileItem): String {
    val p = mutableListOf<String>()
    if (c.prefs.showType.value) p += if (f.isDirectory) "Folder" else f.extension.uppercase().ifEmpty { "File" }
    if (c.prefs.showSize.value && !f.isDirectory) p += formatSize(f.size)
    if (c.prefs.showDate.value && f.modified > 0) p += formatDate(f.modified)
    return p.joinToString(" · ")
}

// =================================================================== favorites

@Composable
private fun FavoritesPanel(c: FilesController, shape: RoundedCornerShape) {
    LaunchedEffect(c.tab, c.favorites.items.size) { c.refreshFavorites() }
    if (c.favorites.items.isEmpty()) {
        Text("No favorites yet. Select files or folders and tap ★ Fav.", modifier = Modifier.padding(16.dp))
        return
    }
    val names = c.sources().associate { it.id to it.label }
    LazyColumn(contentPadding = androidx.compose.foundation.layout.PaddingValues(8.dp), verticalArrangement = Arrangement.spacedBy(c.prefs.spacingDp.value.dp)) {
        items(c.favorites.items.toList().size) { i ->
            val f = c.favorites.items[i]
            val status = c.favoriteStatus[f.sourceId + "|" + f.id]
            Card(shape = shape, modifier = Modifier.fillMaxWidth().clickable(enabled = status != false) { c.openFavorite(f) }) {
                Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(if (f.isDirectory) "📁" else "📄", fontSize = 26.sp)
                    Spacer(Modifier.size(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text(f.name, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(names[f.sourceId] ?: "Unknown source", fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        when (status) {
                            false -> Text("Unavailable (moved, deleted or storage missing)", color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
                            null -> Text("Status unknown (not connected)", fontSize = 12.sp)
                            true -> {}
                        }
                    }
                    if (status == false) TextButton(onClick = { c.favorites.remove(f) }) { Text("Remove") }
                    else TextButton(onClick = { c.favorites.remove(f) }) { Text("★") }
                }
            }
        }
    }
}

// ===================================================================== search

@Composable
private fun SearchPanel(c: FilesController, shape: RoundedCornerShape) {
    var text by remember { mutableStateOf("") }
    LaunchedEffect(text, c.searchHereOnly, c.source) { delay(250); c.runSearch(text) }
    Column(Modifier.fillMaxSize().padding(8.dp)) {
        OutlinedTextField(
            value = text, onValueChange = { text = it }, singleLine = true, modifier = Modifier.fillMaxWidth(),
            label = { Text("Search: name, .py, project .py, type:kt") },
        )
        if (c.source.kind == SourceKind.LOCAL) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(checked = c.searchHereOnly, onCheckedChange = { c.searchHereOnly = it })
                Text("Only this folder", fontSize = 13.sp, modifier = Modifier.weight(1f))
                TextButton(onClick = { c.buildIndex(force = c.indexCount() == 0L) }) {
                    Text(if (c.indexCount() == 0L) "Build index" else "Update index")
                }
            }
            Text("Index: ${c.indexCount()} items" + if (c.prefs.lastIndexed > 0) " · updated ${formatDate(c.prefs.lastIndexed)}" else "", fontSize = 11.sp)
            if (c.indexMissing) Text("The search index has not been built yet. Tap \"Build index\" (one-time scan, then searches are instant).", color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
        } else {
            Text("Searching ${if (c.stack.isEmpty()) "from the top" else "inside the current folder"} (live search, not indexed).", fontSize = 11.sp)
        }
        if (c.searchBusy) LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        LazyColumn(verticalArrangement = Arrangement.spacedBy(c.prefs.spacingDp.value.dp)) {
            items(c.searchResults.size) { i ->
                val f = c.searchResults[i]
                Card(shape = shape, modifier = Modifier.fillMaxWidth().clickable { c.openResult(f) }) {
                    Row(Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        FileIcon(c, f, 32.dp)
                        Spacer(Modifier.size(8.dp))
                        Column {
                            Text(f.name, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(f.parentId ?: "", fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            }
        }
    }
}

// ====================================================================== icons

internal fun defaultGlyph(item: FileItem): String {
    val mime = item.mime.orEmpty()
    return when {
        item.isDirectory -> "📁"
        ArchiveEngine.isArchive(item.name) -> "🗜️"
        mime.startsWith("image/") -> "🖼️"
        mime.startsWith("video/") -> "🎞️"
        mime.startsWith("audio/") -> "🎵"
        mime == "application/pdf" -> "📕"
        mime == "application/vnd.android.package-archive" -> "📦"
        mime.startsWith("text/") -> "📝"
        else -> "📄"
    }
}

/** Icon by extension: user-assigned image/emoji if present, else the default glyph for that type. */
@Composable
internal fun FileIcon(c: FilesController, item: FileItem, size: Dp) {
    val key = if (item.isDirectory) FileIconStore.FOLDER else item.extension
    val mapping = if (key.isEmpty()) null else c.icons.mappings[key]
    val bmp = remember(key, mapping) { if (mapping?.startsWith("img:") == true) c.icons.bitmap(key) else null }
    val emoji = remember(key, mapping) { if (mapping?.startsWith("emoji:") == true) mapping.removePrefix("emoji:") else null }
    val folderTint = c.prefs.folderColor.value
    Box(
        Modifier.size(size).let { if (item.isDirectory && folderTint != 0 && bmp == null) it.background(Color(folderTint).copy(alpha = 0.3f), RoundedCornerShape(8.dp)) else it },
        contentAlignment = Alignment.Center,
    ) {
        when {
            bmp != null -> Image(bmp.asImageBitmap(), contentDescription = null, modifier = Modifier.size(size))
            else -> Text(emoji ?: defaultGlyph(item), fontSize = (size.value * 0.62f).sp)
        }
    }
}

internal tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

@Composable
internal fun HostPromptDialog(check: HostCheck, onTrust: () -> Unit, onCancel: () -> Unit) {
    when (check) {
        is HostCheck.Unknown -> AlertDialog(
            onDismissRequest = onCancel,
            title = { Text("Trust this SSH server?") },
            text = {
                Column {
                    Text("First connection to ${check.host}.")
                    Text("Key type: ${check.keyType}", fontSize = 13.sp)
                    Text("Fingerprint:\n${check.fingerprint}", fontSize = 13.sp)
                    Spacer(Modifier.height(8.dp))
                    Text("Compare it with the server (in Termux: ssh-keygen -lf \$PREFIX/etc/ssh/ssh_host_ecdsa_key.pub). Only trust it if it matches.", fontSize = 12.sp)
                }
            },
            confirmButton = { TextButton(onClick = onTrust) { Text("Trust and connect") } },
            dismissButton = { TextButton(onClick = onCancel) { Text("Cancel") } },
        )
        is HostCheck.Changed -> AlertDialog(
            onDismissRequest = onCancel,
            title = { Text("⚠ Host key CHANGED", color = MaterialTheme.colorScheme.error) },
            text = {
                Column {
                    Text("The key of ${check.host} is different from the one you trusted. This can mean the server was reinstalled (new Termux install), or someone is intercepting the connection.")
                    Spacer(Modifier.height(8.dp))
                    Text("Trusted:\n${check.knownFingerprint}", fontSize = 12.sp)
                    Text("Now presented:\n${check.fingerprint}", fontSize = 12.sp)
                }
            },
            confirmButton = { TextButton(onClick = onCancel) { Text("Cancel (recommended)") } },
            dismissButton = { TextButton(onClick = onTrust) { Text("I reinstalled it: replace key", color = MaterialTheme.colorScheme.error) } },
        )
    }
}

