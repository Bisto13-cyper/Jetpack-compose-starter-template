package com.superapp.app.features.settings.canvas

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.superapp.app.core.canvas.CanvasStore
import com.superapp.app.core.theme.ThemeSettings
import com.superapp.app.core.settings.SettingsEnv
import com.superapp.app.features.settings.appearance.LabeledSlider
import kotlin.math.roundToInt

/**
 * CANVAS FILE 4 - Settings > Canvas > Node Layout.
 *
 * Three sliders that shape the automatic layout on the Home screen:
 *  - Circle size   : size of every feature circle (saved in the theme)
 *  - Center size   : size of the center "Super App" circle
 *  - Ring spread   : how far the circles sit from the center
 *
 * The Home screen updates live. Circles you moved yourself with Drag & Drop
 * keep their own spot; ring spread only affects the automatic ones.
 *
 * Uses LabeledSlider from appearance/GlowPage.kt.
 *
 * HOW TO CHANGE:
 *  - Slider limits : change the ranges below
 *    (very large circles or a very wide spread can run off the screen)
 */
@Composable
fun NodeLayoutPage(env: SettingsEnv) {
    val context = LocalContext.current
    val colors = MaterialTheme.colorScheme

    val theme by env.repository.theme.collectAsState()
    val store = remember { CanvasStore.get(context) }
    val canvas by store.state.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Text(
            text = "Open Home after changing these to see the result.",
            color = colors.onBackground.copy(alpha = 0.7f),
            fontSize = 13.sp
        )

        LabeledSlider(
            label = "Circle size",
            valueText = "${(theme.circleScale * 100).roundToInt()}%",
            value = theme.circleScale,
            range = 0.6f..1.4f
        ) { v -> env.repository.updateTheme { it.copy(circleScale = v) } }

        LabeledSlider(
            label = "Center circle size",
            valueText = "${(canvas.centerScale * 100).roundToInt()}%",
            value = canvas.centerScale,
            range = 0.6f..1.5f
        ) { v -> store.update { it.copy(centerScale = v) } }

        LabeledSlider(
            label = "Ring spread",
            valueText = "${(canvas.ringSpread * 100).roundToInt()}%",
            value = canvas.ringSpread,
            range = 0.6f..1.15f
        ) { v -> store.update { it.copy(ringSpread = v) } }

        OutlinedButton(
            onClick = {
                val d = ThemeSettings()
                env.repository.updateTheme { it.copy(circleScale = d.circleScale) }
                store.update { it.copy(centerScale = 1f, ringSpread = 1f) }
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Reset sizes")
        }
    }
}
