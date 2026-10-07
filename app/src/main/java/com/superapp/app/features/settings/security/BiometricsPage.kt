package com.superapp.app.features.settings.security

import androidx.biometric.BiometricManager
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.superapp.app.core.settings.SettingsEnv

@Composable
fun BiometricsPage(env: SettingsEnv) {
    val settings by env.security.settings.collectAsState()
    val status = env.securityController.biometricAvailability()
    var message by remember { mutableStateOf<String?>(null) }

    val available = status == BiometricManager.BIOMETRIC_SUCCESS

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.weight(1f)) {
                Text("Biometrics", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(4.dp))
                Text(
                    text = when (status) {
                        BiometricManager.BIOMETRIC_SUCCESS ->
                            "Fingerprint or face authentication is available."
                        BiometricManager.BIOMETRIC_ERROR_NO_HARDWARE ->
                            "This device has no biometric hardware."
                        BiometricManager.BIOMETRIC_ERROR_HW_UNAVAILABLE ->
                            "Biometric hardware is temporarily unavailable."
                        BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED ->
                            "No biometric is enrolled. Add a fingerprint or face in Android settings."
                        else ->
                            "Biometric authentication is unavailable on this device."
                    },
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f)
                )
            }

            Switch(
                checked = settings.biometricsEnabled,
                onCheckedChange = { enabled ->
                    message = null
                    if (enabled && available) {
                        env.security.setBiometricsEnabled(true)
                    } else {
                        env.security.setBiometricsEnabled(false)
                    }
                },
                enabled = available || settings.biometricsEnabled
            )
        }

        Button(
            onClick = {
                message = null
                env.securityController.authenticate(
                    reason = "Test biometric authentication",
                    biometricOnly = true,
                    onSuccess = { message = "Authentication successful." }
                )
            },
            enabled = available
        ) {
            Text("Test biometric")
        }

        message?.let {
            Text(text = it, color = MaterialTheme.colorScheme.primary)
        }
    }
}
