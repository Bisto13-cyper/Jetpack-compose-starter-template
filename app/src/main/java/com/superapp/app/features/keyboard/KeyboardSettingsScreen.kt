package com.superapp.app.features.keyboard

import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.io.File
import java.io.FileOutputStream

@Composable
fun KeyboardSettingsScreen(onClose: () -> Unit) {
    val context = LocalContext.current
    val settings = KeyboardSettingsStore.settings

    val pickImage = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            val path = copyToInternal(context, uri)
            if (path != null) {
                KeyboardSettingsStore.update { it.copy(backgroundImagePath = path) }
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Keyboard settings", fontSize = 20.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.weight(1f))
            TextButton(onClick = onClose) { Text("Done") }
        }

        // ---- Appearance ---------------------------------------------------
        SectionTitle("Appearance")

        ColorRow(
            label = "Background color",
            value = settings.backgroundColor
        ) { newValue ->
            KeyboardSettingsStore.update { it.copy(backgroundColor = newValue) }
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Background image", modifier = Modifier.weight(1f))
            TextButton(onClick = { pickImage.launch(arrayOf("image/*")) }) {
                Text(if (settings.backgroundImagePath == null) "Choose" else "Change")
            }
            if (settings.backgroundImagePath != null) {
                TextButton(onClick = {
                    KeyboardSettingsStore.update { it.copy(backgroundImagePath = null) }
                }) { Text("Remove") }
            }
        }

        ColorRow("Key color", settings.keyColor) {
            KeyboardSettingsStore.update { s -> s.copy(keyColor = it) }
        }
        ColorRow("Key (special) color", settings.keySpecialColor) {
            KeyboardSettingsStore.update { s -> s.copy(keySpecialColor = it) }
        }
        ColorRow("Key text color", settings.keyTextColor) {
            KeyboardSettingsStore.update { s -> s.copy(keyTextColor = it) }
        }
        ColorRow("Toolbar color", settings.toolbarColor) {
            KeyboardSettingsStore.update { s -> s.copy(toolbarColor = it) }
        }

        SectionTitle("Key shape")
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            KeyShape.values().forEach { shape ->
                val selected = shape == settings.keyShape
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(
                            if (selected) Color(0xFF4C8BF5) else Color(0xFF2A2A30)
                        )
                        .clickable {
                            KeyboardSettingsStore.update { it.copy(keyShape = shape) }
                        }
                        .padding(horizontal = 10.dp, vertical = 8.dp)
                ) {
                    Text(shape.name, color = Color.White, fontSize = 12.sp)
                }
            }
        }

        SliderRow(
            label = "Corner radius: ${settings.keyCornerRadius.toInt()}dp",
            value = settings.keyCornerRadius,
            range = 0f..32f
        ) {
            KeyboardSettingsStore.update { s -> s.copy(keyCornerRadius = it) }
        }
        SliderRow(
            label = "Key spacing: ${settings.keySpacing.toInt()}dp",
            value = settings.keySpacing,
            range = 0f..16f
        ) {
            KeyboardSettingsStore.update { s -> s.copy(keySpacing = it) }
        }
        SliderRow(
            label = "Key border: ${settings.keyBorderWidth.toInt()}dp",
            value = settings.keyBorderWidth,
            range = 0f..6f
        ) {
            KeyboardSettingsStore.update { s -> s.copy(keyBorderWidth = it) }
        }
        SliderRow(
            label = "Key height: ${settings.keyHeightDp}dp",
            value = settings.keyHeightDp.toFloat(),
            range = 32f..72f
        ) {
            KeyboardSettingsStore.update { s -> s.copy(keyHeightDp = it.toInt()) }
        }

        ColorRow("Border color", settings.keyBorderColor) {
            KeyboardSettingsStore.update { s -> s.copy(keyBorderColor = it) }
        }

        // ---- Toolbar ------------------------------------------------------
        SectionTitle("Toolbar")
        ToggleRow("Show toolbar above keyboard", settings.showToolbar) {
            KeyboardSettingsStore.update { s -> s.copy(showToolbar = it) }
        }
        ToggleRow("Haptic feedback", settings.hapticFeedback) {
            KeyboardSettingsStore.update { s -> s.copy(hapticFeedback = it) }
        }

        // ---- Languages ----------------------------------------------------
        SectionTitle("Languages")
        KeyboardLanguages.all.forEach { lang ->
            val enabled = settings.enabledLanguages.contains(lang.code)
            ToggleRow("${lang.displayName} (${lang.layoutKind.name})", enabled) { on ->
                KeyboardSettingsStore.update { s ->
                    val next = s.enabledLanguages.toMutableSet()
                    if (on) next.add(lang.code) else next.remove(lang.code)
                    val current = if (next.contains(s.currentLanguageCode)) {
                        s.currentLanguageCode
                    } else {
                        next.firstOrNull() ?: "en"
                    }
                    s.copy(enabledLanguages = next, currentLanguageCode = current)
                }
            }
        }
        Text(
            "Additional languages can be added in KeyboardLanguages.all without " +
                "changing the keyboard architecture.",
            fontSize = 11.sp
        )

        // ---- Preview ------------------------------------------------------
        SectionTitle("Preview")
        KeyboardPreview(settings)

        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(text, fontWeight = FontWeight.Bold, fontSize = 15.sp)
}

