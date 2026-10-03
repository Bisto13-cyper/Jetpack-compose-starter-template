package com.superapp.app

import android.graphics.BitmapFactory
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import com.superapp.app.core.feature.FeatureRegistry
import com.superapp.app.core.navigation.Navigator
import com.superapp.app.core.navigation.Routes
import com.superapp.app.core.settings.SettingsRepository
import com.superapp.app.core.theme.AppTheme
import com.superapp.app.core.ui.SideMenuButton
import com.superapp.app.features.home.HomeScreen
import com.superapp.app.features.settings.SettingsEnv
import com.superapp.app.features.settings.SettingsScreen

/**
 * FILE 9 - The entry point. It connects everything:
 *   settings -> theme -> background -> current screen -> side button.
 *
 * You rarely need to edit this file. To add a feature, edit AppFeatures.kt.
 *
 * WHAT IT DOES:
 *  - Loads the saved settings and applies the colors.
 *  - Draws the background image (if the user picked one).
 *  - Shows the screen for the current route: Home, Settings, or a feature.
 *  - Draws the side button on top of every screen.
 *  - Phone back button: goes to Home from anywhere else.
 *
 * HOW TO CHANGE:
 *  - Background image visibility: change alpha = 0.35f in BackgroundImage
 *  - Space reserved under the side button: change 60.dp
 *  - Settings screen: replace SettingsComingSoon when the real one exists
 */
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val repository = SettingsRepository(this)
        val registry = FeatureRegistry(appFeatures)

        setContent {
            AppRoot(repository = repository, registry = registry)
        }
    }
}

@Composable
fun AppRoot(
    repository: SettingsRepository,
    registry: FeatureRegistry
) {
    val theme by repository.theme.collectAsState()
    val styles by repository.featureStyles.collectAsState()
    val navigator = remember { Navigator() }

    AppTheme(settings = theme) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
        ) {
            BackgroundImage(path = theme.backgroundImagePath)

            // Phone back button: return to Home from any other screen.
            BackHandler(enabled = !navigator.isHome) { navigator.home() }

            when (val route = navigator.current) {
                Routes.HOME -> HomeScreen(
                    features = registry.all(),
                    styles = styles,
                    onOpen = { navigator.go(it) },
                    onOpenSettings = { navigator.settings() },
                    modifier = Modifier.navigationBarsPadding()
                )

                Routes.SETTINGS -> ScreenFrame {
			SettingsScreen(env = remember {
			SettingsEnv(repository,registry)			
})
                    }

                else -> {
                    val feature = registry.find(route)
                    if (feature != null) {
                        ScreenFrame { feature.Content(onBack = { navigator.home() }) }
                    } else {
                        // Unknown route: go home.
                        LaunchedEffect(route) { navigator.home() }
                    }
                }
            }

            SideMenuButton(
                features = registry.all(),
                currentRoute = navigator.current,
                onNavigate = { navigator.go(it) },
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .statusBarsPadding()
                    .padding(top = 8.dp, start = 12.dp)
            )
        }
    }
}

/** Leaves room for the side button and the system bars around a screen. */
@Composable
private fun ScreenFrame(content: @Composable () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(top = 60.dp)
    ) {
        content()
    }
}



/** Draws the user's background image faintly behind everything. */
@Composable
private fun BackgroundImage(path: String?) {
    if (path == null) return
    val bitmap = remember(path) { loadScaledBitmap(path) } ?: return

    Image(
        bitmap = bitmap,
        contentDescription = null,
        contentScale = ContentScale.Crop,
        alpha = 0.35f,
        modifier = Modifier.fillMaxSize()
    )
}

/** Loads an image from the phone, shrunk so big photos don't use too much memory. */
private fun loadScaledBitmap(path: String, maxSide: Int = 1440): ImageBitmap? {
    return runCatching {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(path, bounds)

        var sample = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / sample > maxSide) sample *= 2

        val options = BitmapFactory.Options().apply { inSampleSize = sample }
        BitmapFactory.decodeFile(path, options)?.asImageBitmap()
    }.getOrNull()
}
l
