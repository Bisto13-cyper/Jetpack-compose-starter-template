package com.superapp.app.features.dashboard

import android.content.Context
import android.text.format.Formatter

enum class DashboardCategory(val title: String) {
    OVERVIEW("Overview"),
    CPU("CPU"),
    RAM("RAM"),
    BATTERY("Battery"),
    STORAGE("Storage"),
    NETWORK("Network"),
    DEVICE("Device"),
    DISPLAY("Display"),
    SENSORS("Sensors"),
    SYSTEM("System"),
    OTHER("Other")
}

/** One label/value line. A null [value] means Android does not expose it here; the UI shows "Unavailable". */
data class InfoRow(val label: String, val value: String?, val note: String? = null)

/**
 * One card. [progress] (0..1) draws a usage bar. [actionId]/[actionLabel] let a card ask the screen
 * for a user action (currently only the optional location permission for the Wi-Fi name).
 */
data class InfoSection(
    val title: String,
    val rows: List<InfoRow>,
    val progress: Float? = null,
    val footnote: String? = null,
    val actionId: String? = null,
    val actionLabel: String? = null
)

const val UNAVAILABLE = "Unavailable"
const val ACTION_GRANT_LOCATION = "grant_location"

fun fileSize(context: Context, bytes: Long): String = Formatter.formatFileSize(context, bytes)

fun percent(fraction: Double): String = "%.0f%%".format(fraction * 100.0)
