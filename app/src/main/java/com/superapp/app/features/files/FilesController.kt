package com.superapp.app.features.files

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.FileProvider
import com.superapp.app.features.files.archive.ArchiveEngine
import com.superapp.app.features.files.archive.ArchiveFormat
import com.superapp.app.features.files.index.LiveSearch
import com.superapp.app.features.files.index.SearchQuery
import com.superapp.app.features.files.model.ConflictPolicy
import com.superapp.app.features.files.model.FileDetails
import com.superapp.app.features.files.model.FileItem
import com.superapp.app.features.files.model.HostCheck
import com.superapp.app.features.files.model.HostVerificationException
import com.superapp.app.features.files.model.Progress
import com.superapp.app.features.files.model.SourceKind
import com.superapp.app.features.files.model.userMessage
import com.superapp.app.features.files.source.FileOps
import com.superapp.app.features.files.source.FileSource
import com.superapp.app.features.files.source.LocalFileSource
import com.superapp.app.features.files.source.OpContext
import com.superapp.app.features.files.source.OpStats
import com.superapp.app.features.files.source.SafFileSource
import com.superapp.app.features.files.source.SshFileSource
import com.superapp.app.features.files.ssh.SshProfile
import com.superapp.app.features.files.store.FavoriteRef
import com.superapp.app.features.files.store.FilesServices
import com.superapp.app.features.files.store.SortBy
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.nio.file.Files
import java.nio.file.attribute.BasicFileAttributes

class ClipboardState(val source: FileSource, val items: List<FileItem>, val move: Boolean)

enum class FilesTab { BROWSE, FAVORITES }

/**
 * All state + operations of the Files screen. Works on any FileSource, so the same UI serves
 * local storage, SAF folders and Termux/SSH. Every blocking call runs on Dispatchers.IO and is cancellable.
 */
class FilesController(private val context: Context) {
    private val appCtx = context.applicationContext
    private val svc = FilesServices.get(appCtx)
    val prefs = svc.prefs
    val icons = svc.icons
    val favorites = svc.favorites
    val recents = svc.recents
    val profiles = svc.profiles
    val ssh = svc.ssh

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val local = LocalFileSource(appCtx)
    private val safCache = HashMap<String, SafFileSource>()

    var source: FileSource by mutableStateOf(local)
        private set
    val stack = mutableStateListOf<FileItem>()
    var items by mutableStateOf<List<FileItem>>(emptyList())
        private set
    var loading by mutableStateOf(false)
        private set
    var error by mutableStateOf<String?>(null)
        private set
    var selection by mutableStateOf<Set<String>>(emptySet())
        private set
    var clipboard by mutableStateOf<ClipboardState?>(null)
        private set
    var progress by mutableStateOf<Progress?>(null)
        private set
    var message by mutableStateOf<String?>(null)
    var hostPrompt by mutableStateOf<HostCheck?>(null)
    var tab by mutableStateOf(FilesTab.BROWSE)
    var sharedAccess by mutableStateOf(LocalFileSource.hasSharedAccess(appCtx))
        private set

    // search
    var searchOpen by mutableStateOf(false)
        private set
    var searchBusy by mutableStateOf(false)
        private set
    var searchResults by mutableStateOf<List<FileItem>>(emptyList())
        private set
    var indexMissing by mutableStateOf(false)
        private set
    var searchHereOnly by mutableStateOf(false)

    val favoriteStatus = mutableStateMapOf<String, Boolean?>() // true=available false=unavailable null=unknown

    private var loadJob: Job? = null
    private var opJob: Job? = null
    private var searchJob: Job? = null

    init {
        scope.launch(Dispatchers.IO) { File(appCtx.cacheDir, "files_open").deleteRecursively() }
    }

    fun dispose() { scope.cancel() }

    // ---------------------------------------------------------------- sources

    fun sources(): List<FileSource> =
        listOf<FileSource>(local) +
            prefs.safTrees().map { safCache.getOrPut(it) { SafFileSource(appCtx, Uri.parse(it)) } } +
            profiles.list().map { SshFileSource(it, ssh) }

    fun openSource(s: FileSource) {
        source = s
        stack.clear()
        selection = emptySet()
        searchOpen = false
        tab = FilesTab.BROWSE
        load(null)
    }

    fun addSafTree(uri: Uri) {
        try {
            appCtx.contentResolver.takePersistableUriPermission(
                uri, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            )
        } catch (_: Exception) {}
        prefs.addSafTree(uri.toString())
        openSource(safCache.getOrPut(uri.toString()) { SafFileSource(appCtx, uri) })
    }

