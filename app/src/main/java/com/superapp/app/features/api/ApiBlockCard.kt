package com.superapp.app.features.api

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val MonoStyle = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 13.sp)

@Composable
fun ApiBlockCard(state: ApiBlockState, workspace: ApiWorkspaceState, modifier: Modifier = Modifier) {
    var confirmDelete by remember { mutableStateOf(false) }

    Card(modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = state.config.name,
                    onValueChange = { v -> workspace.update(state) { it.copy(name = v) } },
                    singleLine = true,
                    label = { Text("Block name") },
                    modifier = Modifier.weight(1f)
                )
                TextButton(onClick = { workspace.duplicate(state) }) { Text("⧉") }
                TextButton(onClick = { workspace.reset(state) }) { Text("↺") }
                TextButton(onClick = { confirmDelete = true }) { Text("🗑") }
            }
            RequestSection(state, workspace)
            ResponseSection(state)
            TransformSection(state, workspace)
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Delete block?") },
            text = { Text("\"${state.config.name}\" will be removed.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    workspace.delete(state)
                }) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancel") } }
        )
    }
}

// ------------------------------------------------------------------ shared pieces

@Composable
fun Section(title: String, initiallyExpanded: Boolean, content: @Composable ColumnScope.() -> Unit) {
    var expanded by rememberSaveable { mutableStateOf(initiallyExpanded) }
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            (if (expanded) "▼ " else "▶ ") + title,
            fontWeight = FontWeight.Bold,
            fontSize = 14.sp,
            modifier = Modifier.fillMaxWidth().clickable { expanded = !expanded }.padding(vertical = 6.dp)
        )
        if (expanded) content()
    }
}

@Composable
fun KeyValueEditor(
    items: List<KeyValue>,
    onChange: (List<KeyValue>) -> Unit,
    keyLabel: String = "Key",
    valueLabel: String = "Value",
    maskValue: (KeyValue) -> Boolean = { false }
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        items.forEach { kv ->
            key(kv.id) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(
                        checked = kv.enabled,
                        onCheckedChange = { c -> onChange(items.map { if (it.id == kv.id) it.copy(enabled = c) else it }) }
                    )
                    OutlinedTextField(
                        value = kv.key,
                        onValueChange = { v -> onChange(items.map { if (it.id == kv.id) it.copy(key = v) else it }) },
                        singleLine = true,
                        label = { Text(keyLabel) },
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(Modifier.width(4.dp))
                    OutlinedTextField(
                        value = kv.value,
                        onValueChange = { v -> onChange(items.map { if (it.id == kv.id) it.copy(value = v) else it }) },
                        singleLine = true,
                        label = { Text(valueLabel) },
                        visualTransformation = if (maskValue(kv)) PasswordVisualTransformation() else VisualTransformation.None,
                        modifier = Modifier.weight(1f)
                    )
                    TextButton(onClick = { onChange(items.filterNot { it.id == kv.id }) }) { Text("✕") }
                }
            }
        }
        TextButton(onClick = { onChange(items + KeyValue()) }) { Text("+ Add") }
    }
}

@Composable
private fun ErrorBox(title: String, message: String) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
        Column(Modifier.padding(10.dp)) {
            Text(title, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onErrorContainer)
            SelectionContainer {
                Text(message, fontSize = 13.sp, color = MaterialTheme.colorScheme.onErrorContainer)
            }
        }
    }
}

@Composable
private fun RadioRow(label: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.clickable(onClick = onClick).padding(end = 12.dp, top = 4.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(selected = selected, onClick = null)
        Text(label, modifier = Modifier.padding(start = 6.dp), fontSize = 14.sp)
    }
}

private fun formatSize(bytes: Long): String = when {
    bytes < 1024 -> "$bytes B"
    bytes < 1024 * 1024 -> String.format("%.1f KB", bytes / 1024.0)
    else -> String.format("%.2f MB", bytes / (1024.0 * 1024.0))
}

// ------------------------------------------------------------------ REQUEST

