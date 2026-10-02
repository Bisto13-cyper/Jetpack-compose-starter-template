package com.superapp.app.core.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance

/**
 * FILE 2 - The theme system. Any color, not only dark/light.
 *
 * ThemeSettings holds the choices the user makes in the Settings dashboard.
 * Colors are stored as plain Int (ARGB) so they are easy to save later.
 *
 * HOW TO CHANGE THE DEFAULT LOOK:
 *   Edit the default values in ThemeSettings below.
 *   Format: 0xFFRRGGBB.toInt()   (FF = fully visible, then red, green, blue)
 *
 * HOW TO READ THE SETTINGS FROM ANY SCREEN:
 *   val settings = LocalThemeSettings.current
 *   Example: settings.backgroundImagePath, settings.circleScale
 */
data class ThemeSettings(
    /** Main screen background color. */
    val background: Int = 0xFF050B12.toInt(),

    /** Cards, panels and sheets. */
    val surface: Int = 0xFF0E1A26.toInt(),

    /** Main accent: buttons, highlights, selected items. */
    val primary: Int = 0xFF22D3EE.toInt(),

    /** Text and icons drawn on top of the background. */
    val onBackground: Int = 0xFFE6F1F8.toInt(),

    /** Path of a background image picked by the user. null = no image, use color only. */
    val backgroundImagePath: String? = null,

    /** Size of the home circles. 1.0 = normal, 0.8 = smaller, 1.2 = bigger. */
    val circleScale: Float = 1f
)

/** Lets any screen read the current settings without passing them around. */
val LocalThemeSettings = staticCompositionLocalOf { ThemeSettings() }

/** Turns a saved Int color back into a Compose Color. */
fun Int.toColor(): Color = Color(this)

/** Picks black or white text so it is readable on top of the given color. */
private fun readableOn(color: Color): Color =
    if (color.luminance() > 0.5f) Color.Black else Color.White

/**
 * Wrap the whole app in this. It builds the Material theme from the
 * user's colors. If the background is bright it uses a light base,
 * otherwise a dark base, so any color combination stays usable.
 */
@Composable
fun AppTheme(
    settings: ThemeSettings,
    content: @Composable () -> Unit
) {
    val background = settings.background.toColor()
    val surface = settings.surface.toColor()
    val primary = settings.primary.toColor()
    val text = settings.onBackground.toColor()

    val base = if (background.luminance() < 0.5f) darkColorScheme() else lightColorScheme()

    val scheme = base.copy(
        primary = primary,
        onPrimary = readableOn(primary),
        secondary = primary,
        onSecondary = readableOn(primary),
        background = background,
        onBackground = text,
        surface = surface,
        onSurface = text,
        surfaceVariant = surface,
        onSurfaceVariant = text,
        outline = primary.copy(alpha = 0.5f)
    )

    CompositionLocalProvider(LocalThemeSettings provides settings) {
        MaterialTheme(colorScheme = scheme, content = content)
    }
}
