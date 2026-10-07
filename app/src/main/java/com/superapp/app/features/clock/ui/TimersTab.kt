package com.superapp.app.features.clock.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.superapp.app.features.clock.ActiveTimer
import com.superapp.app.features.clock.ClockSounds
import com.superapp.app.features.clock.ClockText
import com.superapp.app.features.clock.TimerStatus

@Composable
fun TimersTab(state: ClockState, nowMs: Long) {
    val context = LocalContext.current
    var name by remember { mutableStateOf("") }
    var durationMs by remember { mutableStateOf(5 * 60_000L) }
    var sound by remember { mutableStateOf<String?>(null) }
    val pick = rememberSoundPicker { sound = it }

    LazyColumn(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp)
    ) {
        item {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("New timer", style = MaterialTheme.typography.titleMedium)
                    OutlinedTextField(
                        value = name, onValueChange = { name = it }, label = { Text("Name (optional)") },
                        singleLine = true, modifier = Modifier.fillMaxWidth()
                    )
                    DurationFields(durationMs) { durationMs = it }
                    OutlinedButton(onClick = { pick(sound) }, modifier = Modifier.fillMaxWidth()) {
                        Text("Sound: " + ClockSounds.title(context, sound))
                    }
                    Button(
                        enabled = durationMs > 0L,
                        onClick = { state.startTimer(name, durationMs, sound) },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("Start") }
                }
            }
        }
        if (state.timers.isEmpty()) item { Text("No timers running.") }
        items(state.timers, key = { it.id }) { timer -> TimerCard(state, timer, nowMs) }
    }
}

@Composable
private fun TimerCard(state: ClockState, timer: ActiveTimer, nowMs: Long) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(timer.name, style = MaterialTheme.typography.titleMedium)
            Text(
                if (timer.status == TimerStatus.FINISHED) "Finished" else ClockText.duration(timer.remaining(nowMs)),
                style = MaterialTheme.typography.displaySmall
            )
            Text(
                "of ${ClockText.durationWords(timer.durationMs)} - " + timer.status.name.lowercase(),
                style = MaterialTheme.typography.bodySmall
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                when (timer.status) {
                    TimerStatus.RUNNING -> OutlinedButton(onClick = { state.pause(timer) }) { Text("Pause") }
                    TimerStatus.PAUSED -> OutlinedButton(onClick = { state.resume(timer) }) { Text("Resume") }
                    TimerStatus.FINISHED -> Unit
                }
                OutlinedButton(onClick = { state.reset(timer) }) { Text("Reset") }
                OutlinedButton(onClick = { state.cancel(timer) }) {
                    Text(if (timer.status == TimerStatus.FINISHED) "Dismiss" else "Cancel")
                }
            }
        }
    }
}
