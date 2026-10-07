package com.superapp.app.features.password

import android.content.Context
import android.hardware.biometrics.BiometricManager
import android.hardware.biometrics.BiometricPrompt
import android.os.Build
import android.os.CancellationSignal
import androidx.annotation.RequiresApi
import javax.crypto.Cipher

/**
 * Uses the official framework BiometricPrompt (android.hardware.biometrics, API 30+) with a CryptoObject,
 * so the vault key is only released by the Keystore after a real biometric (Class 3 / strong:
 * fingerprint, or face where the device classifies it as strong) or the device screen lock.
 * No custom/fake biometric logic. No androidx.biometric dependency is needed.
 */
object VaultBiometric {

    private const val AUTHENTICATORS =
        BiometricManager.Authenticators.BIOMETRIC_STRONG or BiometricManager.Authenticators.DEVICE_CREDENTIAL

    fun isAvailable(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return false
        val bm = context.getSystemService(BiometricManager::class.java) ?: return false
        return bm.canAuthenticate(AUTHENTICATORS) == BiometricManager.BIOMETRIC_SUCCESS
    }

    @RequiresApi(Build.VERSION_CODES.R)
    fun authenticate(
        context: Context,
        cipher: Cipher,
        title: String,
        subtitle: String? = null,
        onSuccess: (Cipher) -> Unit,
        onFailure: () -> Unit,
    ): CancellationSignal {
        val builder = BiometricPrompt.Builder(context)
            .setTitle(title)
            .setAllowedAuthenticators(AUTHENTICATORS)
        if (subtitle != null) builder.setSubtitle(subtitle)
        val signal = CancellationSignal()
        builder.build().authenticate(
            BiometricPrompt.CryptoObject(cipher),
            signal,
            context.mainExecutor,
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    val c = result.cryptoObject?.cipher
                    if (c != null) onSuccess(c) else onFailure()
                }

                override fun onAuthenticationError(errorCode: Int, errString: CharSequence?) {
                    onFailure()
                }
            }
        )
        return signal
    }
}
