package com.superapp.app.features.files.source

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Environment
import android.os.storage.StorageManager
import androidx.core.content.ContextCompat
import com.superapp.app.features.files.model.FileItem
import com.superapp.app.features.files.model.FilesException
import com.superapp.app.features.files.model.SourceKind
import com.superapp.app.features.files.model.mimeFor
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.nio.file.Files

/**
 * Local storage through java.io.File.
 * Always available: the app's own folders. Shared storage ("Internal storage", SD card) is only listed when
 * the user has granted the official "All files access" (API 30+) / storage permission (API <= 29).
 * Anything else must be added through the Storage Access Framework (SafFileSource).
 */
class LocalFileSource(private val context: Context) : FileSource {
    override val id = ID
    override val kind = SourceKind.LOCAL
    override val label = "Local files"

    companion object {
        const val ID = "local"

        fun hasSharedAccess(context: Context): Boolean =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) Environment.isExternalStorageManager()
            else ContextCompat.checkSelfPermission(context, Manifest.permission.READ_EXTERNAL_STORAGE) ==
                PackageManager.PERMISSION_GRANTED &&
                ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_EXTERNAL_STORAGE) ==
                PackageManager.PERMISSION_GRANTED
    }

    private fun item(f: File, nameOverride: String? = null): FileItem {
        val link = try { Files.isSymbolicLink(f.toPath()) } catch (_: Exception) { false }
        val dir = f.isDirectory
        return FileItem(
            id = f.absolutePath,
            name = nameOverride ?: f.name.ifEmpty { f.absolutePath },
            isDirectory = dir,
            size = if (dir) -1L else f.length(),
            modified = f.lastModified(),
            mime = if (dir) null else mimeFor(f.name),
            sourceId = id,
            parentId = f.parent,
            isSymlink = link,
        )
    }

    override fun roots(): List<FileItem> {
        val out = mutableListOf<FileItem>()
        if (hasSharedAccess(context)) {
            out += item(Environment.getExternalStorageDirectory(), "Internal storage")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                context.getSystemService(StorageManager::class.java)?.storageVolumes?.forEach { v ->
                    val d = v.directory
                    if (d != null && !v.isPrimary && v.state == Environment.MEDIA_MOUNTED) {
                        out += item(d, v.getDescription(context))
                    }
                }
            }
        }
        context.getExternalFilesDir(null)?.let { out += item(it, "App files (shared storage area)") }
        out += item(context.filesDir, "App private files")
        return out
    }

    override fun list(dir: FileItem): List<FileItem> {
        val files = File(dir.id).listFiles()
            ?: throw FilesException("Cannot read this folder (permission denied or storage unavailable).")
        return files.map { item(it) }
    }

    override fun stat(id: String): FileItem? = File(id).takeIf { it.exists() }?.let { item(it) }

    override fun createFolder(parent: FileItem, name: String): FileItem {
        validateName(name)
        val f = File(parent.id, name)
        if (f.exists()) throw FilesException("\"$name\" already exists.")
        if (!f.mkdir()) throw FilesException("Could not create the folder (permission denied or read-only location).")
        return item(f)
    }

    override fun createFile(parent: FileItem, name: String): FileItem {
        validateName(name)
        val f = File(parent.id, name)
        if (f.exists()) throw FilesException("\"$name\" already exists.")
        try {
            if (!f.createNewFile()) throw FilesException("Could not create the file.")
        } catch (e: java.io.IOException) {
            throw FilesException("Could not create the file (permission denied or read-only location).", e)
        }
        return item(f)
    }

    override fun rename(item: FileItem, newName: String): FileItem {
        validateName(newName)
        val from = File(item.id)
        val to = File(from.parentFile, newName)
        if (to.exists()) throw FilesException("\"$newName\" already exists.")
        if (!from.renameTo(to)) throw FilesException("Could not rename (permission denied or read-only location).")
        return item(to)
    }

    override fun deleteOne(item: FileItem) {
        if (!File(item.id).delete()) throw FilesException("Could not delete \"${item.name}\" (permission denied or not empty).")
    }

    override fun openInput(item: FileItem): InputStream = FileInputStream(item.id)

    override fun openOutput(parent: FileItem, name: String): OutputStream {
        validateName(name)
        return FileOutputStream(File(parent.id, name))
    }

    override fun moveWithin(item: FileItem, newParent: FileItem): Boolean =
        File(item.id).renameTo(File(newParent.id, item.name))
}

