package com.superapp.app.features.api

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import org.json.JSONObject
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** AES-256-GCM encryption with a non-exportable key held in the Android Keystore. No extra dependency. */
object ApiSecureStore {
    private const val ALIAS = "super_app_api_playground_key"
    private const val PROVIDER = "AndroidKeyStore"
    private const val TRANSFORMATION = "AES/GCM/NoPadding"
    private const val IV_SIZE = 12

    @Volatile
    private var cachedKey: SecretKey? = null

    private fun key(): SecretKey {
        cachedKey?.let { return it }
        val ks = KeyStore.getInstance(PROVIDER).apply { load(null) }
        val existing = ks.getKey(ALIAS, null) as? SecretKey
        val result = existing ?: KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, PROVIDER).run {
            init(
                KeyGenParameterSpec.Builder(
                    ALIAS,
                    KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
                )
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setKeySize(256)
                    .build()
            )
            generateKey()
        }
        cachedKey = result
        return result
    }

    /** Returns null on failure. Callers must never fall back to storing plain text. */
    fun encrypt(plain: String): String? = try {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, key())
        val encrypted = cipher.doFinal(plain.toByteArray(Charsets.UTF_8))
        Base64.encodeToString(cipher.iv + encrypted, Base64.NO_WRAP)
    } catch (e: Exception) {
        null
    }

    fun decrypt(encoded: String): String? = try {
        val all = Base64.decode(encoded, Base64.NO_WRAP)
        val iv = all.copyOfRange(0, IV_SIZE)
        val data = all.copyOfRange(IV_SIZE, all.size)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, iv))
        String(cipher.doFinal(data), Charsets.UTF_8)
    } catch (e: Exception) {
        null
    }
}

/**
 * Persists the whole workspace (blocks, variables, history) as ONE encrypted blob,
 * so tokens, passwords, headers and bodies are never stored in plain text.
 */
class ApiStorage(context: Context) {
    private val prefs = context.applicationContext
        .getSharedPreferences("api_playground_store", Context.MODE_PRIVATE)

    fun load(): StoredWorkspace {
        val encrypted = prefs.getString(KEY_WORKSPACE, null) ?: return StoredWorkspace()
        val text = ApiSecureStore.decrypt(encrypted) ?: return StoredWorkspace()
        return try {
            storedWorkspaceFromJson(JSONObject(text))
        } catch (e: Exception) {
            StoredWorkspace()
        }
    }

    fun save(workspace: StoredWorkspace) {
        val encrypted = ApiSecureStore.encrypt(workspace.toJson().toString()) ?: return
        prefs.edit().putString(KEY_WORKSPACE, encrypted).apply()
    }

    private companion object {
        const val KEY_WORKSPACE = "workspace_encrypted"
    }
}
