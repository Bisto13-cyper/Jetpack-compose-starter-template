package com.superapp.app.features.editor.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.superapp.app.features.editor.search.SearchEngine

@Composable
fun FindReplaceBar(
    text: String,
    onReplace: (newText: String) -> Unit
) {
    var query by remember { mutableStateOf("") }
    var replace by remember { mutableStateOf("") }
    var caseSensitive by remember { mutableStateOf(false) }
    var wholeWord by remember { mutableStateOf(false) }

    Row(
        Modifier.fillMaxWidth().padding(4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        OutlinedTextField(query, { query = it }, label = { Text("Find") },
            singleLine = true, modifier = Modifier.weight(1f))
        OutlinedTextField(replace, { replace = it }, label = { Text("Replace") },
            singleLine = true, modifier = Modifier.weight(1f))
        TextButton(onClick = {
            val matches = SearchEngine.findInText(text, query, caseSensitive, wholeWord)
            if (matches.isEmpty()) return@TextButton
            val sb = StringBuilder()
            var cursor = 0
            for (m in matches) {
                sb.append(text, cursor, m.start).append(replace)
                cursor = m.end
            }
            sb.append(text, cursor, text.length)
            onReplace(sb.toString())
        }) { Text("Replace all", fontSize = 12.sp) }
    }
}
