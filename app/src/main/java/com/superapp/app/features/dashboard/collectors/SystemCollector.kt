package com.superapp.app.features.dashboard.collectors

import android.app.ActivityManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.PowerManager
import android.os.SystemClock
import android.provider.Settings
import com.superapp.app.features.dashboard.InfoRow
import com.superapp.app.features.dashboard.InfoSection
import java.text.DateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/** Device, System and Other categories. Only public Build / Settings / PackageManager data is read. */
object SystemCollector {

    fun device(context: Context): List<InfoSection> {
        val deviceName = try {
            Settings.Global.getString(context.contentResolver, "device_name")?.takeIf { it.isNotBlank() }
        } catch (e: Exception) { null }
        val tz = TimeZone.getDefault()
        return listOf(
            InfoSection(
                "Device",
                listOf(
                    InfoRow("Manufacturer", Build.MANUFACTURER),
                    InfoRow("Brand", Build.BRAND),
                    InfoRow("Model", Build.MODEL),
                    InfoRow("Device name", deviceName),
                    InfoRow("Product", Build.PRODUCT),
                    InfoRow("Board", Build.BOARD),
                    InfoRow("Hardware", Build.HARDWARE),
                    InfoRow("Architecture (primary ABI)", Build.SUPPORTED_ABIS.firstOrNull()),
                    InfoRow("Supported ABIs", Build.SUPPORTED_ABIS.joinToString(", ").ifBlank { null })
                )
            ),
            InfoSection(
                "Software",
                listOf(
                    InfoRow("Android version", Build.VERSION.RELEASE),
                    InfoRow("SDK level", Build.VERSION.SDK_INT.toString()),
                    InfoRow("Security patch", if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) Build.VERSION.SECURITY_PATCH else null),
                    InfoRow("Build ID", Build.ID),
                    InfoRow("Build display", Build.DISPLAY),
                    InfoRow("Build type / tags", "${Build.TYPE} / ${Build.TAGS}"),
                    InfoRow("Build time", DateFormat.getDateTimeInstance().format(Date(Build.TIME))),
                    InfoRow("Incremental", Build.VERSION.INCREMENTAL)
                )
            ),
            InfoSection(
                "Region",
                listOf(
                    InfoRow("Locale", Locale.getDefault().toLanguageTag()),
                    InfoRow("Time zone", "${tz.id} (${tz.getDisplayName(false, TimeZone.SHORT)})")
                )
            ),
            InfoSection(
                "Not shown on purpose",
                listOf(
                    InfoRow("Serial number, IMEI, MAC address", null, "Android restricts these identifiers; this app never requests them")
                )
            )
        )
    }

    fun system(context: Context): List<InfoSection> {
        val elapsed = SystemClock.elapsedRealtime()
        val bootTime = System.currentTimeMillis() - elapsed
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val pm = context.packageManager
        val features = pm.systemAvailableFeatures.mapNotNull { it.name }.sorted()
        val hasFeature = { f: String -> if (pm.hasSystemFeature(f)) "Yes" else "No" }

        return listOf(
            InfoSection(
                "System",
                listOf(
                    InfoRow("Android version", "${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})"),
                    InfoRow("Kernel", System.getProperty("os.version")?.takeIf { it.isNotBlank() }, "From the Java runtime property os.version"),
                    InfoRow("Kernel architecture", System.getProperty("os.arch")),
                    InfoRow("Fingerprint", Build.FINGERPRINT),
                    InfoRow("Bootloader", Build.BOOTLOADER),
                    InfoRow("Radio firmware", try { Build.getRadioVersion()?.takeIf { it.isNotBlank() } } catch (e: Exception) { null }),
                    InfoRow("OpenGL ES", am.deviceConfigurationInfo.glEsVersion),
                    InfoRow("Uptime (incl. sleep)", formatDuration(elapsed)),
                    InfoRow("Boot time", DateFormat.getDateTimeInstance().format(Date(bootTime)), "Derived: now minus uptime")
                )
            ),
            InfoSection(
                "Key hardware features",
                listOf(
                    InfoRow("Camera", hasFeature(PackageManager.FEATURE_CAMERA_ANY)),
                    InfoRow("Fingerprint", hasFeature(PackageManager.FEATURE_FINGERPRINT)),
                    InfoRow("NFC", hasFeature(PackageManager.FEATURE_NFC)),
                    InfoRow("Bluetooth", hasFeature(PackageManager.FEATURE_BLUETOOTH)),
                    InfoRow("Bluetooth LE", hasFeature(PackageManager.FEATURE_BLUETOOTH_LE)),
                    InfoRow("Wi-Fi", hasFeature(PackageManager.FEATURE_WIFI)),
                    InfoRow("GPS / location", hasFeature(PackageManager.FEATURE_LOCATION_GPS)),
                    InfoRow("Telephony", hasFeature(PackageManager.FEATURE_TELEPHONY)),
                    InfoRow("Vulkan", hasFeature(PackageManager.FEATURE_VULKAN_HARDWARE_LEVEL))
                )
            ),
            InfoSection(
                "All system features (${features.size})",
                features.map { InfoRow(it.removePrefix("android.hardware.").removePrefix("android.software."), "Available") }
            )
        )
    }

    fun other(context: Context): List<InfoSection> {
        val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        val runtime = Runtime.getRuntime()
        return listOf(
            InfoSection(
                "Power state",
                listOf(
                    InfoRow("Screen interactive", if (pm.isInteractive) "Yes" else "No"),
                    InfoRow("Power saver", if (pm.isPowerSaveMode) "On" else "Off"),
                    InfoRow("Device idle (Doze)", if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) (if (pm.isDeviceIdleMode) "Yes" else "No") else null),
                    InfoRow("Ignoring battery optimisation", if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M)
                        (if (pm.isIgnoringBatteryOptimizations(context.packageName)) "Yes" else "No") else null)
                )
            ),
            InfoSection(
                "Runtime",
                listOf(
                    InfoRow("Java VM", System.getProperty("java.vm.name")),
                    InfoRow("VM version", System.getProperty("java.vm.version")),
                    InfoRow("CPU cores visible to app", runtime.availableProcessors().toString())
                )
            ),
            InfoSection(
                "Limits",
                listOf(
                    InfoRow("CPU temperature, per-app battery drain, other apps' data", null, "Not exposed to normal apps by Android")
                )
            )
        )
    }

    private fun formatDuration(ms: Long): String {
        val s = ms / 1000
        val d = s / 86400
        val h = (s % 86400) / 3600
        val m = (s % 3600) / 60
        return if (d > 0) "${d}d ${h}h ${m}m" else "${h}h ${m}m"
    }
}
