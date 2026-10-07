package com.superapp.app.features.keyboard

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

enum class KeyboardPanel { NONE, CLIPBOARD, EMOJI, TRANSLATE, MORE }

/** Root composable hosted inside the IME ComposeView. */
@Composable
fun KeyboardRoot(service: CustomKeyboardService) {
    val context = LocalContext.current
    val settings = KeyboardSettingsStore.settings

    LaunchedEffect(Unit) {
        KeyboardSettingsStore.init(context)
        KeyboardClipboard.init(context)
        KeyboardClipboard.refreshFromSystem(context)
    }

    var shifted by remember { mutableStateOf(false) }
    var symbols by remember { mutableStateOf(false) }
    var panel by remember { mutableStateOf(KeyboardPanel.NONE) }

    val language = remember(settings.currentLanguageCode) {
        KeyboardLanguages.byCode(settings.currentLanguageCode)
    }
    val rows = remember(language.layoutKind, symbols) {
        KeyboardLayouts.rows(language.layoutKind, symbols)
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(settings.backgroundColor))
    ) {
        settings.backgroundImagePath?.let { path ->
            val bmp = rememberKeyboardBitmap(path)
            if (bmp != null) {
                Image(
                    bitmap = bmp,
                    contentDescription = null,
                    modifier = Modifier.matchParentSize(),
                    contentScale = ContentScale.Crop,
                    alpha = settings.backgroundImageAlpha
                )
            }
        }

        Column(modifier = Modifier.fillMaxWidth()) {
            if (settings.showToolbar) {
                KeyboardToolbar(
                    service = service,
                    settings = settings,
                    activePanel = panel,
                    onPanel = { panel = it }
                )
            }

            when (panel) {
                KeyboardPanel.NONE -> Unit
                KeyboardPanel.CLIPBOARD -> ClipboardPanel(service, settings)
                KeyboardPanel.EMOJI -> EmojiPanel(service, settings)
                KeyboardPanel.TRANSLATE -> TranslatePanel(service, settings)
                KeyboardPanel.MORE -> MoreToolsPanel(
                    service = service,
                    settings = settings,
                    onPanel = { panel = it }
                )
            }

            KeyGrid(
                settings = settings,
                rows = rows,
                shifted = shifted,
                onKey = { key ->
                    when (val action = key.action) {
                        is KeyAction.Text -> {
                            val out = if (shifted) action.value.uppercase() else action.value
                            service.commitText(out)
                            if (shifted) shifted = false
                        }
                        KeyAction.Shift -> shifted = !shifted
                        KeyAction.Backspace -> service.backspace()
                        KeyAction.Enter -> service.sendEnter()
                        KeyAction.Space -> service.commitText(" ")
                        KeyAction.LanguageSwitch -> {
                            val enabled = settings.enabledLanguages.toList()
                            if (enabled.size > 1) {
                                val idx = enabled.indexOf(settings.currentLanguageCode)
                                val next = enabled[(idx + 1).mod(enabled.size)]
                                KeyboardSettingsStore.update { it.copy(currentLanguageCode = next) }
                            }
                        }
                        KeyAction.Symbols -> symbols = true
                        KeyAction.Letters -> symbols = false
                        is KeyAction.Custom -> service.runCustomKey(action.kind)
                    }
                }
            )
        }
    }
}

@Composable
private fun KeyGrid(
    settings: KeyboardSettings,
    rows: List<List<KeyDef>>,
    shifted: Boolean,
    onKey: (KeyDef) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = settings.keySpacing.dp),
        verticalArrangement = Arrangement.spacedBy(settings.keySpacing.dp)
    ) {
        rows.forEach { row ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = settings.keySpacing.dp),
                horizontalArrangement = Arrangement.spacedBy(settings.keySpacing.dp)
            ) {
                row.forEach { key ->
                    KeyboardKey(
                        key = key,
                        settings = settings,
                        shifted = shifted,
                        modifier = Modifier.weight(key.weight),
                        onClick = { onKey(key) }
                    )
                }
            }
        }
    }
}

