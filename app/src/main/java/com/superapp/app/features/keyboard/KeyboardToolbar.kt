package com.superapp.app.features.keyboard

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Compact toolbar that sits above the keyboard.
 * The trailing "⋯" opens the More panel with the remaining tools.
 */
@Composable
fun KeyboardToolbar(
    service: CustomKeyboardService,
    settings: KeyboardSettings,
    activePanel: KeyboardPanel,
    onPanel: (KeyboardPanel) -> Unit
) {
    val tint = Color(settings.toolbarTextColor)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(settings.toolbarColor))
            .padding(horizontal = 6.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        ToolbarButton("📋", "Clip", tint, activePanel == KeyboardPanel.CLIPBOARD) {
            onPanel(if (activePanel == KeyboardPanel.CLIPBOARD) KeyboardPanel.NONE else KeyboardPanel.CLIPBOARD)
        }
        ToolbarButton("😀", "Emoji", tint, activePanel == KeyboardPanel.EMOJI) {
            onPanel(if (activePanel == KeyboardPanel.EMOJI) KeyboardPanel.NONE else KeyboardPanel.EMOJI)
        }
        ToolbarButton("文", "Translate", tint, activePanel == KeyboardPanel.TRANSLATE) {
            onPanel(if (activePanel == KeyboardPanel.TRANSLATE) KeyboardPanel.NONE else KeyboardPanel.TRANSLATE)
        }
        ToolbarButton("↶", "Undo", tint, false) {
            onPanel(KeyboardPanel.NONE)
            service.undo()
        }
        ToolbarButton("↷", "Redo", tint, false) {
            onPanel(KeyboardPanel.NONE)
            service.redo()
        }
        ToolbarButton("⚙", "Settings", tint, false) {
            KeyboardSettingsActivity.start(service)
        }
        ToolbarButton("⋯", "More", tint, activePanel == KeyboardPanel.MORE) {
            onPanel(if (activePanel == KeyboardPanel.MORE) KeyboardPanel.NONE else KeyboardPanel.MORE)
        }
    }
}

@Composable
private fun ToolbarButton(
    glyph: String,
    label: String,
    tint: Color,
    active: Boolean,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .size(40.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(
                if (active) tint.copy(alpha = 0.18f) else Color.Transparent
            )
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = glyph,
            color = tint,
            fontSize = 18.sp,
            fontWeight = if (active) FontWeight.Bold else FontWeight.Normal
        )
    }
}
