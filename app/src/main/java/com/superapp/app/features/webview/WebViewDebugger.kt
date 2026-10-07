package com.superapp.app.features.webview

import android.content.Context
import android.content.pm.ApplicationInfo
import android.graphics.Bitmap
import android.net.http.SslError
import android.os.Handler
import android.os.Looper
import android.webkit.ConsoleMessage
import android.webkit.CookieManager
import android.webkit.SslErrorHandler
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.json.JSONObject
import org.json.JSONTokener
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class ConsoleEntry(
    val id: Long,
    val time: Long,
    val level: String,
    val message: String,
    val source: String,
    val line: Int
)

data class ErrorEntry(
    val id: Long,
    val time: Long,
    val type: String,
    val url: String,
    val description: String
)

data class NetworkEntry(
    val id: Long,
    val time: Long,
    val method: String,
    val url: String,
    val isMainFrame: Boolean,
    val headers: Map<String, String>
)

fun formatDebugTime(millis: Long): String =
    SimpleDateFormat("HH:mm:ss.SSS", Locale.US).format(Date(millis))

/** Holds everything the developer panel shows. Normal browsing UI only reads url/title/progress/canGoBack. */
class WebViewDebugState {
    val console = mutableStateListOf<ConsoleEntry>()
    val errors = mutableStateListOf<ErrorEntry>()
    val network = mutableStateListOf<NetworkEntry>()

    var currentUrl by mutableStateOf("")
    var pageTitle by mutableStateOf("")
    var userAgent by mutableStateOf("")
    var progress by mutableStateOf(100)
    var canGoBack by mutableStateOf(false)

    private var nextId = 0L
    private val mainHandler = Handler(Looper.getMainLooper())

    private fun onMain(block: () -> Unit) {
        if (Looper.myLooper() == Looper.getMainLooper()) block() else mainHandler.post(block)
    }

    private fun <T> trim(list: MutableList<T>) {
        while (list.size > MAX_ENTRIES) list.removeAt(0)
    }

    fun addConsole(level: String, message: String, source: String, line: Int) = onMain {
        console.add(ConsoleEntry(nextId++, System.currentTimeMillis(), level, message, source, line))
        trim(console)
    }

    fun addError(type: String, url: String, description: String) = onMain {
        errors.add(ErrorEntry(nextId++, System.currentTimeMillis(), type, url, description))
        trim(errors)
    }

    fun addRequest(method: String, url: String, isMainFrame: Boolean, headers: Map<String, String>) = onMain {
        network.add(NetworkEntry(nextId++, System.currentTimeMillis(), method, url, isMainFrame, headers))
        trim(network)
    }

    fun clearConsole() = console.clear()
    fun clearErrors() = errors.clear()
    fun clearNetwork() = network.clear()

    companion object {
        const val MAX_ENTRIES = 500
    }
}

class DebugWebViewClient(private val state: WebViewDebugState) : WebViewClient() {

    // Called on a background thread. Logging only; returning null lets WebView load normally.
    override fun shouldInterceptRequest(view: WebView?, request: WebResourceRequest?): WebResourceResponse? {
        if (request != null) {
            state.addRequest(
                method = request.method ?: "GET",
                url = request.url.toString(),
                isMainFrame = request.isForMainFrame,
                headers = request.requestHeaders ?: emptyMap()
            )
        }
        return null
    }

    override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
        super.onPageStarted(view, url, favicon)
        state.currentUrl = url ?: ""
        state.canGoBack = view?.canGoBack() == true
    }

    override fun onPageFinished(view: WebView?, url: String?) {
        super.onPageFinished(view, url)
        state.currentUrl = url ?: state.currentUrl
        state.pageTitle = view?.title ?: ""
        state.canGoBack = view?.canGoBack() == true
    }

    override fun doUpdateVisitedHistory(view: WebView?, url: String?, isReload: Boolean) {
        super.doUpdateVisitedHistory(view, url, isReload)
        if (!url.isNullOrBlank()) state.currentUrl = url
        state.canGoBack = view?.canGoBack() == true
    }

    override fun onReceivedError(view: WebView?, request: WebResourceRequest?, error: WebResourceError?) {
        super.onReceivedError(view, request, error)
        state.addError(
            type = if (request?.isForMainFrame == true) "Page error" else "Resource error",
            url = request?.url?.toString() ?: "",
            description = "${error?.errorCode ?: 0}: ${error?.description ?: "unknown"}"
        )
    }

    override fun onReceivedHttpError(view: WebView?, request: WebResourceRequest?, errorResponse: WebResourceResponse?) {
        super.onReceivedHttpError(view, request, errorResponse)
        state.addError(
            type = "HTTP error",
            url = request?.url?.toString() ?: "",
            description = "${errorResponse?.statusCode ?: 0} ${errorResponse?.reasonPhrase ?: ""}".trim()
        )
    }

    // The connection is always cancelled (never proceed past a certificate problem); the error is logged.
    override fun onReceivedSslError(view: WebView?, handler: SslErrorHandler?, error: SslError?) {
        state.addError(
            type = "SSL error",
            url = error?.url ?: "",
            description = "primary error code ${error?.primaryError ?: -1}"
        )
        handler?.cancel()
    }
}

