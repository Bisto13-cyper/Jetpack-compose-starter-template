package com.superapp.app.features.settings.canvas

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.superapp.app.core.canvas.CanvasStore
import com.superapp.app.core.feature.FeatureIcon
import com.superapp.app.core.feature.FeatureModule
import com.superapp.app.core.settings.SettingsEnv

/**
 * CANVAS FILE 3 - Settings > Canvas > Visibility.
 *
 * One switch per feature. Turn a switch off and that circle disappears
 * from the Home screen. Nothing is deleted: the feature is still in the
 * side menu (the top button), and you can turn it back on here.
 *
 * HOW TO CHANGE:
 *  - Row look : change the 16.dp / padding values in FeatureRow
 */
@Composable
fun VisibilityPage(env: SettingsEnv) {
    val context = LocalContext.current
    val colors = MaterialTheme.colorScheme

    val store = remember { CanvasStore.get(context) }
    val canvas by store.state.collectAsState()
    val features = remember { env.registry.all() }

    val shownCount = features.count { it.id !in canvas.hidden }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text(
            text = "$shownCount of ${features.size} circles shown on Home. " +
                "Hidden ones stay available in the side menu.",
            color = colors.onBackground.copy(alpha = 0.7f),
            fontSize = 13.sp
        )

        features.forEach { feature ->
            FeatureRow(
                feature = feature,
                shown = feature.id !in canvas.hidden,
                onChange = { show ->
                    store.update {
                        it.copy(hidden = if (show) it.hidden - feature.id else it.hidden + feature.id)
                    }
                }
            )
        }

        OutlinedButton(
            onClick = { store.update { it.copy(hidden = emptySet()) } },
            enabled = canvas.hidden.isNotEmpty(),
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Show all circles")
        }
    }
}

@Composable
private fun FeatureRow(
    feature: FeatureModule,
    shown: Boolean,
    onChange: (Boolean) -> Unit
) {
    val colors = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(16.dp)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(colors.surface)
            .border(1.dp, colors.primary.copy(alpha = 0.25f), shape)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = symbolOf(feature), fontSize = 22.sp)
        Spacer(Modifier.width(14.dp))
        Text(
            text = feature.title,
            color = if (shown) colors.onSurface else colors.onSurface.copy(alpha = 0.5f),
            fontSize = 16.sp,
            modifier = Modifier.weight(1f)
        )
        Switch(checked = shown, onCheckedChange = onChange)
    }
}

/** Emoji icons show as they are. Other icon types show the first letter. */
private fun symbolOf(feature: FeatureModule): String =
    when (val icon = feature.icon) {
        is FeatureIcon.Emoji -> icon.value
        else -> feature.title.take(1)
    }
