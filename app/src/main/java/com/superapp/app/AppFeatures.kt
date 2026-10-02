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

/**
 * FILE 8 - THE ONLY LIST OF FEATURES IN THE APP.
 *
 * Every circle on the home screen and every item in the side menu
 * comes from this list, in this order.
 *
 * HOW TO ADD A FEATURE:
 *   1. Create your feature class (it implements FeatureModule).
 *   2. Add one line to the list below:  MyFeature(),
 *
 * HOW TO REMOVE A FEATURE: delete its line.
 * HOW TO REORDER: move the lines.
 *
 * Right now every item is a "Coming soon" placeholder so you can see
 * the whole home screen working. As each real feature is delivered,
 * its placeholder line is replaced by one real line.
 */
val appFeatures: List<FeatureModule> = listOf(
    ComingSoonFeature("apis", "APIs", "🔌", Color(0xFFFFC107)),
    ComingSoonFeature("editor", "Code Editor", "</>", Color(0xFFA855F7)),
    ComingSoonFeature("webview", "WebView", "🌐", Color(0xFF22D3EE)),
    ComingSoonFeature("dashboard", "Dashboard", "📊", Color(0xFF84CC16)),
    ComingSoonFeature("files", "Files", "📁", Color(0xFF60A5FA)),
    ComingSoonFeature("favorites", "Favorites", "⭐", Color(0xFFFB7185)),
    ComingSoonFeature("clock", "Clock", "⏰", Color(0xFFF97316)),
    ComingSoonFeature("markdown", "Markdown", "📝", Color(0xFF2DD4BF)),
    ComingSoonFeature("keyboard", "Keyboard", "⌨️", Color(0xFFE879F9))
)

/**
 * A temporary feature that just says "Coming soon".
 * It lets you see and test the home screen, the circles, the side
 * menu and the navigation before the real features exist.
 * Once a real feature replaces it, you can delete this class.
 */
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
            Text(
                text = title,
                color = defaultAccent,
                fontSize = 28.sp
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = "Coming soon",
                color = MaterialTheme.colorScheme.onBackground
            )
            Spacer(Modifier.height(24.dp))
            Button(onClick = onBack) {
                Text("Back to Home")
            }
        }
    }
}
