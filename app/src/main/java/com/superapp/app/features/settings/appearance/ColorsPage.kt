package com.superapp.app.features.settings.appearance

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.superapp.app.core.feature.FeatureIcon
import com.superapp.app.core.feature.FeatureModule
import com.superapp.app.core.theme.ThemePreset
import com.superapp.app.core.theme.ThemeSettings
import com.superapp.app.core.theme.themePresets
import com.superapp.app.core.ui.ColorPicker
import com.superapp.app.core.settings.SettingsEnv

/**
 * SETTINGS FILE 7 - Settings > Appearance > Colors & Themes.
 *
 * Three things on this page:
 *  1) Ready-made themes   : one tap changes all colors (see ThemePresets.kt)
 *  2) Your colors         : background, panels, accent and text, any color
 *  3) Circle colors       : a separate color for every home circle
 *
 * Every change is saved at once and the whole app updates live.
 * The background PHOTO is not touched here (see BackgroundsPage.kt).
 *
 * HOW TO CHANGE:
 *  - Add or remove ready-made themes in core/theme/ThemePresets.kt
 *  - Add another color row: copy one ColorPicker block below
 *    (the field must exist in ThemeSettings first)
 */
@Composable
fun ColorsPage(env: SettingsEnv) {
    val theme by env.repository.theme.collectAsState()
    val styles by env.repository.featureStyles.collectAsState()
    val features = remember { env.registry.all() }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        SectionLabel("Ready-made themes")
        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            themePresets.forEach { preset ->
                PresetChip(
                    preset = preset,
                    selected = theme.background == preset.background.toInt() &&
                        theme.primary == preset.primary.toInt(),
                    onClick = { env.repository.updateTheme { preset.applyTo(it) } }
                )
            }
        }

        SectionLabel("Your colors")
        ColorPicker(
            title = "Background",
            color = theme.background,
            onColorChange = { c -> env.repository.updateTheme { it.copy(background = c) } }
        )
        ColorPicker(
            title = "Panels and cards",
            color = theme.surface,
            onColorChange = { c -> env.repository.updateTheme { it.copy(surface = c) } }
        )
        ColorPicker(
            title = "Accent (buttons, rings, highlights)",
            color = theme.primary,
            onColorChange = { c -> env.repository.updateTheme { it.copy(primary = c) } }
        )
        ColorPicker(
            title = "Text",
            color = theme.onBackground,
            onColorChange = { c -> env.repository.updateTheme { it.copy(onBackground = c) } }
        )

        OutlinedButton(
            onClick = {
                // Back to the default colors. Photo and circle size stay as they are.
                env.repository.updateTheme {
                    ThemeSettings().copy(
                        backgroundImagePath = it.backgroundImagePath,
                        circleScale = it.circleScale
                    )
                }
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Reset colors to default")
        }

        SectionLabel("Circle colors")
        features.forEach { feature ->
            val accent = styles[feature.id]?.accent ?: feature.defaultAccent.toArgb()
            ColorPicker(
                title = "${symbolOf(feature)}  ${feature.title}",
                color = accent,
                onColorChange = { c ->
                    env.repository.updateFeatureStyle(feature.id) { it.copy(accent = c) }
                }
            )
        }

        OutlinedButton(
            onClick = {
                features.forEach { feature ->
                    env.repository.updateFeatureStyle(feature.id) { it.copy(accent = null) }
                }
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Reset circle colors")
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Spacer(Modifier.height(6.dp))
    Text(
        text = text,
        color = MaterialTheme.colorScheme.primary,
        fontSize = 13.sp,
        fontWeight = FontWeight.Bold
    )
}

@Composable
private fun PresetChip(preset: ThemePreset, selected: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(14.dp)
    Column(
        modifier = Modifier
            .width(96.dp)
            .clip(shape)
            .background(Color(preset.background))
            .border(if (selected) 3.dp else 1.dp, Color(preset.primary), shape)
            .clickable(onClick = onClick)
            .padding(10.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            Dot(Color(preset.primary))
            Dot(Color(preset.surface))
            Dot(Color(preset.text))
        }
        Spacer(Modifier.height(6.dp))
        Text(text = preset.name, color = Color(preset.text), fontSize = 12.sp)
    }
}

@Composable
private fun Dot(color: Color) {
    Box(
        modifier = Modifier
            .size(14.dp)
            .clip(CircleShape)
            .background(color)
            .border(1.dp, Color.White.copy(alpha = 0.3f), CircleShape)
    )
}

/** Emoji icons show as they are. Other icon types show the first letter. */
private fun symbolOf(feature: FeatureModule): String =
    when (val icon = feature.icon) {
        is FeatureIcon.Emoji -> icon.value
        else -> feature.title.take(1)
    }
