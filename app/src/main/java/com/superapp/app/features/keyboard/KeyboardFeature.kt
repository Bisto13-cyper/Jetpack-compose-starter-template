package com.superapp.app.features.keyboard

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.superapp.app.core.feature.FeatureIcon
import com.superapp.app.core.feature.FeatureModule

class KeyboardFeature : FeatureModule {
    override val id = "keyboard"
    override val title = "Keyboard"
    override val icon = FeatureIcon.Emoji("⌨️")
    override val defaultAccent = Color(0xFFE879F9)

    @Composable
    override fun Content(onBack: () -> Unit) {
        KeyboardSettingsScreen(onClose = onBack)
    }
}
