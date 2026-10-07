package com.superapp.app.features.clock.ui

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.superapp.app.features.clock.ActiveTimer
import com.superapp.app.features.clock.AlarmGroup
import com.superapp.app.features.clock.AlarmItem
import com.superapp.app.features.clock.AlarmScheduler
import com.superapp.app.features.clock.ClockStore
import com.superapp.app.features.clock.SavedTimer
import com.superapp.app.features.clock.TimerController
import com.superapp.app.features.clock.widget.TimerWidgetProvider

/** UI-side state holder: every mutation is persisted, (re)scheduled, and re-read. */
class ClockState(context: Context) {
    private val app = context.applicationContext
    private val store = ClockStore.get(app)

    var groups by mutableStateOf(store.loadGroups()); private set
    var saved by mutableStateOf(store.loadSaved()); private set
    var timers by mutableStateOf(store.loadActive()); private set

    fun reload() {
        groups = store.loadGroups()
        saved = store.loadSaved()
        timers = store.loadActive()
    }

    fun reloadTimers() {
        val fresh = store.loadActive()
        if (fresh != timers) timers = fresh
    }

    // ---- Alarm groups ----
    fun addGroup(name: String) {
        store.updateGroups { it + AlarmGroup(name = name.ifBlank { "Alarms" }) }
        groups = store.loadGroups()
    }

    fun deleteGroup(id: String) {
        groups.firstOrNull { it.id == id }?.let { AlarmScheduler.cancelGroup(app, it) }
        store.updateGroups { list -> list.filterNot { it.id == id } }
        groups = store.loadGroups()
    }

    fun updateGroup(id: String, transform: (AlarmGroup) -> AlarmGroup) {
        store.updateGroups { list -> list.map { if (it.id == id) transform(it) else it } }
        groups = store.loadGroups()
        groups.firstOrNull { it.id == id }?.let { g -> g.alarms.forEach { AlarmScheduler.schedule(app, g, it) } }
    }

    fun addAlarm(groupId: String, hour: Int, minute: Int) {
        val group = groups.firstOrNull { it.id == groupId } ?: return
        updateGroup(groupId) { it.copy(alarms = it.alarms + AlarmItem(hour = hour, minute = minute, days = group.defaultDays)) }
    }

    fun updateAlarm(groupId: String, alarmId: String, transform: (AlarmItem) -> AlarmItem) =
        updateGroup(groupId) { g -> g.copy(alarms = g.alarms.map { if (it.id == alarmId) transform(it) else it }) }

    fun removeAlarm(groupId: String, alarmId: String) {
        groups.firstOrNull { it.id == groupId }?.alarms?.firstOrNull { it.id == alarmId }
            ?.let { AlarmScheduler.cancel(app, it) }
        updateGroup(groupId) { g -> g.copy(alarms = g.alarms.filterNot { it.id == alarmId }) }
    }

    // ---- Saved timers ----
    fun saveTimer(timer: SavedTimer) {
        store.updateSaved { list ->
            if (list.any { it.id == timer.id }) list.map { if (it.id == timer.id) timer else it } else list + timer
        }
        saved = store.loadSaved()
        TimerWidgetProvider.refreshAll(app)
    }

    fun deleteSaved(id: String) {
        store.updateSaved { list -> list.filterNot { it.id == id } }
        saved = store.loadSaved()
        TimerWidgetProvider.refreshAll(app)
    }

    // ---- Running timers ----
    fun startTimer(name: String, durationMs: Long, soundUri: String?, savedId: String? = null) {
        TimerController.start(app, name, durationMs, soundUri, savedId)
        timers = store.loadActive()
    }

    fun pause(t: ActiveTimer) { TimerController.pause(app, t.id); timers = store.loadActive() }
    fun resume(t: ActiveTimer) { TimerController.resume(app, t.id); timers = store.loadActive() }
    fun reset(t: ActiveTimer) { TimerController.reset(app, t.id); timers = store.loadActive() }
    fun cancel(t: ActiveTimer) { TimerController.cancel(app, t.id); timers = store.loadActive() }
}