@Composable
private fun RequestSection(state: ApiBlockState, workspace: ApiWorkspaceState) {
    val cfg = state.config
    Section("REQUEST", true) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            var menuOpen by remember { mutableStateOf(false) }
            Box {
                TextButton(onClick = { menuOpen = true }) { Text(cfg.method.name + " ▾") }
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    ApiMethod.values().forEach { m ->
                        DropdownMenuItem(
                            text = { Text(m.name) },
                            onClick = {
                                menuOpen = false
                                workspace.update(state) { it.copy(method = m) }
                            }
                        )
                    }
                }
            }
            OutlinedTextField(
                value = cfg.url,
                onValueChange = { v -> workspace.update(state) { it.copy(url = v) } },
                singleLine = true,
                placeholder = { Text("https://api.example.com/{{path}}") },
                modifier = Modifier.weight(1f)
            )
        }
        Button(onClick = { workspace.send(state) }, modifier = Modifier.fillMaxWidth()) {
            Text(if (state.sending) "Cancel" else "Send")
        }

        var tab by rememberSaveable { mutableStateOf(0) }
        val titles = listOf(
            "Params (${cfg.params.count { it.enabled && it.key.isNotBlank() }})",
            "Headers (${cfg.headers.count { it.enabled && it.key.isNotBlank() }})",
            "Body",
            "Auth"
        )
        ScrollableTabRow(selectedTabIndex = tab, edgePadding = 0.dp) {
            titles.forEachIndexed { i, t ->
                Tab(selected = tab == i, onClick = { tab = i }, text = { Text(t, maxLines = 1) })
            }
        }
        when (tab) {
            0 -> KeyValueEditor(
                items = cfg.params,
                onChange = { list -> workspace.update(state) { it.copy(params = list) } },
                keyLabel = "Name",
                maskValue = { looksSensitive(it.key) }
            )
            1 -> KeyValueEditor(
                items = cfg.headers,
                onChange = { list -> workspace.update(state) { it.copy(headers = list) } },
                keyLabel = "Header",
                maskValue = { looksSensitive(it.key) }
            )
            2 -> BodyEditor(state, workspace)
            else -> AuthEditor(state, workspace)
        }
        Text(
            "Use {{name}} from the Variables section in the URL, params, headers, body and auth.",
            fontSize = 11.sp,
            color = MaterialTheme.colorScheme.outline
        )
    }
}

@Composable
private fun BodyEditor(state: ApiBlockState, workspace: ApiWorkspaceState) {
    val cfg = state.config
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row {
            BodyType.values().forEach { t ->
                RadioRow(t.label, cfg.bodyType == t) { workspace.update(state) { it.copy(bodyType = t) } }
            }
        }
        if (!cfg.method.allowsBody && cfg.bodyType != BodyType.NONE) {
            Text("${cfg.method.name} requests are sent without a body.", fontSize = 12.sp, color = MaterialTheme.colorScheme.error)
        }
        if (cfg.bodyType != BodyType.NONE) {
            OutlinedTextField(
                value = cfg.body,
                onValueChange = { v -> workspace.update(state) { it.copy(body = v) } },
                label = { Text(if (cfg.bodyType == BodyType.JSON) "JSON body" else "Text body") },
                textStyle = MonoStyle,
                minLines = 5,
                modifier = Modifier.fillMaxWidth()
            )
            if (cfg.bodyType == BodyType.JSON) {
                val parsed = remember(cfg.body) { parseJsonOrNull(cfg.body) }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    val ok = cfg.body.isBlank() || parsed != null
                    Text(
                        if (ok) "Valid JSON object/array" else "Not a JSON object/array (sent as-is)",
                        fontSize = 12.sp,
                        color = if (ok) Color(0xFF2E7D32) else MaterialTheme.colorScheme.error,
                        modifier = Modifier.weight(1f)
                    )
                    TextButton(
                        enabled = parsed != null,
                        onClick = { parsed?.let { p -> workspace.update(state) { it.copy(body = jsonToText(p, true)) } } }
                    ) { Text("Format") }
                }
            }
        }
        Text(
            "Content-Type follows the body type unless you add your own Content-Type header.",
            fontSize = 11.sp,
            color = MaterialTheme.colorScheme.outline
        )
    }
}

