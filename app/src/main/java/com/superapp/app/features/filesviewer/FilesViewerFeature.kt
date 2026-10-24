package com.superapp.app.features.filesviewer

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.superapp.app.core.feature.FeatureIcon
import com.superapp.app.core.feature.FeatureModule

/**
 * Files Viewer node.
 *  - Opened from a file tap in Files  -> shows that file.
 *  - Opened from the circle/side menu -> shows the Files picker; choosing a file opens it here.
 */
class FilesViewerFeature : FeatureModule {
    override val id = FileOpenRouter.ROUTE
    override val title = "Files Viewer"
    override val icon = FeatureIcon.Emoji("📄")
    override val defaultAccent = Color(0xFF2DD4BF)

    @Composable
    override fun Content(onBack: () -> Unit) {
        FilesViewerScreen(onClose = onBack)
    }
}
