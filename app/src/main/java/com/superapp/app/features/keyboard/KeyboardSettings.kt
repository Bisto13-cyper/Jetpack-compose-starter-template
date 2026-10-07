package com.superapp.app.features.keyboard

enum class KeyShape {
    SQUARE,
    ROUNDED_SQUARE,
    CIRCLE,
    PILL
}

/**
 * All persisted appearance + behaviour options for the custom keyboard.
 * Values are stored via KeyboardSettingsStore (SharedPreferences) and read
 * reactively by the IME Compose UI.
 */
data class KeyboardSettings(
    val backgroundColor: Long = 0xFF1B1B1F,
    val backgroundImagePath: String? = null,
    val backgroundImageAlpha: Float = 0.35f,
    val keyColor: Long = 0xFF2A2A30,
    val keyPressedColor: Long = 0xFF3F3F48,
    val keySpecialColor: Long = 0xFF1F1F24,
    val keyTextColor: Long = 0xFFFFFFFF,
    val keyShape: KeyShape = KeyShape.ROUNDED_SQUARE,
    val keyCornerRadius: Float = 10f,
    val keySpacing: Float = 4f,
    val keyBorderWidth: Float = 0f,
    val keyBorderColor: Long = 0xFF000000,
    val toolbarColor: Long = 0xFF111114,
    val toolbarTextColor: Long = 0xFFFFFFFF,
    val enabledLanguages: Set<String> = setOf("en"),
    val currentLanguageCode: String = "en",
    val showToolbar: Boolean = true,
    val hapticFeedback: Boolean = true,
    val keyHeightDp: Int = 46
)
