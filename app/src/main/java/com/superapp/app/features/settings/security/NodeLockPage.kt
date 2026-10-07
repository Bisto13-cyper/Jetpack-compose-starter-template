package com.superapp.app.features.settings.security

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
fun NodeLockPage(env: SettingsEnv) {
    val settings by env.security.settings.collectAsState()
    val features = env.registry.all()

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
            text = "Choose which registered features require authentication before opening.",
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f)
        )

        if (features.isEmpty()) {
            Text(
                text = "No features are currently registered.",
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f)
            )
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(features, key = { it.id }) { feature ->
                    Row(modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(feature.title, style = MaterialTheme.typography.titleMedium)
                            Spacer(Modifier.height(2.dp))
                            Text(
                                text = feature.id,
                                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.55f),
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                        Switch(
                            checked = feature.id in settings.lockedNodeIds,
                            onCheckedChange = { locked ->
                                env.security.setNodeLocked(feature.id, locked)
                            }
                        )
                    }
                }
            }
        }
    }
}