@Composable
private fun AuthEditor(state: ApiBlockState, workspace: ApiWorkspaceState) {
    val cfg = state.config
    val hidden = PasswordVisualTransformation()
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row {
            AuthType.values().forEach { t ->
                RadioRow(t.label, cfg.authType == t) { workspace.update(state) { it.copy(authType = t) } }
            }
        }
        when (cfg.authType) {
            AuthType.NONE -> Text("No authentication.", fontSize = 12.sp)
            AuthType.BEARER -> OutlinedTextField(
                value = cfg.authToken,
                onValueChange = { v -> workspace.update(state) { it.copy(authToken = v) } },
                label = { Text("Token") },
                singleLine = true,
                visualTransformation = hidden,
                modifier = Modifier.fillMaxWidth()
            )
            AuthType.BASIC -> {
                OutlinedTextField(
                    value = cfg.authUser,
                    onValueChange = { v -> workspace.update(state) { it.copy(authUser = v) } },
                    label = { Text("Username") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = cfg.authPassword,
                    onValueChange = { v -> workspace.update(state) { it.copy(authPassword = v) } },
                    label = { Text("Password") },
                    singleLine = true,
                    visualTransformation = hidden,
                    modifier = Modifier.fillMaxWidth()
                )
            }
            AuthType.API_KEY -> {
                OutlinedTextField(
                    value = cfg.apiKeyName,
                    onValueChange = { v -> workspace.update(state) { it.copy(apiKeyName = v) } },
                    label = { Text("Key name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = cfg.apiKeyValue,
                    onValueChange = { v -> workspace.update(state) { it.copy(apiKeyValue = v) } },
                    label = { Text("Key value") },
                    singleLine = true,
                    visualTransformation = hidden,
                    modifier = Modifier.fillMaxWidth()
                )
                Row {
                    RadioRow("Send in header", cfg.apiKeyInHeader) { workspace.update(state) { it.copy(apiKeyInHeader = true) } }
                    RadioRow("Send in query", !cfg.apiKeyInHeader) { workspace.update(state) { it.copy(apiKeyInHeader = false) } }
                }
            }
        }
        if (cfg.authType != AuthType.NONE) {
            Text(
                "Credentials are saved encrypted (Android Keystore), never logged, and not copied into history.",
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.outline
            )
        }
    }
}

// ------------------------------------------------------------------ RESPONSE

@Composable
private fun ResponseSection(state: ApiBlockState) {
    Section("RESPONSE", true) {
        if (state.sending) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                Text("  Sending…")
            }
        }
        state.error?.let { ErrorBox(it.kind, it.message) }

        val r = state.response
        if (r != null) {
            val color = when (r.code) {
                in 200..299 -> Color(0xFF2E7D32)
                in 300..399 -> Color(0xFF1565C0)
                else -> MaterialTheme.colorScheme.error
            }
            Text(
                "${r.code} ${r.message}".trim(),
                color = color,
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp
            )
            Text(
                "${r.timeMs} ms · ${formatSize(r.sizeBytes)} · ${r.contentType.ifBlank { "no content-type" }}",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.outline
            )
            if (r.truncated) {
                Text("Body truncated at 2 MB.", fontSize = 12.sp, color = MaterialTheme.colorScheme.error)
            }
            var tab by rememberSaveable { mutableStateOf(0) }
            ScrollableTabRow(selectedTabIndex = tab, edgePadding = 0.dp) {
                Tab(selected = tab == 0, onClick = { tab = 0 }, text = { Text("Body") })
                Tab(selected = tab == 1, onClick = { tab = 1 }, text = { Text("Headers (${r.headers.size})") })
            }
            if (tab == 0) {
                BodyViewer(r.body)
            } else {
                SelectionContainer {
                    Column {
                        r.headers.forEach { (k, v) ->
                            Text("$k: $v", style = MonoStyle.copy(fontSize = 12.sp))
                        }
                    }
                }
            }
        } else if (!state.sending && state.error == null) {
            Text("No response yet. Press Send.", fontSize = 12.sp, color = MaterialTheme.colorScheme.outline)
        }
    }
}

// ------------------------------------------------------------------ TRANSFORM

@Composable
private fun TransformSection(state: ApiBlockState, workspace: ApiWorkspaceState) {
    Section("TRANSFORM (JavaScript)", false) {
        Text(
            "Variables: data = parsed JSON (or the text), vars = your variables, " +
                "response = {status, statusText, contentType, timeMs, headers}. " +
                "console.log() output is listed below. End with return. async/await works. " +
                "No network and no Android access. Stops after 5 seconds.",
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.outline
        )
        OutlinedTextField(
            value = state.config.transformCode,
            onValueChange = { v -> workspace.update(state) { it.copy(transformCode = v) } },
            label = { Text("JavaScript") },
            textStyle = MonoStyle,
            minLines = 6,
            modifier = Modifier.fillMaxWidth()
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            Button(onClick = { workspace.runTransform(state) }, enabled = !state.transformRunning) { Text("Run") }
            TextButton(onClick = { workspace.resetTransform(state) }) { Text("Reset") }
            if (state.transformRunning) {
                CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
            }
        }
        state.transformError?.let { ErrorBox("Script error", it) }
        if (state.transformLogs.isNotEmpty()) {
            Text("Console", fontWeight = FontWeight.Bold, fontSize = 12.sp)
            SelectionContainer {
                Text(state.transformLogs.joinToString("\n"), style = MonoStyle.copy(fontSize = 12.sp))
            }
        }
        state.transformOutput?.let {
            Text("Output", fontWeight = FontWeight.Bold, fontSize = 12.sp)
            BodyViewer(it)
        }
    }
}
