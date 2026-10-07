package com.superapp.app.features.files.archive

import com.superapp.app.features.files.model.FileItem
import com.superapp.app.features.files.model.FilesException
import com.superapp.app.features.files.source.FileOps
import com.superapp.app.features.files.source.FileSource
import com.superapp.app.features.files.source.OpContext
import java.io.BufferedOutputStream
import java.io.ByteArrayOutputStream
import java.io.EOFException
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/** Formats we can CREATE. Only formats implemented below are offered or extracted. */
enum class ArchiveFormat(val label: String, val suffix: String) {
    ZIP("ZIP (.zip)", ".zip"),
    TAR_GZ("TAR.GZ (.tar.gz)", ".tar.gz"),
}

/** Formats we can EXTRACT. */
enum class ExtractKind { ZIP, TAR_GZ, TAR, GZ }

object ArchiveEngine {
    private const val MAX_ENTRIES = 500_000

    fun kindOf(name: String): ExtractKind? {
        val n = name.lowercase()
        return when {
            n.endsWith(".zip") -> ExtractKind.ZIP
            n.endsWith(".tar.gz") || n.endsWith(".tgz") -> ExtractKind.TAR_GZ
            n.endsWith(".tar") -> ExtractKind.TAR
            n.endsWith(".gz") -> ExtractKind.GZ
            else -> null
        }
    }

    fun isArchive(name: String) = kindOf(name) != null

    private fun stripArchiveSuffix(name: String): String {
        val n = name.lowercase()
        for (s in listOf(".tar.gz", ".tgz", ".zip", ".tar", ".gz")) if (n.endsWith(s)) return name.dropLast(s.length).ifEmpty { "extracted" }
        return name
    }

    // ------------------------------------------------------------------ compress

    fun compress(
        src: FileSource, items: List<FileItem>, dst: FileSource, dstParent: FileItem,
        baseName: String, format: ArchiveFormat, ctx: OpContext, cacheDir: File,
    ): FileItem {
        val entries = mutableListOf<Pair<FileItem, String>>()
        for (it in items) FileOps.walk(src, it, it.name, ctx, entries)
        if (entries.size > MAX_ENTRIES) throw FilesException("Too many files to archive.")
        val outName = FileOps.uniqueName(dst.list(dstParent).map { it.name }.toSet(), baseName.removeSuffix(format.suffix) + format.suffix)
        var created = false
        try {
            dst.openOutput(dstParent, outName).use { raw ->
                created = true
                val out = BufferedOutputStream(raw, 64 * 1024)
                when (format) {
                    ArchiveFormat.ZIP -> writeZip(src, entries, out, ctx)
                    ArchiveFormat.TAR_GZ -> writeTarGz(src, entries, out, ctx, cacheDir)
                }
                out.flush()
            }
        } catch (e: Throwable) {
            if (created) try { dst.child(dstParent, outName)?.let { dst.deleteOne(it) } } catch (_: Exception) {}
            throw e
        }
        return dst.child(dstParent, outName) ?: throw FilesException("Archive was not created.")
    }

    private fun writeZip(src: FileSource, entries: List<Pair<FileItem, String>>, out: OutputStream, ctx: OpContext) {
        val z = ZipOutputStream(out)
        z.setLevel(6)
        for ((item, rel) in entries) {
            ctx.check()
            if (item.isSymlink && item.isDirectory) continue
            val e = ZipEntry(if (item.isDirectory) "$rel/" else rel)
            if (item.modified > 0) e.time = item.modified
            z.putNextEntry(e)
            if (!item.isDirectory) {
                ctx.label(item.name)
                src.openInput(item).use { FileOps.pump(it, z, ctx) }
            }
            z.closeEntry()
        }
        z.finish()
    }

    private fun writeTarGz(src: FileSource, entries: List<Pair<FileItem, String>>, out: OutputStream, ctx: OpContext, cacheDir: File) {
        val gz = GZIPOutputStream(out, 64 * 1024)
        val tar = TarWriter(gz)
        for ((item, rel) in entries) {
            ctx.check()
            if (item.isSymlink && item.isDirectory) continue
            if (item.isDirectory) { tar.writeDir(rel, item.modified); continue }
            ctx.label(item.name)
            var size = item.size
            var tmp: File? = null
            try {
                if (size < 0) { // unknown size (some SAF providers): measure through a temp file
                    tmp = File.createTempFile("tar", ".tmp", cacheDir)
                    tmp.outputStream().use { o -> src.openInput(item).use { FileOps.pump(it, o, ctx) } }
                    size = tmp.length()
                }
                tar.writeFile(rel, size, item.modified) { o ->
                    val input = tmp?.inputStream() ?: src.openInput(item)
                    input.use { copyExactly(it, o, size, ctx, tmp == null) }
                }
            } finally {
                tmp?.delete()
            }
        }
        tar.finish()
        gz.finish()
    }

