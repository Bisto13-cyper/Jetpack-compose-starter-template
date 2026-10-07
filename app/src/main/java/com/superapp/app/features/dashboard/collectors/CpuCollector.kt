package com.superapp.app.features.dashboard.collectors

import android.content.Context
import android.os.Build
import android.os.PowerManager
import android.os.Process
import android.os.SystemClock
import com.superapp.app.features.dashboard.InfoRow
import com.superapp.app.features.dashboard.InfoSection
import com.superapp.app.features.dashboard.percent
import java.io.File

/**
 * CPU information using only what Android exposes to normal apps.
 *
 * Not obtainable on modern Android (and therefore never faked): system-wide CPU load on Android 8+
 * (/proc/stat is blocked by SELinux), CPU temperature, and per-core frequency on most devices.
 * Each of these is attempted through a normal file read and reported as Unavailable on failure.
 */
class CpuCollector(private val context: Context) {

    private var lastTotal = -1L
    private var lastIdle = 0L
    private var lastProcCpuMs = -1L
    private var lastWallMs = 0L

    fun collect(): List<InfoSection> {
        val cores = Runtime.getRuntime().availableProcessors()
        val info = readCpuInfo()

        val usage = mutableListOf<InfoRow>()
        usage += InfoRow("System CPU usage", systemUsage(), "Android 8+ blocks apps from reading system-wide CPU load")
        usage += InfoRow("This app's CPU usage", appUsage(cores), "Share of total CPU capacity used by this app's process")
        usage += InfoRow("Thermal status", thermalStatus(), "Coarse state only; Android does not expose CPU temperature")

        val summary = InfoSection(
            title = "Processor",
            rows = buildList {
                add(InfoRow("Available processors", cores.toString()))
                add(InfoRow("Primary ABI", Build.SUPPORTED_ABIS.firstOrNull()))
                add(InfoRow("Supported ABIs", Build.SUPPORTED_ABIS.joinToString(", ").ifBlank { null }))
                add(InfoRow("64-bit process", if (Process.is64Bit()) "Yes" else "No"))
                add(InfoRow("Hardware", info["hardware"] ?: Build.HARDWARE))
                add(InfoRow("Processor name", info["model name"] ?: info["processor"], "From /proc/cpuinfo when readable"))
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    add(InfoRow("SoC manufacturer", Build.SOC_MANUFACTURER.takeUnless { it == Build.UNKNOWN }))
                    add(InfoRow("SoC model", Build.SOC_MODEL.takeUnless { it == Build.UNKNOWN }))
                }
                add(InfoRow("Features", info["features"]))
            }
        )

        val load = InfoSection("Load", usage)

        val perCore = InfoSection(
            title = "Per-core frequency",
            rows = (0 until cores).map { i ->
                val cur = readKhz("/sys/devices/system/cpu/cpu$i/cpufreq/scaling_cur_freq")
                val max = readKhz("/sys/devices/system/cpu/cpu$i/cpufreq/cpuinfo_max_freq")
                val value = when {
                    cur != null && max != null -> "${cur / 1000} MHz (max ${max / 1000} MHz)"
                    cur != null -> "${cur / 1000} MHz"
                    max != null -> "max ${max / 1000} MHz"
                    else -> null
                }
                InfoRow("Core $i", value)
            },
            footnote = "Frequency files under /sys are restricted on many devices; offline or protected cores show Unavailable."
        )
        return listOf(summary, load, perCore)
    }

    private fun systemUsage(): String? {
        val line = try {
            File("/proc/stat").bufferedReader().use { it.readLine() }
        } catch (e: Exception) {
            return null
        } ?: return null
        val p = line.trim().split(Regex("\\s+"))
        if (p.size < 8 || p[0] != "cpu") return null
        val v = p.drop(1).mapNotNull { it.toLongOrNull() }
        if (v.size < 7) return null
        val idle = v[3] + v[4]
        val total = v.take(8).sum()
        val result = if (lastTotal >= 0 && total > lastTotal) {
            val dTotal = (total - lastTotal).toDouble()
            val dIdle = (idle - lastIdle).toDouble()
            percent(((dTotal - dIdle) / dTotal).coerceIn(0.0, 1.0))
        } else "Measuring..."
        lastTotal = total
        lastIdle = idle
        return result
    }

    private fun appUsage(cores: Int): String {
        val cpuMs = Process.getElapsedCpuTime()
        val wallMs = SystemClock.elapsedRealtime()
        val result = if (lastProcCpuMs >= 0 && wallMs > lastWallMs) {
            val frac = (cpuMs - lastProcCpuMs).toDouble() / ((wallMs - lastWallMs).toDouble() * cores)
            percent(frac.coerceIn(0.0, 1.0))
        } else "Measuring..."
        lastProcCpuMs = cpuMs
        lastWallMs = wallMs
        return result
    }

    private fun thermalStatus(): String? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return null
        val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        return when (pm.currentThermalStatus) {
            PowerManager.THERMAL_STATUS_NONE -> "Normal"
            PowerManager.THERMAL_STATUS_LIGHT -> "Light throttling"
            PowerManager.THERMAL_STATUS_MODERATE -> "Moderate throttling"
            PowerManager.THERMAL_STATUS_SEVERE -> "Severe throttling"
            PowerManager.THERMAL_STATUS_CRITICAL -> "Critical"
            PowerManager.THERMAL_STATUS_EMERGENCY -> "Emergency"
            PowerManager.THERMAL_STATUS_SHUTDOWN -> "Shutdown imminent"
            else -> null
        }
    }

    private fun readKhz(path: String): Long? = try {
        File(path).readText().trim().toLong()
    } catch (e: Exception) {
        null
    }

    private fun readCpuInfo(): Map<String, String> {
        val out = mutableMapOf<String, String>()
        try {
            File("/proc/cpuinfo").forEachLine { line ->
                val idx = line.indexOf(':')
                if (idx > 0) {
                    val key = line.substring(0, idx).trim().lowercase()
                    val value = line.substring(idx + 1).trim()
                    if (value.isNotEmpty() && key !in out) out[key] = value
                }
            }
        } catch (e: Exception) {
            // Unreadable on this device.
        }
        return out
    }
}
