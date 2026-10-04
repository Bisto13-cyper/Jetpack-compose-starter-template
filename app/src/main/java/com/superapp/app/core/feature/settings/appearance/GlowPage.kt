package com.superapp.app.features.settings.appearance

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.superapp.app.core.theme.ThemeSettings
import com.superapp.app.core.ui.ColorPicker
import com.superapp.app.core.ui.FeatureCircle
import com.superapp.app.features.settings.SettingsEnv
import kotlin.math.roundToInt

/**
 * SETTINGS FILE 10 - Settings > Appearance > Glow.
 *
 * Controls the soft light around every circle:
 *  - Strength : how bright it is (0% = no glow at all)
 *  - Size     : how far it spreads
 *  - Color    : each circle's own color, or one color for all
 *
 * The three sample circles at the top change live while you drag.
 * The same settings apply on the Home screen.
 *
 * This file also holds two small helpers used by the Connection Lines
 * page too: LabeledSlider and SwitchRow.
 *
 * HOW TO CHANGE:
 *  - Slider limits : change the ranges (0f..1f and 0.3f..1.6f)
 *  - Sample circle size : change 72.dp
 */
@Composable
fun GlowPage(env: SettingsEnv) {
    val colors = MaterialTheme.colorScheme
    val theme by env.repository.theme.collectAsState()
    val styles by env.repository.featureStyles.collectAsState()
    val samples = remember { env.registry.all().take(3) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Text(
            text = "Preview",
            color = colors.primary,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 18.dp),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            samples.forEach { feature ->
                FeatureCircle(
                    title = feature.title,
                    icon = feature.icon,
                    accent = styles[feature.id]?.accent?.let { Color(it) }
                        ?: feature.defaultAccent,
                    diameter = 72.dp,
                    onClick = {}
                )
            }
        }

        LabeledSlider(
            label = "Strength",
            valueText = "${(theme.glowStrength * 100).roundToInt()}%",
            value = theme.glowStrength,
            range = 0f..1f
        ) { v -> env.repository.updateTheme { it.copy(glowStrength = v) } }

        LabeledSlider(
            label = "Size",
            valueText = "%.2fx".format(theme.glowSize),
            value = theme.glowSize,
            range = 0.3f..1.6f
        ) { v -> env.repository.updateTheme { it.copy(glowSize = v) } }

        SwitchRow(
            label = "Each circle glows in its own color",
            checked = theme.glowColor == null
        ) { own ->
            env.repository.updateTheme {
                it.copy(glowColor = if (own) null else it.primary)
            }
        }

        if (theme.glowColor != null) {
            ColorPicker(
                title = "Glow color (all circles)",
                color = theme.glowColor ?: theme.primary
            ) { c -> env.repository.updateTheme { it.copy(glowColor = c) } }
        }

        OutlinedButton(
            onClick = {
                val d = ThemeSettings()
                env.repository.updateTheme {
                    it.copy(
                        glowStrength = d.glowStrength,
                        glowSize = d.glowSize,
                        glowColor = d.glowColor
                    )
                }
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Reset glow")
        }
    }
}

/** A title with its current value on the right, and a slider under it. */
@Composable
internal fun LabeledSlider(
    label: String,
    valueText: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    onChange: (Float) -> Unit
) {
    val colors = MaterialTheme.colorScheme
    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(text = label, color = colors.onBackground)
            Text(text = valueText, color = colors.primary)
        }
        Slider(
            value = value.coerceIn(range.start, range.endInclusive),
            onValueChange = onChange,
            valueRange = range
        )
    }
}

/** A text on the left and an on/off switch on the right. */
@Composable
internal fun SwitchRow(
    label: String,
    checked: Boolean,
    onChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier
                .weight(1f)
                .padding(end = 12.dp)
        )
        Switch(checked = checked, onCheckedChange = onChange)
    }
}