@Composable
private fun KeyboardKey(
    key: KeyDef,
    settings: KeyboardSettings,
    shifted: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val haptics = LocalHapticFeedback.current
    val shape = shapeFor(settings.keyShape, settings.keyCornerRadius)
    val bg = if (key.isSpecial) Color(settings.keySpecialColor) else Color(settings.keyColor)
    val label = if (key.action is KeyAction.Text && shifted) key.label.uppercase() else key.label

    Box(
        modifier = modifier
            .height(settings.keyHeightDp.dp)
            .clip(shape)
            .background(bg)
            .then(
                if (settings.keyBorderWidth > 0f) {
                    Modifier.border(
                        settings.keyBorderWidth.dp,
                        Color(settings.keyBorderColor),
                        shape
                    )
                } else Modifier
            )
            .clickable {
                if (settings.hapticFeedback) {
                    haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                }
                onClick()
            },
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            color = Color(settings.keyTextColor),
            fontSize = 17.sp,
            maxLines = 1
        )
    }
}

private fun shapeFor(shape: KeyShape, radius: Float): Shape = when (shape) {
    KeyShape.SQUARE -> RoundedCornerShape(0.dp)
    KeyShape.ROUNDED_SQUARE -> RoundedCornerShape(radius.dp)
    KeyShape.CIRCLE -> CircleShape
    KeyShape.PILL -> RoundedCornerShape(percent = 50)
}

@Composable
private fun rememberKeyboardBitmap(path: String?): ImageBitmap? {
    val bmp by produceState<ImageBitmap?>(initialValue = null, path) {
        value = if (path.isNullOrBlank()) null else withContext(Dispatchers.IO) {
            runCatching { BitmapFactory.decodeFile(path)?.asImageBitmap() }.getOrNull()
        }
    }
    return bmp
}

// ---------------------------------------------------------------------------
// Panels
// ---------------------------------------------------------------------------

@Composable
private fun ClipboardPanel(service: CustomKeyboardService, settings: KeyboardSettings) {
    val context = LocalContext.current
    LaunchedEffect(Unit) { KeyboardClipboard.refreshFromSystem(context) }
    val tint = Color(settings.toolbarTextColor)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(max = 240.dp)
            .background(Color(settings.toolbarColor).copy(alpha = 0.9f))
            .padding(8.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Clipboard", color = tint, fontWeight = FontWeight.Bold)
            Spacer(Modifier.width(8.dp))
            Text(
                "Android exposes only the current clip, not full history.",
                color = tint.copy(alpha = 0.6f),
                fontSize = 11.sp
            )
            Spacer(Modifier.weight(1f))
            TextButton(onClick = { KeyboardClipboard.clearRecent() }) {
                Text("Clear", color = tint)
            }
        }

        Text("Recent (session)", color = tint.copy(alpha = 0.7f), fontSize = 12.sp)
        if (KeyboardClipboard.recent.isEmpty()) {
            Text("No recent clips", color = tint.copy(alpha = 0.5f), fontSize = 12.sp)
        } else {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                items(KeyboardClipboard.recent) { text ->
                    ClipChip(
                        text = text,
                        tint = tint,
                        primaryLabel = "Insert",
                        onPrimary = { service.commitText(text) },
                        secondaryLabel = "Pin",
                        onSecondary = { KeyboardClipboard.pin(text) }
                    )
                }
            }
        }

        Spacer(Modifier.height(8.dp))

        Text("Pinned (saved in app)", color = tint.copy(alpha = 0.7f), fontSize = 12.sp)
        if (KeyboardClipboard.pinned.isEmpty()) {
            Text("Nothing pinned", color = tint.copy(alpha = 0.5f), fontSize = 12.sp)
        } else {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                items(KeyboardClipboard.pinned, key = { it.id }) { item ->
                    ClipChip(
                        text = item.text,
                        tint = tint,
                        primaryLabel = "Insert",
                        onPrimary = { service.commitText(item.text) },
                        secondaryLabel = "Unpin",
                        onSecondary = { KeyboardClipboard.unpin(item.id) }
                    )
                }
            }
        }
    }
}

