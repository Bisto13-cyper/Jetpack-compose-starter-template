package com.superapp.app.features.filesviewer.viewers

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
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
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val TagName = Color(0xFF569CD6)
private val AttrName = Color(0xFF9CDCFE)
private val AttrValue = Color(0xFFCE9178)
private val Punct = Color(0xFFD4D4D4)

@Composable
fun XmlViewer(text: String) {
    val tree = remember(text) { runCatching { parseXmlLite(text) }.getOrNull() }
    if (tree == null) {
        Text("Invalid XML", color = Color(0xFFEF4444), fontFamily = FontFamily.Monospace, modifier = Modifier.padding(16.dp))
        return
    }
    SelectionContainer {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(8.dp)) {
            XmlElementView(tree, depth = 0)
        }
    }
}

// --- Minimal XML parser (no dependency) ---

private class XmlElement(
    val name: String,
    val attributes: Map<String, String>,
    val children: List<XmlElement>,
    val text: String? = null
)

private class MutableElement(
    val name: String,
    val attributes: Map<String, String>
) {
    var text: String? = null
    val children = mutableListOf<XmlElement>()
    fun build() = XmlElement(name, attributes, children.toList(), text)
}

private fun String.decodeEntities(): String =
    replace("&lt;", "<").replace("&gt;", ">").replace("&quot;", "\"").replace("&apos;", "'").replace("&amp;", "&")

private fun MutableElement.addText(t: String) {
    text = (text?.let { "$it " } ?: "") + t
}

private fun parseXmlLite(input: String): XmlElement {
    val stack = ArrayDeque<MutableElement>()
    var i = 0
    var root: XmlElement? = null
    while (i < input.length) {
        val c = input[i]
        when {
            input.startsWith("<!--", i) -> {
                val end = input.indexOf("-->", i).takeIf { it != -1 } ?: input.length
                i = end + 3
            }
            input.startsWith("<![CDATA[", i) -> {
                val end = input.indexOf("]]>", i).takeIf { it != -1 } ?: input.length
                val raw = input.substring(i + 9, minOf(end, input.length)).trim()
                if (raw.isNotEmpty()) stack.lastOrNull()?.addText(raw)
                i = end + 3
            }
            input.startsWith("<!", i) -> { // <!DOCTYPE ...> and similar declarations
                val end = input.indexOf('>', i).takeIf { it != -1 } ?: input.length
                i = end + 1
            }
            input.startsWith("<?", i) -> {
                val end = input.indexOf("?>", i).takeIf { it != -1 } ?: input.length
                i = end + 2
            }
            input.startsWith("</", i) -> {
                val end = input.indexOf('>', i)
                if (end != -1) {
                    val el = stack.removeLastOrNull()?.build()
                    if (el != null) {
                        if (stack.isEmpty()) root = el else stack.last().children.add(el)
                    }
                    i = end + 1
                } else i = input.length
            }
            c == '<' -> {
                val end = input.indexOf('>', i)
                if (end == -1) { i = input.length; continue }
                val inner = input.substring(i + 1, end).trim()
                val selfClosing = inner.endsWith("/")
                val clean = if (selfClosing) inner.dropLast(1).trim() else inner
                val spaceIdx = clean.indexOfFirst { it.isWhitespace() }
                val name = if (spaceIdx == -1) clean else clean.substring(0, spaceIdx)
                val attrsRaw = if (spaceIdx == -1) "" else clean.substring(spaceIdx)
                val el = MutableElement(name, parseAttrs(attrsRaw))
                if (selfClosing) {
                    if (stack.isEmpty()) root = el.build() else stack.last().children.add(el.build())
                } else {
                    stack.addLast(el)
                }
                i = end + 1
            }
            else -> {
                val nextLt = input.indexOf('<', i).takeIf { it != -1 } ?: input.length
                val txt = input.substring(i, nextLt).trim()
                if (txt.isNotEmpty()) stack.lastOrNull()?.addText(txt.decodeEntities())
                i = nextLt
            }
        }
    }
    // close any leftovers
    while (stack.isNotEmpty()) {
        val el = stack.removeLast().build()
        if (stack.isEmpty()) root = root ?: el else stack.last().children.add(el)
    }
    return root ?: XmlElement("root", emptyMap(), emptyList())
}

