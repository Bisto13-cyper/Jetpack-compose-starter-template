package com.superapp.app.features.dashboard

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.superapp.app.core.feature.FeatureIcon
import com.superapp.app.core.feature.FeatureModule
import com.superapp.app.features.dashboard.ui.DashboardScreen

class DashboardFeature : FeatureModule {
    override val id = "dashboard"
    override val title = "Dashboard"
    override val icon = FeatureIcon.Emoji("📊")
    override val defaultAccent = Color(0xFF84CC16)

    @Composable
    override fun Content(onBack: () -> Unit) {
        DashboardScreen(onBack = onBack)
    }
}
