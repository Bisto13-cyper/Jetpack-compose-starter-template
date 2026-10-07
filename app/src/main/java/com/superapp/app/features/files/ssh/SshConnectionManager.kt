package com.superapp.app.features.files.ssh

import android.content.Context
import android.util.Base64
import com.jcraft.jsch.ChannelSftp
import com.jcraft.jsch.HostKey
import com.jcraft.jsch.HostKeyRepository
import com.jcraft.jsch.JSch
import com.jcraft.jsch.JSchException
import com.jcraft.jsch.KeyPair
import com.jcraft.jsch.Session
import com.jcraft.jsch.UserInfo
import com.superapp.app.features.files.model.HostCheck
import com.superapp.app.features.files.model.HostVerificationException
import java.io.ByteArrayOutputStream
import java.security.MessageDigest

enum class KeyKind { ECDSA_256, RSA_3072 }

/**
 * Real SSH through JSch (com.github.mwiede:jsch). Security rules enforced here:
 *  - StrictHostKeyChecking = yes with OUR repository: unknown hosts are refused until the user confirms the
 *    fingerprint; changed host keys are refused with a warning. Nothing is accepted silently.
 *  - Passwords / private keys are decrypted only while connecting and are never logged.
 *  - Host, port, user and credentials always come from the saved profile (nothing hardcoded).
 */
class SshConnectionManager(context: Context, private val profiles: SshProfileStore) {

    val knownHosts = KnownHostsStore(context)
    private val sessions = HashMap<String, Session>()
    private val lock = Any()

    fun isConnected(profileId: String): Boolean = synchronized(lock) { sessions[profileId]?.isConnected == true }

    /** Blocking. Throws HostVerificationException (ask the user) or a JSch/IO exception. */
    fun connect(profile: SshProfile) {
        synchronized(lock) {
            sessions.remove(profile.id)?.disconnect()
            sessions[profile.id] = open(profile)
        }
    }

    fun disconnect(profileId: String) {
        synchronized(lock) { sessions.remove(profileId)?.disconnect() }
    }

    fun disconnectAll() {
        synchronized(lock) { sessions.values.forEach { it.disconnect() }; sessions.clear() }
    }

    fun trustHost(check: HostCheck) {
        when (check) {
            is HostCheck.Unknown -> knownHosts.put(check.host, check.keyBlob)
            is HostCheck.Changed -> knownHosts.put(check.host, check.keyBlob)
        }
    }

    fun forgetHost(profile: SshProfile) {
        knownHosts.remove(hostKeyName(profile.host, profile.port))
    }

    /** Opens a fresh SFTP channel (reconnects once if the session died). Caller must disconnect() it. */
    fun openSftp(profile: SshProfile): ChannelSftp {
        for (attempt in 0..1) {
            try {
                val s = sessionFor(profile)
                val ch = s.openChannel("sftp") as ChannelSftp
                ch.connect(15_000)
                return ch
            } catch (e: JSchException) {
                if (attempt == 1) throw e
                synchronized(lock) { sessions.remove(profile.id)?.disconnect() }
            }
        }
        throw IllegalStateException()
    }

    fun <T> withSftp(profile: SshProfile, block: (ChannelSftp) -> T): T {
        val ch = openSftp(profile)
        try {
            return block(ch)
        } finally {
            ch.disconnect()
        }
    }

    private fun sessionFor(profile: SshProfile): Session = synchronized(lock) {
        sessions[profile.id]?.takeIf { it.isConnected } ?: open(profile).also { sessions[profile.id] = it }
    }