    private fun copyExactly(input: InputStream, out: OutputStream, size: Long, ctx: OpContext, report: Boolean) {
        val buf = ByteArray(64 * 1024)
        var left = size
        while (left > 0) {
            ctx.check()
            val n = input.read(buf, 0, minOf(buf.size.toLong(), left).toInt())
            if (n < 0) throw IOException("File changed while archiving")
            out.write(buf, 0, n)
            left -= n
            if (report) ctx.add(n)
        }
        if (input.read() >= 0) throw IOException("File changed while archiving")
    }

    // ------------------------------------------------------------------- extract

    /** Extracts into a NEW folder named after the archive. On failure/cancel the partial folder is removed. */
    fun extract(src: FileSource, archive: FileItem, dst: FileSource, dstParent: FileItem, ctx: OpContext): FileItem {
        val kind = kindOf(archive.name) ?: throw FilesException("Unsupported archive type (supported: zip, tar, tar.gz, tgz, gz).")
        val folderName = FileOps.uniqueName(dst.list(dstParent).map { it.name }.toSet(), stripArchiveSuffix(archive.name))
        if (kind == ExtractKind.GZ) return extractGz(src, archive, dst, dstParent, folderName, ctx)
        val root = dst.createFolder(dstParent, folderName)
        try {
            val writer = Writer(dst, root, ctx)
            src.openInput(archive).use { raw ->
                when (kind) {
                    ExtractKind.ZIP -> {
                        val z = ZipInputStream(raw.buffered(64 * 1024))
                        var count = 0
                        while (true) {
                            ctx.check()
                            val e = z.nextEntry ?: break
                            if (++count > MAX_ENTRIES) throw FilesException("Archive has too many entries.")
                            if (e.isDirectory) writer.dir(e.name) else writer.file(e.name, z)
                            z.closeEntry()
                        }
                        if (count == 0) throw FilesException("The file is not a valid ZIP archive or it is empty.")
                    }
                    ExtractKind.TAR_GZ -> readTar(GZIPInputStream(raw.buffered(64 * 1024)), writer, ctx)
                    ExtractKind.TAR -> readTar(raw.buffered(64 * 1024), writer, ctx)
                    ExtractKind.GZ -> {}
                }
            }
            if (writer.skippedUnsafe > 0 || writer.skippedSpecial > 0) {
                throw SkippedNotice(root, writer.skippedUnsafe, writer.skippedSpecial)
            }
        } catch (e: SkippedNotice) {
            throw FilesException("Extracted, but skipped ${e.unsafe} unsafe path(s) and ${e.special} link/special entr(ies).")
        } catch (e: Throwable) {
            try { FileOps.deleteRecursive(dst, root, ctx) } catch (_: Throwable) {}
            throw e
        }
        return root
    }

    private class SkippedNotice(val root: FileItem, val unsafe: Int, val special: Int) : Exception()

    private fun extractGz(src: FileSource, archive: FileItem, dst: FileSource, dstParent: FileItem, ignored: String, ctx: OpContext): FileItem {
        val base = archive.name.dropLast(3).ifEmpty { "extracted" }
        val outName = FileOps.uniqueName(dst.list(dstParent).map { it.name }.toSet(), base)
        var created = false
        try {
            src.openInput(archive).use { raw ->
                GZIPInputStream(raw.buffered(64 * 1024)).use { gz ->
                    dst.openOutput(dstParent, outName).use { o -> created = true; FileOps.pump(gz, o, ctx) }
                }
            }
        } catch (e: Throwable) {
            if (created) try { dst.child(dstParent, outName)?.let { dst.deleteOne(it) } } catch (_: Exception) {}
            throw e
        }
        return dst.child(dstParent, outName) ?: throw FilesException("Extraction failed.")
    }

