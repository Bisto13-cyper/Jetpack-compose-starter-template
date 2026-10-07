package com.superapp.app.features.dashboard

import android.content.Context
import android.hardware.Sensor
import com.superapp.app.features.dashboard.collectors.BatteryCollector
import com.superapp.app.features.dashboard.collectors.CpuCollector
import com.superapp.app.features.dashboard.collectors.DisplayCollector
import com.superapp.app.features.dashboard.collectors.MemoryCollector
import com.superapp.app.features.dashboard.collectors.NetworkCollector
import com.superapp.app.features.dashboard.collectors.SensorCollector
import com.superapp.app.features.dashboard.collectors.StorageCollector
import com.superapp.app.features.dashboard.collectors.SystemCollector
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Loads dashboard data off the main thread. Holds the CPU sampler state between refreshes. */
class DashboardRepository(context: Context) {
    private val app = context.applicationContext
    private val cpu = CpuCollector(app)

    suspend fun load(category: DashboardCategory, locationGranted: Boolean): List<InfoSection> =
        withContext(Dispatchers.Default) {
            when (category) {
                DashboardCategory.OVERVIEW -> overview(locationGranted)
                DashboardCategory.CPU -> cpu.collect()
                DashboardCategory.RAM -> MemoryCollector.collect(app)
                DashboardCategory.BATTERY -> BatteryCollector.collect(app)
                DashboardCategory.STORAGE -> StorageCollector.collect(app)
                DashboardCategory.NETWORK -> NetworkCollector.collect(app, locationGranted)
                DashboardCategory.DEVICE -> SystemCollector.device(app)
                DashboardCategory.DISPLAY -> DisplayCollector.collect(app)
                DashboardCategory.SYSTEM -> SystemCollector.system(app)
                DashboardCategory.OTHER -> SystemCollector.other(app)
                DashboardCategory.SENSORS -> emptyList()
            }
        }

    /** Sensor list is static metadata, so it is loaded once per visit. */
    suspend fun sensors(): List<Pair<InfoSection, Sensor>> =
        withContext(Dispatchers.Default) { SensorCollector.collect(app) }

    /** Refresh period in ms, or 0 when the data does not change while the screen is open. */
    fun refreshIntervalMs(category: DashboardCategory): Long = when (category) {
        DashboardCategory.OVERVIEW, DashboardCategory.CPU, DashboardCategory.RAM -> 2_000L
        DashboardCategory.BATTERY -> 3_000L
        DashboardCategory.NETWORK -> 5_000L
        DashboardCategory.STORAGE -> 15_000L
        DashboardCategory.SYSTEM -> 30_000L
        else -> 0L
    }

    private fun overview(locationGranted: Boolean): List<InfoSection> = listOfNotNull(
        cpu.collect().getOrNull(1)?.copy(title = "CPU"),
        MemoryCollector.collect(app).firstOrNull(),
        BatteryCollector.collect(app).firstOrNull(),
        StorageCollector.collect(app).firstOrNull(),
        NetworkCollector.collect(app, locationGranted).firstOrNull(),
        SystemCollector.device(app).firstOrNull()
    )
}
