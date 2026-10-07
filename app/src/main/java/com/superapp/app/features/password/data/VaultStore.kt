package com.superapp.app.features.password.data

import android.content.Context
import android.util.AtomicFile
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * One vault item. toString() is redacted so the secret can never end up in a log/exception message by accident.
 */
data class VaultEntry(
    val id: String,
    val name: String,
    val value: String,
    val createdAt: Long,
    val updatedAt: Long,
) {
    override fun toString(): String = "VaultEntry(id=$id)"
}

/**
 * Whole vault = one AES-256-GCM encrypted file (filesDir/vault/vault.dat).
 * Names AND passwords are inside the encrypted payload. Writes are atomic (AtomicFile).
 * Pure local file I/O: no network classes are used anywhere in this package.
 */
internal class VaultStore(context: Context) {

    private val file = AtomicFile(File(File(context.filesDir, "vault").apply { mkdirs() }, "vault.dat"))

    fun load(dek: ByteArray): List<VaultEntry> {
        if (!file.baseFile.exists()) return emptyList()
        val plain = VaultCrypto.open(dek, file.readFully(), AAD)
        try {
            val arr = JSONObject(String(plain, Charsets.UTF_8)).getJSONArray("entries")
            return (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                VaultEntry(
                    id = o.getString("id"),
                    name = o.getString("name"),
                    value = o.getString("value"),
                    createdAt = o.optLong("created", 0L),
                    updatedAt = o.optLong("updated", 0L),
                )
            }
        } finally {
            VaultCrypto.wipe(plain)
        }
    }

    fun save(dek: ByteArray, entries: List<VaultEntry>) {
        val arr = JSONArray()
        entries.forEach {
            arr.put(
                JSONObject().put("id", it.id).put("name", it.name).put("value", it.value)
                    .put("created", it.createdAt).put("updated", it.updatedAt)
            )
        }
        val plain = JSONObject().put("v", 1).put("entries", arr).toString().toByteArray(Charsets.UTF_8)
        try {
            val blob = VaultCrypto.seal(dek, plain, AAD)
            val out = file.startWrite()
            try {
                out.write(blob)
                file.finishWrite(out)
            } catch (e: Exception) {
                file.failWrite(out)
                throw e
            }
        } finally {
            VaultCrypto.wipe(plain)
        }
    }

    fun wipe() {
        file.delete()
    }

    private companion object {
        val AAD = "superapp.vault.data.v1".toByteArray()
    }
}
