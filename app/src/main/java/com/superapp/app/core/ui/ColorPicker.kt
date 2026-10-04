package com.superapp.app.core.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt

/**
 * SETTINGS FILE 6 - A color picker you can reuse anywhere.
 *
 * Shows one row: a color dot, a title and the hex code.
 * Tap the row to open it: three sliders (red, green, blue) and a hex box
 * where you can type or paste a code like #22D3EE.
 *
 * HOW TO USE:
 *   ColorPicker(
 *       title = "Background",
 *       color = theme.background,              // an ARGB Int
 *       onColorChange = { newColor -> ... }    // called on every change
 *   )
 *
 * HOW TO CHANGE:
 *  - Row roundness : 16.dp
 *  - Slider colors : the Color.Red / Green / Blue values in ColorPicker
 */
@Composable
fun ColorPicker(
    title: String,
    color: Int,
    onColorChange: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(16.dp)
    var expanded by remember { mutableStateOf(false) }

    val red = (color shr 16) and 0xFF
    val green = (color shr 8) and 0xFF
    val blue = color and 0xFF

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(colors.surface)
            .border(1.dp, colors.primary.copy(alpha = 0.25f), shape)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { expanded = !expanded }
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .clip(CircleShape)
                    .background(Color(color))
                    .border(1.dp, colors.onSurface.copy(alpha = 0.4f), CircleShape)
            )
            Spacer(Modifier.width(12.dp))
            Text(
                text = title,
                color = colors.onSurface,
                modifier = Modifier.weight(1f)
            )
            Text(
                text = hexOf(color),
                color = colors.onSurface.copy(alpha = 0.6f),
                fontSize = 12.sp
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = if (expanded) "▲" else "▼",
                color = colors.onSurface.copy(alpha = 0.5f),
                fontSize = 12.sp
            )
        }

        if (expanded) {
            Column(modifier = Modifier.padding(start = 14.dp, end = 14.dp, bottom = 14.dp)) {
                ChannelSlider("R", red, Color.Red) { onColorChange(rgb(it, green, blue)) }
                ChannelSlider("G", green, Color.Green) { onColorChange(rgb(red, it, blue)) }
                ChannelSlider("B", blue, Color.Blue) { onColorChange(rgb(red, green, it)) }

                // Restarts from the real color whenever a slider moves.
                var text by remember(color) { mutableStateOf(hexOf(color)) }
                OutlinedTextField(
                    value = text,
                    onValueChange = { typed ->
                        text = typed
                        parseHex(typed)?.let(onColorChange)
                    },
                    label = { Text("Hex code") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 6.dp)
                )
            }
        }
    }
}

@Composable
private fun ChannelSlider(
    label: String,
    value: Int,
    tint: Color,
    onChange: (Int) -> Unit
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = label,
            color = tint,
            modifier = Modifier.width(20.dp)
        )
        Slider(
            value = value.toFloat(),
            onValueChange = { onChange(it.roundToInt()) },
            valueRange = 0f..255f,
            colors = SliderDefaults.colors(
                thumbColor = tint,
                activeTrackColor = tint
            ),
            modifier = Modifier.weight(1f)
        )
        Text(
            text = value.toString(),
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
            fontSize = 12.sp,
            modifier = Modifier.width(32.dp)
        )
    }
}

/** Builds a fully visible ARGB color from red, green and blue (0 to 255). */
private fun rgb(r: Int, g: Int, b: Int): Int =
    (0xFF shl 24) or
        (r.coerceIn(0, 255) shl 16) or
        (g.coerceIn(0, 255) shl 8) or
        b.coerceIn(0, 255)

/** Example: #22D3EE */
private fun hexOf(color: Int): String = "#%06X".format(color and 0xFFFFFF)

/** Reads "#22D3EE" or "22D3EE". Returns null if it isn't a valid 6-digit code. */
private fun parseHex(input: String): Int? {
    val digits = input.trim().removePrefix("#")
    if (digits.length != 6) return null
    val value = digits.toLongOrNull(16) ?: return null
    return (0xFF000000L or value).toInt()
}
