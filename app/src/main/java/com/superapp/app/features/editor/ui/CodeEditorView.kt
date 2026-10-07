package com.superapp.app.features.editor.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.superapp.app.features.editor.data.EditorSettings
import com.superapp.app.features.editor.data.EditorSettingsStore
import com.superapp.app.features.editor.model.OpenDocument
import com.superapp.app.features.editor.model.TokenStyle
import com.superapp.app.features.editor.syntax.Highlighter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun CodeEditorView(
    document: OpenDocument,
    onContentChange: (String) -> Unit
) {
    val settings = EditorSettingsStore.settings
    var textValue by remember(document.uri) {
        mutableStateOf(TextFieldValue(document.content))
    }
    var highlighted by remember { mutableStateOf<AnnotatedString?>(null) }

    // Keep local text in sync when document is swapped
    LaunchedEffect(document.uri, document.content) {
        if (textValue.text != document.content) {
            textValue = TextFieldValue(document.content)
        }
    }

    // Debounced highlighting on background
    LaunchedEffect(textValue.text, document.languageId, settings) {
        highlighted = withContext(Dispatchers.Default) {
            buildHighlighted(textValue.text, document.languageId, settings)
        }
    }

    val lineCount = textValue.text.count { it == '\n' } + 1
    val lineNumberWidth = (lineCount.toString().length * 10 + 16).dp

    Row(
        Modifier
            .fillMaxSize()
            .background(Color(settings.backgroundColor))
    ) {
        if (settings.showLineNumbers && !settings.wordWrap) {
            val vScroll = rememberScrollState()
            Box(
                Modifier
                    .width(lineNumberWidth)
                    .verticalScroll(vScroll)
                    .background(Color(settings.backgroundColor))
                    .padding(horizontal = 6.dp, vertical = 8.dp)
            ) {
                Text(
                    text = (1..lineCount).joinToString("\n"),
                    color = Color(settings.lineNumberColor),
                    fontSize = settings.fontSizeSp.sp,
                    fontFamily = FontFamily.Monospace,
                    lineHeight = (settings.fontSizeSp * settings.lineHeightMultiplier).sp
                )
            }
        }

        val hScroll = rememberScrollState()
        val vScroll = rememberScrollState()
        BasicTextField(
            value = textValue,
            onValueChange = { new ->
                textValue = new
                onContentChange(new.text)
            },
            modifier = Modifier
                .fillMaxSize()
                .then(
                    if (settings.wordWrap) Modifier
                    else Modifier.horizontalScroll(hScroll)
                )
                .verticalScroll(vScroll)
                .padding(8.dp),
            textStyle = TextStyle(
                color = Color(settings.textColor),
                fontSize = settings.fontSizeSp.sp,
                fontFamily = FontFamily.Monospace,
                lineHeight = (settings.fontSizeSp * settings.lineHeightMultiplier).sp
            ),
            cursorBrush = SolidColor(Color(settings.cursorColor)),
            decorationBox = { inner ->
                Box {
                    // Draw highlighted version underneath when available
                    highlighted?.let { ann ->
                        Text(
                            text = ann,
                            style = TextStyle(
                                fontSize = settings.fontSizeSp.sp,
                                fontFamily = FontFamily.Monospace,
                                lineHeight = (settings.fontSizeSp * settings.lineHeightMultiplier).sp
                            )
                        )
                    }
                    // Transparent editable field on top
                    Box(Modifier.background(Color.Transparent)) {
                        // Make the actual input text transparent so only highlight shows
                        // (BasicTextField still handles caret/selection)
                        androidx.compose.runtime.CompositionLocalProvider(
                            // no-op; we rely on decorationBox drawing highlight
                        ) {
                            inner()
                        }
                    }
                }
            }
        )
    }
}

private fun buildHighlighted(text: String, languageId: String, settings: EditorSettings): AnnotatedString {
    val tokens = Highlighter.highlight(text, languageId, settings)
    return buildAnnotatedString {
        var cursor = 0
        for (t in tokens.sortedBy { it.start }) {
            if (t.start > cursor) {
                withStyle(SpanStyle(color = Color(settings.textColor))) {
                    append(text.substring(cursor, t.start.coerceAtMost(text.length)))
                }
            }
            val end = t.end.coerceAtMost(text.length)
            if (t.start < end) {
                withStyle(SpanStyle(color = colorFor(t.style, settings))) {
                    append(text.substring(t.start.coerceAtLeast(0), end))
                }
            }
            cursor = end
        }
        if (cursor < text.length) {
            withStyle(SpanStyle(color = Color(settings.textColor))) {
                append(text.substring(cursor))
            }
        }
    }
}

private fun colorFor(style: TokenStyle, s: EditorSettings): Color = when (style) {
    TokenStyle.KEYWORD -> Color(s.keywordColor)
    TokenStyle.TYPE -> Color(s.typeColor)
    TokenStyle.STRING -> Color(s.stringColor)
    TokenStyle.NUMBER -> Color(s.numberColor)
    TokenStyle.COMMENT -> Color(s.commentColor)
    TokenStyle.OPERATOR -> Color(s.operatorColor)
    TokenStyle.FUNCTION -> Color(s.functionColor)
    TokenStyle.TAG -> Color(s.tagColor)
    TokenStyle.ATTRIBUTE -> Color(s.attrColor)
    TokenStyle.PROPERTY -> Color(s.propertyColor)
    TokenStyle.CONSTANT -> Color(s.typeColor)
    TokenStyle.PLAIN -> Color(s.textColor)
}