    /** Creates folders/files below [root] with zip-slip protection (no "..", no absolute paths). */
    private class Writer(val dst: FileSource, val root: FileItem, val ctx: OpContext) {
        var skippedUnsafe = 0
        var skippedSpecial = 0
        private val dirs = HashMap<String, FileItem>().apply { put("", root) }
        private val namesIn = HashMap<String, MutableSet<String>>()

        private fun parts(path: String): List<String>? {
            val p = path.replace('\\', '/').split('/').filter { it.isNotEmpty() && it != "." }
            if (p.any { it == ".." || it.contains('\u0000') }) { skippedUnsafe++; return null }
            return p
        }

        private fun ensureDir(parts: List<String>): FileItem {
            var key = ""
            var cur = root
            for (p in parts) {
                key = if (key.isEmpty()) p else "$key/$p"
                cur = dirs.getOrPut(key) { dst.child(cur, p)?.takeIf { it.isDirectory } ?: dst.createFolder(cur, p) }
            }
            return cur
        }

        fun dir(path: String) { parts(path)?.let { ensureDir(it) } }

        fun skipSpecial() { skippedSpecial++ }

        fun file(path: String, data: InputStream) {
            val p = parts(path) ?: return
            if (p.isEmpty()) return
            val parent = ensureDir(p.dropLast(1))
            val key = p.dropLast(1).joinToString("/")
            val used = namesIn.getOrPut(key) { dst.list(parent).map { it.name }.toMutableSet() }
            val name = FileOps.uniqueName(used, p.last())
            used += name
            ctx.label(name)
            dst.openOutput(parent, name).use { FileOps.pump(data, it, ctx) }
        }
    }

    private fun readTar(input: InputStream, writer: Writer, ctx: OpContext) {
        val tar = TarReader(input)
        var count = 0
        while (true) {
            ctx.check()
            val e = tar.next() ?: break
            if (++count > MAX_ENTRIES) throw FilesException("Archive has too many entries.")
            when (e.type) {
                '0', '\u0000', '7' -> writer.file(e.name, tar.entryStream())
                '5' -> writer.dir(e.name)
                else -> writer.skipSpecial() // symlinks, hardlinks, devices: never created
            }
        }
    }
}

// =============================================================== minimal TAR

/** Minimal ustar writer. Names longer than 100 bytes use the GNU 'L' (long name) extension. Files must be < 8 GiB. */
internal class TarWriter(private val out: OutputStream) {
    private fun octal(buf: ByteArray, off: Int, len: Int, value: Long) {
        val s = java.lang.Long.toOctalString(value).padStart(len - 1, '0')
        for (i in 0 until len - 1) buf[off + i] = s[i].code.toByte()
        buf[off + len - 1] = 0
    }

    private fun header(name: String, size: Long, mtimeSec: Long, type: Char, mode: Int): ByteArray {
        val h = ByteArray(512)
        val nb = name.toByteArray(Charsets.UTF_8)
        System.arraycopy(nb, 0, h, 0, minOf(nb.size, 100))
        octal(h, 100, 8, mode.toLong()); octal(h, 108, 8, 0); octal(h, 116, 8, 0)
        octal(h, 124, 12, size); octal(h, 136, 12, mtimeSec)
        for (i in 148 until 156) h[i] = ' '.code.toByte()
        h[156] = type.code.toByte()
        "ustar".toByteArray().copyInto(h, 257); h[262] = 0
        h[263] = '0'.code.toByte(); h[264] = '0'.code.toByte()
        var sum = 0L
        for (b in h) sum += (b.toInt() and 0xff)
        val cs = java.lang.Long.toOctalString(sum).padStart(6, '0')
        for (i in 0 until 6) h[148 + i] = cs[i].code.toByte()
        h[154] = 0; h[155] = ' '.code.toByte()
        return h
    }

    private fun longName(name: String) {
        val nb = name.toByteArray(Charsets.UTF_8)
        if (nb.size <= 100) return
        val data = nb + byteArrayOf(0)
        out.write(header("././@LongLink", data.size.toLong(), 0, 'L', 0))
        out.write(data)
        val pad = (512 - data.size % 512) % 512
        if (pad > 0) out.write(ByteArray(pad))
    }

    fun writeDir(name: String, modified: Long) {
        val n = name.trimEnd('/') + "/"
        longName(n)
        out.write(header(n, 0, modified / 1000, '5', 0b111_101_101))
    }

