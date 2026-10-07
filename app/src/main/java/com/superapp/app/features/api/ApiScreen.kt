package com.superapp.app.features.api

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** API workspace: many independent API blocks, shared {{variables}}, and a lightweight history. */
@Composable
fun ApiScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val workspace = remember { ApiWorkspaceState(context, scope) }
    var showHistory by remember { mutableStateOf(false) }

    DisposableEffect(Unit) {
        onDispose { workspace.flush() }
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item(key = "header") {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("API Playground", fontWeight = FontWeight.Bold, fontSize = 20.sp, modifier = Modifier.weight(1f))
                TextButton(onClick = { showHistory = true }) { Text("History") }
                TextButton(onClick = { workspace.addBlock() }) { Text("+ Block") }
            }
        }
        item(key = "variables") {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp)) {
                    Section("VARIABLES  (use as {{name}})", false) {
                        KeyValueEditor(
                            items = workspace.variables,
                            onChange = workspace::setVariables,
                            keyLabel = "Name",
                            maskValue = { looksSensitive(it.key) }
                        )
                        Text(
                            "Variables are saved encrypted. Scripts can read them as vars.name.",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.outline
                        )
                    }
                }
            }
        }
        if (workspace.blocks.isEmpty()) {
            item(key = "empty") {
                Text("No blocks yet. Tap + Block to create one.", color = MaterialTheme.colorScheme.outline)
            }
        }
        items(workspace.blocks, key = { it.id }) { state ->
            ApiBlockCard(state, workspace)
        }
    }

    if (showHistory) {
        HistoryDialog(workspace, onDismiss = { showHistory = false })
    }
}

@Composable
private fun HistoryDialog(workspace: ApiWorkspaceState, onDismiss: () -> Unit) {
    val format = remember { SimpleDateFormat("MMM d, HH:mm", Locale.getDefault()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("History") },
        text = {
            if (workspace.history.isEmpty()) {
                Text("Nothing sent yet.")
            } else {
                LazyColumn(Modifier.heightIn(max = 360.dp)) {
                    items(workspace.history, key = { it.id }) { entry ->
                        Column(
                            Modifier
                                .fillMaxWidth()
                                .clickable {
                                    workspace.reopen(entry)
                                    onDismiss()
                                }
                                .padding(vertical = 8.dp)
                        ) {
                            // Query strings are hidden because they may contain tokens.
                            Text(
                                "${entry.block.method.name}  ${entry.block.url.substringBefore('?')}",
                                fontSize = 13.sp,
                                maxLines = 2
                            )
                            Text(
                                format.format(Date(entry.time)) + "  ·  tap to reopen as a new block",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.outline
                            )
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } },
        dismissButton = {
            if (workspace.history.isNotEmpty()) {
                TextButton(onClick = { workspace.clearHistory() }) { Text("Clear") }
            }
        }
    )
}
