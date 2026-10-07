package com.superapp.app.features.password.data

import android.util.Base64
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.security.GeneralSecurityException
import java.util.UUID
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

class VaultBackupException(val kind: Kind) : Exception(kind.name) {
    enum class Kind { NOT_A_BACKUP, UNSUPPORTED, WRONG_PASSWORD_OR_TAMPERED, CORRUPT, TOO_LARGE }
}

/**
 * Backup format (a real .zip container):
 *
 *   backup.json  - header (format, version, KDF name, iterations, random salt). Not secret, but it is
 *                  bound to the ciphertext as AES-GCM AAD, so changing it makes decryption fail.
 *   vault.enc    - IV || AES-256-GCM(PBKDF2-HMAC-SHA256(backupPassword, salt)) of the entries JSON.
 *
 * The backup password is never written anywhere. Tampering / wrong password => GCM tag failure.
 * Nothing is imported unless the entire payload decrypts AND validates.
 */
object VaultBackup {
    const val FORMAT = "superapp-vault-backup"
    const val VERSION = 1
    const val MAX_ZIP_BYTES = 32 * 1024 * 1024
    private const val MAX_HEADER_BYTES = 4 * 1024
    private const val MAX_ENTRIES = 5_000
    private const val MAX_NAME = 200
    private const val MAX_VALUE = 4_096
    private const val HEADER_NAME = "backup.json"
    private const val DATA_NAME = "vault.enc"

    internal fun create(entries: List<VaultEntry>, password: CharArray): ByteArray {
        val salt = VaultCrypto.randomBytes(VaultCrypto.SALT_BYTES)
        val header = JSONObject()
            .put("format", FORMAT)
            .put("version", VERSION)
            .put("kdf", "PBKDF2WithHmacSHA256")
            .put("iterations", VaultCrypto.PBKDF2_ITERATIONS)
            .put("cipher", "AES-256-GCM")
            .put("salt", Base64.encodeToString(salt, Base64.NO_WRAP))
            .toString().toByteArray(Charsets.UTF_8)

        val arr = JSONArray()
        entries.forEach {
            arr.put(JSONObject().put("name", it.name).put("value", it.value)
                .put("created", it.createdAt).put("updated", it.updatedAt))
        }
        val plain = JSONObject().put("entries", arr).toString().toByteArray(Charsets.UTF_8)
        val key = VaultCrypto.deriveKey(password, salt, VaultCrypto.PBKDF2_ITERATIONS)
        try {
            val blob = VaultCrypto.seal(key, plain, header)
            val bos = ByteArrayOutputStream()
            ZipOutputStream(bos).use { z ->
                z.putNextEntry(ZipEntry(HEADER_NAME)); z.write(header); z.closeEntry()
                z.putNextEntry(ZipEntry(DATA_NAME)); z.write(blob); z.closeEntry()
            }
            return bos.toByteArray()
        } finally {
            VaultCrypto.wipe(key)
            VaultCrypto.wipe(plain)
        }
    }

    /** Returns validated entries (fresh ids) or throws [VaultBackupException]. Never returns partial data. */
    internal fun parse(zip: ByteArray, password: CharArray): List<VaultEntry> {
        if (zip.size > MAX_ZIP_BYTES) throw VaultBackupException(VaultBackupException.Kind.TOO_LARGE)
        var header: ByteArray? = null
        var data: ByteArray? = null
        try {
            ZipInputStream(zip.inputStream()).use { z ->
                while (true) {
                    val e = z.nextEntry ?: break
                    when (e.name) {
                        HEADER_NAME -> header = readLimited(z, MAX_HEADER_BYTES)
                        DATA_NAME -> data = readLimited(z, MAX_ZIP_BYTES)
                        else -> throw VaultBackupException(VaultBackupException.Kind.NOT_A_BACKUP)
                    }
                    z.closeEntry()
                }
            }
        } catch (e: VaultBackupException) {
            throw e
        } catch (e: Exception) {
            throw VaultBackupException(VaultBackupException.Kind.NOT_A_BACKUP)
        }
        val h = header ?: throw VaultBackupException(VaultBackupException.Kind.NOT_A_BACKUP)
        val d = data ?: throw VaultBackupException(VaultBackupException.Kind.NOT_A_BACKUP)

        val (iterations, salt) = try {
            val o = JSONObject(String(h, Charsets.UTF_8))
            if (o.getString("format") != FORMAT) throw VaultBackupException(VaultBackupException.Kind.NOT_A_BACKUP)
            if (o.getInt("version") != VERSION ||
                o.getString("kdf") != "PBKDF2WithHmacSHA256" ||
                o.getString("cipher") != "AES-256-GCM"
            ) throw VaultBackupException(VaultBackupException.Kind.UNSUPPORTED)
            Pair(o.getInt("iterations"), Base64.decode(o.getString("salt"), Base64.NO_WRAP))
        } catch (e: VaultBackupException) {
            throw e
        } catch (e: Exception) {
            throw VaultBackupException(VaultBackupException.Kind.CORRUPT)
        }
        if (iterations !in VaultCrypto.MIN_ACCEPTED_ITERATIONS..VaultCrypto.MAX_ACCEPTED_ITERATIONS ||
            salt.size != VaultCrypto.SALT_BYTES
        ) throw VaultBackupException(VaultBackupException.Kind.CORRUPT)

        val key = VaultCrypto.deriveKey(password, salt, iterations)
        val plain = try {
            VaultCrypto.open(key, d, h)
        } catch (e: GeneralSecurityException) {
            throw VaultBackupException(VaultBackupException.Kind.WRONG_PASSWORD_OR_TAMPERED)
        } finally {
            VaultCrypto.wipe(key)
        }
        try {
            val arr = JSONObject(String(plain, Charsets.UTF_8)).getJSONArray("entries")
            if (arr.length() > MAX_ENTRIES) throw VaultBackupException(VaultBackupException.Kind.TOO_LARGE)
            val now = System.currentTimeMillis()
            return (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                val name = o.getString("name")
                val value = o.getString("value")
                if (name.isBlank() || name.length > MAX_NAME || value.length > MAX_VALUE) {
                    throw VaultBackupException(VaultBackupException.Kind.CORRUPT)
                }
                VaultEntry(UUID.randomUUID().toString(), name, value, o.optLong("created", now), o.optLong("updated", now))
            }
        } catch (e: VaultBackupException) {
            throw e
        } catch (e: Exception) {
            throw VaultBackupException(VaultBackupException.Kind.CORRUPT)
        } finally {
            VaultCrypto.wipe(plain)
        }
    }

    /** Reads at most [max] bytes; throws TOO_LARGE otherwise (also protects against zip bombs). */
    fun readLimited(input: InputStream, max: Int): ByteArray {
        val out = ByteArrayOutputStream()
        val buf = ByteArray(8192)
        var total = 0
        while (true) {
            val n = input.read(buf)
            if (n < 0) break
            total += n
            if (total > max) throw VaultBackupException(VaultBackupException.Kind.TOO_LARGE)
            out.write(buf, 0, n)
        }
        return out.toByteArray()
    }
}
