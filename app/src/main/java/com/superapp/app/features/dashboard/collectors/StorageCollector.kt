package com.superapp.app.features.dashboard.collectors

import android.content.Context
import android.os.Environment
import android.os.StatFs
import com.superapp.app.features.dashboard.InfoRow
import com.superapp.app.features.dashboard.InfoSection
import com.superapp.app.features.dashboard.fileSize
import com.superapp.app.features.dashboard.percent
import java.io.File

/** Storage totals via StatFs. Scoped storage: only volumes' capacity is read, never other apps' files. */
object StorageCollector {

    fun collect(context: Context): List<InfoSection> {
        val sections = mutableListOf<InfoSection>()
        val external = context.getExternalFilesDirs(null).filterNotNull()

        // The shared internal volume users see as "internal storage" is the primary external dir.
        external.firstOrNull()?.let { dir ->
            stat("Internal storage", context, dir)?.let { sections += it }
        }
        stat("App data partition", context, Environment.getDataDirectory())?.let { sections += it }

        external.drop(1).forEachIndexed { i, dir ->
            val removable = try { Environment.isExternalStorageRemovable(dir) } catch (e: Exception) { false }
            val title = if (removable) "Removable storage ${i + 1}" else "Additional volume ${i + 1}"
            stat(title, context, dir)?.let { sections += it }
        }
        if (sections.isEmpty()) {
            sections += InfoSection("Storage", listOf(InfoRow("Storage", null)))
        }
        return sections
    }

    private fun stat(title: String, context: Context, path: File): InfoSection? {
        return try {
            val s = StatFs(path.path)
            val total = s.totalBytes
            val free = s.availableBytes
            val used = (total - free).coerceAtLeast(0L)
            val fraction = if (total > 0) used.toDouble() / total else 0.0
            InfoSection(
                title = title,
                progress = fraction.toFloat(),
                rows = listOf(
                    InfoRow("Total", fileSize(context, total)),
                    InfoRow("Used", fileSize(context, used)),
                    InfoRow("Free", fileSize(context, free)),
                    InfoRow("Usage", percent(fraction))
                )
            )
        } catch (e: Exception) {
            null
        }
    }
}
