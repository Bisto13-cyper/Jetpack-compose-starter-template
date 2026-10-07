package com.superapp.app.features.webview

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.superapp.app.core.feature.FeatureIcon
import com.superapp.app.core.feature.FeatureModule

class WebViewFeature : FeatureModule {
    override val id = "webview"
    override val title = "WebView"
    override val icon = FeatureIcon.Emoji("🌐")
    override val defaultAccent = Color(0xFF22D3EE)

    @Composable
    override fun Content(onBack: () -> Unit) {
        WebViewScreen()
    }
}
