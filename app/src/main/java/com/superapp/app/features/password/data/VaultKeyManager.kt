package com.superapp.app.features.password.data

import android.content.Context
import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyPermanentlyInvalidatedException
import android.security.keystore.KeyProperties
import android.security.keystore.StrongBoxUnavailableException
import android.util.AtomicFile
import android.util.Base64
import androidx.annotation.RequiresApi
import org.json.JSONObject
import java.io.File
import java.security.GeneralSecurityException
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Key hierarchy
 *
 *   DEK (random 256-bit, encrypts vault.dat)
 *    |-- wrapped by KEK = PBKDF2(vault password, salt)          [AES-GCM]
 *    |      and that blob is encrypted again by a non-exportable
 *    |      Android Keystore key ("pepper")  -> offline brute force needs this device
 *    '-- (optional) wrapped by an auth-bound Keystore key         [biometric / device credential, API 30+]
 *
 * The vault password is never stored. It is "verified" by successfully
 * unwrapping the DEK (the GCM tag fails for a wrong password).
 * Only non-secret data (salt, iterations, counters) is stored in plain JSON.
 */
internal class VaultKeyManager(context: Context) {

    sealed interface Attempt {
        class Success(val dek: ByteArray) : Attempt
        object WrongPassword : Attempt
        class LockedOut(val remainingMs: Long) : Attempt
        object Error : Attempt
    }

    private data class Meta(
        val iterations: Int,
        val salt: ByteArray,
        val pinWrap: ByteArray,
        val bioIv: ByteArray?,
        val bioWrap: ByteArray?,
        val fails: Int,
        val lockUntil: Long,
        val autoLockMs: Long,
    ) {
        fun toJson(): String = JSONObject()
            .put("v", 1)
            .put("iter", iterations)
            .put("salt", b64(salt))
            .put("pin", b64(pinWrap))
            .put("fails", fails)
            .put("lockUntil", lockUntil)
            .put("autoLockMs", autoLockMs)
            .apply {
                if (bioIv != null && bioWrap != null) {
                    put("bioIv", b64(bioIv))
                    put("bio", b64(bioWrap))
                }
            }.toString()

        companion object {
            fun parse(text: String): Meta {
                val o = JSONObject(text)
                val bio = o.optString("bio", "")
                val bioIv = o.optString("bioIv", "")
                return Meta(
                    iterations = o.getInt("iter"),
                    salt = unb64(o.getString("salt")),
                    pinWrap = unb64(o.getString("pin")),
                    bioIv = if (bio.isNotEmpty() && bioIv.isNotEmpty()) unb64(bioIv) else null,
                    bioWrap = if (bio.isNotEmpty() && bioIv.isNotEmpty()) unb64(bio) else null,
                    fails = o.optInt("fails", 0),
                    lockUntil = o.optLong("lockUntil", 0L),
                    autoLockMs = o.optLong("autoLockMs", DEFAULT_AUTOLOCK_MS),
                )
            }
        }
    }

    private val dir = File(context.filesDir, "vault").apply { mkdirs() }
    private val metaFile = AtomicFile(File(dir, "vault_meta.json"))
    private val lock = Any()

    fun isSetUp(): Boolean = metaFile.baseFile.exists()

    // ---------------------------------------------------------------- password

    fun setUp(password: CharArray): ByteArray = synchronized(lock) {
        val dek = VaultCrypto.randomBytes(VaultCrypto.KEY_BYTES)
        val salt = VaultCrypto.randomBytes(VaultCrypto.SALT_BYTES)
        val kek = VaultCrypto.deriveKey(password, salt, VaultCrypto.PBKDF2_ITERATIONS)
        try {
            val wrap = pepperEncrypt(VaultCrypto.seal(kek, dek, AAD_DEK))
            writeMeta(
                Meta(VaultCrypto.PBKDF2_ITERATIONS, salt, wrap, null, null, 0, 0L, DEFAULT_AUTOLOCK_MS)
            )
        } catch (e: Exception) {
            VaultCrypto.wipe(dek)
            throw e
        } finally {
            VaultCrypto.wipe(kek)
        }
        dek
    }