class DebugWebChromeClient(private val state: WebViewDebugState) : WebChromeClient() {

    override fun onConsoleMessage(consoleMessage: ConsoleMessage?): Boolean {
        if (consoleMessage == null) return false
        state.addConsole(
            level = consoleMessage.messageLevel().name,
            message = consoleMessage.message() ?: "",
            source = consoleMessage.sourceId() ?: "",
            line = consoleMessage.lineNumber()
        )
        return true
    }

    override fun onProgressChanged(view: WebView?, newProgress: Int) {
        super.onProgressChanged(view, newProgress)
        state.progress = newProgress
    }

    override fun onReceivedTitle(view: WebView?, title: String?) {
        super.onReceivedTitle(view, title)
        state.pageTitle = title ?: ""
    }
}

/**
 * On-demand inspection using only supported WebView APIs (evaluateJavascript, CookieManager).
 * No JavaScript bridge is exposed to web pages.
 */
object WebViewInspector {

    const val PAGE_INFO = "(function(){return JSON.stringify({" +
        "title:document.title,url:location.href,readyState:document.readyState," +
        "viewport:[innerWidth,innerHeight],elements:document.getElementsByTagName('*').length," +
        "cookieEnabled:navigator.cookieEnabled,language:navigator.language,userAgent:navigator.userAgent" +
        "},null,2);})()"

    const val DOM_HTML = "(function(){return document.documentElement.outerHTML;})()"

    const val WEB_STORAGE = "(function(){function d(s){var o={};try{for(var i=0;i<s.length;i++){" +
        "var k=s.key(i);o[k]=s.getItem(k);}}catch(e){o.__error=String(e);}return o;}" +
        "return JSON.stringify({localStorage:d(window.localStorage),sessionStorage:d(window.sessionStorage)},null,2);})()"

    const val RESOURCE_TIMING = "(function(){return JSON.stringify(performance.getEntriesByType('resource')" +
        ".slice(-200).map(function(e){return {name:e.name,type:e.initiatorType,ms:Math.round(e.duration)," +
        "size:e.transferSize,status:e.responseStatus};}),null,1);})()"

    fun querySelectorScript(selector: String): String =
        "(function(){try{var n=document.querySelectorAll(${JSONObject.quote(selector)});" +
            "var out='Matches: '+n.length+'\\n';" +
            "for(var i=0;i<Math.min(n.length,20);i++){out+='\\n['+i+'] '+n[i].outerHTML.slice(0,1000)+'\\n';}" +
            "return out;}catch(e){return 'Error: '+e;}})()"

    fun evaluate(webView: WebView, script: String, callback: (String) -> Unit) {
        webView.evaluateJavascript(script) { raw -> callback(decode(raw)) }
    }

    private fun decode(raw: String?): String {
        if (raw == null || raw == "null") return "(no result)"
        return try {
            (JSONTokener(raw).nextValue() as? String) ?: raw
        } catch (e: Exception) {
            raw
        }
    }

    fun cookiesFor(url: String): String {
        if (url.isBlank()) return "(no page loaded)"
        val raw = CookieManager.getInstance().getCookie(url)
        if (raw.isNullOrBlank()) return "(no cookies for this URL)"
        return raw.split("; ").joinToString("\n")
    }

    /** Lets chrome://inspect on a desktop see the WebView, only for debuggable builds. */
    fun enableRemoteDebuggingIfDebuggable(context: Context) {
        val debuggable = (context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0
        if (debuggable) WebView.setWebContentsDebuggingEnabled(true)
    }
}
