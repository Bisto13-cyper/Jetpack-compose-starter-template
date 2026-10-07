package com.superapp.app.features.password

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.superapp.app.core.feature.FeatureIcon
import com.superapp.app.core.feature.FeatureModule

class PasswordFeature : FeatureModule {
    override val id = "passwords"
    override val title = "Passwords Storage"
    override val icon = FeatureIcon.Emoji("🗝️")
    override val defaultAccent = Color(0xFF333333)

    @Composable
    override fun Content(onBack: () -> Unit) {
        PasswordVaultScreen(onClose = onBack)
    }
}