private val ATTR_REGEX = Regex("""([\w:.-]+)\s*=\s*"([^"]*)"|([\w:.-]+)\s*=\s*'([^']*)'""")

private fun parseAttrs(s: String): Map<String, String> {
    val map = LinkedHashMap<String, String>()
    ATTR_REGEX.findAll(s).forEach { m ->
        val k = m.groups[1]?.value ?: m.groups[3]?.value ?: return@forEach
        val v = m.groups[2]?.value ?: m.groups[4]?.value ?: ""
        map[k] = v.decodeEntities()
    }
    return map
}

/** One line for the tag: <name a="b">text</name> — all in one Text so long lines wrap instead of being cut. */
private fun tagLine(el: XmlElement, expanded: Boolean): AnnotatedString = buildAnnotatedString {
    withStyle(SpanStyle(color = Punct)) { append("<") }
    withStyle(SpanStyle(color = TagName)) { append(el.name) }
    el.attributes.forEach { (k, v) ->
        append(" ")
        withStyle(SpanStyle(color = AttrName)) { append(k) }
        withStyle(SpanStyle(color = Punct)) { append("=") }
        withStyle(SpanStyle(color = AttrValue)) { append("\"$v\"") }
    }
    when {
        el.children.isEmpty() && el.text == null ->
            withStyle(SpanStyle(color = Punct)) { append(" />") }
        el.children.isEmpty() -> { // text only: keep it on the same line
            withStyle(SpanStyle(color = Punct)) { append(">") }
            append(el.text ?: "")
            withStyle(SpanStyle(color = Punct)) { append("</") }
            withStyle(SpanStyle(color = TagName)) { append(el.name) }
            withStyle(SpanStyle(color = Punct)) { append(">") }
        }
        else -> {
            withStyle(SpanStyle(color = Punct)) { append(">") }
            if (!expanded) {
                withStyle(SpanStyle(color = Punct)) { append(" … </") }
                withStyle(SpanStyle(color = TagName)) { append(el.name) }
                withStyle(SpanStyle(color = Punct)) { append(">") }
            }
        }
    }
}

@Composable
private fun XmlElementView(el: XmlElement, depth: Int) {
    var expanded by remember { mutableStateOf(depth < 4) }
    val hasChildren = el.children.isNotEmpty()
    Column(Modifier.padding(start = (depth * 12).dp, top = 1.dp, bottom = 1.dp)) {
        Row(
            verticalAlignment = Alignment.Top,
            modifier = if (hasChildren) Modifier.clickable { expanded = !expanded } else Modifier
        ) {
            Text(
                if (hasChildren) (if (expanded) "▼" else "▶") else "",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 10.sp,
                modifier = Modifier.width(20.dp).padding(top = 3.dp)
            )
            Text(tagLine(el, expanded), fontFamily = FontFamily.Monospace, fontSize = 13.sp)
        }
        if (expanded && hasChildren) {
            el.text?.let {
                Text(
                    it,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 13.sp,
                    modifier = Modifier.padding(start = 20.dp)
                )
            }
            el.children.forEach { XmlElementView(it, depth + 1) }
            Text(
                buildAnnotatedString {
                    withStyle(SpanStyle(color = Punct)) { append("</") }
                    withStyle(SpanStyle(color = TagName)) { append(el.name) }
                    withStyle(SpanStyle(color = Punct)) { append(">") }
                },
                fontFamily = FontFamily.Monospace,
                fontSize = 13.sp,
                modifier = Modifier.padding(start = 20.dp)
            )
        }
    }
}
