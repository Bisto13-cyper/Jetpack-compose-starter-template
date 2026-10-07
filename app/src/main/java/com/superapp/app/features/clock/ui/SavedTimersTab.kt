package com.superapp.app.features.clock.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.superapp.app.features.clock.ClockSounds
import com.superapp.app.features.clock.ClockText
import com.superapp.app.features.clock.SavedTimer

@Composable
fun SavedTimersTab(state: ClockState, onStarted: () -> Unit) {
    val context = LocalContext.current
    var editing by remember { mutableStateOf<SavedTimer?>(null) }
    var creating by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf<SavedTimer?>(null) }

    LazyColumn(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = PaddingValues(16.dp)
    ) {
        item { Button(onClick = { creating = true }, modifier = Modifier.fillMaxWidth()) { Text("New saved timer") } }
        if (state.saved.isEmpty()) item { Text("Save timers like \"Tea\" or \"Workout\" to start them in one tap, also from the home-screen widget.") }
        items(state.saved, key = { it.id }) { timer ->
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(timer.name, style = MaterialTheme.typography.titleMedium)
                            Text(
                                ClockText.durationWords(timer.durationMs) + " - " + ClockSounds.title(context, timer.soundUri),
                                style = MaterialTheme.typography.bodySmall
                            )
                            Text(
                                if (timer.enabled) "Shown in widget" else "Hidden from widget",
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                        Switch(checked = timer.enabled, onCheckedChange = { v -> state.saveTimer(timer.copy(enabled = v)) })
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = {
                            state.startTimer(timer.name, timer.durationMs, timer.soundUri, timer.id)
                            onStarted()
                        }) { Text("Start") }
                        OutlinedButton(onClick = { editing = timer }) { Text("Edit") }
                        OutlinedButton(onClick = { deleting = timer }) { Text("Delete") }
                    }
                }
            }
        }
    }

    if (creating) SavedTimerDialog(null, { creating = false }) { state.saveTimer(it); creating = false }
    editing?.let { t -> SavedTimerDialog(t, { editing = null }) { state.saveTimer(it); editing = null } }
    deleting?.let { t ->
        ConfirmDialog("Delete \"${t.name}\"?", "Delete", { deleting = null }) { state.deleteSaved(t.id); deleting = null }
    }
}
