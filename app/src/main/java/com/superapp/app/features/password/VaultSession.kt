package com.superapp.app.features.password

import android.content.Context
import android.os.Build
import android.os.SystemClock
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.superapp.app.features.password.data.VaultBackup
import com.superapp.app.features.password.data.VaultBackupException
import com.superapp.app.features.password.data.VaultClipboard
import com.superapp.app.features.password.data.VaultCrypto
import com.superapp.app.features.password.data.VaultEntry
import com.superapp.app.features.password.data.VaultKeyManager
import com.superapp.app.features.password.data.VaultStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.UUID
import javax.crypto.Cipher

sealed interface UnlockOutcome {
    object Success : UnlockOutcome
    object WrongPassword : UnlockOutcome
    class LockedOut(val remainingMs: Long) : UnlockOutcome
    object Failure : UnlockOutcome
}

class ImportResult(val added: Int, val skipped: Int, val error: String?)

/**
 * Holds the unlocked state. Decrypted entries and the data key exist in memory ONLY while state == UNLOCKED;
 * [lock] zeroes the key, empties the list and clears the clipboard.
 * (JVM Strings cannot be wiped; they become unreachable on lock and are left to the GC.)
 */
class VaultSession(context: Context) {

    enum class State { NEEDS_SETUP, LOCKED, UNLOCKED }

    private val appContext = context.applicationContext
    private val keys = VaultKeyManager(appContext)
    private val store = VaultStore(appContext)
    private var dek: ByteArray? = null

    var state by mutableStateOf(if (keys.isSetUp()) State.LOCKED else State.NEEDS_SETUP)
        private set
    val entries = mutableStateListOf<VaultEntry>()
    var revealedIds by mutableStateOf<Set<String>>(emptySet())
        private set
    var autoLockMs by mutableStateOf(keys.getAutoLockMs())
        private set
    var biometricEnrolled by mutableStateOf(keys.isBiometricEnrolled())
        private set

    /** True while a system picker (SAF) / biometric UI is on top, so leaving the activity does not lock mid-operation. */
    @Volatile var externalUiOpen = false
    @Volatile var lastTouch = SystemClock.elapsedRealtime()
        private set

    fun touch() {
        val now = SystemClock.elapsedRealtime()
        if (now - lastTouch > 500) lastTouch = now
    }

    // ------------------------------------------------------------- lock / unlock

    suspend fun setUp(password: CharArray): Boolean = try {
        val newDek = withContext(Dispatchers.Default) { keys.setUp(password) }
        withContext(Dispatchers.IO) { store.save(newDek, emptyList()) }
        dek = newDek
        entries.clear()
        state = State.UNLOCKED
        touch()
        true
    } catch (e: Exception) {
        keys.wipeAll(); store.wipe()
        false
    }

    suspend fun unlockWithPassword(password: CharArray): UnlockOutcome {
        val attempt = withContext(Dispatchers.Default) { keys.unlockWithPassword(password) }
        return when (attempt) {
            is VaultKeyManager.Attempt.Success ->
                if (finishUnlock(attempt.dek)) UnlockOutcome.Success else UnlockOutcome.Failure
            VaultKeyManager.Attempt.WrongPassword -> UnlockOutcome.WrongPassword
            is VaultKeyManager.Attempt.LockedOut -> UnlockOutcome.LockedOut(attempt.remainingMs)
            VaultKeyManager.Attempt.Error -> UnlockOutcome.Failure
        }
    }

    private suspend fun finishUnlock(newDek: ByteArray): Boolean = try {
        val loaded = withContext(Dispatchers.IO) { store.load(newDek) }
        VaultCrypto.wipe(dek)
        dek = newDek
        entries.clear()
        entries.addAll(loaded.sortedBy { it.name.lowercase() })
        state = State.UNLOCKED
        touch()
        true
    } catch (e: Exception) {
        VaultCrypto.wipe(newDek)
        false
    }

    fun lock() {
        if (state == State.UNLOCKED) {
            VaultCrypto.wipe(dek)
            dek = null
            entries.clear()
            revealedIds = emptySet()
            state = State.LOCKED
            VaultClipboard.clearNow(appContext)
        }
    }

    // ------------------------------------------------------------------ reveal

    fun toggleReveal(id: String) {
        revealedIds = if (id in revealedIds) revealedIds - id else revealedIds + id
    }

    // ----------------------------------------------------------------- entries

    suspend fun upsert(id: String?, name: String, value: String): Boolean {
        val key = dek?.copyOf() ?: return false
        val now = System.currentTimeMillis()
        val current = entries.toList()
        val updated = if (id == null) {
            current + VaultEntry(UUID.randomUUID().toString(), name, value, now, now)
        } else {
            current.map { if (it.id == id) it.copy(name = name, value = value, updatedAt = now) else it }
        }
        return commit(key, updated)
    }