    fun writeFile(name: String, size: Long, modified: Long, body: (OutputStream) -> Unit) {
        if (size >= 8L * 1024 * 1024 * 1024) throw FilesException("A file is too large for TAR (8 GiB limit). Use ZIP.")
        longName(name)
        out.write(header(name, size, modified / 1000, '0', 0b110_100_100))
        body(out)
        val pad = ((512 - size % 512) % 512).toInt()
        if (pad > 0) out.write(ByteArray(pad))
    }

    fun finish() { out.write(ByteArray(1024)) }
}

internal class TarEntry(val name: String, val size: Long, val type: Char)

/** Minimal tar reader: ustar, GNU long names ('L') and basic pax headers ('x': path, size). */
internal class TarReader(private val input: InputStream) {
    private var remaining = 0L
    private var padding = 0

    private fun readFully(buf: ByteArray): Boolean {
        var off = 0
        while (off < buf.size) {
            val n = input.read(buf, off, buf.size - off)
            if (n < 0) { if (off == 0) return false else throw EOFException() }
            off += n
        }
        return true
    }

    private fun skipBytes(count: Long) {
        var left = count
        val buf = ByteArray(8192)
        while (left > 0) {
            val n = input.read(buf, 0, minOf(buf.size.toLong(), left).toInt())
            if (n < 0) throw EOFException()
            left -= n
        }
    }

    private fun str(h: ByteArray, off: Int, len: Int): String {
        var end = off
        while (end < off + len && h[end].toInt() != 0) end++
        return String(h, off, end - off, Charsets.UTF_8)
    }

    private fun num(h: ByteArray, off: Int, len: Int): Long {
        if (h[off].toInt() and 0x80 != 0) { // base-256
            var v = (h[off].toInt() and 0x7f).toLong()
            for (i in 1 until len) v = (v shl 8) or (h[off + i].toLong() and 0xff)
            return v
        }
        val s = str(h, off, len).trim()
        return if (s.isEmpty()) 0 else s.toLong(8)
    }

    private fun readData(size: Long): ByteArray {
        if (size > 1_000_000) throw IOException("Corrupted TAR header")
        val b = ByteArray(size.toInt())
        if (size > 0 && !readFully(b)) throw EOFException()
        skipBytes((512 - size % 512) % 512)
        return b
    }

    fun next(): TarEntry? {
        skipBytes(remaining + padding)
        remaining = 0; padding = 0
        var longName: String? = null
        var paxPath: String? = null
        var paxSize: Long? = null
        val h = ByteArray(512)
        while (true) {
            if (!readFully(h)) return null
            if (h.all { it.toInt() == 0 }) return null
            var sum = 0L
            for (i in 0 until 512) sum += if (i in 148..155) 32 else (h[i].toInt() and 0xff)
            val stored = try { str(h, 148, 8).trim().toLong(8) } catch (_: Exception) { -1L }
            if (stored != sum) throw IOException("Corrupted TAR header")
            val type = h[156].toInt().toChar()
            var size = try { num(h, 124, 12) } catch (_: Exception) { throw IOException("Corrupted TAR header") }
            when (type) {
                'L' -> { longName = str(readData(size), 0, size.toInt().coerceAtLeast(0)); continue }
                'x' -> {
                    val text = String(readData(size), Charsets.UTF_8)
                    for (line in text.split('\n')) {
                        val kv = line.substringAfter(' ', "")
                        if (kv.startsWith("path=")) paxPath = kv.substring(5)
                        if (kv.startsWith("size=")) paxSize = kv.substring(5).toLongOrNull()
                    }
                    continue
                }
                'g' -> { readData(size); continue }
            }
            paxSize?.let { size = it }
            var name = str(h, 0, 100)
            val prefix = str(h, 345, 155)
            if (prefix.isNotEmpty() && str(h, 257, 5) == "ustar") name = "$prefix/$name"
            name = paxPath ?: longName ?: name
            remaining = size
            padding = ((512 - size % 512) % 512).toInt()
            return TarEntry(name, size, type)
        }
    }

    /** Stream limited to the current entry's data. */
    fun entryStream(): InputStream = object : InputStream() {
        override fun read(): Int {
            if (remaining <= 0) return -1
            val b = input.read()
            if (b < 0) throw EOFException()
            remaining--
            return b
        }

        override fun read(b: ByteArray, off: Int, len: Int): Int {
            if (remaining <= 0) return -1
            val n = input.read(b, off, minOf(len.toLong(), remaining).toInt())
            if (n < 0) throw EOFException()
            remaining -= n
            return n
        }
    }
}