    fun removeSource(s: FileSource) {
        when (s) {
            is SafFileSource -> {
                try { appCtx.contentResolver.releasePersistableUriPermission(s.treeUri, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION) } catch (_: Exception) {}
                prefs.removeSafTree(s.treeUri.toString()); safCache.remove(s.treeUri.toString())
            }
            is SshFileSource -> { ssh.disconnect(s.profile.id); profiles.delete(s.profile.id) }
        }
        if (source.id == s.id) openSource(local)
    }

    fun connect(profile: SshProfile) = openSource(SshFileSource(profile, ssh))

    fun saveProfile(p: SshProfile, password: String?, key: String?, passphrase: String?) {
        profiles.save(p, password, key, passphrase)
        ssh.disconnect(p.id)
    }

    fun testProfile(p: SshProfile) {
        scope.launch {
            message = "Connecting…"
            try {
                val home = withContext(Dispatchers.IO) {
                    ssh.connect(p)
                    ssh.withSftp(p) { it.home }
                }
                message = "Connected. Home folder: $home"
            } catch (e: CancellationException) { throw e }
            catch (e: Throwable) { handleError(e) }
        }
    }

    fun trustHostAndRetry() {
        val check = hostPrompt ?: return
        ssh.trustHost(check)
        hostPrompt = null
        reload()
    }

    fun onResume() {
        val now = LocalFileSource.hasSharedAccess(appCtx)
        if (now != sharedAccess) { sharedAccess = now; if (source.kind == SourceKind.LOCAL && stack.isEmpty()) reload() }
    }

    // -------------------------------------------------------------- navigation

    private fun handleError(e: Throwable) {
        if (e is HostVerificationException) hostPrompt = e.check else message = e.userMessage()
    }

    fun reload() = load(stack.lastOrNull())

    private fun load(dir: FileItem?) {
        loadJob?.cancel()
        val src = source
        loading = true
        error = null
        loadJob = scope.launch {
            try {
                val raw = withContext(Dispatchers.IO) { if (dir == null) src.roots() else src.list(dir) }
                if (src === source) items = sort(raw)
            } catch (e: CancellationException) { throw e }
            catch (e: Throwable) {
                items = emptyList()
                if (e is HostVerificationException) hostPrompt = e.check else error = e.userMessage()
            } finally { loading = false }
        }
    }

    private fun sort(list: List<FileItem>): List<FileItem> {
        val asc = prefs.sortAsc.value
        val base = when (prefs.sortBy.value) {
            SortBy.NAME -> compareBy<FileItem> { it.name.lowercase() }
            SortBy.SIZE -> compareBy<FileItem> { it.size }
            SortBy.DATE -> compareBy<FileItem> { it.modified }
            SortBy.TYPE -> compareBy<FileItem>({ it.extension }, { it.name.lowercase() })
        }
        val cmp = if (asc) base else base.reversed()
        return list.filter { prefs.showHidden.value || !it.name.startsWith(".") }
            .sortedWith(compareByDescending<FileItem> { it.isDirectory }.then(cmp))
    }

    fun resort() { items = sort(items) }

    fun openDir(item: FileItem) {
        stack.add(item)
        selection = emptySet()
        recents.record(source.id, item.id, item.name)
        load(item)
    }

    fun goTo(index: Int) {
        while (stack.size > index + 1) stack.removeAt(stack.lastIndex)
        selection = emptySet()
        load(stack.lastOrNull())
    }

    fun goRoots() { stack.clear(); selection = emptySet(); load(null) }

    /** @return true if the back press was handled inside the Files screen. */
    fun back(): Boolean {
        if (searchOpen) { closeSearch(); return true }
        if (selection.isNotEmpty()) { selection = emptySet(); return true }
        if (tab == FilesTab.FAVORITES) { tab = FilesTab.BROWSE; return true }
        if (stack.isNotEmpty()) { stack.removeAt(stack.lastIndex); load(stack.lastOrNull()); return true }
        return false
    }

    fun openRecent(sourceId: String, id: String, name: String) {
        val src = sources().firstOrNull { it.id == sourceId } ?: return
        jumpTo(src, id)
    }

