package com.superapp.app.features.filesviewer.viewers

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private const val MIN_COL_WIDTH = 80
private const val MAX_COL_WIDTH = 400

@Composable
fun CsvViewer(
    text: String,
    onTextChange: ((String) -> Unit)? = null,
    delimiter: Char = ','
) {
    var rows by remember(text, delimiter) { mutableStateOf(parseCsv(text, delimiter)) }
    val editable = onTextChange != null

    // widths مشتركة بين كل الصفوف
    val columnCount = rows.maxOfOrNull { it.size } ?: 0
    val widths = remember(columnCount) {
        mutableStateListOf<Dp>().apply {
            repeat(columnCount) { add(140.dp) }
        }
    }

    if (rows.isEmpty()) {
        Text("CSV is empty", modifier = Modifier.padding(16.dp))
        return
    }

    fun emitChange() {
        onTextChange?.invoke(rows.joinToString("\n") { it.joinToString(delimiter.toString()) { cell -> escapeCsv(cell, delimiter) } })
    }

    val hScroll = rememberScrollState()
    val tableWidth = widths.fold(0.dp) { a, b -> a + b } + if (editable) 40.dp else 0.dp

    Column(Modifier.fillMaxSize()) {
        Box(Modifier.weight(1f).fillMaxWidth().horizontalScroll(hScroll)) {
        LazyColumn(Modifier.width(tableWidth).fillMaxHeight()) {
            // header
            item {
                Row(Modifier.background(MaterialTheme.colorScheme.surfaceVariant)) {
                    rows.firstOrNull()?.forEachIndexed { idx, header ->
                        HeaderCell(
                            text = header,
                            width = widths.getOrElse(idx) { 140.dp },
                            onResize = { delta ->
                                val cur = widths[idx]
                                val newW = (cur.value + delta).coerceIn(MIN_COL_WIDTH.toFloat(), MAX_COL_WIDTH.toFloat())
                                widths[idx] = newW.dp
                            }
                        )
                    }
                    if (editable) Spacer(Modifier.width(40.dp)) // مساحة زرار الحذف
                }
                Divider()
            }

            // صفوف البيانات
            itemsIndexed(rows.drop(1)) { rowIdx, row ->
                val actualIdx = rowIdx + 1
                Row(Modifier.fillMaxWidth()) {
                    row.forEachIndexed { colIdx, cell ->
                        var value by remember(row, cell) { mutableStateOf(cell) }
                        Box(
                            modifier = Modifier
                                .width(widths.getOrElse(colIdx) { 140.dp })
                                .borderHairline()
                                .padding(6.dp)
                        ) {
                            if (editable) {
                                BasicTextFieldWrapper(value) {
                                    value = it
                                    rows = rows.toMutableList().also { list ->
                                        list[actualIdx] = list[actualIdx].toMutableList().also { it[colIdx] = value }
                                    }
                                    emitChange()
                                }
                            } else {
                                Text(value, fontFamily = FontFamily.Monospace, fontSize = 13.sp)
                            }
                        }
                    }
                    if (editable) {
                        IconButton(onClick = {
                            rows = rows.toMutableList().also { it.removeAt(actualIdx) }
                            emitChange()
                        }) { Icon(Icons.Default.Close, null) }
                    }
                }
                Divider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
            }
        }
        }

        if (editable) {
            Divider()
            TextButton(
                onClick = {
                    val newRow = List(columnCount) { "" }
                    rows = rows + listOf(newRow)
                    emitChange()
                }
            ) {
                Icon(Icons.Default.Add, null); Spacer(Modifier.width(4.dp)); Text("Add row")
            }
        }
    }
}

@Composable
private fun HeaderCell(text: String, width: Dp, onResize: (Float) -> Unit) {
    val density = LocalDensity.current
    Box(
        modifier = Modifier
            .width(width)
            .padding(6.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(text, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold, fontSize = 13.sp)
            Spacer(Modifier.weight(1f))
            Box(
                modifier = Modifier
                    .width(8.dp)
                    .height(20.dp)
                    .background(MaterialTheme.colorScheme.outline.copy(alpha = 0.5f), RoundedCornerShape(2.dp))
                    .pointerInput(Unit) {
                        detectDragGestures { change, dragAmount ->
                            change.consume()
                            val deltaDp = with(density) { dragAmount.x.toDp().value }
                            onResize(deltaDp)
                        }
                    }
            )
        }
    }
}

@Composable
private fun BasicTextFieldWrapper(value: String, onValueChange: (String) -> Unit) {
    androidx.compose.foundation.text.BasicTextField(
        value = value,
        onValueChange = onValueChange,
        textStyle = androidx.compose.ui.text.TextStyle(
            fontFamily = FontFamily.Monospace,
            fontSize = 13.sp,
            color = MaterialTheme.colorScheme.onSurface
        ),
        modifier = Modifier.fillMaxWidth()
    )
}

private fun Modifier.borderHairline(): Modifier = this.then(
    Modifier.background(Color.Transparent)
)

// ---- CSV parsing ----

private fun parseCsv(raw: String, delimiter: Char): List<List<String>> {
    val text = raw.removePrefix("\uFEFF")
    if (text.isBlank()) return emptyList()
    val out = mutableListOf<List<String>>()
    val currentRow = mutableListOf<String>()
    val current = StringBuilder()
    var inQuotes = false
    var i = 0
    while (i < text.length) {
        val c = text[i]
        when {
            inQuotes -> {
                if (c == '"') {
                    if (i + 1 < text.length && text[i + 1] == '"') { current.append('"'); i++ }
                    else inQuotes = false
                } else current.append(c)
            }
            c == '"' -> inQuotes = true
            c == delimiter -> { currentRow += current.toString(); current.clear() }
            c == '\n' || c == '\r' -> {
                if (c == '\r' && i + 1 < text.length && text[i + 1] == '\n') i++
                currentRow += current.toString(); current.clear()
                out += currentRow.toList(); currentRow.clear()
            }
            else -> current.append(c)
        }
        i++
    }
    if (current.isNotEmpty() || currentRow.isNotEmpty()) {
        currentRow += current.toString()
        out += currentRow.toList()
    }
    // drop blank lines (a line with no text parses as one empty cell)
    return out.filter { it.size > 1 || it.firstOrNull()?.isNotEmpty() == true }
}

private fun escapeCsv(s: String, delimiter: Char): String =
    if (s.contains(delimiter) || s.contains('"') || s.contains('\n'))
        "\"${s.replace("\"", "\"\"")}\""
    else s
