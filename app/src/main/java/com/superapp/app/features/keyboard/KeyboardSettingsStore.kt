package com.superapp.app.features.keyboard

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * Process-wide reactive store for KeyboardSettings.
 *
 * The IME service and the KeyboardSettingsActivity both call init(context)
 * and then read/write through this object. Because the IME runs in the same
 * process as the app, updates from the settings screen are visible
 * immediately in the keyboard UI.
 */
object KeyboardSettingsStore {

    private const val PREFS = "keyboard_settings"
    private var prefs: SharedPreferences? = null

    var settings by mutableStateOf(KeyboardSettings())
        private set

    fun init(context: Context) {
        if (prefs != null) return
        prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        settings = load()
    }

    fun update(transform: (KeyboardSettings) -> KeyboardSettings) {
        val next = transform(settings)
        settings = next
        save(next)
    }

    private fun load(): KeyboardSettings {
        val p = prefs ?: return KeyboardSettings()
        return KeyboardSettings(
            backgroundColor = p.getLong("backgroundColor", 0xFF1B1B1F),
            backgroundImagePath = p.getString("backgroundImagePath", null),
            backgroundImageAlpha = p.getFloat("backgroundImageAlpha", 0.35f),
            keyColor = p.getLong("keyColor", 0xFF2A2A30),
            keyPressedColor = p.getLong("keyPressedColor", 0xFF3F3F48),
            keySpecialColor = p.getLong("keySpecialColor", 0xFF1F1F24),
            keyTextColor = p.getLong("keyTextColor", 0xFFFFFFFF),
            keyShape = runCatching {
                KeyShape.valueOf(p.getString("keyShape", KeyShape.ROUNDED_SQUARE.name)!!)
            }.getOrDefault(KeyShape.ROUNDED_SQUARE),
            keyCornerRadius = p.getFloat("keyCornerRadius", 10f),
            keySpacing = p.getFloat("keySpacing", 4f),
            keyBorderWidth = p.getFloat("keyBorderWidth", 0f),
            keyBorderColor = p.getLong("keyBorderColor", 0xFF000000),
            toolbarColor = p.getLong("toolbarColor", 0xFF111114),
            toolbarTextColor = p.getLong("toolbarTextColor", 0xFFFFFFFF),
            enabledLanguages = p.getStringSet("enabledLanguages", setOf("en")) ?: setOf("en"),
            currentLanguageCode = p.getString("currentLanguageCode", "en") ?: "en",
            showToolbar = p.getBoolean("showToolbar", true),
            hapticFeedback = p.getBoolean("hapticFeedback", true),
            keyHeightDp = p.getInt("keyHeightDp", 46)
        )
    }

    private fun save(s: KeyboardSettings) {
        prefs?.edit()?.apply {
            putLong("backgroundColor", s.backgroundColor)
            if (s.backgroundImagePath.isNullOrBlank()) {
                remove("backgroundImagePath")
            } else {
                putString("backgroundImagePath", s.backgroundImagePath)
            }
            putFloat("backgroundImageAlpha", s.backgroundImageAlpha)
            putLong("keyColor", s.keyColor)
            putLong("keyPressedColor", s.keyPressedColor)
            putLong("keySpecialColor", s.keySpecialColor)
            putLong("keyTextColor", s.keyTextColor)
            putString("keyShape", s.keyShape.name)
            putFloat("keyCornerRadius", s.keyCornerRadius)
            putFloat("keySpacing", s.keySpacing)
            putFloat("keyBorderWidth", s.keyBorderWidth)
            putLong("keyBorderColor", s.keyBorderColor)
            putLong("toolbarColor", s.toolbarColor)
            putLong("toolbarTextColor", s.toolbarTextColor)
            putStringSet("enabledLanguages", s.enabledLanguages)
            putString("currentLanguageCode", s.currentLanguageCode)
            putBoolean("showToolbar", s.showToolbar)
            putBoolean("hapticFeedback", s.hapticFeedback)
            putInt("keyHeightDp", s.keyHeightDp)
            apply()
        }
    }
}
