package com.superapp.app.features.api

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.superapp.app.core.feature.FeatureIcon
import com.superapp.app.core.feature.FeatureModule

class ApiFeature : FeatureModule {
    override val id = "apis"
    override val title = "APIs"
    override val icon = FeatureIcon.Emoji("🔌")
    override val defaultAccent = Color(0xFFFFC107)

    @Composable
    override fun Content(onBack: () -> Unit) {
        ApiScreen()
    }
}
