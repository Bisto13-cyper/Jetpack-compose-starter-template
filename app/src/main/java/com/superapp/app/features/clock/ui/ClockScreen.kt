package com.superapp.app.features.clock.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.text.format.DateFormat
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.superapp.app.core.ui.rememberIsVisible
import com.superapp.app.features.clock.AlarmScheduler
import com.superapp.app.features.clock.ClockText
import java.util.Date
import java.util.TimeZone
import kotlinx.coroutines.delay

private val TAB_TITLES = listOf("Clock", "Alarms", "Timers", "Saved")

/**
 * Entry point of the Clock feature. [initialTab] may be 0..3 (Clock, Alarms, Timers, Saved); the
 * home-screen widget / alarm notification pass "timers" / "alarms" via AlarmScheduler.EXTRA_OPEN_CLOCK.
 */
@Composable
fun ClockScreen(initialTab: Int = 0, onBack: (() -> Unit)? = null) {
    val context = LocalContext.current
    val state = remember { ClockState(context) }
    var tab by rememberSaveable { mutableStateOf(initialTab.coerceIn(0, TAB_TITLES.lastIndex)) }
    var nowMs by remember { mutableStateOf(System.currentTimeMillis()) }
    val visible by rememberIsVisible()

    // Ticks only while the screen is visible; stops automatically otherwise.
    LaunchedEffect(visible) {
        if (!visible) return@LaunchedEffect
        state.reload()
        while (true) {
            nowMs = System.currentTimeMillis()
            if (state.timers.isNotEmpty()) state.reloadTimers()
            delay(500)
        }
    }

    var notificationsGranted by remember {
        mutableStateOf(
            Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
                PackageManager.PERMISSION_GRANTED
        )
    }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        notificationsGranted = it
    }

    Column(Modifier.fillMaxSize()) {
        if (onBack != null) TextButton(onClick = onBack) { Text("< Back") }
        if (!notificationsGranted) {
            Card(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
                Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("Allow notifications so alarms and timers can alert you.", Modifier.weight(1f))
                    TextButton(onClick = { permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS) }) { Text("Allow") }
                }
            }
        }
        TabRow(selectedTabIndex = tab) {
            TAB_TITLES.forEachIndexed { i, title ->
                Tab(selected = tab == i, onClick = { tab = i }, text = { Text(title) })
            }
        }
        when (tab) {
            0 -> ClockTab(state, nowMs)
            1 -> AlarmsTab(state)
            2 -> TimersTab(state, nowMs)
            else -> SavedTimersTab(state) { tab = 2 }
        }
    }
}

@Composable
private fun ClockTab(state: ClockState, nowMs: Long) {
    val context = LocalContext.current
    val date = Date(nowMs)
    val timePattern = if (DateFormat.is24HourFormat(context)) "HH:mm:ss" else "hh:mm:ss a"
    val next = AlarmScheduler.nextAlarm(state.groups, nowMs)
    Column(
        Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(DateFormat.format(timePattern, date).toString(), fontSize = 52.sp)
        Text(DateFormat.getLongDateFormat(context).format(date), style = MaterialTheme.typography.titleMedium)
        Text(TimeZone.getDefault().id, style = MaterialTheme.typography.bodySmall)
        if (next != null) {
            val mins = ((next.second - nowMs) / 60_000L).coerceAtLeast(0)
            Text(
                "Next alarm: ${next.first.name}, in ${mins / 60} h ${mins % 60} min",
                style = MaterialTheme.typography.bodyMedium
            )
        } else {
            Text("No upcoming alarms", style = MaterialTheme.typography.bodyMedium)
        }
        val running = state.timers.count { it.status == com.superapp.app.features.clock.TimerStatus.RUNNING }
        if (running > 0) {
            Text("$running timer(s) running", style = MaterialTheme.typography.bodyMedium)
            state.timers.filter { it.status == com.superapp.app.features.clock.TimerStatus.RUNNING }.take(3).forEach {
                Text("${it.name}  ${ClockText.duration(it.remaining(nowMs))}")
            }
        }
    }
}
