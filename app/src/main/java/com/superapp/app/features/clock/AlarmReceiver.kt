package com.superapp.app.features.clock

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** Receives alarm, snooze and timer broadcasts from AlarmManager. */
class AlarmReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            AlarmScheduler.ACTION_ALARM -> handleAlarm(context, intent)
            AlarmScheduler.ACTION_SNOOZE_FIRE ->
                RingRequest.fromIntent(intent)?.let { AlarmRingService.start(context, it) }
            TimerController.ACTION_TIMER ->
                intent.getStringExtra(TimerController.EXTRA_TIMER_ID)?.let { TimerController.onTimerFired(context, it) }
        }
    }

    private fun handleAlarm(context: Context, intent: Intent) {
        val groupId = intent.getStringExtra(AlarmScheduler.EXTRA_GROUP_ID) ?: return
        val alarmId = intent.getStringExtra(AlarmScheduler.EXTRA_ALARM_ID) ?: return
        val store = ClockStore.get(context)
        val group = store.loadGroups().firstOrNull { it.id == groupId } ?: return
        val alarm = group.alarms.firstOrNull { it.id == alarmId } ?: return
        if (!group.enabled || !alarm.enabled) return

        AlarmRingService.start(
            context,
            RingRequest(
                kind = RingRequest.KIND_ALARM,
                title = group.name,
                message = ClockText.time(context, alarm.hour, alarm.minute),
                soundUri = group.soundUri,
                volumePercent = group.volumePercent,
                vibrate = group.vibrate,
                snoozeMinutes = group.snoozeMinutes
            )
        )

        if (alarm.days.isEmpty()) {
            // One-time alarm: switch it off after it has rung.
            store.updateGroups { list ->
                list.map { g ->
                    if (g.id != groupId) g
                    else g.copy(alarms = g.alarms.map { if (it.id == alarmId) it.copy(enabled = false) else it })
                }
            }
        } else {
            AlarmScheduler.schedule(context, group, alarm)
        }
    }
}
