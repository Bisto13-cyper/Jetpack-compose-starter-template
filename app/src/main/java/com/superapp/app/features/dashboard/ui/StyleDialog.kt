package com.superapp.app.features.dashboard.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.superapp.app.features.dashboard.CardLook
import com.superapp.app.features.dashboard.CardShape
import com.superapp.app.features.dashboard.DashboardStyle

private val SWATCHES = listOf(
    0xFF1E88E5.toInt(), 0xFF00897B.toInt(), 0xFF43A047.toInt(), 0xFFFDD835.toInt(),
    0xFFFB8C00.toInt(), 0xFFE53935.toInt(), 0xFF8E24AA.toInt(), 0xFF212121.toInt(),
    0xFF607D8B.toInt(), 0xFFFFFFFF.toInt()
)

/**
 * Dashboard appearance editor. Each colour can follow the app theme ("Auto") or use a swatch.
 * Integration note: replace [ColorRow] with the project's core/ui/ColorPicker if full-colour
 * picking is wanted; the DashboardStyle model needs no change.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StyleDialog(initial: DashboardStyle, onDismiss: () -> Unit, onApply: (DashboardStyle) -> Unit) {
    var s by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Customize dashboard") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                ColorRow("Background", s.backgroundColor) { s = s.copy(backgroundColor = it) }
                ColorRow("Card colour", s.cardColor) { s = s.copy(cardColor = it) }
                ColorRow("Text colour", s.textColor) { s = s.copy(textColor = it) }
                ColorRow("Accent colour", s.accentColor) { s = s.copy(accentColor = it) }
                ColorRow("Progress colour", s.progressColor) { s = s.copy(progressColor = it) }

                Text("Card transparency: ${(s.cardAlpha * 100).toInt()}% opaque")
                Slider(s.cardAlpha, { s = s.copy(cardAlpha = it) }, valueRange = 0.2f..1f)
                Text("Corner radius: ${s.cornerRadiusDp} dp")
                Slider(s.cornerRadiusDp.toFloat(), { s = s.copy(cornerRadiusDp = it.toInt()) }, valueRange = 0f..32f)
                Text("Spacing: ${s.spacingDp} dp")
                Slider(s.spacingDp.toFloat(), { s = s.copy(spacingDp = it.toInt()) }, valueRange = 4f..28f)

                Text("Card shape")
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    CardShape.values().forEach {
                        FilterChip(selected = s.cardShape == it, onClick = { s = s.copy(cardShape = it) },
                            label = { Text(it.name.lowercase().replaceFirstChar { c -> c.uppercase() }) })
                    }
                }
                Text("Card look")
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    CardLook.values().forEach {
                        FilterChip(selected = s.cardLook == it, onClick = { s = s.copy(cardLook = it) },
                            label = { Text(it.name.lowercase().replaceFirstChar { c -> c.uppercase() }) })
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = { onApply(s) }) { Text("Apply") } },
        dismissButton = {
            Row {
                TextButton(onClick = { s = DashboardStyle() }) { Text("Reset") }
                TextButton(onClick = onDismiss) { Text("Cancel") }
            }
        }
    )
}

@Composable
private fun ColorRow(label: String, value: Int?, onChange: (Int?) -> Unit) {
    Column {
        Text(label, style = MaterialTheme.typography.bodySmall)
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            TextButton(onClick = { onChange(null) }) { Text(if (value == null) "Auto *" else "Auto") }
            SWATCHES.forEach { c ->
                androidx.compose.foundation.layout.Box(
                    Modifier.size(26.dp).clip(CircleShape).background(Color(c))
                        .border(if (value == c) 3.dp else 1.dp, MaterialTheme.colorScheme.onSurface, CircleShape)
                        .clickable { onChange(c) }
                )
            }
        }
    }
}
