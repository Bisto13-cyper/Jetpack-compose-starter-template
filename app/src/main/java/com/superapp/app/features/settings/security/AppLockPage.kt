package com.superapp.app.features.settings.security

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.superapp.app.core.settings.SettingsEnv

@Composable
fun AppLockPage(env: SettingsEnv) {
    val settings by env.security.settings.collectAsState()
    val biometricAvailable =
        env.securityController.biometricAvailability() ==
            androidx.biometric.BiometricManager.BIOMETRIC_SUCCESS
    val credentialAvailable = env.securityController.deviceCredentialAvailable()

    Column(
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.weight(1f)) {
                Text("App Lock", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(4.dp))
                Text(
                    text = if (settings.appLockEnabled) {
                        "The app requires device authentication when it starts or resumes."
                    } else {
                        "Protect the entire application with Android device authentication."
                    },
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f)
                )
            }
            Switch(
                checked = settings.appLockEnabled,
                onCheckedChange = {
                    env.securityController.setAppLockEnabled(it)
                },
                enabled = biometricAvailable || credentialAvailable
            )
        }

        Text(
            text = when {
                biometricAvailable && settings.biometricsEnabled ->
                    "Authentication: biometric with device credential fallback."
                biometricAvailable ->
                    "Authentication: device credential. Enable Biometrics to use fingerprint or face."
                credentialAvailable ->
                    "Authentication: device credential. Biometrics are unavailable."
                else ->
                    "No supported biometric or device credential is configured on this device."
            },
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f)
        )
    }
}
