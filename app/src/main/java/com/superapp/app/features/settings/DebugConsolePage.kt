package com.superapp.app.features.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.superapp.app.core.debug.DebugLog

@Composable
fun DebugConsolePage(env: com.superapp.app.core.settings.SettingsEnv) {
    val context = LocalContext.current
    var logs by remember { mutableStateOf(DebugLog.read(context)) }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Debug Console", style = MaterialTheme.typography.titleLarge)
        Text("Local diagnostics only. Passwords and typed field contents should never be logged.", style = MaterialTheme.typography.bodySmall)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { logs = DebugLog.read(context) }) { Text("Refresh") }
            OutlinedButton(onClick = { DebugLog.clear(context); logs = DebugLog.read(context) }) { Text("Clear logs") }
            OutlinedButton(onClick = {
                val clipboard = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                clipboard.setPrimaryClip(android.content.ClipData.newPlainText("Debug logs", logs))
            }) { Text("Copy") }
        }
        Text(logs, fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodySmall)
    }
}
