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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val CmdColor = Color(0xFF569CD6)
private val FlagColor = Color(0xFFEF4444)
private val CommentColor = Color(0xFF6A9955)
private val StrColor = Color(0xFFCE9178)
private val ShebangColor = Color(0xFFC586C0)
private val KeywordColor = Color(0xFFC586C0)
private val DefaultColor = Color(0xFFD4D4D4)

private val KEYWORDS = setOf(
    "if", "then", "else", "elif", "fi", "for", "while", "do", "done",
    "case", "esac", "function", "return", "export", "local", "readonly",
    "source", "alias", "unalias", "set", "unset", "shift", "trap"
)

@Composable
fun ShViewer(text: String) {
    val annotated = buildAnnotatedString {
        text.lines().forEachIndexed { i, line ->
            highlightShellLine(line)
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

private fun androidx.compose.ui.text.AnnotatedString.Builder.highlightShellLine(line: String) {
    val trimmed = line.trimStart()
    when {
        trimmed.startsWith("#!") -> {
            withStyle(SpanStyle(color = ShebangColor)) { append(line) }
            return
        }
        trimmed.startsWith("#") -> {
            withStyle(SpanStyle(color = CommentColor)) { append(line) }
            return
        }
    }

    var i = 0
    while (i < line.length) {
        val c = line[i]
        when {
            c == '#' -> {
                // inline comment
                withStyle(SpanStyle(color = CommentColor)) { append(line.substring(i)) }
                return
            }
            c == '"' || c == '\'' -> {
                val quote = c
                var j = i + 1
                while (j < line.length && line[j] != quote) {
                    if (line[j] == '\\' && quote == '"') j++
                    j++
                }
                val end = if (j < line.length) j + 1 else line.length
                withStyle(SpanStyle(color = StrColor)) { append(line.substring(i, end)) }
                i = end
            }
            c == '-' && i + 1 < line.length && (line[i + 1] == '-' || line[i + 1].isLetter()) -> {
                // flag: -a, --all, -abc
                val start = i
                var j = i + 1
                if (j < line.length && line[j] == '-') j++
                while (j < line.length && (line[j].isLetterOrDigit() || line[j] == '-')) j++
                withStyle(SpanStyle(color = FlagColor)) { append(line.substring(start, j)) }
                i = j
            }
            c.isLetter() || c == '_' || c == '/' || c == '.' -> {
                val start = i
                var j = i
                while (j < line.length && !line[j].isWhitespace() && line[j] !in "-#'\"") j++
                val word = line.substring(start, j)
                val lower = word.lowercase()
                when {
                    lower in KEYWORDS -> withStyle(SpanStyle(color = KeywordColor, fontWeight = FontWeight.Bold)) { append(word) }
                    start == 0 || line.substring(0, start).isBlank() -> withStyle(SpanStyle(color = CmdColor)) { append(word) }
                    else -> withStyle(SpanStyle(color = DefaultColor)) { append(word) }
                }
                i = j
            }
            else -> {
                append(c)
                i++
            }
        }
    }
}
