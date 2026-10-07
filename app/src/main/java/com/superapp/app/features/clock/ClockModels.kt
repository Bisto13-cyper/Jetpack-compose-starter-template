package com.superapp.app.features.clock

import android.content.Context
import android.media.RingtoneManager
import android.net.Uri
import android.text.format.DateFormat
import java.util.Calendar
import java.util.UUID

/** One alarm inside a group. [days] uses Calendar.SUNDAY..Calendar.SATURDAY (1..7); empty = one-time. */
data class AlarmItem(
    val id: String = UUID.randomUUID().toString(),
    val hour: Int,
    val minute: Int,
    val enabled: Boolean = true,
    val days: Set<Int> = emptySet()
)

/** A named group of alarms sharing sound, volume, vibration, snooze and default schedule. */
data class AlarmGroup(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val enabled: Boolean = true,
    /** null = default alarm sound, [ClockSounds.SILENT] = silent, otherwise a content/file URI string. */
    val soundUri: String? = null,
    val volumePercent: Int = 100,
    val vibrate: Boolean = true,
    val snoozeMinutes: Int = 10,
    /** Schedule applied to newly added alarms of this group (empty = one-time). */
    val defaultDays: Set<Int> = emptySet(),
    val alarms: List<AlarmItem> = emptyList()
)

/** A reusable timer preset. [enabled] controls whether it is offered in the home-screen widget. */
data class SavedTimer(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val durationMs: Long,
    val soundUri: String? = null,
    val enabled: Boolean = true
)

enum class TimerStatus { RUNNING, PAUSED, FINISHED }

/** A timer that has been started (running, paused or finished and not yet dismissed). */
data class ActiveTimer(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val durationMs: Long,
    val soundUri: String?,
    val status: TimerStatus,
    /** Wall-clock end time; meaningful while RUNNING. */
    val endAtWallMs: Long = 0L,
    /** Remaining time; meaningful while PAUSED. */
    val remainingMs: Long = durationMs,
    val savedId: String? = null
) {
    fun remaining(nowWallMs: Long): Long = when (status) {
        TimerStatus.RUNNING -> (endAtWallMs - nowWallMs).coerceAtLeast(0L)
        TimerStatus.PAUSED -> remainingMs
        TimerStatus.FINISHED -> 0L
    }
}

object ClockSounds {
    const val SILENT = "silent"

    fun title(context: Context, soundUri: String?): String {
        if (soundUri == null) return "Default alarm sound"
        if (soundUri == SILENT) return "Silent"
        return try {
            RingtoneManager.getRingtone(context, Uri.parse(soundUri))?.getTitle(context) ?: "Custom sound"
        } catch (e: Exception) {
            "Custom sound"
        }
    }
}

object ClockText {
    private val displayOrder = listOf(
        Calendar.MONDAY, Calendar.TUESDAY, Calendar.WEDNESDAY, Calendar.THURSDAY,
        Calendar.FRIDAY, Calendar.SATURDAY, Calendar.SUNDAY
    )
    private val shortNames = mapOf(
        Calendar.MONDAY to "Mon", Calendar.TUESDAY to "Tue", Calendar.WEDNESDAY to "Wed",
        Calendar.THURSDAY to "Thu", Calendar.FRIDAY to "Fri", Calendar.SATURDAY to "Sat",
        Calendar.SUNDAY to "Sun"
    )

    fun dayOrder(): List<Int> = displayOrder
    fun dayName(day: Int): String = shortNames[day] ?: "?"

    fun days(days: Set<Int>): String = when {
        days.isEmpty() -> "Once"
        days.size == 7 -> "Every day"
        days == setOf(Calendar.MONDAY, Calendar.TUESDAY, Calendar.WEDNESDAY, Calendar.THURSDAY, Calendar.FRIDAY) -> "Weekdays"
        days == setOf(Calendar.SATURDAY, Calendar.SUNDAY) -> "Weekends"
        else -> displayOrder.filter { it in days }.joinToString(" ") { dayName(it) }
    }

    fun time(context: Context, hour: Int, minute: Int): String {
        val c = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, minute)
        }
        return DateFormat.getTimeFormat(context).format(c.time)
    }

    /** mm:ss, or h:mm:ss when an hour or more. Rounds up so 0:00 only shows at the true end. */
    fun duration(ms: Long): String {
        val total = (ms.coerceAtLeast(0L) + 999L) / 1000L
        val h = total / 3600
        val m = (total % 3600) / 60
        val s = total % 60
        return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%02d:%02d".format(m, s)
    }

    fun durationWords(ms: Long): String {
        val total = ms / 1000L
        val h = total / 3600
        val m = (total % 3600) / 60
        val s = total % 60
        val parts = buildList {
            if (h > 0) add("$h h")
            if (m > 0) add("$m min")
            if (s > 0) add("$s s")
        }
        return if (parts.isEmpty()) "0 s" else parts.joinToString(" ")
    }
}
