package com.superapp.app.features.files.source

import com.jcraft.jsch.ChannelSftp
import com.jcraft.jsch.SftpException
import com.superapp.app.features.files.model.FileItem
import com.superapp.app.features.files.model.FilesException
import com.superapp.app.features.files.model.SourceKind
import com.superapp.app.features.files.model.mimeFor
import com.superapp.app.features.files.ssh.SshConnectionManager
import com.superapp.app.features.files.ssh.SshProfile
import java.io.FilterInputStream
import java.io.FilterOutputStream
import java.io.InputStream
import java.io.OutputStream

/**
 * Remote files over SFTP (the file protocol of the normal OpenSSH server that Termux provides).
 * Item ids are ABSOLUTE REMOTE paths and sourceId is "ssh:<profile>", so they can never be confused with local paths.
 * Every operation uses its own SFTP channel; streams close their channel when closed.
 */
class SshFileSource(val profile: SshProfile, private val manager: SshConnectionManager) : FileSource {
    override val id = "ssh:${profile.id}"
    override val kind = SourceKind.SSH
    override val label = "Termux / SSH: ${profile.display}"

    /** JSch treats * ? [ ] \ in paths as glob patterns; escape them for existing paths. */
    private fun q(p: String) = buildString {
        for (c in p) { if (c == '\\' || c == '*' || c == '?' || c == '[' || c == ']') append('\\'); append(c) }
    }

    private fun checkName(name: String) {
        validateName(name)
        if (name.any { it == '*' || it == '?' || it == '[' || it == ']' || it == '\\' }) {
            throw FilesException("Names with * ? [ ] \\ are not supported over SSH.")
        }
    }

    private fun join(dir: String, name: String) = if (dir.endsWith("/")) dir + name else "$dir/$name"

    private fun <T> sftp(block: (ChannelSftp) -> T): T = try {
        manager.withSftp(profile, block)
    } catch (e: SftpException) {
        throw mapSftp(e)
    }

    private fun mapSftp(e: SftpException): FilesException = when (e.id) {
        ChannelSftp.SSH_FX_NO_SUCH_FILE -> FilesException("File or folder not found.", e)
        ChannelSftp.SSH_FX_PERMISSION_DENIED -> FilesException("Permission denied.", e)
        else -> FilesException("Remote operation failed.", e)
    }

    private fun toItem(ch: ChannelSftp, parent: String, e: ChannelSftp.LsEntry): FileItem {
        val path = join(parent, e.filename)
        var dir = e.attrs.isDir
        val link = e.attrs.isLink
        var size = e.attrs.size
        var mtime = e.attrs.mTime.toLong() * 1000
        if (link) {
            try {
                val t = ch.stat(q(path))
                dir = t.isDir; size = t.size; mtime = t.mTime.toLong() * 1000
            } catch (_: Exception) { dir = false }
        }
        return FileItem(path, e.filename, dir, if (dir) -1L else size, mtime,
            if (dir) null else mimeFor(e.filename), id, parent, link)
    }

    override fun roots(): List<FileItem> = sftp { ch ->
        val home = ch.home
        listOf(
            FileItem(home, "Home (~)", true, sourceId = id, parentId = null),
            FileItem("/", "/ (root)", true, sourceId = id, parentId = null),
        )
    }

    override fun list(dir: FileItem): List<FileItem> = sftp { ch ->
        val out = mutableListOf<FileItem>()
        for (e in ch.ls(q(dir.id))) {
            val entry = e as? ChannelSftp.LsEntry ?: continue
            if (entry.filename == "." || entry.filename == "..") continue
            out += toItem(ch, dir.id, entry)
        }
        out
    }

    override fun stat(id: String): FileItem? = try {
        sftp { ch ->
            val a = ch.stat(q(id))
            val name = id.trimEnd('/').substringAfterLast('/').ifEmpty { "/" }
            FileItem(id, name, a.isDir, if (a.isDir) -1L else a.size, a.mTime.toLong() * 1000,
                if (a.isDir) null else mimeFor(name), this.id, id.trimEnd('/').substringBeforeLast('/', "/"))
        }
    } catch (e: FilesException) {
        if (e.message?.contains("not found") == true) null else throw e
    }

    override fun createFolder(parent: FileItem, name: String): FileItem {
        checkName(name)
        val path = join(parent.id, name)
        sftp { ch -> ch.mkdir(path) }
        return stat(path) ?: throw FilesException("Could not create the folder.")
    }

    override fun createFile(parent: FileItem, name: String): FileItem {
        checkName(name)
        val path = join(parent.id, name)
        if (stat(path) != null) throw FilesException("\"$name\" already exists.")
        sftp { ch -> ch.put(path, ChannelSftp.OVERWRITE).close() }
        return stat(path) ?: throw FilesException("Could not create the file.")
    }

    override fun rename(item: FileItem, newName: String): FileItem {
        checkName(newName)
        val dest = join(item.parentId ?: item.id.substringBeforeLast('/', "/"), newName)
        if (stat(dest) != null) throw FilesException("\"$newName\" already exists.")
        sftp { ch -> ch.rename(q(item.id), dest) }
        return stat(dest) ?: item.copy(id = dest, name = newName)
    }

    override fun deleteOne(item: FileItem) {
        sftp { ch -> if (item.isDirectory && !item.isSymlink) ch.rmdir(q(item.id)) else ch.rm(q(item.id)) }
    }

    override fun openInput(item: FileItem): InputStream {
        val ch = try { manager.openSftp(profile) } catch (e: com.jcraft.jsch.JSchException) { throw e }
        try {
            val s = ch.get(q(item.id))
            return object : FilterInputStream(s) {
                override fun close() { try { super.close() } finally { ch.disconnect() } }
            }
        } catch (e: SftpException) {
            ch.disconnect()
            throw mapSftp(e)
        }
    }

    override fun openOutput(parent: FileItem, name: String): OutputStream {
        checkName(name)
        val path = join(parent.id, name)
        val ch = manager.openSftp(profile)
        try {
            val s = ch.put(path, ChannelSftp.OVERWRITE)
            return object : FilterOutputStream(s) {
                override fun write(b: ByteArray, off: Int, len: Int) { out.write(b, off, len) }
                override fun close() { try { super.close() } finally { ch.disconnect() } }
            }
        } catch (e: SftpException) {
            ch.disconnect()
            throw mapSftp(e)
        }
    }

    override fun moveWithin(item: FileItem, newParent: FileItem): Boolean = try {
        val dest = join(newParent.id, item.name)
        sftp { ch -> ch.rename(q(item.id), dest) }
        true
    } catch (_: Exception) { false }
}

