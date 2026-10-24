package com.superapp.app.features.filesviewer.viewers

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val TableColor = Color(0xFFC586C0)   // [section]
private val KeyColor = Color(0xFF9CDCFE)
private val StrColor = Color(0xFFCE9178)
private val NumColor = Color(0xFFB5CEA8)
private val BoolColor = Color(0xFF569CD6)
private val CommentColor = Color(0xFF6A9955)
private val EqualsColor = Color(0xFFD4D4D4)

@Composable
fun TomlViewer(text: String) {
    val annotated = buildAnnotatedString {
        text.lines().forEachIndexed { i, line ->
            val trimmed = line.trimStart()
            when {
                trimmed.startsWith("#") -> {
                    withStyle(SpanStyle(color = CommentColor)) { append(line) }
                }
                trimmed.startsWith("[") && trimmed.contains("]") -> {
                    // table header, e.g. [package] or [[bin]]
                    withStyle(SpanStyle(color = TableColor)) { append(line) }
                }
                else -> {
                    val eq = line.indexOf('=')
                    if (eq == -1) append(line)
                    else {
                        val key = line.substring(0, eq)
                        val rest = line.substring(eq + 1)
                        // key (may be dotted)
                        withStyle(SpanStyle(color = KeyColor)) { append(key) }
                        withStyle(SpanStyle(color = EqualsColor)) { append("=") }
                        appendTomlValue(rest)
                    }
                }
            }
            if (i < text.lines().lastIndex) append("\n")
        }
    }
    SelectionContainer {
        Text(
            annotated,
            fontFamily = FontFamily.Monospace,
            fontSize = 13.sp,
            modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(12.dp)
        )
    }
}

private fun androidx.compose.ui.text.AnnotatedString.Builder.appendTomlValue(raw: String) {
    val value = raw.trim()
    when {
        value.startsWith("#") -> withStyle(SpanStyle(color = CommentColor)) { append(" " + value) }
        value.startsWith("\"\"\"") || value.startsWith("'''") ->
            withStyle(SpanStyle(color = StrColor)) { append(" " + value) } // multi-line string start
        value.startsWith("\"") || value.startsWith("'") -> {
            // split trailing comment
            val quote = value.first()
            val end = value.indexOf(quote, 1)
            if (end != -1) {
                withStyle(SpanStyle(color = StrColor)) { append(" " + value.substring(0, end + 1)) }
                if (end + 1 < value.length) {
                    withStyle(SpanStyle(color = CommentColor)) { append(" " + value.substring(end + 1)) }
                }
            } else {
                withStyle(SpanStyle(color = StrColor)) { append(" " + value) }
            }
        }
        value == "true" || value == "false" -> withStyle(SpanStyle(color = BoolColor)) { append(" " + value) }
        value.toDoubleOrNull() != null || value.toLongOrNull() != null ->
            withStyle(SpanStyle(color = NumColor)) { append(" " + value) }
        else -> append(" " + value)
    }
}
