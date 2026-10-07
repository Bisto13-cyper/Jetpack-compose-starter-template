package com.superapp.app.features.webview

import android.webkit.WebView
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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

private const val MAX_OUTPUT_CHARS = 20_000

/** Developer tools for the WebView. Opened only from its own button, never part of normal browsing UI. */
@Composable
fun WebViewDevToolsPanel(
    state: WebViewDebugState,
    webView: WebView?,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    val tabs = listOf("Console", "Errors", "Network", "Storage", "Page")
    var tab by remember { mutableStateOf(0) }

    Surface(modifier = modifier, tonalElevation = 6.dp) {
        Column(Modifier.fillMaxSize()) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                ScrollableTabRow(
                    selectedTabIndex = tab,
                    modifier = Modifier.weight(1f),
                    edgePadding = 0.dp
                ) {
                    tabs.forEachIndexed { index, title ->
                        val label = if (index == 1 && state.errors.isNotEmpty()) "$title (${state.errors.size})" else title
                        Tab(selected = tab == index, onClick = { tab = index }, text = { Text(label, maxLines = 1) })
                    }
                }
                TextButton(onClick = onClose) { Text("✕") }
            }
            when (tab) {
                0 -> ConsoleTab(state)
                1 -> ErrorsTab(state)
                2 -> NetworkTab(state, webView)
                3 -> StorageTab(state, webView)
                else -> PageTab(state, webView)
            }
        }
    }
}

@Composable
private fun ToolbarRow(summary: String, onClear: (() -> Unit)?) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(summary, fontSize = 12.sp)
        if (onClear != null) TextButton(onClick = onClear) { Text("Clear") }
    }
}

@Composable
private fun MonoText(text: String, color: Color = MaterialTheme.colorScheme.onSurface) {
    Text(text, fontFamily = FontFamily.Monospace, fontSize = 11.sp, color = color)
}

@Composable
private fun ConsoleTab(state: WebViewDebugState) {
    Column(Modifier.fillMaxSize()) {
        ToolbarRow("${state.console.size} messages", state::clearConsole)
        LazyColumn(Modifier.fillMaxSize(), contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp, vertical = 4.dp)) {
            items(state.console, key = { it.id }) { e ->
                val color = when (e.level) {
                    "ERROR" -> MaterialTheme.colorScheme.error
                    "WARNING" -> Color(0xFFB26A00)
                    else -> MaterialTheme.colorScheme.onSurface
                }
                Column(Modifier.padding(vertical = 3.dp)) {
                    MonoText("[${formatDebugTime(e.time)}] ${e.level}  ${e.message}", color)
                    if (e.source.isNotBlank()) MonoText("${e.source}:${e.line}", MaterialTheme.colorScheme.outline)
                }
            }
        }
    }
}

@Composable
private fun ErrorsTab(state: WebViewDebugState) {
    Column(Modifier.fillMaxSize()) {
        ToolbarRow("${state.errors.size} errors", state::clearErrors)
        LazyColumn(Modifier.fillMaxSize(), contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp, vertical = 4.dp)) {
            items(state.errors, key = { it.id }) { e ->
                Column(Modifier.padding(vertical = 3.dp)) {
                    MonoText("[${formatDebugTime(e.time)}] ${e.type}: ${e.description}", MaterialTheme.colorScheme.error)
                    MonoText(e.url, MaterialTheme.colorScheme.outline)
                }
            }
        }
    }
}

