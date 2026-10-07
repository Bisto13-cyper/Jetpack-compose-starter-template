package com.superapp.app.features.files.index

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import com.superapp.app.features.files.model.FileItem
import com.superapp.app.features.files.model.extensionOf
import com.superapp.app.features.files.model.mimeFor
import com.superapp.app.features.files.source.FileSource
import com.superapp.app.features.files.source.LocalFileSource
import com.superapp.app.features.files.source.OpContext
import java.io.File

/**
 * Parsed search text.
 *   project            -> name contains "project"
 *   .py  /  *.py  /  type:py  /  ext:.py,.kt -> extension filter
 *   project .py        -> both (AND)
 *   name:report        -> explicit name term
 *   type:folder / type:file
 */
data class SearchQuery(
    val nameTokens: List<String>,
    val exts: List<String>,
    val dirsOnly: Boolean,
    val filesOnly: Boolean,
) {
    val isEmpty get() = nameTokens.isEmpty() && exts.isEmpty() && !dirsOnly && !filesOnly

    fun matches(name: String, isDir: Boolean): Boolean {
        if (dirsOnly && !isDir) return false
        if (filesOnly && isDir) return false
        val lc = name.lowercase()
        if (!nameTokens.all { lc.contains(it) }) return false
        if (exts.isNotEmpty() && extensionOf(name, isDir) !in exts) return false
        return true
    }

    companion object {
        fun parse(raw: String): SearchQuery {
            val names = mutableListOf<String>()
            val exts = mutableListOf<String>()
            var dirs = false
            var files = false
            for (t in raw.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }) {
                val l = t.lowercase()
                when {
                    l.startsWith("type:") || l.startsWith("ext:") -> {
                        for (v in l.substringAfter(':').split(',').map { it.trim().removePrefix(".") }.filter { it.isNotEmpty() }) {
                            when (v) { "dir", "folder" -> dirs = true; "file" -> files = true; else -> exts += v }
                        }
                    }
                    l.startsWith("name:") -> l.substringAfter(':').takeIf { it.isNotEmpty() }?.let { names += it }
                    l.startsWith("*.") && l.length > 2 -> exts += l.substring(2)
                    l.startsWith(".") && l.length > 1 && !l.substring(1).contains('.') -> exts += l.substring(1)
                    else -> names += l
                }
            }
            return SearchQuery(names, exts, dirs, files)
        }
    }
}

/** Generic (non-indexed) search used for SAF and SSH: bounded breadth-first walk, cancellable. */
object LiveSearch {
    fun run(
        src: FileSource, start: List<FileItem>, q: SearchQuery, limit: Int, maxScanned: Int,
        ctx: OpContext, onHit: (FileItem) -> Unit,
    ) {
        val queue = ArrayDeque(start)
        var scanned = 0
        var hits = 0
        while (queue.isNotEmpty() && scanned < maxScanned && hits < limit) {
            ctx.check()
            val dir = queue.removeFirst()
            val children = try { src.list(dir) } catch (_: Exception) { continue }
            for (c in children) {
                scanned++
                if (q.matches(c.name, c.isDirectory)) { onHit(c); hits++ }
                if (c.isDirectory && !c.isSymlink) queue.addLast(c)
            }
        }
    }
}

/**
 * Lightweight SQLite index of LOCAL files (path, name, extension, type, size, modified).
 * Kept valid by: (1) incremental refresh that only re-lists folders whose modification time changed,
 * (2) immediate shallow refresh after every change made through the app, (3) verification of results against
 * the real file system before they are shown. It never scans in the background by itself.
 * Folders Android does not let us read are skipped silently.
 */
class FileIndex(context: Context) {
    private val helper = object : SQLiteOpenHelper(
        context.applicationContext, File(context.applicationContext.noBackupFilesDir, "files_index.db").path, null, 1
    ) {
        override fun onCreate(db: SQLiteDatabase) {
            db.execSQL("CREATE TABLE entries(path TEXT PRIMARY KEY, parent TEXT NOT NULL, name TEXT NOT NULL, name_lc TEXT NOT NULL, ext TEXT NOT NULL, is_dir INTEGER NOT NULL, size INTEGER NOT NULL, modified INTEGER NOT NULL)")
            db.execSQL("CREATE INDEX idx_name ON entries(name_lc)")
            db.execSQL("CREATE INDEX idx_ext ON entries(ext)")
            db.execSQL("CREATE INDEX idx_parent ON entries(parent)")
            db.execSQL("CREATE TABLE dirs(path TEXT PRIMARY KEY, mtime INTEGER NOT NULL)")
        }

        override fun onUpgrade(db: SQLiteDatabase, o: Int, n: Int) {}
    }

    fun count(): Long = helper.readableDatabase.compileStatement("SELECT COUNT(*) FROM entries").simpleQueryForLong()

    fun clear() {
        helper.writableDatabase.apply { delete("entries", null, null); delete("dirs", null, null) }
    }

