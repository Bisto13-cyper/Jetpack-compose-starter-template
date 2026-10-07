package com.superapp.app.features.api

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.json.JSONArray
import org.json.JSONObject

private const val MAX_DISPLAY_CHARS = 100_000

fun copyToClipboard(context: Context, text: String) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    clipboard.setPrimaryClip(ClipData.newPlainText("api", text))
    Toast.makeText(context, "Copied", Toast.LENGTH_SHORT).show()
}

/**
 * Shows a response/transform output.
 * JSON objects/arrays: Tree (expand/collapse, copy values), Pretty, Raw.
 * Anything else (text, HTML, empty): shown as raw text.
 */
@Composable
fun BodyViewer(text: String, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val parsed = remember(text) { parseJsonOrNull(text) }
    var mode by rememberSaveable { mutableStateOf(0) } // 0 tree, 1 pretty, 2 raw

    Column(modifier.fillMaxWidth()) {
        if (text.isEmpty()) {
            Text("(empty)", fontSize = 12.sp, color = MaterialTheme.colorScheme.outline)
        } else {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (parsed != null) {
                    listOf("Tree", "Pretty", "Raw").forEachIndexed { i, label ->
                        TextButton(onClick = { mode = i }) {
                            Text(label, fontWeight = if (mode == i) FontWeight.Bold else FontWeight.Normal)
                        }
                    }
                } else {
                    Text("Text", fontSize = 12.sp, modifier = Modifier.padding(horizontal = 12.dp))
                }
                Spacer(Modifier.weight(1f))
                TextButton(onClick = {
                    val copy = if (parsed != null && mode != 2) jsonToText(parsed, true) else text
                    copyToClipboard(context, copy)
                }) { Text("Copy") }
            }
            when {
                parsed != null && mode == 0 -> JsonNode(null, parsed, 0)
                parsed != null && mode == 1 -> MonoBlock(jsonToText(parsed, true))
                else -> MonoBlock(text)
            }
        }
    }
}

@Composable
private fun MonoBlock(text: String) {
    val shown = if (text.length > MAX_DISPLAY_CHARS) text.take(MAX_DISPLAY_CHARS) else text
    SelectionContainer {
        Column {
            Text(shown, fontFamily = FontFamily.Monospace, fontSize = 12.sp)
            if (shown.length < text.length) {
                Text(
                    "Display truncated at $MAX_DISPLAY_CHARS characters (Copy still copies everything).",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.outline
                )
            }
        }
    }
}

@Composable
private fun JsonNode(label: String?, value: Any?, depth: Int) {
    when (value) {
        is JSONObject, is JSONArray -> JsonContainer(label, value, depth)
        else -> JsonLeaf(label, value)
    }
}

private fun jsonChildren(value: Any): List<Pair<String, Any?>> = when (value) {
    is JSONObject -> value.keys().asSequence().toList().map { jsonQuote(it) to value.opt(it) }
    is JSONArray -> (0 until value.length()).map { "[$it]" to value.opt(it) }
    else -> emptyList()
}

@Composable
private fun JsonContainer(label: String?, value: Any, depth: Int) {
    val context = LocalContext.current
    val isObject = value is JSONObject
    val children = remember(value) { jsonChildren(value) }
    var expanded by remember { mutableStateOf(depth < 1) }
    var shown by remember { mutableStateOf(100) }
    val open = if (isObject) "{" else "["
    val close = if (isObject) "}" else "]"
    val unit = if (isObject) "keys" else "items"

    Column {
        Row(
            Modifier.fillMaxWidth().clickable { expanded = !expanded }.padding(vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            val arrow = if (expanded) "▼ " else "▶ "
            val name = if (label != null) "$label: " else ""
            val summary = if (expanded) open else "$open … $close"
            Text(
                "$arrow$name$summary  ${children.size} $unit",
                fontFamily = FontFamily.Monospace,
                fontSize = 12.sp,
                modifier = Modifier.weight(1f)
            )
            Text(
                "copy",
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .clickable { copyToClipboard(context, jsonToText(value, true)) }
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            )
        }
        if (expanded) {
            Column(Modifier.padding(start = 14.dp)) {
                children.take(shown).forEach { (k, v) -> JsonNode(k, v, depth + 1) }
                if (children.size > shown) {
                    Text(
                        "… ${children.size - shown} more (tap to show)",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.clickable { shown += 100 }.padding(vertical = 4.dp)
                    )
                }
            }
            Text(close, fontFamily = FontFamily.Monospace, fontSize = 12.sp)
        }
    }
}

@Composable
private fun JsonLeaf(label: String?, value: Any?) {
    val context = LocalContext.current
    val isNull = value == null || value === JSONObject.NULL
    val shownText: String
    val color: Color
    when {
        isNull -> {
            shownText = "null"
            color = Color.Gray
        }
        value is String -> {
            shownText = jsonQuote(if (value.length > 500) value.take(500) + "…" else value)
            color = Color(0xFF2E7D32)
        }
        value is Boolean -> {
            shownText = value.toString()
            color = Color(0xFFEF6C00)
        }
        else -> {
            shownText = jsonToText(value, false)
            color = Color(0xFF1565C0)
        }
    }
    val copyText = if (value is String) value else shownText

    Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(
            buildString {
                if (label != null) append("$label: ")
            },
            fontFamily = FontFamily.Monospace,
            fontSize = 12.sp
        )
        Text(
            shownText,
            fontFamily = FontFamily.Monospace,
            fontSize = 12.sp,
            color = color,
            modifier = Modifier.weight(1f)
        )
        Text(
            "copy",
            fontSize = 11.sp,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier
                .clickable { copyToClipboard(context, copyText) }
                .padding(horizontal = 8.dp, vertical = 4.dp)
        )
    }
}
