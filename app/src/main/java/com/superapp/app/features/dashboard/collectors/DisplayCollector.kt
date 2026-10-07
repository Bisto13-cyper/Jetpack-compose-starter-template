package com.superapp.app.features.dashboard.collectors

import android.content.Context
import android.content.res.Configuration
import android.hardware.display.DisplayManager
import android.os.Build
import android.util.DisplayMetrics
import android.view.Display
import com.superapp.app.features.dashboard.InfoRow
import com.superapp.app.features.dashboard.InfoSection
import kotlin.math.sqrt

object DisplayCollector {

    @Suppress("DEPRECATION")
    fun collect(context: Context): List<InfoSection> {
        val dm = context.getSystemService(Context.DISPLAY_SERVICE) as DisplayManager
        val display = dm.getDisplay(Display.DEFAULT_DISPLAY)
        val metrics = DisplayMetrics()
        display?.getRealMetrics(metrics)
        val res = context.resources
        val cfg = res.configuration

        val w = metrics.widthPixels
        val h = metrics.heightPixels
        val diagonal = if (metrics.xdpi > 0 && metrics.ydpi > 0 && w > 0 && h > 0)
            sqrt(((w / metrics.xdpi).toDouble().let { it * it }) + ((h / metrics.ydpi).toDouble().let { it * it }))
        else null

        val main = InfoSection(
            title = "Screen",
            rows = listOf(
                InfoRow("Resolution", if (w > 0 && h > 0) "$w x $h px" else null),
                InfoRow("Density", "${metrics.density} (${densityBucket(metrics.densityDpi)})"),
                InfoRow("DPI", metrics.densityDpi.toString()),
                InfoRow("Physical DPI (x / y)", if (metrics.xdpi > 0) "%.0f / %.0f".format(metrics.xdpi, metrics.ydpi) else null, "As reported by the manufacturer; may be approximate"),
                InfoRow("Approx. diagonal", diagonal?.let { "%.1f in".format(it) }, "Derived from reported physical DPI"),
                InfoRow("Size in dp", "${res.configuration.screenWidthDp} x ${res.configuration.screenHeightDp} dp"),
                InfoRow("Refresh rate", display?.refreshRate?.let { "%.0f Hz".format(it) }),
                InfoRow("Orientation", if (cfg.orientation == Configuration.ORIENTATION_LANDSCAPE) "Landscape" else "Portrait"),
                InfoRow("Wide colour gamut", if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) yesNo(cfg.isScreenWideColorGamut) else null),
                InfoRow("Font scale", "%.2f".format(cfg.fontScale)),
                InfoRow("Dark mode", if (cfg.uiMode and Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES) "On" else "Off")
            )
        )

        val hdr = InfoSection(
            title = "HDR",
            rows = listOf(InfoRow("Supported HDR types", hdrTypes(display)))
        )

        val modes = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && display != null) {
            InfoSection(
                title = "Display modes",
                rows = display.supportedModes.sortedWith(
                    compareByDescending<Display.Mode> { it.physicalWidth * it.physicalHeight }.thenByDescending { it.refreshRate }
                ).map {
                    InfoRow("Mode ${it.modeId}", "${it.physicalWidth} x ${it.physicalHeight} @ %.0f Hz".format(it.refreshRate))
                }
            )
        } else InfoSection("Display modes", listOf(InfoRow("Modes", null)))
        return listOf(main, hdr, modes)
    }

    private fun yesNo(b: Boolean) = if (b) "Yes" else "No"

    private fun densityBucket(dpi: Int) = when {
        dpi <= 120 -> "ldpi"
        dpi <= 160 -> "mdpi"
        dpi <= 240 -> "hdpi"
        dpi <= 320 -> "xhdpi"
        dpi <= 480 -> "xxhdpi"
        else -> "xxxhdpi"
    }

    @Suppress("DEPRECATION")
    private fun hdrTypes(display: Display?): String? {
        if (display == null || Build.VERSION.SDK_INT < Build.VERSION_CODES.N) return null
        val types: IntArray = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            display.mode.supportedHdrTypes
        } else {
            display.hdrCapabilities?.supportedHdrTypes ?: IntArray(0)
        }
        if (types.isEmpty()) return "None reported"
        return types.joinToString(", ") {
            when (it) {
                1 -> "Dolby Vision"
                2 -> "HDR10"
                3 -> "HLG"
                4 -> "HDR10+"
                else -> "Type $it"
            }
        }
    }
}
