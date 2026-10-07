package com.superapp.app.features.files.model

import android.webkit.MimeTypeMap
import java.io.EOFException
import java.io.FileNotFoundException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.text.DateFormat
import java.util.Date
import java.util.zip.ZipException

enum class SourceKind { LOCAL, SAF, SSH }

/**
 * One file/folder of any source.
 * id: LOCAL = absolute path, SAF = document Uri string, SSH = absolute remote path.
 * sourceId always says WHICH file system the id belongs to, so local and remote paths can never be mixed up.
 */
data class FileItem(
    val id: String,
    val name: String,
    val isDirectory: Boolean,
    val size: Long = -1L,
    val modified: Long = 0L,
    val mime: String? = null,
    val sourceId: String,
    val parentId: String? = null,
    val isSymlink: Boolean = false,
) {
    val extension: String get() = extensionOf(name, isDirectory)
}

fun extensionOf(name: String, isDirectory: Boolean): String {
    if (isDirectory) return ""
    val i = name.lastIndexOf('.')
    return if (i <= 0 || i == name.length - 1) "" else name.substring(i + 1).lowercase()
}

fun mimeFor(name: String): String? =
    MimeTypeMap.getSingleton().getMimeTypeFromExtension(extensionOf(name, false))

data class Progress(val label: String, val done: Long, val total: Long)

enum class ConflictPolicy { KEEP_BOTH, OVERWRITE, SKIP }

data class FileDetails(
    val name: String,
    val path: String,
    val isDirectory: Boolean,
    val size: Long,
    val extension: String,
    val mime: String?,
    val modified: Long,
    val created: Long,
    val itemCount: Int?,
    val countIsPartial: Boolean,
    val totalSize: Long?,
)

/** An error whose message is already safe and readable for the user. */
open class FilesException(message: String, cause: Throwable? = null) : Exception(message, cause)

/** SSH host key problems. The UI must ask the user; the key is never accepted silently. */
sealed interface HostCheck {
    class Unknown(
        val host: String, val keyType: String, val fingerprint: String, val keyBlob: ByteArray,
    ) : HostCheck

    class Changed(
        val host: String, val keyType: String, val fingerprint: String,
        val knownFingerprint: String, val keyBlob: ByteArray,
    ) : HostCheck
}

class HostVerificationException(val check: HostCheck) : FilesException("SSH host key not verified")

fun Throwable.userMessage(): String {
    if (this is FilesException) return message ?: "Operation failed."
    val m = message.orEmpty()
    return when {
        m.contains("Permission denied", true) || m.contains("EACCES") -> "Permission denied."
        m.contains("ENOSPC") || m.contains("No space left", true) -> "Disk full."
        this is FileNotFoundException -> "File or folder not found."
        this is UnknownHostException -> "Unknown host. Check the address."
        this is ConnectException || m.contains("Connection refused", true) ->
            "Could not connect. Is sshd running and is the port correct?"
        this is SocketTimeoutException || m.contains("timeout", true) || m.contains("timed out", true) ->
            "Connection timed out."
        javaClass.name.startsWith("com.jcraft.jsch") -> sshMessage(m)
        this is ZipException -> "The archive is corrupted or unsupported (e.g. password-protected)."
        this is EOFException -> "Unexpected end of data (corrupted or interrupted)."
        else -> "Operation failed (${javaClass.simpleName})."
    }
}

private fun sshMessage(m: String): String = when {
    m.contains("Auth fail", true) || m.contains("Auth cancel", true) ->
        "SSH authentication failed. Check the username, password or key."
    m.contains("privatekey", true) || m.contains("passphrase", true) ->
        "The private key is invalid or needs the correct passphrase."
    m.contains("No such file", true) -> "File or folder not found."
    m.contains("session is down", true) || m.contains("channel is not opened", true) ||
        m.contains("connection is closed", true) -> "SSH connection lost. Try again."
    m.contains("Algorithm negotiation", true) ->
        "No common SSH algorithm with the server (see the Termux setup notes)."
    else -> "SSH error."
}

fun formatSize(bytes: Long): String {
    if (bytes < 0) return "—"
    if (bytes < 1024) return "$bytes B"
    val units = arrayOf("KB", "MB", "GB", "TB")
    var v = bytes.toDouble()
    var i = -1
    while (v >= 1024 && i < units.lastIndex) { v /= 1024; i++ }
    return String.format("%.1f %s", v, units[i])
}

fun formatDate(ms: Long): String =
    if (ms <= 0) "—" else DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(ms))

