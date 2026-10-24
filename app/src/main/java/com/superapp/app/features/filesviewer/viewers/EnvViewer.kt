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

private val KeyBlue = Color(0xFF60A5FA)
private val ValueGreen = Color(0xFF4ADE80)
private val CommentGray = Color(0xFF9CA3AF)

@Composable
fun EnvViewer(text: String) {
    val annotated = buildAnnotatedString {
        text.lines().forEachIndexed { index, line ->
            val trimmed = line.trimStart()
            when {
                trimmed.isEmpty() -> {}
                trimmed.startsWith("#") -> {
                    withStyle(SpanStyle(color = CommentGray)) { append(line) }
                }
                else -> {
                    val eq = line.indexOf('=')
                    if (eq == -1) {
                        append(line)
                    } else {
                        val key = line.substring(0, eq)
                        val value = line.substring(eq + 1)
                        withStyle(SpanStyle(color = KeyBlue)) { append(key) }
                        append("=")
                        withStyle(SpanStyle(color = ValueGreen)) { append(value) }
                    }
                }
            }
            if (index < text.lines().lastIndex) append("\n")
        }
    }
    SelectionContainer {
        Text(
            text = annotated,
            fontFamily = FontFamily.Monospace,
            fontSize = 14.sp,
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(12.dp)
        )
    }
}