    fun unlockWithPassword(password: CharArray): Attempt = synchronized(lock) {
        val meta = readMeta() ?: return Attempt.Error
        val now = System.currentTimeMillis()
        if (meta.lockUntil > now) return Attempt.LockedOut(meta.lockUntil - now)

        val inner = try {
            pepperDecrypt(meta.pinWrap)
        } catch (e: Exception) {
            return Attempt.Error // Keystore problem, NOT a wrong password: do not count it
        }
        var kek: ByteArray? = null
        try {
            kek = VaultCrypto.deriveKey(password, meta.salt, meta.iterations)
            val dek = VaultCrypto.open(kek, inner, AAD_DEK)
            writeMeta(meta.copy(fails = 0, lockUntil = 0L))
            return Attempt.Success(dek)
        } catch (e: GeneralSecurityException) {
            val fails = meta.fails + 1
            val until = if (fails >= FREE_ATTEMPTS) {
                val step = (fails - FREE_ATTEMPTS).coerceAtMost(6)
                now + (BASE_LOCKOUT_MS shl step).coerceAtMost(MAX_LOCKOUT_MS)
            } else 0L
            try { writeMeta(meta.copy(fails = fails, lockUntil = until)) } catch (_: Exception) {}
            return if (until > now) Attempt.LockedOut(until - now) else Attempt.WrongPassword
        } catch (e: Exception) {
            return Attempt.Error
        } finally {
            VaultCrypto.wipe(kek)
            VaultCrypto.wipe(inner)
        }
    }

    /** Re-wraps the (already unlocked) [dek] under a new password. Biometric wrap stays valid. */
    fun changePassword(dek: ByteArray, newPassword: CharArray) = synchronized(lock) {
        val meta = readMeta() ?: throw IllegalStateException()
        val salt = VaultCrypto.randomBytes(VaultCrypto.SALT_BYTES)
        val kek = VaultCrypto.deriveKey(newPassword, salt, VaultCrypto.PBKDF2_ITERATIONS)
        try {
            val wrap = pepperEncrypt(VaultCrypto.seal(kek, dek, AAD_DEK))
            writeMeta(meta.copy(iterations = VaultCrypto.PBKDF2_ITERATIONS, salt = salt, pinWrap = wrap, fails = 0, lockUntil = 0L))
        } finally {
            VaultCrypto.wipe(kek)
        }
    }

    // --------------------------------------------------------------- auto-lock

    fun getAutoLockMs(): Long = readMeta()?.autoLockMs ?: DEFAULT_AUTOLOCK_MS

    fun setAutoLockMs(ms: Long) = synchronized(lock) {
        readMeta()?.let { writeMeta(it.copy(autoLockMs = ms)) }
    }

    // --------------------------------------------------------------- biometric

    fun isBiometricEnrolled(): Boolean = readMeta()?.bioWrap != null

    /** Cipher (ENCRYPT) bound to a fresh auth-required Keystore key. Must be used through BiometricPrompt.CryptoObject. */
    @RequiresApi(Build.VERSION_CODES.R)
    fun newBiometricEncryptCipher(): Cipher {
        deleteKey(BIO_ALIAS)
        val key = generateBioKey()
        return Cipher.getInstance(TRANSFORM).apply { init(Cipher.ENCRYPT_MODE, key) }
    }

    /** Call with the cipher returned from a successful BiometricPrompt. */
    fun storeBiometricWrap(cipher: Cipher, dek: ByteArray) = synchronized(lock) {
        val meta = readMeta() ?: throw IllegalStateException()
        val wrap = cipher.doFinal(dek)
        writeMeta(meta.copy(bioIv = cipher.iv, bioWrap = wrap))
    }

