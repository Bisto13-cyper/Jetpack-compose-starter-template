package com.superapp.app.features.editor.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.superapp.app.features.editor.data.EditorSettingsStore
import com.superapp.app.features.editor.model.WorkspaceNode
import com.superapp.app.features.editor.search.FileHit
import com.superapp.app.features.editor.search.SearchEngine
import kotlinx.coroutines.delay

@Composable
fun WorkspaceSearchOverlay(
    root: WorkspaceNode,
    onClose: () -> Unit,
    onOpenHit: (FileHit) -> Unit
) {
    val context = LocalContext.current
    val s = EditorSettingsStore.settings

    var query by remember { mutableStateOf("") }
    var caseSensitive by remember { mutableStateOf(false) }
    var wholeWord by remember { mutableStateOf(false) }
    var results by remember { mutableStateOf<List<FileHit>>(emptyList()) }
    var scanning by remember { mutableStateOf(false) }
    var progress by remember { mutableStateOf(0) }

    LaunchedEffect(query, caseSensitive, wholeWord) {
        if (query.isBlank()) { results = emptyList(); return@LaunchedEffect }
        delay(300) // debounce
        scanning = true
        progress = 0
        results = runCatching {
            SearchEngine.searchWorkspace(
                context, root, query, caseSensitive, wholeWord,
                onFileProgress = { progress = it }
            )
        }.getOrDefault(emptyList())
        scanning = false
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(Color(s.backgroundColor))
            .padding(12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Workspace search", color = Color(s.textColor),
                fontWeight = FontWeight.Bold, fontSize = 16.sp,
                modifier = Modifier.weight(1f))
            TextButton(onClick = onClose) { Text("Close") }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                label = { Text("Query") },
                modifier = Modifier.weight(1f),
                singleLine = true
            )
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Case sensitive", color = Color(s.textColor), fontSize = 12.sp)
            Spacer(Modifier.padding(4.dp))
            Switch(checked = caseSensitive, onCheckedChange = { caseSensitive = it })
            Spacer(Modifier.padding(4.dp))
            Text("Whole word", color = Color(s.textColor), fontSize = 12.sp)
            Spacer(Modifier.padding(4.dp))
            Switch(checked = wholeWord, onCheckedChange = { wholeWord = it })
        }
        if (scanning) {
            Text("Scanning… $progress files", color = Color(s.textColor), fontSize = 12.sp)
        }
        Text("${results.size} results", color = Color(s.lineNumberColor), fontSize = 12.sp)
        Spacer(Modifier.height(4.dp))

        LazyColumn(Modifier.fillMaxWidth()) {
            items(results) { hit ->
                Column(
                    Modifier
                        .fillMaxWidth()
                        .clickable { onOpenHit(hit) }
                        .padding(vertical = 6.dp)
                ) {
                    Text("${hit.fileName}:${hit.lineNumber}",
                        color = Color(s.accentColor),
                        fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    Text(hit.relativePath,
                        color = Color(s.lineNumberColor), fontSize = 11.sp)
                    Text(hit.lineText.take(200),
                        color = Color(s.textColor), fontSize = 12.sp)
                }
            }
        }
    }
}
