package com.superapp.app.features.dashboard.collectors

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.PowerManager
import com.superapp.app.features.dashboard.InfoRow
import com.superapp.app.features.dashboard.InfoSection

/** Battery information from the sticky battery broadcast and BatteryManager properties. */
object BatteryCollector {

    private const val EXTRA_CYCLE_COUNT = "android.os.extra.CYCLE_COUNT" // Android 14+
    private const val PLUGGED_DOCK = 8 // BatteryManager.BATTERY_PLUGGED_DOCK, Android 13+

    fun collect(context: Context): List<InfoSection> {
        val intent = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val bm = context.getSystemService(Context.BATTERY_SERVICE) as BatteryManager
        val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager

        val level = intent?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = intent?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
        val pct = if (level >= 0 && scale > 0) level * 100f / scale else null

        val status = intent?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
        val plugged = intent?.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) ?: 0
        val health = intent?.getIntExtra(BatteryManager.EXTRA_HEALTH, -1) ?: -1
        val tempTenths = intent?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, Int.MIN_VALUE) ?: Int.MIN_VALUE
        val voltage = intent?.getIntExtra(BatteryManager.EXTRA_VOLTAGE, 0) ?: 0
        val technology = intent?.getStringExtra(BatteryManager.EXTRA_TECHNOLOGY)?.takeIf { it.isNotBlank() }
        val present = intent?.getBooleanExtra(BatteryManager.EXTRA_PRESENT, true) ?: true

        val currentNow = prop(bm, BatteryManager.BATTERY_PROPERTY_CURRENT_NOW)
        val currentAvg = prop(bm, BatteryManager.BATTERY_PROPERTY_CURRENT_AVERAGE)
        val chargeCounter = prop(bm, BatteryManager.BATTERY_PROPERTY_CHARGE_COUNTER)
        val energy = prop(bm, BatteryManager.BATTERY_PROPERTY_ENERGY_COUNTER)

        val main = InfoSection(
            title = "Battery",
            progress = pct?.let { it / 100f },
            rows = listOf(
                InfoRow("Level", pct?.let { "%.0f%%".format(it) }),
                InfoRow("Status", statusName(status)),
                InfoRow("Power source", if (plugged == 0) "Not plugged in" else pluggedName(plugged)),
                InfoRow("Health", healthName(health)),
                InfoRow("Temperature", tempTenths.takeIf { it != Int.MIN_VALUE }?.let { "%.1f °C".format(it / 10.0) }),
                InfoRow("Voltage", voltage.takeIf { it > 0 }?.let { "$it mV" }),
                InfoRow("Technology", technology),
                InfoRow("Battery present", if (present) "Yes" else "No"),
                InfoRow("Cycle count", intent?.takeIf { it.hasExtra(EXTRA_CYCLE_COUNT) }?.getIntExtra(EXTRA_CYCLE_COUNT, -1)?.takeIf { it >= 0 }?.toString(), "Android 14+ and supporting hardware only"),
                InfoRow("Power saver", if (pm.isPowerSaveMode) "On" else "Off")
            )
        )

        val electrical = InfoSection(
            title = "Current and capacity",
            rows = listOf(
                InfoRow("Current now", currentNow?.let { "$it µA" }, "As reported by the device; sign and unit scaling vary by manufacturer"),
                InfoRow("Current average", currentAvg?.let { "$it µA" }),
                InfoRow("Charge counter", chargeCounter?.let { "%.0f mAh".format(it / 1000.0) }, "Remaining charge as reported by the device"),
                InfoRow(
                    "Estimated full charge",
                    if (chargeCounter != null && chargeCounter > 0 && pct != null && pct >= 5f)
                        "%.0f mAh".format(chargeCounter / 1000.0 / (pct / 100.0)) else null,
                    "Estimate = charge counter / level. Not the rated capacity; Android has no public API for that."
                ),
                InfoRow("Energy counter", energy?.let { "$it nWh" })
            ),
            footnote = "Rows show Unavailable when the device does not report the value."
        )
        return listOf(main, electrical)
    }

    private fun prop(bm: BatteryManager, id: Int): Long? {
        val v = bm.getLongProperty(id)
        return if (v == Long.MIN_VALUE || v == Int.MIN_VALUE.toLong() || v == 0L) null else v
    }

    private fun statusName(s: Int) = when (s) {
        BatteryManager.BATTERY_STATUS_CHARGING -> "Charging"
        BatteryManager.BATTERY_STATUS_DISCHARGING -> "Discharging"
        BatteryManager.BATTERY_STATUS_FULL -> "Full"
        BatteryManager.BATTERY_STATUS_NOT_CHARGING -> "Not charging"
        BatteryManager.BATTERY_STATUS_UNKNOWN -> "Unknown"
        else -> null
    }

    private fun pluggedName(p: Int): String {
        val parts = mutableListOf<String>()
        if (p and BatteryManager.BATTERY_PLUGGED_AC != 0) parts += "AC"
        if (p and BatteryManager.BATTERY_PLUGGED_USB != 0) parts += "USB"
        if (p and BatteryManager.BATTERY_PLUGGED_WIRELESS != 0) parts += "Wireless"
        if (p and PLUGGED_DOCK != 0) parts += "Dock"
        return parts.joinToString(", ").ifEmpty { "Unknown" }
    }

    private fun healthName(h: Int) = when (h) {
        BatteryManager.BATTERY_HEALTH_GOOD -> "Good"
        BatteryManager.BATTERY_HEALTH_OVERHEAT -> "Overheat"
        BatteryManager.BATTERY_HEALTH_DEAD -> "Dead"
        BatteryManager.BATTERY_HEALTH_OVER_VOLTAGE -> "Over voltage"
        BatteryManager.BATTERY_HEALTH_UNSPECIFIED_FAILURE -> "Unspecified failure"
        BatteryManager.BATTERY_HEALTH_COLD -> "Cold"
        BatteryManager.BATTERY_HEALTH_UNKNOWN -> null
        else -> null
    }
}
