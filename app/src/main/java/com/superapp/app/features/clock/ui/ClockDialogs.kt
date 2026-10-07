package com.superapp.app.features.clock.ui

import android.app.Activity
import android.app.TimePickerDialog
import android.content.Context
import android.content.Intent
import android.media.RingtoneManager
import android.net.Uri
import android.text.format.DateFormat
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.text.KeyboardOptions
import com.superapp.app.features.clock.ClockSounds
import com.superapp.app.features.clock.ClockText
import com.superapp.app.features.clock.SavedTimer

/** Opens the system sound picker. [onPicked] gets null for "default", ClockSounds.SILENT, or a URI string. */
@Composable
fun rememberSoundPicker(onPicked: (String?) -> Unit): (String?) -> Unit {
    val callback by rememberUpdatedState(onPicked)
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val data = result.data
        if (result.resultCode == Activity.RESULT_OK && data != null &&
            data.hasExtra(RingtoneManager.EXTRA_RINGTONE_PICKED_URI)
        ) {
            @Suppress("DEPRECATION")
            val uri = data.getParcelableExtra<Uri>(RingtoneManager.EXTRA_RINGTONE_PICKED_URI)
            callback(uri?.toString() ?: ClockSounds.SILENT)
        }
    }
    return { current ->
        val defaultUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
        val existing = when (current) {
            null -> defaultUri
            ClockSounds.SILENT -> null
            else -> Uri.parse(current)
        }
        launcher.launch(
            Intent(RingtoneManager.ACTION_RINGTONE_PICKER).apply {
                putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE, RingtoneManager.TYPE_ALARM)
                putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_DEFAULT, true)
                putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_SILENT, true)
                putExtra(RingtoneManager.EXTRA_RINGTONE_DEFAULT_URI, defaultUri)
                putExtra(RingtoneManager.EXTRA_RINGTONE_EXISTING_URI, existing)
            }
        )
    }
}

fun showTimePicker(context: Context, hour: Int, minute: Int, onPicked: (Int, Int) -> Unit) {
    TimePickerDialog(
        context, { _, h, m -> onPicked(h, m) }, hour, minute, DateFormat.is24HourFormat(context)
    ).show()
}

@Composable
fun TextInputDialog(
    title: String,
    initial: String,
    confirmLabel: String = "OK",
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var text by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { OutlinedTextField(value = text, onValueChange = { text = it }, singleLine = true) },
        confirmButton = { TextButton(onClick = { onConfirm(text.trim()) }) { Text(confirmLabel) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
fun ConfirmDialog(message: String, confirmLabel: String, onDismiss: () -> Unit, onConfirm: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        text = { Text(message) },
        confirmButton = { TextButton(onClick = onConfirm) { Text(confirmLabel) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DaysDialog(
    title: String,
    initial: Set<Int>,
    onDismiss: () -> Unit,
    onConfirm: (Set<Int>) -> Unit
) {
    var days by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("No day selected = one-time alarm.")
                ClockText.dayOrder().chunked(4).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        row.forEach { d ->
                            FilterChip(
                                selected = d in days,
                                onClick = { days = if (d in days) days - d else days + d },
                                label = { Text(ClockText.dayName(d)) }
                            )
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = { onConfirm(days) }) { Text("OK") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

/** Hours / minutes / seconds number entry. */
@Composable
fun DurationFields(totalMs: Long, onChange: (Long) -> Unit) {
    val total = totalMs / 1000L
    val h = total / 3600
    val m = (total % 3600) / 60
    val s = total % 60

    @Composable
    fun field(label: String, value: Long, max: Long, apply: (Long) -> Long) {
        OutlinedTextField(
            value = if (value == 0L) "" else value.toString(),
            onValueChange = { raw ->
                val v = raw.filter { it.isDigit() }.take(3).toLongOrNull() ?: 0L
                onChange(apply(v.coerceAtMost(max)) * 1000L)
            },
            label = { Text(label) },
            placeholder = { Text("0") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.width(88.dp)
        )
    }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        field("h", h, 99) { v -> v * 3600 + m * 60 + s }
        field("min", m, 59) { v -> h * 3600 + v * 60 + s }
        field("sec", s, 59) { v -> h * 3600 + m * 60 + v }
    }
}

@Composable
fun SavedTimerDialog(
    initial: SavedTimer?,
    onDismiss: () -> Unit,
    onConfirm: (SavedTimer) -> Unit
) {
    val context = LocalContext.current
    var name by remember { mutableStateOf(initial?.name ?: "") }
    var durationMs by remember { mutableStateOf(initial?.durationMs ?: 5 * 60_000L) }
    var sound by remember { mutableStateOf(initial?.soundUri) }
    val pick = rememberSoundPicker { sound = it }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial == null) "New saved timer" else "Edit saved timer") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedTextField(
                    value = name, onValueChange = { name = it }, label = { Text("Name") },
                    singleLine = true, modifier = Modifier.fillMaxWidth()
                )
                DurationFields(durationMs) { durationMs = it }
                OutlinedButton(onClick = { pick(sound) }, modifier = Modifier.fillMaxWidth()) {
                    Text("Sound: " + ClockSounds.title(context, sound))
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = durationMs > 0L,
                onClick = {
                    val base = initial ?: SavedTimer(name = "", durationMs = durationMs)
                    onConfirm(base.copy(name = name.ifBlank { "Timer" }, durationMs = durationMs, soundUri = sound))
                }
            ) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}
