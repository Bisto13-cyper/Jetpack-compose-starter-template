package com.superapp.app.features.filesviewer.viewers

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import org.json.JSONTokener

private val KeyColor = Color(0xFF7DD3FC)
private val StringColor = Color(0xFF4ADE80)
private val NumberColor = Color(0xFFFBBF24)
private val BooleanColor = Color(0xFFF472B6)
private val NullColor = Color(0xFF9CA3AF)
private val PunctColor = Color(0xFF6B7280)

/** Children drawn at first in one object/array; the rest appear on tap (keeps big files fast). */
private const val PAGE = 200

@Composable
fun JsonViewer(text: String) {
    val parsed = remember(text) { runCatching { parseJson(text) }.getOrNull() }

    if (parsed == null) {
        Text(
            "Invalid JSON",
            color = Color(0xFFEF4444),
            fontFamily = FontFamily.Monospace,
            modifier = Modifier.padding(16.dp)
        )
        return
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(8.dp)
    ) {
        JsonNodeView(parsed, depth = 0)
    }
}

// ---- Internal ----

private sealed class JsonNode {
    class Obj(val entries: List<Pair<String, JsonNode>>) : JsonNode()
    class Arr(val items: List<JsonNode>) : JsonNode()
    class Str(val value: String) : JsonNode()
    class Num(val value: String) : JsonNode()
    class Bool(val value: Boolean) : JsonNode()
    object Null : JsonNode()
}

/**
 * Walks the org.json tree directly. (The old version re-parsed every value from its toString(),
 * which dropped the quotes of strings, so "123" or "true" showed up as a number/boolean.)
 */
private fun toNode(v: Any?): JsonNode = when (v) {
    is JSONObject -> JsonNode.Obj(v.keys().asSequence().map { k -> k to toNode(v.opt(k)) }.toList())
    is JSONArray -> JsonNode.Arr((0 until v.length()).map { toNode(v.opt(it)) })
    is String -> JsonNode.Str(v)
    is Boolean -> JsonNode.Bool(v)
    is Number -> JsonNode.Num(v.toString())
    else -> JsonNode.Null // null or JSONObject.NULL
}

private fun parseJson(text: String): JsonNode {
    val t = text.trim().removePrefix("\uFEFF")
    return when {
        t.startsWith("{") -> toNode(JSONObject(t))
        t.startsWith("[") -> toNode(JSONArray(t))
        else -> {
            val v = JSONTokener(t).nextValue()
            // org.json is lenient and turns any plain word into a string; only accept a real quoted one.
            if (v is String && !t.startsWith("\"")) throw JSONException("Not JSON")
            toNode(v)
        }
    }
}

@Composable
private fun JsonNodeView(node: JsonNode, depth: Int, key: String? = null) {
    when (node) {
        is JsonNode.Obj -> {
            var shown by remember { mutableStateOf(PAGE) }
            CollapsibleSection(label = key, open = "{ ${node.entries.size} }", depth = depth) {
                node.entries.take(shown).forEach { (k, v) -> JsonNodeView(v, depth + 1, k) }
                MoreRow(node.entries.size - shown, depth) { shown += PAGE * 2 }
            }
        }
        is JsonNode.Arr -> {
            var shown by remember { mutableStateOf(PAGE) }
            CollapsibleSection(label = key, open = "[ ${node.items.size} ]", depth = depth) {
                node.items.take(shown).forEachIndexed { i, v -> JsonNodeView(v, depth + 1, "$i") }
                MoreRow(node.items.size - shown, depth) { shown += PAGE * 2 }
            }
        }
        else -> LeafRow(key = key, node = node, depth = depth)
    }
}

@Composable
private fun MoreRow(remaining: Int, depth: Int, onMore: () -> Unit) {
    if (remaining <= 0) return
    Text(
        "… $remaining more (tap to show)",
        color = MaterialTheme.colorScheme.primary,
        fontFamily = FontFamily.Monospace,
        fontSize = 13.sp,
        modifier = Modifier
            .clickable(onClick = onMore)
            .padding(start = (depth * 12 + 32).dp, top = 4.dp, bottom = 4.dp)
    )
}

@Composable
private fun CollapsibleSection(
    label: String?,
    open: String,
    depth: Int,
    content: @Composable () -> Unit
) {
    var expanded by remember { mutableStateOf(depth < 3) }
    Column(modifier = Modifier.padding(start = (depth * 12).dp)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .clickable { expanded = !expanded }
                .padding(vertical = 2.dp)
        ) {
            Text(
                if (expanded) "▼" else "▶",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 10.sp,
                modifier = Modifier.width(16.dp)
            )
            Spacer(Modifier.width(4.dp))
            if (label != null) {
                HighlightKey(label)
                Text(": ", color = PunctColor, fontFamily = FontFamily.Monospace, fontSize = 13.sp)
            }
            Text(open, color = PunctColor, fontFamily = FontFamily.Monospace, fontSize = 13.sp)
        }
        if (expanded) content()
    }
}

@Composable
private fun LeafRow(key: String?, node: JsonNode, depth: Int) {
    // Tap a leaf to highlight it
    val highlightBg = MaterialTheme.colorScheme.primary.copy(alpha = 0.18f)
    var pressed by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier
            .clickable { pressed = !pressed }
            .background(if (pressed) highlightBg else Color.Transparent)
            .padding(start = (depth * 12 + 20).dp, top = 2.dp, bottom = 2.dp)
    ) {
        if (key != null) {
            HighlightKey(key)
            Text(": ", color = PunctColor, fontFamily = FontFamily.Monospace, fontSize = 13.sp)
        }
        val (shownText, color) = when (node) {
            is JsonNode.Str -> "\"${node.value}\"" to StringColor
            is JsonNode.Num -> node.value to NumberColor
            is JsonNode.Bool -> node.value.toString() to BooleanColor
            else -> "null" to NullColor
        }
        Text(shownText, color = color, fontFamily = FontFamily.Monospace, fontSize = 13.sp)
    }
}

@Composable
private fun HighlightKey(key: String) {
    Text(
        "\"$key\"",
        color = KeyColor,
        fontFamily = FontFamily.Monospace,
        fontSize = 13.sp
    )
}