@Composable
private fun NetworkTab(state: WebViewDebugState, webView: WebView?) {
    var expanded by remember { mutableStateOf<Long?>(null) }
    var timing by remember { mutableStateOf("") }

    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text("${state.network.size} requests", fontSize = 12.sp)
            Row {
                TextButton(onClick = {
                    webView?.let { WebViewInspector.evaluate(it, WebViewInspector.RESOURCE_TIMING) { r -> timing = r } }
                }) { Text("Resource timing") }
                TextButton(onClick = { state.clearNetwork(); timing = "" }) { Text("Clear") }
            }
        }
        LazyColumn(Modifier.fillMaxSize(), contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp, vertical = 4.dp)) {
            if (timing.isNotEmpty()) {
                item {
                    SelectionContainer { MonoText(timing.take(MAX_OUTPUT_CHARS)) }
                }
            }
            items(state.network, key = { it.id }) { e ->
                Column(
                    Modifier
                        .fillMaxWidth()
                        .clickable { expanded = if (expanded == e.id) null else e.id }
                        .padding(vertical = 3.dp)
                ) {
                    val tag = if (e.isMainFrame) " [main]" else ""
                    MonoText("[${formatDebugTime(e.time)}] ${e.method}$tag ${e.url}")
                    if (expanded == e.id) {
                        MonoText(
                            if (e.headers.isEmpty()) "(no request headers)"
                            else e.headers.entries.joinToString("\n") { "${it.key}: ${it.value}" },
                            MaterialTheme.colorScheme.outline
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun StorageTab(state: WebViewDebugState, webView: WebView?) {
    var cookies by remember { mutableStateOf("") }
    var storage by remember { mutableStateOf("") }

    fun refresh() {
        cookies = WebViewInspector.cookiesFor(state.currentUrl)
        webView?.let { WebViewInspector.evaluate(it, WebViewInspector.WEB_STORAGE) { r -> storage = r } }
    }

    LaunchedEffect(state.currentUrl) { refresh() }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(12.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text("Cookies (sent for current URL)", fontSize = 12.sp)
            TextButton(onClick = { refresh() }) { Text("Refresh") }
        }
        SelectionContainer { MonoText(cookies) }
        Text("Web storage", fontSize = 12.sp, modifier = Modifier.padding(top = 12.dp))
        SelectionContainer { MonoText(storage.take(MAX_OUTPUT_CHARS)) }
    }
}

@Composable
private fun PageTab(state: WebViewDebugState, webView: WebView?) {
    var output by remember { mutableStateOf("") }
    var selector by remember { mutableStateOf("") }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(12.dp)) {
        Text("Current URL", fontSize = 12.sp)
        SelectionContainer { MonoText(state.currentUrl.ifBlank { "(none)" }) }
        Text("Title", fontSize = 12.sp, modifier = Modifier.padding(top = 8.dp))
        MonoText(state.pageTitle.ifBlank { "(none)" })
        Text("User-Agent", fontSize = 12.sp, modifier = Modifier.padding(top = 8.dp))
        SelectionContainer { MonoText(state.userAgent.ifBlank { "(unknown)" }) }

        Row(Modifier.fillMaxWidth().padding(top = 8.dp)) {
            TextButton(onClick = { webView?.reload() }) { Text("Reload") }
            TextButton(onClick = {
                webView?.let { WebViewInspector.evaluate(it, WebViewInspector.PAGE_INFO) { r -> output = r } }
            }) { Text("Page info") }
            TextButton(onClick = {
                webView?.let { WebViewInspector.evaluate(it, WebViewInspector.DOM_HTML) { r -> output = r } }
            }) { Text("Load HTML") }
        }

        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = selector,
                onValueChange = { selector = it },
                label = { Text("CSS selector") },
                singleLine = true,
                modifier = Modifier.weight(1f)
            )
            TextButton(onClick = {
                if (selector.isNotBlank()) {
                    webView?.let { WebViewInspector.evaluate(it, WebViewInspector.querySelectorScript(selector)) { r -> output = r } }
                }
            }) { Text("Query") }
        }

        if (output.isNotEmpty()) {
            Text(
                if (output.length > MAX_OUTPUT_CHARS) "Output (first $MAX_OUTPUT_CHARS characters)" else "Output",
                fontSize = 12.sp,
                modifier = Modifier.padding(top = 12.dp)
            )
            SelectionContainer { MonoText(output.take(MAX_OUTPUT_CHARS)) }
        }
    }
}
