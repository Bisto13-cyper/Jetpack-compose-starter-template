package com.superapp.app.features.clock.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.superapp.app.features.clock.AlarmGroup
import com.superapp.app.features.clock.AlarmItem
import com.superapp.app.features.clock.ClockSounds
import com.superapp.app.features.clock.ClockText

@Composable
fun AlarmsTab(state: ClockState) {
    var newGroup by remember { mutableStateOf(false) }
    LazyColumn(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp)
    ) {
        item {
            Button(onClick = { newGroup = true }, modifier = Modifier.fillMaxWidth()) { Text("New alarm group") }
        }
        if (state.groups.isEmpty()) {
            item { Text("No groups yet. Create one such as \"Morning\" or \"Work\", then add its alarms.") }
        }
        items(state.groups, key = { it.id }) { group -> GroupCard(state, group) }
    }
    if (newGroup) {
        TextInputDialog("Group name", "", "Create", { newGroup = false }) {
            state.addGroup(it); newGroup = false
        }
    }
}

@Composable
private fun GroupCard(state: ClockState, group: AlarmGroup) {
    val context = LocalContext.current
    var expanded by remember { mutableStateOf(false) }
    var rename by remember { mutableStateOf(false) }
    var delete by remember { mutableStateOf(false) }
    var groupDays by remember { mutableStateOf(false) }
    var editDaysFor by remember { mutableStateOf<AlarmItem?>(null) }
    val pickSound = rememberSoundPicker { uri -> state.updateGroup(group.id) { it.copy(soundUri = uri) } }

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f).clickable { expanded = !expanded }) {
                    Text(group.name, style = MaterialTheme.typography.titleMedium)
                    Text(
                        "${group.alarms.size} alarm(s) - " + ClockSounds.title(context, group.soundUri),
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                Switch(checked = group.enabled, onCheckedChange = { v -> state.updateGroup(group.id) { it.copy(enabled = v) } })
            }

            group.alarms.sortedWith(compareBy({ it.hour }, { it.minute })).forEach { alarm ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(
                        Modifier.weight(1f).clickable {
                            showTimePicker(context, alarm.hour, alarm.minute) { h, m ->
                                state.updateAlarm(group.id, alarm.id) { it.copy(hour = h, minute = m, enabled = true) }
                            }
                        }
                    ) {
                        Text(ClockText.time(context, alarm.hour, alarm.minute), style = MaterialTheme.typography.headlineSmall)
                        Text(
                            ClockText.days(alarm.days),
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.clickable { editDaysFor = alarm }
                        )
                    }
                    Switch(
                        checked = alarm.enabled,
                        onCheckedChange = { v -> state.updateAlarm(group.id, alarm.id) { it.copy(enabled = v) } }
                    )
                    TextButton(onClick = { state.removeAlarm(group.id, alarm.id) }) { Text("Remove") }
                }
            }

            OutlinedButton(
                onClick = {
                    showTimePicker(context, 7, 0) { h, m -> state.addAlarm(group.id, h, m) }
                },
                modifier = Modifier.fillMaxWidth()
            ) { Text("Add alarm") }

            if (expanded) {
                OutlinedButton(onClick = { pickSound(group.soundUri) }, modifier = Modifier.fillMaxWidth()) {
                    Text("Sound: " + ClockSounds.title(context, group.soundUri))
                }
                Text("Volume ${group.volumePercent}%", style = MaterialTheme.typography.bodySmall)
                Slider(
                    value = group.volumePercent.toFloat(),
                    onValueChange = { v -> state.updateGroup(group.id) { it.copy(volumePercent = v.toInt()) } },
                    valueRange = 10f..100f
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Vibrate", Modifier.weight(1f))
                    Switch(checked = group.vibrate, onCheckedChange = { v -> state.updateGroup(group.id) { it.copy(vibrate = v) } })
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        if (group.snoozeMinutes == 0) "Snooze: off" else "Snooze: ${group.snoozeMinutes} min",
                        Modifier.weight(1f)
                    )
                    TextButton(onClick = { state.updateGroup(group.id) { it.copy(snoozeMinutes = (it.snoozeMinutes - 5).coerceAtLeast(0)) } }) { Text("-5") }
                    TextButton(onClick = { state.updateGroup(group.id) { it.copy(snoozeMinutes = (it.snoozeMinutes + 5).coerceAtMost(60)) } }) { Text("+5") }
                }
                OutlinedButton(onClick = { groupDays = true }, modifier = Modifier.fillMaxWidth()) {
                    Text("Default schedule for new alarms: " + ClockText.days(group.defaultDays))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { rename = true }) { Text("Rename") }
                    OutlinedButton(onClick = { delete = true }) { Text("Delete group") }
                }
            } else {
                TextButton(onClick = { expanded = true }) { Text("Group settings") }
            }
        }
    }

    if (rename) TextInputDialog("Rename group", group.name, "Rename", { rename = false }) {
        if (it.isNotBlank()) state.updateGroup(group.id) { g -> g.copy(name = it) }
        rename = false
    }
    if (delete) ConfirmDialog("Delete \"${group.name}\" and its alarms?", "Delete", { delete = false }) {
        state.deleteGroup(group.id); delete = false
    }
    if (groupDays) DaysDialog("Default schedule", group.defaultDays, { groupDays = false }) { days ->
        state.updateGroup(group.id) { it.copy(defaultDays = days) }; groupDays = false
    }
    editDaysFor?.let { alarm ->
        DaysDialog("Repeat on", alarm.days, { editDaysFor = null }) { days ->
            state.updateAlarm(group.id, alarm.id) { it.copy(days = days, enabled = true) }; editDaysFor = null
        }
    }
}
