package com.superapp.app.features.dashboard.collectors

import android.app.ActivityManager
import android.content.Context
import com.superapp.app.features.dashboard.InfoRow
import com.superapp.app.features.dashboard.InfoSection
import com.superapp.app.features.dashboard.fileSize
import com.superapp.app.features.dashboard.percent

object MemoryCollector {

    fun collect(context: Context): List<InfoSection> {
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val mi = ActivityManager.MemoryInfo().also { am.getMemoryInfo(it) }
        val total = mi.totalMem
        val avail = mi.availMem
        val used = (total - avail).coerceAtLeast(0L)
        val fraction = if (total > 0) used.toDouble() / total else 0.0

        val system = InfoSection(
            title = "Memory",
            progress = if (total > 0) fraction.toFloat() else null,
            rows = listOf(
                InfoRow("Used", fileSize(context, used)),
                InfoRow("Available", fileSize(context, avail)),
                InfoRow("Total", fileSize(context, total)),
                InfoRow("Usage", percent(fraction)),
                InfoRow("Low-memory state", if (mi.lowMemory) "Low" else "Normal"),
                InfoRow("Low-memory threshold", fileSize(context, mi.threshold)),
                InfoRow("Low-RAM device", if (am.isLowRamDevice) "Yes" else "No")
            ),
            footnote = "Total is the RAM visible to Android, which can be slightly below the advertised size."
        )

        val rt = Runtime.getRuntime()
        val appHeap = InfoSection(
            title = "This app",
            rows = listOf(
                InfoRow("Heap in use", fileSize(context, rt.totalMemory() - rt.freeMemory())),
                InfoRow("Heap limit", fileSize(context, rt.maxMemory())),
                InfoRow("Memory class", "${am.memoryClass} MB"),
                InfoRow("Large memory class", "${am.largeMemoryClass} MB")
            )
        )
        return listOf(system, appHeap)
    }
}
