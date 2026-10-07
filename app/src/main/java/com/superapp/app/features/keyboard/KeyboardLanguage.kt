package com.superapp.app.features.keyboard

/**
 * The physical layout the keyboard renders for a given language.
 * Adding a new language later only requires adding a KeyboardLanguage entry
 * (and, if needed, a new KeyboardLayoutKind).
 */
enum class KeyboardLayoutKind { QWERTY, QWERTZ, AZERTY }

data class KeyboardLanguage(
    val code: String,
    val displayName: String,
    val layoutKind: KeyboardLayoutKind
)

object KeyboardLanguages {

    /**
     * Curated language list. New languages can be appended here without
     * touching the keyboard service or UI code.
     */
    val all: List<KeyboardLanguage> = listOf(
        KeyboardLanguage("en", "English", KeyboardLayoutKind.QWERTY),
        KeyboardLanguage("de", "Deutsch", KeyboardLayoutKind.QWERTZ),
        KeyboardLanguage("fr", "Français", KeyboardLayoutKind.AZERTY),
        KeyboardLanguage("es", "Español", KeyboardLayoutKind.QWERTY),
        KeyboardLanguage("it", "Italiano", KeyboardLayoutKind.QWERTY),
        KeyboardLanguage("pt", "Português", KeyboardLayoutKind.QWERTY)
    )

    fun byCode(code: String): KeyboardLanguage =
        all.firstOrNull { it.code == code } ?: all.first()
}
