-----------------------------------------------------------------------
package com.superapp.app.features.files.source

import com.superapp.app.features.files.model.ConflictPolicy
import com.superapp.app.features.files.model.FileItem
import com.superapp.app.features.files.model.FilesException
import com.superapp.app.features.files.model.Progress
import com.superapp.app.features.files.model.SourceKind
import java.io.InputStream
import java.io.OutputStream

/**
 * A file system the Files UI can browse. All methods are BLOCKING and must be called off the main thread.
 * Implementations: LocalFileSource (java.io.File), SafFileSource (Storage Access Framework), SshFileSource (SFTP).
 * (Not named FileProvider on purpose: that is androidx.core.content.FileProvider, used for sharing.)
 */
interface FileSource {
    val id: String
    val kind: SourceKind
    val label: String

    fun roots(): List<FileItem>
    fun list(dir: FileItem): List<FileItem>
    fun stat(id: String): FileItem?
    fun createFolder(parent: FileItem, name: String): FileItem
    fun createFile(parent: FileItem, name: String): FileItem
    fun rename(item: FileItem, newName: String): FileItem
    /** Deletes a file or an EMPTY folder. */
    fun deleteOne(item: FileItem)
    fun openInput(item: FileItem): InputStream
    /** Creates or truncates [name] inside [parent]. */
    fun openOutput(parent: FileItem, name: String): OutputStream
    /** Fast same-source move. Returns false if unsupported (caller falls back to copy + delete). */
    fun moveWithin(item: FileItem, newParent: FileItem): Boolean = false
    fun child(parent: FileItem, name: String): FileItem? = list(parent).firstOrNull { it.name == name }
}

fun validateName(name: String) {
    if (name.isBlank() || name == "." || name == ".." || name.contains('/') || name.contains('\u0000') || name.length > 255) {
        throw FilesException("Invalid name.")
    }
}

/** Progress/cancellation context handed to long operations. */
class OpContext(
    var total: Long,
    private val checkFn: () -> Unit,
    private val report: (Progress) -> Unit,
) {
    var done = 0L
    var label = ""
    private var last = 0L

    fun check() = checkFn()

    fun label(text: String) {
        label = text
        report(Progress(label, done, total))
    }

    fun add(n: Int) {
        done += n
        val now = System.nanoTime()
        if (now - last > 100_000_000L) {
            last = now
            report(Progress(label, done, total))
        }
    }
}

class OpStats {
    var skipped = 0
    var files = 0
}

object FileOps {
    private const val BUF = 64 * 1024

    fun uniqueName(existing: Set<String>, name: String): String {
        if (name !in existing) return name
        val dot = name.lastIndexOf('.').takeIf { it > 0 }
        val base = if (dot == null) name else name.substring(0, dot)
        val ext = if (dot == null) "" else name.substring(dot)
        var i = 1
        while (true) {
            val cand = "$base ($i)$ext"
            if (cand !in existing) return cand
            i++
        }
    }

    fun pump(input: InputStream, output: OutputStream, ctx: OpContext) {
        val buf = ByteArray(BUF)
        while (true) {
            ctx.check()
            val n = input.read(buf)
            if (n < 0) break
            output.write(buf, 0, n)
            ctx.add(n)
        }
        output.flush()
    }

    fun deleteRecursive(src: FileSource, item: FileItem, ctx: OpContext) {
        ctx.check()
        if (item.isDirectory && !item.isSymlink) {
            for (c in src.list(item)) deleteRecursive(src, c, ctx)
        }
        src.deleteOne(item)
    }

    fun countBytes(src: FileSource, item: FileItem, ctx: OpContext): Long {
        ctx.check()
        if (item.isSymlink && item.isDirectory) return 0
        if (!item.isDirectory) return item.size.coerceAtLeast(0)
        return src.list(item).sumOf { countBytes(src, it, ctx) }
    }

    /** Collects (item, relativePath) for [item] and everything below it. Symlinked folders are not followed. */
    fun walk(src: FileSource, item: FileItem, rel: String, ctx: OpContext, out: MutableList<Pair<FileItem, String>>) {
        ctx.check()
        out += item to rel
        if (item.isDirectory && !item.isSymlink) {
            for (c in src.list(item)) walk(src, c, "$rel/${c.name}", ctx, out)
        }
    }

    /**
     * Copies [item] (recursively) into [toParent]. Works between ANY two sources, so the same code
     * performs local copies, uploads (local -> SSH) and downloads (SSH -> local).
     */
    fun copy(
        from: FileSource, item: FileItem, to: FileSource, toParent: FileItem,
        policy: ConflictPolicy, ctx: OpContext, stats: OpStats,
    ) {
        ctx.check()
        if (from.id == to.id && item.isDirectory && !item.isSymlink &&
            (toParent.id == item.id || (from.kind != SourceKind.SAF && toParent.id.startsWith(item.id.trimEnd('/') + "/")))
        ) throw FilesException("A folder cannot be copied into itself.")
        if (item.isSymlink && item.isDirectory) { stats.skipped++; return }

        val existingNames by lazy { to.list(toParent).map { it.name }.toSet() }
        val existing = to.child(toParent, item.name)

        if (item.isDirectory) {
            val target = when {
                existing == null -> to.createFolder(toParent, item.name)
                existing.isDirectory -> existing // merge into the existing folder
                policy == ConflictPolicy.KEEP_BOTH -> to.createFolder(toParent, uniqueName(existingNames, item.name))
                else -> { stats.skipped++; return }
            }
            for (c in from.list(item)) copy(from, c, to, target, policy, ctx, stats)
            return
        }

        val sameFile = existing != null && from.id == to.id && existing.id == item.id
        val outName = when {
            existing == null -> item.name
            sameFile || policy == ConflictPolicy.KEEP_BOTH -> uniqueName(existingNames, item.name)
            policy == ConflictPolicy.SKIP -> { stats.skipped++; return }
            existing.isDirectory -> { stats.skipped++; return }
            else -> item.name // OVERWRITE
        }
        ctx.label(item.name)
        var created = false
        try {
            from.openInput(item).use { input ->
                to.openOutput(toParent, outName).use { output ->
                    created = true
                    pump(input, output, ctx)
                }
            }
            stats.files++
        } catch (e: Throwable) {
            if (created) {
                try { to.child(toParent, outName)?.let { to.deleteOne(it) } } catch (_: Exception) {}
            }
            throw e
        }
    }

    fun move(
        from: FileSource, item: FileItem, to: FileSource, toParent: FileItem,
        policy: ConflictPolicy, ctx: OpContext, stats: OpStats,
    ) {
        ctx.check()
        if (from.id == to.id) {
            if (item.parentId == toParent.id) return // already there
            if (to.child(toParent, item.name) == null && from.moveWithin(item, toParent)) {
                stats.files++
                return
            }
        }
        val skippedBefore = stats.skipped
        copy(from, item, to, toParent, policy, ctx, stats)
        if (stats.skipped == skippedBefore) deleteRecursive(from, item, ctx) // never delete what was not fully copied
    }
}

-----------------------------------------------------------------------