    private fun jumpTo(src: FileSource, id: String) {
        scope.launch {
            try {
                val item = withContext(Dispatchers.IO) { src.stat(id) }
                if (item == null) { message = "This location is no longer available."; return@launch }
                if (item.isDirectory) {
                    source = src; stack.clear(); stack.add(item); selection = emptySet()
                    tab = FilesTab.BROWSE; searchOpen = false
                    recents.record(src.id, item.id, item.name)
                    load(item)
                } else open(item, src)
            } catch (e: CancellationException) { throw e }
            catch (e: Throwable) { handleError(e) }
        }
    }

    // --------------------------------------------------------------- selection

    fun toggleSelect(id: String) { selection = if (id in selection) selection - id else selection + id }
    fun selectAll() { selection = items.map { it.id }.toSet() }
    fun clearSelection() { selection = emptySet() }
    fun selectedItems(): List<FileItem> = items.filter { it.id in selection }

    // -------------------------------------------------------------- operations

    private fun runOp(label: String, onSuccess: (() -> Unit)? = null, block: (OpContext) -> String?) {
        if (opJob?.isActive == true) { message = "Another operation is still running."; return }
        progress = Progress(label, 0, -1)
        opJob = scope.launch {
            try {
                val result = withContext(Dispatchers.IO) {
                    val job = currentCoroutineContext()[Job]!!
                    block(OpContext(-1, { job.ensureActive() }) { p -> progress = p })
                }
                if (result != null) message = result
                onSuccess?.invoke()
            } catch (e: CancellationException) { message = "Cancelled." }
            catch (e: Throwable) { handleError(e) }
            finally { progress = null; reload() }
        }
    }

    fun cancelOperation() { opJob?.cancel() }

    private fun localChanged(src: FileSource, vararg dirIds: String?) {
        if (src.kind != SourceKind.LOCAL) return
        for (d in dirIds.filterNotNull().toSet()) try { svc.index.refreshShallow(File(d), prefs.showHidden.value) } catch (_: Exception) {}
    }

    fun createFolder(name: String) {
        val parent = stack.lastOrNull() ?: run { message = "Open a folder first."; return }
        val src = source
        runOp("Creating folder") { src.createFolder(parent, name); localChanged(src, parent.id); "Folder created." }
    }

    fun createFile(name: String) {
        val parent = stack.lastOrNull() ?: run { message = "Open a folder first."; return }
        val src = source
        runOp("Creating file") { src.createFile(parent, name); localChanged(src, parent.id); "File created." }
    }

    fun rename(item: FileItem, newName: String) {
        val src = source
        runOp("Renaming") { src.rename(item, newName); localChanged(src, item.parentId); selection = emptySet(); "Renamed." }
    }

    fun deleteSelected() {
        val list = selectedItems()
        val src = source
        if (list.isEmpty()) return
        runOp("Deleting") { ctx ->
            list.forEach { FileOps.deleteRecursive(src, it, ctx) }
            localChanged(src, *list.map { it.parentId }.toTypedArray())
            selection = emptySet()
            "Deleted ${list.size} item(s)."
        }
    }

    fun copySelection(move: Boolean) {
        val list = selectedItems()
        if (list.isEmpty()) return
        clipboard = ClipboardState(source, list, move)
        selection = emptySet()
        message = (if (move) "Cut " else "Copied ") + "${list.size} item(s). Open the destination and tap Paste."
    }

    fun clearClipboard() { clipboard = null }

    fun pasteHasConflicts(): Boolean {
        val cb = clipboard ?: return false
        val names = items.map { it.name }.toSet()
        return cb.items.any { it.name in names }
    }

    fun paste(policy: ConflictPolicy) {
        val cb = clipboard ?: return
        val parent = stack.lastOrNull() ?: run { message = "Open a destination folder first."; return }
        val dst = source
        runOp(if (cb.move) "Moving" else "Copying") { ctx ->
            if (cb.source.kind != SourceKind.SSH) ctx.total = cb.items.sumOf { FileOps.countBytes(cb.source, it, ctx) }
            val stats = OpStats()
            for (item in cb.items) {
                if (cb.move) FileOps.move(cb.source, item, dst, parent, policy, ctx, stats)
                else FileOps.copy(cb.source, item, dst, parent, policy, ctx, stats)
            }
            localChanged(dst, parent.id)
            if (cb.move) localChanged(cb.source, *cb.items.map { it.parentId }.toTypedArray())
            if (cb.move) clipboard = null
            "Done: ${stats.files} file(s)" + if (stats.skipped > 0) ", ${stats.skipped} skipped." else "."
        }
    }