    /** Full or incremental scan of [roots]. Reports the number of indexed items through ctx.label. */
    fun refresh(roots: List<File>, force: Boolean, includeHidden: Boolean, ctx: OpContext) {
        val db = helper.writableDatabase
        var n = 0L
        fun scan(dir: File) {
            ctx.check()
            val path = dir.absolutePath
            val mtime = dir.lastModified()
            val known = db.rawQuery("SELECT mtime FROM dirs WHERE path=?", arrayOf(path)).use { if (it.moveToFirst()) it.getLong(0) else null }
            val subdirs = mutableListOf<File>()
            if (!force && known != null && known == mtime) {
                db.rawQuery("SELECT path FROM entries WHERE parent=? AND is_dir=1", arrayOf(path)).use {
                    while (it.moveToNext()) subdirs += File(it.getString(0))
                }
            } else {
                val children = dir.listFiles()?.filter { includeHidden || !it.name.startsWith(".") } ?: return
                db.beginTransaction()
                try {
                    val seen = HashSet<String>()
                    for (f in children) {
                        val isDir = f.isDirectory
                        seen += f.absolutePath
                        upsert(db, f, path, isDir)
                        n++
                        if (isDir && !isSymlink(f)) subdirs += f
                    }
                    db.rawQuery("SELECT path, is_dir FROM entries WHERE parent=?", arrayOf(path)).use {
                        while (it.moveToNext()) {
                            val p = it.getString(0)
                            if (p !in seen) removeTree(db, p)
                        }
                    }
                    db.execSQL("INSERT OR REPLACE INTO dirs(path, mtime) VALUES(?,?)", arrayOf<Any>(path, mtime))
                    db.setTransactionSuccessful()
                } finally {
                    db.endTransaction()
                }
                ctx.label("Indexing… $n items")
            }
            for (s in subdirs) scan(s)
        }
        for (r in roots) if (r.isDirectory) scan(r)
    }

    /** Called after the app changed something inside [dir]: re-lists just that folder. */
    fun refreshShallow(dir: File, includeHidden: Boolean) {
        val db = helper.writableDatabase
        if (db.rawQuery("SELECT 1 FROM dirs WHERE path=?", arrayOf(dir.absolutePath)).use { !it.moveToFirst() }) return // not indexed yet
        val children = dir.listFiles()?.filter { includeHidden || !it.name.startsWith(".") } ?: return
        db.beginTransaction()
        try {
            val seen = HashSet<String>()
            for (f in children) { seen += f.absolutePath; upsert(db, f, dir.absolutePath, f.isDirectory) }
            db.rawQuery("SELECT path FROM entries WHERE parent=?", arrayOf(dir.absolutePath)).use {
                while (it.moveToNext()) { val p = it.getString(0); if (p !in seen) removeTree(db, p) }
            }
            db.execSQL("INSERT OR REPLACE INTO dirs(path, mtime) VALUES(?,?)", arrayOf<Any>(dir.absolutePath, dir.lastModified()))
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    private fun isSymlink(f: File) = try { java.nio.file.Files.isSymbolicLink(f.toPath()) } catch (_: Exception) { false }

    private fun upsert(db: SQLiteDatabase, f: File, parent: String, isDir: Boolean) {
        val cv = ContentValues().apply {
            put("path", f.absolutePath); put("parent", parent); put("name", f.name); put("name_lc", f.name.lowercase())
            put("ext", extensionOf(f.name, isDir)); put("is_dir", if (isDir) 1 else 0)
            put("size", if (isDir) -1L else f.length()); put("modified", f.lastModified())
        }
        db.insertWithOnConflict("entries", null, cv, SQLiteDatabase.CONFLICT_REPLACE)
    }

    private fun removeTree(db: SQLiteDatabase, path: String) {
        val like = path.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "/%"
        db.delete("entries", "path=? OR path LIKE ? ESCAPE '\\'", arrayOf(path, like))
        db.delete("dirs", "path=? OR path LIKE ? ESCAPE '\\'", arrayOf(path, like))
    }

    /** Fast indexed search. Results are re-checked against the real file system (stale rows are dropped). */
    fun search(q: SearchQuery, scopePath: String?, limit: Int): List<FileItem> {
        val where = mutableListOf<String>()
        val args = mutableListOf<String>()
        for (t in q.nameTokens) {
            where += "name_lc LIKE ? ESCAPE '\\'"
            args += "%" + t.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%"
        }
        if (q.exts.isNotEmpty()) { where += "ext IN (${q.exts.joinToString(",") { "?" }})"; args += q.exts }
        if (q.dirsOnly) where += "is_dir=1"
        if (q.filesOnly) where += "is_dir=0"
        if (scopePath != null) {
            where += "path LIKE ? ESCAPE '\\'"
            args += scopePath.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "/%"
        }
        val sql = "SELECT path FROM entries" + (if (where.isEmpty()) "" else " WHERE " + where.joinToString(" AND ")) +
            " ORDER BY is_dir DESC, name_lc LIMIT $limit"
        val out = mutableListOf<FileItem>()
        helper.readableDatabase.rawQuery(sql, args.toTypedArray()).use { c ->
            while (c.moveToNext()) {
                val f = File(c.getString(0))
                if (!f.exists()) continue
                val dir = f.isDirectory
                out += FileItem(f.absolutePath, f.name, dir, if (dir) -1L else f.length(), f.lastModified(),
                    if (dir) null else mimeFor(f.name), LocalFileSource.ID, f.parent)
            }
        }
        return out
    }
}

