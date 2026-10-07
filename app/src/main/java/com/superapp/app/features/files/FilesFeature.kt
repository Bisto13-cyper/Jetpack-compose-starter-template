package com.superapp.app.features.files

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.superapp.app.core.feature.FeatureIcon
import com.superapp.app.core.feature.FeatureModule

class FilesFeature : FeatureModule {
    override val id = "files"
    override val title = "Files"
    override val icon = FeatureIcon.Emoji("📁")
    override val defaultAccent = Color(0xFF60A5FA)

    @Composable
    override fun Content(onBack: () -> Unit) {
        FilesScreen(onClose = onBack)
    }
}