    fun compress(format: ArchiveFormat, name: String) {
        val list = selectedItems()
        val parent = stack.lastOrNull() ?: run { message = "Open a folder first."; return }
        val src = source
        if (list.isEmpty()) return
        runOp("Compressing") { ctx ->
            val out = ArchiveEngine.compress(src, list, src, parent, name, format, ctx, appCtx.cacheDir)
            localChanged(src, parent.id)
            selection = emptySet()
            "Created ${out.name}."
        }
    }

    fun extract(item: FileItem) {
        val parent = stack.lastOrNull() ?: run { message = "Open a folder first."; return }
        val src = source
        runOp("Extracting") { ctx ->
            val out = ArchiveEngine.extract(src, item, src, parent, ctx)
            localChanged(src, parent.id)
            selection = emptySet()
            "Extracted to ${out.name}."
        }
    }

    /** Copies files picked with the system file picker into the current folder (upload on SSH). */
    fun importUris(uris: List<Uri>) {
        val parent = stack.lastOrNull() ?: run { message = "Open a destination folder first."; return }
        val dst = source
        runOp("Uploading") { ctx ->
            val existing = dst.list(parent).map { it.name }.toMutableSet()
            var n = 0
            for (u in uris) {
                ctx.check()
                val name = appCtx.contentResolver.query(u, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use {
                    if (it.moveToFirst()) it.getString(0) else null
                } ?: "file"
                val outName = FileOps.uniqueName(existing, name)
                existing += outName
                ctx.label(outName)
                (appCtx.contentResolver.openInputStream(u) ?: throw java.io.FileNotFoundException()).use { input ->
                    dst.openOutput(parent, outName).use { FileOps.pump(input, it, ctx) }
                }
                n++
            }
            localChanged(dst, parent.id)
            "Added $n file(s)."
        }
    }

    // ------------------------------------------------------- open / share

    private fun localUri(path: String): Uri =
        FileProvider.getUriForFile(appCtx, "${appCtx.packageName}.files.fileprovider", File(path))

    private fun start(i: Intent) {
        if (context !is Activity) i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        try { context.startActivity(i) } catch (_: ActivityNotFoundException) { message = "No app can handle this." }
        catch (e: Exception) { message = "Could not open: ${e.userMessage()}" }
    }

    private fun download(src: FileSource, item: FileItem, ctx: OpContext): File {
        val dir = File(appCtx.cacheDir, "files_open/${System.nanoTime()}").apply { mkdirs() }
        val f = File(dir, item.name.replace('/', '_'))
        ctx.label(item.name)
        src.openInput(item).use { i -> FileOutputStream(f).use { FileOps.pump(i, it, ctx) } }
        return f
    }

    fun open(item: FileItem, src: FileSource = source) {
        if (item.isDirectory) { openDir(item); return }
        fun view(uri: Uri) = start(Intent(Intent.ACTION_VIEW).setDataAndType(uri, item.mime ?: "*/*")
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION))
        when (src.kind) {
            SourceKind.LOCAL -> view(localUri(item.id))
            SourceKind.SAF -> view(Uri.parse(item.id))
            SourceKind.SSH -> {
                var file: File? = null
                runOp("Downloading", onSuccess = { file?.let { view(localUri(it.absolutePath)) } }) { ctx ->
                    file = download(src, item, ctx); null
                }
            }
        }
    }

    fun share(list: List<FileItem>) {
        val files = list.filter { !it.isDirectory }
        if (files.isEmpty()) { message = "Folders cannot be shared directly. Compress them first."; return }
        val src = source
        fun send(uris: List<Uri>) {
            val i = if (uris.size == 1) Intent(Intent.ACTION_SEND).putExtra(Intent.EXTRA_STREAM, uris[0])
            else Intent(Intent.ACTION_SEND_MULTIPLE).putParcelableArrayListExtra(Intent.EXTRA_STREAM, ArrayList(uris))
            i.type = "*/*"
            i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            start(Intent.createChooser(i, "Share"))
        }
        when (src.kind) {
            SourceKind.LOCAL -> send(files.map { localUri(it.id) })
            SourceKind.SAF -> send(files.map { Uri.parse(it.id) })
            SourceKind.SSH -> {
                val got = mutableListOf<Uri>()
                runOp("Downloading", onSuccess = { send(got) }) { ctx ->
                    files.forEach { got += localUri(download(src, it, ctx).absolutePath) }; null
                }
            }
        }
        selection = emptySet()
    }

