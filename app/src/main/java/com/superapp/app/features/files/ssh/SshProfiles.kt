-----------------------------------------------------------------------
package com.superapp.app.features.files.ssh

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import org.json.JSONArray
import org.json.JSONObject
import java.security.KeyStore
import java.util.UUID
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

enum class SshAuth { PASSWORD, KEY }

/** Non-secret part of a connection. Nothing here is hardcoded: host, port and user are always user input. */
data class SshProfile(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val host: String,
    val port: Int,
    val username: String,
    val auth: SshAuth,
    val hasPassword: Boolean = false,
    val hasKey: Boolean = false,
) {
    val display: String get() = "$username@$host:$port"
}

class SshSecrets(val password: String?, val privateKey: String?, val passphrase: String?)

/** AES-256-GCM with a non-exportable Android Keystore key. Used for passwords/private keys at rest. */
internal object SecretBox {
    private const val ALIAS = "superapp_files_secretbox_v1"
    private const val ANDROID_KS = "AndroidKeyStore"
    private const val T = "AES/GCM/NoPadding"

    private fun key(): SecretKey {
        val ks = KeyStore.getInstance(ANDROID_KS).apply { load(null) }
        (ks.getKey(ALIAS, null) as? SecretKey)?.let { return it }
        val gen = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KS)
        gen.init(
            KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256).build()
        )
        return gen.generateKey()
    }

    fun encrypt(plain: String): String {
        val c = Cipher.getInstance(T)
        c.init(Cipher.ENCRYPT_MODE, key())
        return Base64.encodeToString(c.iv + c.doFinal(plain.toByteArray(Charsets.UTF_8)), Base64.NO_WRAP)
    }

    fun decrypt(blob: String): String {
        val raw = Base64.decode(blob, Base64.NO_WRAP)
        val c = Cipher.getInstance(T)
        c.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, raw.copyOfRange(0, 12)))
        return String(c.doFinal(raw, 12, raw.size - 12), Charsets.UTF_8)
    }
}

/** Profiles in SharedPreferences ("files_ssh"); passwords / private keys / passphrases are SecretBox-encrypted. */
class SshProfileStore(context: Context) {
    private val sp = context.applicationContext.getSharedPreferences("files_ssh", Context.MODE_PRIVATE)

    fun list(): List<SshProfile> = try {
        val arr = JSONArray(sp.getString("profiles", "[]"))
        (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            SshProfile(
                id = o.getString("id"), name = o.getString("name"), host = o.getString("host"),
                port = o.getInt("port"), username = o.getString("user"),
                auth = SshAuth.valueOf(o.getString("auth")),
                hasPassword = sp.contains("pw_${o.getString("id")}"),
                hasKey = sp.contains("key_${o.getString("id")}"),
            )
        }
    } catch (_: Exception) { emptyList() }

    /**
     * Saves the profile. Secret arguments: null = keep existing, "" = remove, text = replace.
     */
    fun save(profile: SshProfile, password: String?, privateKey: String?, passphrase: String?) {
        val all = list().filter { it.id != profile.id } + profile
        val arr = JSONArray()
        all.forEach {
            arr.put(JSONObject().put("id", it.id).put("name", it.name).put("host", it.host)
                .put("port", it.port).put("user", it.username).put("auth", it.auth.name))
        }
        val e = sp.edit().putString("profiles", arr.toString())
        fun apply(k: String, v: String?) {
            if (v == null) return
            if (v.isEmpty()) e.remove(k) else e.putString(k, SecretBox.encrypt(v))
        }
        apply("pw_${profile.id}", password)
        apply("key_${profile.id}", privateKey)
        apply("pp_${profile.id}", passphrase)
        e.apply()
    }

    fun secrets(id: String): SshSecrets = SshSecrets(
        password = sp.getString("pw_$id", null)?.let { runCatching { SecretBox.decrypt(it) }.getOrNull() },
        privateKey = sp.getString("key_$id", null)?.let { runCatching { SecretBox.decrypt(it) }.getOrNull() },
        passphrase = sp.getString("pp_$id", null)?.let { runCatching { SecretBox.decrypt(it) }.getOrNull() },
    )

    fun delete(id: String) {
        val arr = JSONArray()
        list().filter { it.id != id }.forEach {
            arr.put(JSONObject().put("id", it.id).put("name", it.name).put("host", it.host)
                .put("port", it.port).put("user", it.username).put("auth", it.auth.name))
        }
        sp.edit().putString("profiles", arr.toString())
            .remove("pw_$id").remove("key_$id").remove("pp_$id").apply()
    }
}

/** Trusted SSH host keys (trust-on-first-use, confirmed by the user). Key = host string as JSch passes it. */
class KnownHostsStore(context: Context) {
    private val sp = context.applicationContext.getSharedPreferences("files_known_hosts", Context.MODE_PRIVATE)

    fun get(host: String): ByteArray? = sp.getString(host, null)?.let { Base64.decode(it, Base64.NO_WRAP) }

    fun put(host: String, blob: ByteArray) {
        sp.edit().putString(host, Base64.encodeToString(blob, Base64.NO_WRAP)).apply()
    }

    fun remove(host: String) {
        sp.edit().remove(host).apply()
    }
}

-----------------------------------------------------------------------
