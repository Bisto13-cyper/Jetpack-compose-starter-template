package com.superapp.app.core.security

import android.content.Context
import android.util.Base64
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.nio.charset.StandardCharsets
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

data class SecuritySettings(
    val appLockEnabled: Boolean = false,
    val biometricsEnabled: Boolean = false,
    val lockedNodeIds: Set<String> = emptySet()
)

class SecurityRepository(context: Context) {

    private val prefs = context.applicationContext
        .getSharedPreferences(FILE_NAME, Context.MODE_PRIVATE)

    private val _settings = MutableStateFlow(load())
    val settings: StateFlow<SecuritySettings> = _settings.asStateFlow()

    fun setAppLockEnabled(enabled: Boolean) {
        update { it.copy(appLockEnabled = enabled) }
    }

    fun setBiometricsEnabled(enabled: Boolean) {
        update { it.copy(biometricsEnabled = enabled) }
    }

    fun setNodeLocked(nodeId: String, locked: Boolean) {
        update {
            val ids = it.lockedNodeIds.toMutableSet()
            if (locked) ids += nodeId else ids -= nodeId
            it.copy(lockedNodeIds = ids)
        }
    }

    fun isNodeLocked(nodeId: String): Boolean =
        nodeId in _settings.value.lockedNodeIds

    private fun update(change: (SecuritySettings) -> SecuritySettings) {
        val updated = change(_settings.value)
        _settings.value = updated
        save(updated)
    }

    private fun load(): SecuritySettings {
        val encrypted = prefs.getString(K_STATE, null) ?: return SecuritySettings()
        val plain = decrypt(encrypted) ?: return SecuritySettings()
        val parts = plain.split('|', limit = 3)
        if (parts.size != 3) return SecuritySettings()

        val nodes = if (parts[2].isBlank()) {
            emptySet()
        } else {
            parts[2].split(',').filter { it.isNotBlank() }.toSet()
        }

        return SecuritySettings(
            appLockEnabled = parts[0] == "1",
            biometricsEnabled = parts[1] == "1",
            lockedNodeIds = nodes
        )
    }

    private fun save(settings: SecuritySettings) {
        val nodes = settings.lockedNodeIds.sorted().joinToString(",")
        val plain = "${if (settings.appLockEnabled) 1 else 0}|" +
            "${if (settings.biometricsEnabled) 1 else 0}|$nodes"
        prefs.edit().putString(K_STATE, encrypt(plain)).apply()
    }

    private fun encrypt(value: String): String {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, key())
        val encrypted = cipher.doFinal(value.toByteArray(StandardCharsets.UTF_8))
        val combined = cipher.iv + encrypted
        return Base64.encodeToString(combined, Base64.NO_WRAP)
    }

    private fun decrypt(value: String): String? = runCatching {
        val combined = Base64.decode(value, Base64.NO_WRAP)
        require(combined.size > GCM_IV_LENGTH)
        val iv = combined.copyOfRange(0, GCM_IV_LENGTH)
        val encrypted = combined.copyOfRange(GCM_IV_LENGTH, combined.size)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(
            Cipher.DECRYPT_MODE,
            key(),
            GCMParameterSpec(GCM_TAG_LENGTH, iv)
        )
        String(cipher.doFinal(encrypted), StandardCharsets.UTF_8)
    }.getOrNull()

    private fun key(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEY_STORE).apply { load(null) }
        val existing = keyStore.getKey(KEY_ALIAS, null)
        if (existing is SecretKey) return existing

        val generator = KeyGenerator.getInstance(KEY_ALGORITHM, ANDROID_KEY_STORE)
        generator.init(
            android.security.keystore.KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                android.security.keystore.KeyProperties.PURPOSE_ENCRYPT or
                    android.security.keystore.KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(android.security.keystore.KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(android.security.keystore.KeyProperties.ENCRYPTION_PADDING_NONE)
                .build()
        )
        return generator.generateKey()
    }

    private companion object {
        const val FILE_NAME = "super_app_security"
        const val K_STATE = "encrypted_security_state"
        const val KEY_ALIAS = "super_app_security_state_key"
        const val ANDROID_KEY_STORE = "AndroidKeyStore"
        const val KEY_ALGORITHM = "AES"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val GCM_IV_LENGTH = 12
        const val GCM_TAG_LENGTH = 128
    }
}
