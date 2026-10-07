package com.superapp.app.features.password.data

import java.security.GeneralSecurityException
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/**
 * Thin wrapper around standard JCA primitives only:
 *  - PBKDF2WithHmacSHA256 for password -> key derivation (random salt)
 *  - AES-256-GCM for authenticated encryption (random 96-bit IV, 128-bit tag)
 * No custom cryptography. No network, no logging.
 */
internal object VaultCrypto {
    const val KEY_BYTES = 32
    const val SALT_BYTES = 16
    const val IV_BYTES = 12
    const val TAG_BITS = 128
    const val TAG_BYTES = 16

    const val PBKDF2_ITERATIONS = 600_000
    const val MIN_ACCEPTED_ITERATIONS = 100_000
    const val MAX_ACCEPTED_ITERATIONS = 5_000_000

    private const val KDF = "PBKDF2WithHmacSHA256" // Android API 26+
    private const val TRANSFORM = "AES/GCM/NoPadding"
    private val rng = SecureRandom()

    fun randomBytes(size: Int): ByteArray = ByteArray(size).also { rng.nextBytes(it) }

    fun deriveKey(secret: CharArray, salt: ByteArray, iterations: Int): ByteArray {
        val spec = PBEKeySpec(secret, salt, iterations, KEY_BYTES * 8)
        try {
            return SecretKeyFactory.getInstance(KDF).generateSecret(spec).encoded
        } finally {
            spec.clearPassword()
        }
    }

    /** Returns IV || ciphertext || tag. [aad] is authenticated but not encrypted. */
    fun seal(key: ByteArray, plaintext: ByteArray, aad: ByteArray): ByteArray {
        val iv = randomBytes(IV_BYTES)
        val cipher = Cipher.getInstance(TRANSFORM)
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(TAG_BITS, iv))
        cipher.updateAAD(aad)
        return iv + cipher.doFinal(plaintext)
    }

    /** Throws [GeneralSecurityException] if the key is wrong or the data/AAD was tampered with. */
    fun open(key: ByteArray, blob: ByteArray, aad: ByteArray): ByteArray {
        if (blob.size < IV_BYTES + TAG_BYTES) throw GeneralSecurityException("Invalid data")
        val iv = blob.copyOfRange(0, IV_BYTES)
        val cipher = Cipher.getInstance(TRANSFORM)
        cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(TAG_BITS, iv))
        cipher.updateAAD(aad)
        return cipher.doFinal(blob, IV_BYTES, blob.size - IV_BYTES)
    }

    fun wipe(bytes: ByteArray?) {
        bytes?.fill(0)
    }
}