    private fun open(profile: SshProfile): Session {
        val secrets = profiles.secrets(profile.id)
        val jsch = JSch()
        val repo = Repo(knownHosts)
        jsch.hostKeyRepository = repo
        if (profile.auth == SshAuth.KEY) {
            val key = secrets.privateKey ?: throw JSchException("invalid privatekey")
            jsch.addIdentity(
                "files-key-${profile.id}", key.toByteArray(Charsets.UTF_8), null,
                secrets.passphrase?.toByteArray(Charsets.UTF_8)
            )
        }
        val session = jsch.getSession(profile.username, profile.host, profile.port)
        if (profile.auth == SshAuth.PASSWORD) session.setPassword(secrets.password ?: "")
        session.setConfig("StrictHostKeyChecking", "yes")
        session.setConfig(
            "PreferredAuthentications",
            if (profile.auth == SshAuth.KEY) "publickey" else "password,keyboard-interactive"
        )
        session.userInfo = NoPromptUserInfo
        session.setServerAliveInterval(15_000)
        session.setServerAliveCountMax(3)
        try {
            session.connect(15_000)
        } catch (e: JSchException) {
            session.disconnect()
            val blob = repo.lastBlob
            val host = repo.lastHost
            if (blob != null && host != null) {
                val fp = fingerprint(blob)
                val type = keyType(blob)
                when (repo.lastResult) {
                    HostKeyRepository.NOT_INCLUDED ->
                        throw HostVerificationException(HostCheck.Unknown(host, type, fp, blob))
                    HostKeyRepository.CHANGED -> throw HostVerificationException(
                        HostCheck.Changed(host, type, fp, fingerprint(knownHosts.get(host) ?: ByteArray(0)), blob)
                    )
                }
            }
            throw e
        }
        return session
    }

    /** JSch must never prompt through any UI; answers come only from the saved profile. */
    private object NoPromptUserInfo : UserInfo {
        override fun getPassphrase(): String? = null
        override fun getPassword(): String? = null
        override fun promptPassword(message: String?) = false
        override fun promptPassphrase(message: String?) = false
        override fun promptYesNo(message: String?) = false // never auto-accept anything
        override fun showMessage(message: String?) {}
    }

    private class Repo(private val known: KnownHostsStore) : HostKeyRepository {
        var lastHost: String? = null
        var lastBlob: ByteArray? = null
        var lastResult = -1

        override fun check(host: String?, key: ByteArray?): Int {
            lastHost = host
            lastBlob = key
            val stored = if (host == null) null else known.get(host)
            lastResult = when {
                host == null || key == null -> HostKeyRepository.NOT_INCLUDED
                stored == null -> HostKeyRepository.NOT_INCLUDED
                stored.contentEquals(key) -> HostKeyRepository.OK
                else -> HostKeyRepository.CHANGED
            }
            return lastResult
        }

        override fun add(hostkey: HostKey?, ui: UserInfo?) {}
        override fun remove(host: String?, type: String?) {}
        override fun remove(host: String?, type: String?, key: ByteArray?) {}
        override fun getKnownHostsRepositoryID(): String = "superapp-files-known-hosts"
        override fun getHostKey(): Array<HostKey> = emptyArray()
        override fun getHostKey(host: String?, type: String?): Array<HostKey> = emptyArray()
    }

    companion object {
        fun hostKeyName(host: String, port: Int) = if (port == 22) host else "[$host]:$port"

        fun fingerprint(blob: ByteArray): String =
            if (blob.isEmpty()) "(none)"
            else "SHA256:" + Base64.encodeToString(MessageDigest.getInstance("SHA-256").digest(blob), Base64.NO_WRAP or Base64.NO_PADDING)

        fun keyType(blob: ByteArray): String = try {
            val len = ((blob[0].toInt() and 0xff) shl 24) or ((blob[1].toInt() and 0xff) shl 16) or
                ((blob[2].toInt() and 0xff) shl 8) or (blob[3].toInt() and 0xff)
            String(blob, 4, len, Charsets.US_ASCII)
        } catch (_: Exception) { "unknown" }

        /** Returns (privateKeyPem, publicKeyLine). Public line goes into Termux ~/.ssh/authorized_keys. */
        fun generateKey(kind: KeyKind, comment: String): Pair<String, String> {
            val jsch = JSch()
            val kp = when (kind) {
                KeyKind.ECDSA_256 -> KeyPair.genKeyPair(jsch, KeyPair.ECDSA, 256)
                KeyKind.RSA_3072 -> KeyPair.genKeyPair(jsch, KeyPair.RSA, 3072)
            }
            try {
                val priv = ByteArrayOutputStream()
                kp.writePrivateKey(priv)
                val pub = ByteArrayOutputStream()
                kp.writePublicKey(pub, comment)
                return String(priv.toByteArray(), Charsets.UTF_8) to String(pub.toByteArray(), Charsets.UTF_8).trim()
            } finally {
                kp.dispose()
            }
        }
    }
}

