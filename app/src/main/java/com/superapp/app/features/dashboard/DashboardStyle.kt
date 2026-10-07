package com.superapp.app.features.dashboard

import android.content.Context

enum class CardShape { ROUNDED, CUT, SQUARE }
enum class CardLook { FILLED, OUTLINED }

/**
 * Dashboard-only appearance overrides. A null colour means "follow the app theme"
 * (MaterialTheme, which the project's AppTheme provides), so there is no second theme system.
 */
data class DashboardStyle(
    val backgroundColor: Int? = null,
    val cardColor: Int? = null,
    val textColor: Int? = null,
    val accentColor: Int? = null,
    val progressColor: Int? = null,
    val cardAlpha: Float = 1f,
    val cornerRadiusDp: Int = 16,
    val spacingDp: Int = 12,
    val cardShape: CardShape = CardShape.ROUNDED,
    val cardLook: CardLook = CardLook.FILLED
)

class DashboardStyleStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("dashboard_style", Context.MODE_PRIVATE)

    fun load(): DashboardStyle = DashboardStyle(
        backgroundColor = color("bg"),
        cardColor = color("card"),
        textColor = color("text"),
        accentColor = color("accent"),
        progressColor = color("progress"),
        cardAlpha = prefs.getFloat("alpha", 1f),
        cornerRadiusDp = prefs.getInt("radius", 16),
        spacingDp = prefs.getInt("spacing", 12),
        cardShape = runCatching { CardShape.valueOf(prefs.getString("shape", "") ?: "") }.getOrDefault(CardShape.ROUNDED),
        cardLook = runCatching { CardLook.valueOf(prefs.getString("look", "") ?: "") }.getOrDefault(CardLook.FILLED)
    )

    fun save(s: DashboardStyle) {
        prefs.edit().apply {
            putColor("bg", s.backgroundColor)
            putColor("card", s.cardColor)
            putColor("text", s.textColor)
            putColor("accent", s.accentColor)
            putColor("progress", s.progressColor)
            putFloat("alpha", s.cardAlpha)
            putInt("radius", s.cornerRadiusDp)
            putInt("spacing", s.spacingDp)
            putString("shape", s.cardShape.name)
            putString("look", s.cardLook.name)
        }.apply()
    }

    private fun color(key: String): Int? = if (prefs.contains(key)) prefs.getInt(key, 0) else null

    private fun android.content.SharedPreferences.Editor.putColor(key: String, value: Int?) {
        if (value == null) remove(key) else putInt(key, value)
    }
}
