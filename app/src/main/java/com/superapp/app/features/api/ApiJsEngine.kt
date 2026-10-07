package com.superapp.app.features.api

import android.annotation.SuppressLint
import android.content.Context
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONObject
import org.json.JSONTokener
import java.io.ByteArrayInputStream
import kotlin.coroutines.resume

data class JsResult(val output: String?, val error: String?, val logs: List<String>)

/**
 * Runs user JavaScript in a throw-away, headless WebView (the system's Chromium JS engine).
 *
 * Isolation:
 *  - NO addJavascriptInterface: the script has no bridge to Kotlin or Android APIs.
 *  - Network is blocked (blockNetworkLoads + every request is answered with an empty 204,
 *    navigation is cancelled), file/content access is off, no storage, no popups.
 *  - Inputs are passed only as JSON text: data, vars, response. The script returns JSON text.
 *  - A fresh WebView is created for every run and destroyed afterwards; runs stop after 5 s.
 */
object ApiJsEngine {
    const val TIMEOUT_MS = 5_000L

    private const val POLL_SCRIPT =
        "(function(){if(!window.__done){return '';}" +
            "return JSON.stringify({ok:window.__ok,out:window.__out,err:window.__err,logs:window.__logs});})()"

    @SuppressLint("SetJavaScriptEnabled")
    suspend fun run(
        context: Context,
        code: String,
        dataJson: String,
        varsJson: String,
        responseJson: String
    ): JsResult = withContext(Dispatchers.Main) {
        val webView = WebView(context.applicationContext)
        try {
            webView.settings.apply {
                javaScriptEnabled = true
                allowFileAccess = false
                allowContentAccess = false
                domStorageEnabled = false
                blockNetworkLoads = true
                cacheMode = WebSettings.LOAD_NO_CACHE
                javaScriptCanOpenWindowsAutomatically = false
                setSupportMultipleWindows(false)
            }
            val loaded = CompletableDeferred<Unit>()
            webView.webViewClient = object : WebViewClient() {
                override fun onPageFinished(view: WebView?, url: String?) {
                    loaded.complete(Unit)
                }

                override fun shouldInterceptRequest(view: WebView?, request: WebResourceRequest?): WebResourceResponse? =
                    WebResourceResponse(
                        "text/plain", "utf-8", 204, "No Content", emptyMap(),
                        ByteArrayInputStream(ByteArray(0))
                    )

                override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean = true
            }
            webView.loadDataWithBaseURL(null, "<html><body></body></html>", "text/html", "utf-8", null)
            if (withTimeoutOrNull(3_000) { loaded.await() } == null) {
                return@withContext JsResult(null, "The JavaScript sandbox failed to start.", emptyList())
            }

            webView.evaluateJavascript(buildScript(code, dataJson, varsJson, responseJson), null)

            withTimeoutOrNull(TIMEOUT_MS) { poll(webView) }
                ?: JsResult(null, "Script timed out after ${TIMEOUT_MS / 1000} s and was stopped.", emptyList())
        } finally {
            webView.stopLoading()
            webView.destroy()
        }
    }

    private suspend fun poll(webView: WebView): JsResult {
        while (true) {
            val raw: String? = suspendCancellableCoroutine { cont ->
                webView.evaluateJavascript(POLL_SCRIPT) { cont.resume(it) }
            }
            val text = decode(raw)
            if (text.isNotEmpty()) return parse(text)
            delay(40)
        }
    }

    private fun decode(raw: String?): String {
        if (raw == null || raw == "null") return ""
        return try {
            (JSONTokener(raw).nextValue() as? String) ?: ""
        } catch (e: Exception) {
            ""
        }
    }

    private fun parse(text: String): JsResult = try {
        val o = JSONObject(text)
        val logsArr = o.optJSONArray("logs")
        val logs = if (logsArr == null) emptyList() else (0 until logsArr.length()).map { logsArr.optString(it) }
        JsResult(
            output = if (o.isNull("out")) null else o.optString("out"),
            error = if (o.isNull("err")) null else o.optString("err"),
            logs = logs
        )
    } catch (e: Exception) {
        JsResult(null, "The script result could not be read.", emptyList())
    }

    private fun buildScript(code: String, dataJson: String, varsJson: String, responseJson: String): String {
        val codeLit = jsonQuote(code)
        val dataLit = jsonQuote(dataJson)
        val varsLit = jsonQuote(varsJson)
        val responseLit = jsonQuote(responseJson)
        return """
(function(){
  window.__done=false; window.__ok=false; window.__out=null; window.__err=null; window.__logs=[];
  var logs=window.__logs;
  function fmt(a){ try{ return typeof a==='string'?a:JSON.stringify(a); }catch(e){ return String(a); } }
  function logger(prefix){ return function(){ logs.push(prefix+Array.prototype.map.call(arguments,fmt).join(' ')); }; }
  var con={log:logger(''),info:logger(''),warn:logger('[warn] '),error:logger('[error] ')};
  try{
    var data=JSON.parse($dataLit), vars=JSON.parse($varsLit), response=JSON.parse($responseLit);
    var fn=new Function('data','vars','response','console','return (async function(){'+$codeLit+'\n})();');
    Promise.resolve(fn(data,vars,response,con)).then(function(r){
      try{
        var s=(r===undefined)?'null':JSON.stringify(r);
        window.__out=(s===undefined)?'null':s;
        window.__ok=true;
      }catch(e){ window.__err='The result cannot be converted to JSON: '+e; }
      window.__done=true;
    },function(e){ window.__err=String((e&&e.stack)||e); window.__done=true; });
  }catch(e){ window.__err=String(e); window.__done=true; }
})();
"""
    }
}
