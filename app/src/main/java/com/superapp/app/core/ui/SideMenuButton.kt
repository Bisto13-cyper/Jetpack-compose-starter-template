package com.superapp.app.core.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.superapp.app.core.feature.FeatureIcon
import com.superapp.app.core.feature.FeatureModule
import com.superapp.app.core.navigation.Routes

/**
 * FILE 5 - The small button at the top of the screen.
 *
 * Tap it and a menu opens with: Home, every feature, and Settings.
 * It builds the list from the features you pass in, so a new feature
 * appears here automatically.
 *
 * HOW TO USE (in the main screen, later):
 *   SideMenuButton(
 *       features = registry.all(),
 *       currentRoute = navigator.current,
 *       onNavigate = { navigator.go(it) },
 *       modifier = Modifier.statusBarsPadding().padding(12.dp)
 *   )
 *
 * HOW TO CHANGE:
 *  - Button size: change 44.dp
 *  - Button symbol: change "☰"
 *  - Menu order or extra items: edit the DropdownMenu block
 */
@Composable
fun SideMenuButton(
    features: List<FeatureModule>,
    currentRoute: String,
    onNavigate: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var open by remember { mutableStateOf(false) }
    val colors = MaterialTheme.colorScheme

    Box(modifier = modifier) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(CircleShape)
                .background(colors.surface)
                .border(BorderStroke(1.dp, colors.primary.copy(alpha = 0.6f)), CircleShape)
                .clickable { open = true },
            contentAlignment = Alignment.Center
        ) {
            Text(text = "☰", color = colors.onSurface, fontSize = 20.sp)
        }

        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            MenuRow(
                label = "🏠  Home",
                selected = currentRoute == Routes.HOME
            ) {
                open = false
                onNavigate(Routes.HOME)
            }

            features.forEach { feature ->
                MenuRow(
                    label = "${symbolOf(feature)}  ${feature.title}",
                    selected = currentRoute == feature.id
                ) {
                    open = false
                    onNavigate(feature.id)
                }
            }

            MenuRow(
                label = "⚙️  Settings",
                selected = currentRoute == Routes.SETTINGS
            ) {
                open = false
                onNavigate(Routes.SETTINGS)
            }
        }
    }
}

@Composable
private fun MenuRow(label: String, selected: Boolean, onClick: () -> Unit) {
    DropdownMenuItem(
        text = {
            Text(
                text = label,
                color = if (selected) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.onSurface,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal
            )
        },
        onClick = onClick
    )
}

/** Emoji icons show as they are. Other icon types show the first letter. */
private fun symbolOf(feature: FeatureModule): String =
    when (val icon = feature.icon) {
        is FeatureIcon.Emoji -> icon.value
        else -> feature.title.take(1)
    }
