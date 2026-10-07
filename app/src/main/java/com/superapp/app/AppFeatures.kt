package com.superapp.app

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.superapp.app.core.feature.FeatureIcon
import com.superapp.app.core.feature.FeatureModule
import com.superapp.app.features.api.ApiFeature
import com.superapp.app.features.clock.ClockFeature
import com.superapp.app.features.dashboard.DashboardFeature
import com.superapp.app.features.editor.EditorFeature
import com.superapp.app.features.favorites.FavoritesFeature
import com.superapp.app.features.files.FilesFeature
import com.superapp.app.features.keyboard.KeyboardFeature
import com.superapp.app.features.password.PasswordFeature
import com.superapp.app.features.webview.WebViewFeature

/**
 * The single authoritative list of Home/SideMenu features.
 * Feature implementations live inside their own feature packages.
 */
val appFeatures: List<FeatureModule> = listOf(
    ApiFeature(),
    EditorFeature(),
    WebViewFeature(),
    DashboardFeature(),
    FilesFeature(),
    FavoritesFeature(),
    ClockFeature(),
    ComingSoonFeature("markdown", "Markdown", "📝", Color(0xFF2DD4BF)),
    KeyboardFeature(),
    PasswordFeature()
)

/** Placeholder for features intentionally not implemented in this integration pass. */
class ComingSoonFeature(
    override val id: String,
    override val title: String,
    emoji: String,
    override val defaultAccent: Color
) : FeatureModule {
    override val icon: FeatureIcon = FeatureIcon.Emoji(emoji)

    @Composable
    override fun Content(onBack: () -> Unit) {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(text = title, color = defaultAccent, fontSize = 28.sp)
            Spacer(Modifier.height(8.dp))
            Text(text = "Coming soon", color = MaterialTheme.colorScheme.onBackground)
            Spacer(Modifier.height(24.dp))
            Button(onClick = onBack) { Text("Back to Home") }
        }
    }
}
