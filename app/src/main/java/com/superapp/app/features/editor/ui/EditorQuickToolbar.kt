package com.superapp.app.features.editor.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.superapp.app.features.editor.data.EditorSettingsStore

@Composable
fun EditorQuickToolbar(
    canSave: Boolean,
    onSave: () -> Unit,
    onFind: () -> Unit,
    onSearchWorkspace: () -> Unit,
    onSettings: () -> Unit
) {
    val s = EditorSettingsStore.settings
    Row(
        Modifier
            .fillMaxWidth()
            .background(Color(s.backgroundColor))
            .padding(horizontal = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        TextButton(onClick = onSave, enabled = canSave) {
            Text("Save", fontSize = 12.sp, color = Color(s.accentColor))
        }
        TextButton(onClick = onFind) {
            Text("Find", fontSize = 12.sp, color = Color(s.textColor))
        }
        TextButton(onClick = onSearchWorkspace) {
            Text("Search", fontSize = 12.sp, color = Color(s.textColor))
        }
        TextButton(onClick = onSettings) {
            Text("⚙", fontSize = 12.sp, color = Color(s.textColor))
        }
    }
}
