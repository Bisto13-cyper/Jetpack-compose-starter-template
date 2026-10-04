package com.superapp.app.core.settings

import android.content.Context
import com.superapp.app.core.theme.LineStyle
import com.superapp.app.core.theme.ThemeSettings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * FILE 3 - Saves and loads the user's settings.
 *
 * Everything is stored privately on the phone using SharedPreferences.
 * Nothing is sent anywhere. No extra library is needed.
 *
 * It keeps two things:
 *  1) theme       : the global colors / background image / circle size
 *  2) featureStyles: per-feature overrides (own accent color, own icon image)
 *
 * HOW TO ADD A NEW SETTING:
 *  1. Add the field to ThemeSettings (core/theme/AppTheme.kt)
 *  2. Add a key below in the companion object
 *  3. Add one line in loadTheme() and one line in updateTheme()
 */

/** Per-feature customization. null means "use the default". */
data class FeatureStyle(
    val accent: Int? = null,
    val iconPath: String? = null
)

class SettingsRepository(context: Context) {

    private val prefs = context.applicationContext
        .getSharedPreferences(FILE_NAME, Context.MODE_PRIVATE)

    private val _theme = MutableStateFlow(loadTheme())

    /** The current theme. Collect it in the UI to react to changes. */
    val theme: StateFlow<ThemeSettings> = _theme.asStateFlow()

    private val _featureStyles = MutableStateFlow(loadFeatureStyles())

    /** Per-feature styles, keyed by feature id. */
    val featureStyles: StateFlow<Map<String, FeatureStyle>> = _featureStyles.asStateFlow()

    /**
     * Change the theme and save it.
     * Example: repo.updateTheme { it.copy(primary = 0xFFFF5722.toInt()) }
     */
    fun updateTheme(change: (ThemeSettings) -> ThemeSettings) {
        val updated = change(_theme.value)
        _theme.value = updated
        prefs.edit()
            .putInt(K_BACKGROUND, updated.background)
            .putInt(K_SURFACE, updated.surface)
            .putInt(K_PRIMARY, updated.primary)
            .putInt(K_ON_BACKGROUND, updated.onBackground)
            .putString(K_BG_IMAGE, updated.backgroundImagePath)
            .putFloat(K_CIRCLE_SCALE, updated.circleScale)
            .putFloat(K_GLOW_STRENGTH, updated.glowStrength)
            .putFloat(K_GLOW_SIZE, updated.glowSize)
            .putString(K_LINE_STYLE, updated.lineStyle.name)
            .putFloat(K_LINE_WIDTH, updated.lineWidth)
            .putFloat(K_LINE_ALPHA, updated.lineAlpha)
            .also { editor ->
                // null means "use the default behavior", so the key is removed.
                putOptionalInt(editor, K_GLOW_COLOR, updated.glowColor)
                putOptionalInt(editor, K_LINE_COLOR, updated.lineColor)
            }
            .apply()
    }

    /**
     * Change the style of one feature and save it.
     * Example: repo.updateFeatureStyle("apis") { it.copy(accent = 0xFFFFC107.toInt()) }
     */
    fun updateFeatureStyle(featureId: String, change: (FeatureStyle) -> FeatureStyle) {
        val updated = change(_featureStyles.value[featureId] ?: FeatureStyle())
        val all = _featureStyles.value + (featureId to updated)
        _featureStyles.value = all

        val editor = prefs.edit()
        val accent = updated.accent
        if (accent != null) {
            editor.putInt(accentKey(featureId), accent)
        } else {
            editor.remove(accentKey(featureId))
        }
        editor.putString(iconKey(featureId), updated.iconPath)
        editor.putStringSet(K_STYLED_IDS, all.keys.toSet())
        editor.apply()
    }

    /** Erase every saved setting and go back to the defaults. */
    fun resetAll() {
        prefs.edit().clear().apply()
        _theme.value = ThemeSettings()
        _featureStyles.value = emptyMap()
    }

    // ---------- loading ----------

    private fun loadTheme(): ThemeSettings {
        val d = ThemeSettings()
        return ThemeSettings(
            background = prefs.getInt(K_BACKGROUND, d.background),
            surface = prefs.getInt(K_SURFACE, d.surface),
            primary = prefs.getInt(K_PRIMARY, d.primary),
            onBackground = prefs.getInt(K_ON_BACKGROUND, d.onBackground),
            backgroundImagePath = prefs.getString(K_BG_IMAGE, null),
            circleScale = prefs.getFloat(K_CIRCLE_SCALE, d.circleScale),
            glowStrength = prefs.getFloat(K_GLOW_STRENGTH, d.glowStrength),
            glowSize = prefs.getFloat(K_GLOW_SIZE, d.glowSize),
            glowColor = getOptionalInt(K_GLOW_COLOR),
            lineStyle = runCatching {
                LineStyle.valueOf(prefs.getString(K_LINE_STYLE, d.lineStyle.name) ?: d.lineStyle.name)
            }.getOrDefault(d.lineStyle),
            lineColor = getOptionalInt(K_LINE_COLOR),
            lineWidth = prefs.getFloat(K_LINE_WIDTH, d.lineWidth),
            lineAlpha = prefs.getFloat(K_LINE_ALPHA, d.lineAlpha)
        )
    }

    private fun getOptionalInt(key: String): Int? =
        if (prefs.contains(key)) prefs.getInt(key, 0) else null

    private fun putOptionalInt(editor: android.content.SharedPreferences.Editor, key: String, value: Int?) {
        if (value != null) editor.putInt(key, value) else editor.remove(key)
    }

    private fun loadFeatureStyles(): Map<String, FeatureStyle> {
        val ids = prefs.getStringSet(K_STYLED_IDS, emptySet()).orEmpty()
        return ids.associateWith { id ->
            FeatureStyle(
                accent = if (prefs.contains(accentKey(id))) prefs.getInt(accentKey(id), 0) else null,
                iconPath = prefs.getString(iconKey(id), null)
            )
        }
    }

    private fun accentKey(id: String) = "accent_$id"
    private fun iconKey(id: String) = "icon_$id"

    private companion object {
        const val FILE_NAME = "super_app_settings"
        const val K_BACKGROUND = "theme_background"
        const val K_SURFACE = "theme_surface"
        const val K_PRIMARY = "theme_primary"
        const val K_ON_BACKGROUND = "theme_on_background"
        const val K_BG_IMAGE = "theme_bg_image"
        const val K_CIRCLE_SCALE = "theme_circle_scale"
        const val K_GLOW_STRENGTH = "theme_glow_strength"
        const val K_GLOW_SIZE = "theme_glow_size"
        const val K_GLOW_COLOR = "theme_glow_color"
        const val K_LINE_STYLE = "theme_line_style"
        const val K_LINE_COLOR = "theme_line_color"
        const val K_LINE_WIDTH = "theme_line_width"
        const val K_LINE_ALPHA = "theme_line_alpha"
        const val K_STYLED_IDS = "styled_feature_ids"
    }
}
