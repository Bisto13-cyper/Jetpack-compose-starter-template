package com.superapp.app.features.webview

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

data class InstalledBrowser(val packageName: String, val label: String)

/**
 * Stores which browser the WebView feature uses.
 * selectedBrowserPackage == null  -> in-app WebView (default)
 * selectedBrowserPackage == "x.y" -> addresses are opened in that installed browser app
 */
object WebViewPreferences {
    private const val PREFS = "webview_feature_prefs"
    private const val KEY_BROWSER = "browser_package"

    var selectedBrowserPackage by mutableStateOf<String?>(null)
        private set

    private var loaded = false

    fun ensureLoaded(context: Context) {
        if (loaded) return
        loaded = true
        selectedBrowserPackage = context.applicationContext
            .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_BROWSER, null)
            ?.takeIf { it.isNotBlank() }
    }

    fun selectBrowser(context: Context, packageName: String?) {
        selectedBrowserPackage = packageName
        context.applicationContext
            .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_BROWSER, packageName ?: "")
            .apply()
    }

    fun installedBrowsers(context: Context): List<InstalledBrowser> {
        val pm = context.packageManager
        val probe = Intent(Intent.ACTION_VIEW, Uri.parse("https://example.com"))
            .addCategory(Intent.CATEGORY_BROWSABLE)
        return pm.queryIntentActivities(probe, PackageManager.MATCH_ALL)
            .map { InstalledBrowser(it.activityInfo.packageName, it.loadLabel(pm).toString()) }
            .distinctBy { it.packageName }
            .sortedBy { it.label.lowercase() }
    }

    /** Returns true if the url was handed to the selected external browser. */
    fun openInSelectedBrowser(context: Context, url: String): Boolean {
        val pkg = selectedBrowserPackage ?: return false
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
            .setPackage(pkg)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return try {
            context.startActivity(intent)
            true
        } catch (e: ActivityNotFoundException) {
            false
        }
    }
}
