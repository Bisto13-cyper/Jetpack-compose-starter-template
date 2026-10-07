package com.superapp.app.features.editor

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.superapp.app.core.feature.FeatureIcon
import com.superapp.app.core.feature.FeatureModule

class EditorFeature : FeatureModule {
    override val id = "editor"
    override val title = "Code Editor"
    override val icon = FeatureIcon.Emoji("</>")
    override val defaultAccent = Color(0xFFA855F7)

    @Composable
    override fun Content(onBack: () -> Unit) {
        BackHandler(onBack = onBack)
        EditorRoot()
    }
}
