package com.superapp.app.features.editor.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.superapp.app.features.editor.data.EditorSettingsStore

@Composable
fun EditorSettingsScreen(onClose: () -> Unit) {
    val s = EditorSettingsStore.settings
    Column(
        Modifier
            .fillMaxSize()
            .background(Color(s.backgroundColor))
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Editor settings", color = Color(s.textColor),
                fontWeight = FontWeight.Bold, fontSize = 18.sp)
            Spacer(Modifier.weight(1f))
            TextButton(onClick = onClose) { Text("Done") }
        }

        ToggleRow("Show line numbers", s.showLineNumbers) {
            EditorSettingsStore.update { it.copy(showLineNumbers = it2(it)) }
        }
        ToggleRow("Word wrap", s.wordWrap) {
            EditorSettingsStore.update { it.copy(wordWrap = it2(it)) }
        }
        ToggleRow("Auto-close brackets", s.autoCloseBrackets) {
            EditorSettingsStore.update { it.copy(autoCloseBrackets = it2(it)) }
        }
        ToggleRow("Auto-close quotes", s.autoCloseQuotes) {
            EditorSettingsStore.update { it.copy(autoCloseQuotes = it2(it)) }
        }
        ToggleRow("Auto-indent", s.autoIndent) {
            EditorSettingsStore.update { it.copy(autoIndent = it2(it)) }
        }
        ToggleRow("Highlight current line", s.highlightCurrentLine) {
            EditorSettingsStore.update { it.copy(highlightCurrentLine = it2(it)) }
        }

        SliderRow("Font size", s.fontSizeSp.toFloat(), 10f..26f) { v ->
            EditorSettingsStore.update { it.copy(fontSizeSp = v.toInt()) }
        }
        SliderRow("Line height", s.lineHeightMultiplier, 1f..2f) { v ->
            EditorSettingsStore.update { it.copy(lineHeightMultiplier = v) }
        }
        SliderRow("Tab width", s.tabWidth.toFloat(), 2f..8f) { v ->
            EditorSettingsStore.update { it.copy(tabWidth = v.toInt()) }
        }

        ColorRow("Background", s.backgroundColor) {
            EditorSettingsStore.update { st -> st.copy(backgroundColor = it) }
        }
        ColorRow("Text", s.textColor) {
            EditorSettingsStore.update { st -> st.copy(textColor = it) }
        }
        ColorRow("Keyword", s.keywordColor) {
            EditorSettingsStore.update { st -> st.copy(keywordColor = it) }
        }
        ColorRow("String", s.stringColor) {
            EditorSettingsStore.update { st -> st.copy(stringColor = it) }
        }
        ColorRow("Number", s.numberColor) {
            EditorSettingsStore.update { st -> st.copy(numberColor = it) }
        }
        ColorRow("Comment", s.commentColor) {
            EditorSettingsStore.update { st -> st.copy(commentColor = it) }
        }
        ColorRow("Function", s.functionColor) {
            EditorSettingsStore.update { st -> st.copy(functionColor = it) }
        }
        ColorRow("Cursor", s.cursorColor) {
            EditorSettingsStore.update { st -> st.copy(cursorColor = it) }
        }
        ColorRow("Line numbers", s.lineNumberColor) {
            EditorSettingsStore.update { st -> st.copy(lineNumberColor = it) }
        }
        ColorRow("Accent", s.accentColor) {
            EditorSettingsStore.update { st -> st.copy(accentColor = it) }
        }

        Spacer(Modifier.height(40.dp))
    }
}

private fun it2(b: Boolean): Boolean = b

@Composable
private fun ToggleRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, modifier = Modifier.weight(1f), color = Color(EditorSettingsStore.settings.textColor))
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
private fun SliderRow(label: String, value: Float, range: ClosedFloatingPointRange<Float>, onChange: (Float) -> Unit) {
    Column {
        Text("$label: %.2f".format(value), fontSize = 13.sp, color = Color(EditorSettingsStore.settings.textColor))
        androidx.compose.material3.Slider(value = value, onValueChange = onChange, valueRange = range)
    }
}

@Composable
private fun ColorRow(label: String, value: Long, onChange: (Long) -> Unit) {
    var hex by remember(value) { mutableStateOf("#%08X".format(value)) }
    Column {
        Text(label, fontSize = 13.sp, color = Color(EditorSettingsStore.settings.textColor))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(28.dp)
                    .clip(CircleShape)
                    .background(Color(value))
            )
            Spacer(Modifier.width(8.dp))
            OutlinedTextField(
                value = hex,
                onValueChange = {
                    hex = it
                    val cleaned = it.removePrefix("#")
                    val parsed = cleaned.toLongOrNull(16) ?: return@OutlinedTextField
                    val argb = if (cleaned.length <= 6) 0xFF000000L or parsed else parsed
                    onChange(argb)
                },
                singleLine = true
            )
        }
    }
}