@Composable
private fun ToggleRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
private fun ColorRow(label: String, value: Long, onChange: (Long) -> Unit) {
    var hex by remember(value) {
        mutableStateOf("#%08X".format(value))
    }
    Column {
        Text(label, fontSize = 13.sp)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .clip(CircleShape)
                    .background(Color(value))
            )
            Spacer(Modifier.width(8.dp))
            OutlinedTextField(
                value = hex,
                onValueChange = { input ->
                    hex = input
                    val cleaned = input.removePrefix("#")
                    val parsed = cleaned.toLongOrNull(16)
                    if (parsed != null) {
                        val argb = if (cleaned.length <= 6) 0xFF000000L or parsed else parsed
                        onChange(argb)
                    }
                },
                singleLine = true,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun SliderRow(
    label: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    onChange: (Float) -> Unit
) {
    Column {
        Text(label, fontSize = 13.sp)
        androidx.compose.material3.Slider(
            value = value,
            onValueChange = onChange,
            valueRange = range
        )
    }
}

@Composable
private fun KeyboardPreview(settings: KeyboardSettings) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(Color(settings.backgroundColor))
            .padding(8.dp),
        verticalArrangement = Arrangement.spacedBy(settings.keySpacing.dp)
    ) {
        val shape = when (settings.keyShape) {
            KeyShape.SQUARE -> RoundedCornerShape(0.dp)
            KeyShape.ROUNDED_SQUARE -> RoundedCornerShape(settings.keyCornerRadius.dp)
            KeyShape.CIRCLE -> CircleShape
            KeyShape.PILL -> RoundedCornerShape(percent = 50)
        }
        val sample = listOf("Q", "W", "E", "R", "T", "Y", "U", "I", "O", "P")
        Row(horizontalArrangement = Arrangement.spacedBy(settings.keySpacing.dp)) {
            sample.forEach { c ->
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(settings.keyHeightDp.dp)
                        .clip(shape)
                        .background(Color(settings.keyColor)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(c, color = Color(settings.keyTextColor), fontSize = 14.sp)
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(settings.keySpacing.dp)) {
            Box(
                modifier = Modifier
                    .weight(6f)
                    .height(settings.keyHeightDp.dp)
                    .clip(shape)
                    .background(Color(settings.keySpecialColor)),
                contentAlignment = Alignment.Center
            ) {
                Text("space", color = Color(settings.keyTextColor), fontSize = 12.sp)
            }
            Box(
                modifier = Modifier
                    .weight(1.5f)
                    .height(settings.keyHeightDp.dp)
                    .clip(shape)
                    .background(Color(settings.keySpecialColor)),
                contentAlignment = Alignment.Center
            ) {
                Text("⏎", color = Color(settings.keyTextColor), fontSize = 14.sp)
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Background image copy helper
// ---------------------------------------------------------------------------

private fun copyToInternal(context: Context, uri: Uri): String? {
    return runCatching {
        val dir = File(context.filesDir, "keyboard_bg").apply { mkdirs() }
        val target = File(dir, "bg_${System.currentTimeMillis()}.img")
        context.contentResolver.openInputStream(uri)?.use { input ->
            FileOutputStream(target).use { output -> input.copyTo(output) }
        }
        target.absolutePath
    }.getOrNull()
}