    /** Returns null if biometrics are not enrolled or the key was invalidated (new fingerprint added etc.). */
    fun newBiometricDecryptCipher(): Cipher? {
        val meta = readMeta() ?: return null
        val iv = meta.bioIv ?: return null
        return try {
            val ks = KeyStore.getInstance(ANDROID_KS).apply { load(null) }
            val key = ks.getKey(BIO_ALIAS, null) as? SecretKey ?: return null
            Cipher.getInstance(TRANSFORM).apply {
                init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(VaultCrypto.TAG_BITS, iv))
            }
        } catch (e: KeyPermanentlyInvalidatedException) {
            disableBiometric()
            null
        } catch (e: Exception) {
            null
        }
    }

    fun unwrapWithBiometric(cipher: Cipher): ByteArray? {
        val wrap = readMeta()?.bioWrap ?: return null
        return try { cipher.doFinal(wrap) } catch (e: Exception) { null }
    }

    fun disableBiometric() = synchronized(lock) {
        deleteKey(BIO_ALIAS)
        readMeta()?.let { writeMeta(it.copy(bioIv = null, bioWrap = null)) }
    }

    // -------------------------------------------------------------------- wipe

    fun wipeAll() = synchronized(lock) {
        deleteKey(BIO_ALIAS)
        deleteKey(PEPPER_ALIAS)
        metaFile.delete()
    }

    // ---------------------------------------------------------------- internals

    private fun readMeta(): Meta? = try {
        if (!metaFile.baseFile.exists()) null else Meta.parse(String(metaFile.readFully(), Charsets.UTF_8))
    } catch (e: Exception) {
        null
    }

    private fun writeMeta(meta: Meta) {
        val out = metaFile.startWrite()
        try {
            out.write(meta.toJson().toByteArray(Charsets.UTF_8))
            metaFile.finishWrite(out)
        } catch (e: Exception) {
            metaFile.failWrite(out)
            throw e
        }
    }

    private fun keyStore(): KeyStore = KeyStore.getInstance(ANDROID_KS).apply { load(null) }

    private fun deleteKey(alias: String) {
        try { keyStore().deleteEntry(alias) } catch (_: Exception) {}
    }

    private fun pepperKey(): SecretKey {
        (keyStore().getKey(PEPPER_ALIAS, null) as? SecretKey)?.let { return it }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            try { return generatePepper(true) } catch (_: StrongBoxUnavailableException) {}
        }
        return generatePepper(false)
    }

    private fun generatePepper(strongBox: Boolean): SecretKey {
        val b = KeyGenParameterSpec.Builder(
            PEPPER_ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
        if (strongBox && Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) b.setIsStrongBoxBacked(true)
        val gen = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KS)
        gen.init(b.build())
        return gen.generateKey()
    }

    @RequiresApi(Build.VERSION_CODES.R)
    private fun generateBioKey(): SecretKey {
        val spec = KeyGenParameterSpec.Builder(
            BIO_ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            .setUserAuthenticationRequired(true)
            .setUserAuthenticationParameters(
                0, KeyProperties.AUTH_BIOMETRIC_STRONG or KeyProperties.AUTH_DEVICE_CREDENTIAL
            )
            .setInvalidatedByBiometricEnrollment(true)
            .build()
        val gen = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KS)
        gen.init(spec)
        return gen.generateKey()
    }

    private fun pepperEncrypt(plain: ByteArray): ByteArray {
        val cipher = Cipher.getInstance(TRANSFORM)
        cipher.init(Cipher.ENCRYPT_MODE, pepperKey())
        return cipher.iv + cipher.doFinal(plain)
    }

    private fun pepperDecrypt(blob: ByteArray): ByteArray {
        val iv = blob.copyOfRange(0, VaultCrypto.IV_BYTES)
        val cipher = Cipher.getInstance(TRANSFORM)
        cipher.init(Cipher.DECRYPT_MODE, pepperKey(), GCMParameterSpec(VaultCrypto.TAG_BITS, iv))
        return cipher.doFinal(blob, VaultCrypto.IV_BYTES, blob.size - VaultCrypto.IV_BYTES)
    }

    private companion object {
        const val ANDROID_KS = "AndroidKeyStore"
        const val PEPPER_ALIAS = "superapp_vault_pepper_v1"
        const val BIO_ALIAS = "superapp_vault_bio_v1"
        const val TRANSFORM = "AES/GCM/NoPadding"
        const val DEFAULT_AUTOLOCK_MS = 60_000L
        const val FREE_ATTEMPTS = 5
        const val BASE_LOCKOUT_MS = 30_000L
        const val MAX_LOCKOUT_MS = 30 * 60_000L
        val AAD_DEK = "superapp.vault.dek.v1".toByteArray()

        fun b64(b: ByteArray): String = Base64.encodeToString(b, Base64.NO_WRAP)
        fun unb64(s: String): ByteArray = Base64.decode(s, Base64.NO_WRAP)
    }
}
