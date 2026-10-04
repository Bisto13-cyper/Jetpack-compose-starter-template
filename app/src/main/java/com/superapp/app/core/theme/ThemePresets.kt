package com.superapp.app.core.theme

/**
 * SETTINGS FILE 5 - Ready-made color themes.
 *
 * Each preset is four colors. Tapping one in Settings > Appearance >
 * Colors & Themes applies all four at once. The user can still change
 * any color afterwards.
 *
 * Color format: 0xFFRRGGBB  (FF = fully visible, then red, green, blue)
 *
 * HOW TO ADD A THEME:
 *   Add one line to themePresets below, for example:
 *   ThemePreset("Ocean", 0xFF03111A, 0xFF0A2231, 0xFF38BDF8, 0xFFE0F2FE),
 *
 * HOW TO REMOVE ONE: delete its line.
 */
data class ThemePreset(
    val name: String,
    val background: Long,
    val surface: Long,
    val primary: Long,
    val text: Long
) {
    /** Returns the given settings with this preset's four colors applied. */
    fun applyTo(base: ThemeSettings): ThemeSettings = base.copy(
        background = background.toInt(),
        surface = surface.toInt(),
        primary = primary.toInt(),
        onBackground = text.toInt()
    )
}

val themePresets: List<ThemePreset> = listOf(
    ThemePreset("Cyber", 0xFF050B12, 0xFF0E1A26, 0xFF22D3EE, 0xFFE6F1F8),
    ThemePreset("Sunset", 0xFF140A0F, 0xFF241218, 0xFFFB7185, 0xFFFFE4E6),
    ThemePreset("Forest", 0xFF07120C, 0xFF10241A, 0xFF84CC16, 0xFFE8F5E9),
    ThemePreset("Royal", 0xFF0B0716, 0xFF1A1230, 0xFFA855F7, 0xFFF1E8FF),
    ThemePreset("Amber", 0xFF120D04, 0xFF241A0A, 0xFFFFC107, 0xFFFFF4D6),
    ThemePreset("Paper", 0xFFF6F7F9, 0xFFFFFFFF, 0xFF2563EB, 0xFF111827),
    ThemePreset("Mono", 0xFF000000, 0xFF161616, 0xFFFFFFFF, 0xFFEDEDED)
)