    // ---------------------------------------------------------------- details

    suspend fun details(item: FileItem): FileDetails = withContext(Dispatchers.IO) {
        val src = source
        var created = 0L
        if (src.kind == SourceKind.LOCAL) {
            try {
                created = Files.readAttributes(File(item.id).toPath(), BasicFileAttributes::class.java).creationTime().toMillis()
            } catch (_: Exception) {}
        }
        var count: Int? = null
        var partial = false
        var total: Long? = null
        if (item.isDirectory) {
            count = try { src.list(item).size } catch (_: Exception) { null }
            if (src.kind != SourceKind.SSH) {
                var sum = 0L
                var seen = 0
                fun rec(d: FileItem) {
                    for (c in try { src.list(d) } catch (_: Exception) { emptyList() }) {
                        if (seen >= 20_000) { partial = true; return }
                        seen++
                        if (c.isDirectory && !c.isSymlink) rec(c) else sum += c.size.coerceAtLeast(0)
                    }
                }
                rec(item)
                total = sum
            }
        }
        FileDetails(item.name, if (src.kind == SourceKind.SSH) "${src.label} : ${item.id}" else item.id,
            item.isDirectory, item.size, item.extension, item.mime, item.modified, created, count, partial, total)
    }

    // --------------------------------------------------------------- favorites

    fun isFavorite(item: FileItem) = favorites.isFavorite(source.id, item.id)

    fun toggleFavorite(item: FileItem) =
        favorites.toggle(FavoriteRef(source.id, item.id, item.name, item.isDirectory))

    fun refreshFavorites() {
        val srcs = sources().associateBy { it.id }
        scope.launch {
            for (f in favorites.items.toList()) {
                val key = f.sourceId + "|" + f.id
                val src = srcs[f.sourceId]
                favoriteStatus[key] = when {
                    src == null -> false
                    src is SshFileSource && !ssh.isConnected(src.profile.id) -> null
                    else -> try { withContext(Dispatchers.IO) { src.stat(f.id) != null } } catch (_: Exception) { null }
                }
            }
        }
    }

    fun openFavorite(f: FavoriteRef) {
        val src = sources().firstOrNull { it.id == f.sourceId }
        if (src == null) { message = "The source of this favorite no longer exists."; return }
        jumpTo(src, f.id)
    }

    // ------------------------------------------------------------------ search

    fun openSearch() { searchOpen = true; searchResults = emptyList() }
    fun closeSearch() { searchJob?.cancel(); searchOpen = false; searchBusy = false }

    fun runSearch(text: String) {
        searchJob?.cancel()
        val q = SearchQuery.parse(text)
        if (q.isEmpty) { searchResults = emptyList(); searchBusy = false; return }
        val src = source
        val here = stack.lastOrNull()
        val scopePath = if (searchHereOnly && src.kind == SourceKind.LOCAL) here?.id else null
        searchBusy = true
        searchJob = scope.launch {
            try {
                val res = withContext(Dispatchers.IO) {
                    val job = currentCoroutineContext()[Job]!!
                    if (src.kind == SourceKind.LOCAL) {
                        if (svc.index.count() == 0L) null else svc.index.search(q, scopePath, 500)
                    } else {
                        val hits = mutableListOf<FileItem>()
                        val ctx = OpContext(-1, { job.ensureActive() }) { }
                        val start = if (here != null) listOf(here) else src.roots()
                        LiveSearch.run(src, start, q, 500, 50_000, ctx) { hits += it }
                        hits
                    }
                }
                indexMissing = res == null
                searchResults = res ?: emptyList()
            } catch (e: CancellationException) { throw e }
            catch (e: Throwable) { handleError(e) }
            finally { searchBusy = false }
        }
    }

    fun buildIndex(force: Boolean) {
        runOp("Indexing…") { ctx ->
            val roots = local.roots().map { File(it.id) }
            svc.index.refresh(roots, force, prefs.showHidden.value, ctx)
            prefs.lastIndexed = System.currentTimeMillis()
            indexMissing = false
            "Index ready: ${svc.index.count()} items."
        }
    }

    fun indexCount(): Long = try { svc.index.count() } catch (_: Exception) { 0L }

    fun openResult(item: FileItem) {
        closeSearch()
        if (item.isDirectory) {
            stack.clear(); stack.add(item); selection = emptySet(); recents.record(source.id, item.id, item.name); load(item)
        } else open(item)
    }
}

