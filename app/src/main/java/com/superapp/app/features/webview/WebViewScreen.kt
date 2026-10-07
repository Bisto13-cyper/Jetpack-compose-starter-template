package com.superapp.app.features.webview

import android.annotation.SuppressLint
import android.net.Uri
import android.webkit.WebView
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView

private fun normalizeUrl(input: String): String {
    val text = input.trim()
    return when {
        text.contains("://") -> text
        text.contains(".") && !text.contains(" ") -> "https://$text"
        else -> "https://www.google.com/search?q=" + Uri.encode(text)
    }
}

/**
 * Normal browsing UI: back, address bar, reload, and two small buttons
 * (settings, developer tools). Developer tools only appear when the 🐞 button is pressed.
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun WebViewScreen(
    modifier: Modifier = Modifier,
    initialUrl: String = "https://www.google.com",
    nodeEmoji: String = "🌐"
) {
    val context = LocalContext.current
    val debug = remember { WebViewDebugState() }
    var webView by remember { mutableStateOf<WebView?>(null) }
    var addressText by remember { mutableStateOf(initialUrl) }
    var showDevTools by remember { mutableStateOf(false) }
    var showSettings by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) { WebViewPreferences.ensureLoaded(context) }
    LaunchedEffect(debug.currentUrl) {
        if (debug.currentUrl.isNotBlank()) addressText = debug.currentUrl
    }
    DisposableEffect(Unit) {
        onDispose { webView?.destroy() }
    }

    // Only intercept Back while the page has history; otherwise the app's existing back handling runs.
    BackHandler(enabled = debug.canGoBack) { webView?.goBack() }

    fun go(input: String) {
        if (input.isBlank()) return
        val url = normalizeUrl(input)
        if (WebViewPreferences.selectedBrowserPackage != null) {
            if (WebViewPreferences.openInSelectedBrowser(context, url)) return
            Toast.makeText(context, "Selected browser unavailable, using in-app view", Toast.LENGTH_SHORT).show()
        }
        webView?.loadUrl(url)
    }

    Column(modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextButton(onClick = { webView?.goBack() }, enabled = debug.canGoBack) { Text("←") }
            OutlinedTextField(
                value = addressText,
                onValueChange = { addressText = it },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
                keyboardActions = KeyboardActions(onGo = { go(addressText) }),
                modifier = Modifier.weight(1f)
            )
            TextButton(onClick = { webView?.reload() }) { Text("⟳") }
            TextButton(onClick = { showSettings = true }) { Text("⚙") }
            TextButton(onClick = { showDevTools = !showDevTools }) { Text("🐞") }
        }

        if (debug.progress in 1..99) {
            LinearProgressIndicator(
                progress = debug.progress / 100f,
                modifier = Modifier.fillMaxWidth()
            )
        }

        AndroidView(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            factory = { ctx ->
                WebViewInspector.enableRemoteDebuggingIfDebuggable(ctx)
                WebView(ctx).apply {
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                    webViewClient = DebugWebViewClient(debug)
                    webChromeClient = DebugWebChromeClient(debug)
                    debug.userAgent = settings.userAgentString
                    loadUrl(normalizeUrl(initialUrl))
                }.also { webView = it }
            }
        )

        if (showDevTools) {
            WebViewDevToolsPanel(
                state = debug,
                webView = webView,
                onClose = { showDevTools = false },
                modifier = Modifier.fillMaxWidth().height(320.dp)
            )
        }
    }

    if (showSettings) {
        WebViewSettingsDialog(nodeEmoji = nodeEmoji, onDismiss = { showSettings = false })
    }
}

@Composable
private fun WebViewSettingsDialog(nodeEmoji: String, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val browsers = remember { WebViewPreferences.installedBrowsers(context) }
    val selected = WebViewPreferences.selectedBrowserPackage

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null && !WebViewNodeIconStore.saveFromUri(context, uri)) {
            Toast.makeText(context, "Could not use that image", Toast.LENGTH_SHORT).show()
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = onDismiss) { Text("Done") } },
        title = { Text("WebView settings") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Text("Node photo")
                Row(
                    Modifier.padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    WebViewNodeIcon(emoji = nodeEmoji, modifier = Modifier.size(56.dp))
                    TextButton(onClick = { picker.launch("image/*") }) { Text("Choose photo") }
                    if (WebViewNodeIconStore.hasCustomIcon(context) || WebViewNodeIconStore.version > 0) {
                        TextButton(onClick = { WebViewNodeIconStore.clear(context) }) { Text("Use emoji") }
                    }
                }

                Text("Browser", modifier = Modifier.padding(top = 12.dp))
                BrowserOption(
                    label = "In-app (WebView)",
                    selected = selected == null,
                    onClick = { WebViewPreferences.selectBrowser(context, null) }
                )
                browsers.forEach { b ->
                    BrowserOption(
                        label = b.label,
                        selected = selected == b.packageName,
                        onClick = { WebViewPreferences.selectBrowser(context, b.packageName) }
                    )
                }
            }
        }
    )
}

@Composable
private fun BrowserOption(label: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(selected = selected, onClick = null)
        Text(label, modifier = Modifier.padding(start = 12.dp))
    }
}