    suspend fun delete(id: String): Boolean {
        val key = dek?.copyOf() ?: return false
        return commit(key, entries.filter { it.id != id })
    }

    private suspend fun commit(key: ByteArray, updated: List<VaultEntry>): Boolean = try {
        withContext(Dispatchers.IO) { store.save(key, updated) }
        if (state == State.UNLOCKED) {
            entries.clear()
            entries.addAll(updated.sortedBy { it.name.lowercase() })
        }
        true
    } catch (e: Exception) {
        false
    } finally {
        VaultCrypto.wipe(key)
    }

    // ------------------------------------------------------------------ backup

    /** Heavy KDF runs off the main thread. Result is ciphertext only (safe to hold briefly). */
    suspend fun createBackup(password: CharArray): ByteArray? {
        val snapshot = entries.toList()
        return try {
            withContext(Dispatchers.Default) { VaultBackup.create(snapshot, password) }
        } catch (e: Exception) {
            null
        }
    }

    suspend fun importBackup(zip: ByteArray, password: CharArray): ImportResult {
        val key = dek?.copyOf() ?: return ImportResult(0, 0, "The vault is locked.")
        try {
            val parsed = try {
                withContext(Dispatchers.Default) { VaultBackup.parse(zip, password) }
            } catch (e: VaultBackupException) {
                return ImportResult(0, 0, when (e.kind) {
                    VaultBackupException.Kind.NOT_A_BACKUP -> "This file is not a Password Vault backup."
                    VaultBackupException.Kind.UNSUPPORTED -> "This backup version is not supported."
                    VaultBackupException.Kind.WRONG_PASSWORD_OR_TAMPERED ->
                        "Wrong backup password, or the backup was modified/corrupted. Nothing was imported."
                    VaultBackupException.Kind.CORRUPT -> "The backup is corrupted. Nothing was imported."
                    VaultBackupException.Kind.TOO_LARGE -> "The backup is too large. Nothing was imported."
                })
            }
            val current = entries.toList()
            val existing = current.map { it.name to it.value }.toHashSet()
            val toAdd = parsed.filter { (it.name to it.value) !in existing }
            if (current.size + toAdd.size > 5_000) return ImportResult(0, 0, "Too many entries. Nothing was imported.")
            if (toAdd.isEmpty()) return ImportResult(0, parsed.size, null)
            // All-or-nothing: one atomic write; in-memory list only changes if it succeeded.
            return if (commit(key.copyOf(), current + toAdd)) {
                ImportResult(toAdd.size, parsed.size - toAdd.size, null)
            } else {
                ImportResult(0, 0, "Could not save the imported entries. Nothing was imported.")
            }
        } finally {
            VaultCrypto.wipe(key)
        }
    }

    // ---------------------------------------------------------------- security

    fun setAutoLock(ms: Long) {
        try { keys.setAutoLockMs(ms); autoLockMs = ms } catch (_: Exception) {}
    }

    suspend fun changePassword(old: CharArray, new: CharArray): Boolean {
        val attempt = withContext(Dispatchers.Default) { keys.unlockWithPassword(old) }
        if (attempt !is VaultKeyManager.Attempt.Success) return false
        return try {
            withContext(Dispatchers.Default) { keys.changePassword(attempt.dek, new) }
            true
        } catch (e: Exception) {
            false
        } finally {
            VaultCrypto.wipe(attempt.dek)
        }
    }

    /** Deletes everything: vault file, wrapped keys and Keystore keys. Irreversible. */
    fun resetVault() {
        lock()
        keys.wipeAll()
        store.wipe()
        biometricEnrolled = false
        state = State.NEEDS_SETUP
    }

    // --------------------------------------------------------------- biometric

    fun biometricEnrollCipher(): Cipher? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return null
        return try { keys.newBiometricEncryptCipher() } catch (e: Exception) { null }
    }

    fun finishBiometricEnroll(cipher: Cipher): Boolean {
        val key = dek ?: return false
        return try {
            keys.storeBiometricWrap(cipher, key)
            biometricEnrolled = true
            true
        } catch (e: Exception) {
            keys.disableBiometric()
            false
        }
    }

    fun disableBiometric() {
        keys.disableBiometric()
        biometricEnrolled = false
    }

    fun biometricUnlockCipher(): Cipher? {
        val c = keys.newBiometricDecryptCipher()
        if (c == null) biometricEnrolled = keys.isBiometricEnrolled()
        return c
    }

    suspend fun finishBiometricUnlock(cipher: Cipher): Boolean {
        val unwrapped = keys.unwrapWithBiometric(cipher) ?: return false
        return finishUnlock(unwrapped)
    }

    companion object {
        const val MIN_PASSWORD_LENGTH = 6
        const val MIN_BACKUP_PASSWORD_LENGTH = 8
        const val MAX_NAME = 200
        const val MAX_VALUE = 4096
    }
}
