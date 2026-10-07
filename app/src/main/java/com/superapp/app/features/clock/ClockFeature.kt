package com.superapp.app.features.clock

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.superapp.app.core.feature.FeatureIcon
import com.superapp.app.core.feature.FeatureModule
import com.superapp.app.features.clock.ui.ClockScreen

class ClockFeature : FeatureModule {
    override val id = "clock"
    override val title = "Clock"
    override val icon = FeatureIcon.Emoji("⏰")
    override val defaultAccent = Color(0xFFF97316)

    @Composable
    override fun Content(onBack: () -> Unit) {
        ClockScreen(onBack = onBack)
    }
}