@Composable
private fun ClipChip(
    text: String,
    tint: Color,
    primaryLabel: String,
    onPrimary: () -> Unit,
    secondaryLabel: String,
    onSecondary: () -> Unit
) {
    Column(
        modifier = Modifier
            .width(180.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(Color.White.copy(alpha = 0.06f))
            .padding(8.dp)
    ) {
        Text(
            text = text.take(80),
            color = tint,
            fontSize = 12.sp,
            maxLines = 2
        )
        Spacer(Modifier.height(4.dp))
        Row {
            TextButton(onClick = onPrimary) { Text(primaryLabel, fontSize = 11.sp, color = tint) }
            TextButton(onClick = onSecondary) { Text(secondaryLabel, fontSize = 11.sp, color = tint) }
        }
    }
}

@Composable
private fun EmojiPanel(service: CustomKeyboardService, settings: KeyboardSettings) {
    val tint = Color(settings.toolbarTextColor)
    var categoryId by remember { mutableStateOf(EmojiRepository.categories.first().id) }
    val currentIndex = EmojiRepository.categories.indexOfFirst { it.id == categoryId }
    val category = EmojiRepository.categories[currentIndex.coerceAtLeast(0)]

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(max = 240.dp)
            .background(Color(settings.toolbarColor).copy(alpha = 0.9f))
    ) {
        ScrollableTabRow(
            selectedTabIndex = currentIndex.coerceAtLeast(0),
            edgePadding = 8.dp
        ) {
            EmojiRepository.categories.forEachIndexed { i, cat ->
                Tab(
                    selected = i == currentIndex,
                    onClick = { categoryId = cat.id },
                    text = { Text(cat.label, fontSize = 12.sp, color = tint) }
                )
            }
        }
        LazyVerticalGrid(
            columns = GridCells.Fixed(8),
            modifier = Modifier
                .fillMaxWidth()
                .height(180.dp)
        ) {
            items(category.emojis) { emoji ->
                Box(
                    modifier = Modifier
                        .height(40.dp)
                        .clickable { service.commitText(emoji) },
                    contentAlignment = Alignment.Center
                ) {
                    Text(emoji, fontSize = 22.sp)
                }
            }
        }
    }
}

@Composable
private fun TranslatePanel(service: CustomKeyboardService, settings: KeyboardSettings) {
    val context = LocalContext.current
    val tint = Color(settings.toolbarTextColor)

    val selected = remember { service.selectedText() }
    val full = remember { service.currentText() }
    val text = selected.ifBlank { full }

    val hasTranslator = remember { TranslationHelper.hasExternalTranslator(context) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(settings.toolbarColor).copy(alpha = 0.9f))
            .padding(12.dp)
    ) {
        Text("Translate", color = tint, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(4.dp))
        Text(
            if (text.isBlank()) "(no text in the editor)" else text.take(160),
            color = tint.copy(alpha = 0.8f),
            fontSize = 13.sp
        )
        Spacer(Modifier.height(8.dp))

        if (hasTranslator) {
            TextButton(onClick = {
                TranslationHelper.translateViaExternalApp(context, text)
            }) {
                Text("Open translator app", color = tint)
            }
        } else {
            Text(
                "No app on this device handles ACTION_PROCESS_TEXT. " +
                    "Install a translation app (e.g. Google Translate) to enable this.",
                color = tint.copy(alpha = 0.7f),
                fontSize = 12.sp
            )
        }
        Text(
            "Android does not expose a silent system-wide translation API to IMEs; " +
                "text is handed off to an installed translation app.",
            color = tint.copy(alpha = 0.5f),
            fontSize = 11.sp
        )
    }
}

@Composable
private fun MoreToolsPanel(
    service: CustomKeyboardService,
    settings: KeyboardSettings,
    onPanel: (KeyboardPanel) -> Unit
) {
    val tint = Color(settings.toolbarTextColor)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(settings.toolbarColor).copy(alpha = 0.9f))
            .padding(8.dp)
    ) {
        Text("More tools", color = tint, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(6.dp))

        LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            items(CustomKeyCatalog.defaults, key = { it.id }) { key ->
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color.White.copy(alpha = 0.06f))
                        .clickable { service.runCustomKey(key.kind) }
                        .padding(horizontal = 12.dp, vertical = 8.dp)
                ) {
                    Text(key.label, color = tint, fontSize = 12.sp)
                }
            }
        }

        Spacer(Modifier.height(6.dp))

        Row {
            TextButton(onClick = { KeyboardSettingsActivity.start(service) }) {
                Text("Keyboard appearance", color = tint)
            }
            TextButton(onClick = { onPanel(KeyboardPanel.NONE) }) {
                Text("Close", color = tint)
            }
        }
    }
}
