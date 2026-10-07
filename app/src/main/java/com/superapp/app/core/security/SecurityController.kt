package com.superapp.app.core.security

import android.app.KeyguardManager
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.activity.result.ActivityResultLauncher
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.fragment.app.FragmentActivity
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.Executor
import androidx.core.content.ContextCompat

class SecurityController(
    private val activity: FragmentActivity,
    private val repository: SecurityRepository
) {
    private val _appLocked = MutableStateFlow(repository.settings.value.appLockEnabled)
    val appLocked: StateFlow<Boolean> = _appLocked.asStateFlow()

    private var authenticating = false
    private var pendingAction: (() -> Unit)? = null
    private var credentialLauncher: ActivityResultLauncher<Intent>? = null

    fun bindCredentialLauncher(launcher: ActivityResultLauncher<Intent>) {
        credentialLauncher = launcher
    }

    fun onStart() {
        if (repository.settings.value.appLockEnabled) {
            if (_appLocked.value && !authenticating) {
                authenticateApp()
            }
        } else {
            _appLocked.value = false
        }
    }

    fun onStop() {
        // Do not relock while Android's biometric/device-credential UI is
        // temporarily covering this Activity. The authentication callback
        // decides whether the app is actually unlocked.
        if (repository.settings.value.appLockEnabled && !authenticating) {
            _appLocked.value = true
        }
    }

    fun setAppLockEnabled(enabled: Boolean) {
        repository.setAppLockEnabled(enabled)
        if (enabled) {
            _appLocked.value = true
            authenticateApp()
        } else {
            _appLocked.value = false
        }
    }

    fun authenticateApp() {
        authenticate("Unlock app") { _appLocked.value = false }
    }

    fun isNodeLocked(nodeId: String): Boolean =
        repository.isNodeLocked(nodeId)

    fun requestRoute(
        route: String,
        title: String,
        onAllowed: () -> Unit
    ) {
        if (_appLocked.value) {
            authenticate("Unlock app", onAllowed)
        } else if (repository.isNodeLocked(route)) {
            authenticate("Unlock $title", onAllowed)
        } else {
            onAllowed()
        }
    }

    fun authenticate(
        reason: String,
        onSuccess: () -> Unit = {},
        biometricOnly: Boolean = false
    ) {
        if (authenticating) return

        val settings = repository.settings.value
        val biometricAvailable =
            biometricAvailability() == BiometricManager.BIOMETRIC_SUCCESS
        val useBiometric = biometricAvailable && (settings.biometricsEnabled || biometricOnly)

        pendingAction = onSuccess
        authenticating = true

        if (useBiometric && Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            showBiometricPrompt(reason, allowCredentialFallback = !biometricOnly)
        } else if (!biometricOnly) {
            launchDeviceCredential(reason)
        } else {
            finishAuthentication(false)
        }
    }

    fun biometricAvailability(): Int =
        BiometricManager.from(activity).canAuthenticate(
            BiometricManager.Authenticators.BIOMETRIC_WEAK
        )

    fun deviceCredentialAvailable(): Boolean =
        (activity.getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager).isDeviceSecure

    private fun showBiometricPrompt(
        reason: String,
        allowCredentialFallback: Boolean
    ) {
        val executor: Executor = ContextCompat.getMainExecutor(activity)
        val prompt = BiometricPrompt(
            activity,
            executor,
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(
                    result: BiometricPrompt.AuthenticationResult
                ) {
                    finishAuthentication(true)
                }

                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    finishAuthentication(false)
                }

                override fun onAuthenticationFailed() {
                    // Keep the prompt active; no sensitive information is exposed.
                }
            }
        )

        val builder = BiometricPrompt.PromptInfo.Builder()
            .setTitle(reason)
            .setSubtitle("Authenticate with your device")

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val authenticators = if (
                allowCredentialFallback && deviceCredentialAvailable()
            ) {
                BiometricManager.Authenticators.BIOMETRIC_WEAK or
                    BiometricManager.Authenticators.DEVICE_CREDENTIAL
            } else {
                BiometricManager.Authenticators.BIOMETRIC_WEAK
            }
            builder.setAllowedAuthenticators(authenticators)
        } else {
            builder.setNegativeButtonText("Cancel")
        }

        prompt.authenticate(builder.build())
    }

    private fun launchDeviceCredential(reason: String) {
        val keyguard = activity.getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager
        val intent = keyguard.createConfirmDeviceCredentialIntent(
            reason,
            "Confirm your device credential"
        )
        if (intent == null) {
            finishAuthentication(false)
        } else {
            credentialLauncher?.launch(intent) ?: finishAuthentication(false)
        }
    }

    fun onCredentialResult(success: Boolean) {
        finishAuthentication(success)
    }

    private fun finishAuthentication(success: Boolean) {
        authenticating = false
        val action = pendingAction
        pendingAction = null
        if (success) action?.invoke()
    }
}
