package com.example

import android.content.Context
import android.os.Bundle
import android.view.ViewGroup
import android.webkit.WebView
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class BrowserNavigationRequest(
    val url: String,
    val forceClearAndRefresh: Boolean = true,
    val requestId: Long = System.currentTimeMillis()
)

object BrowserStateManager {
    private const val PREFS_NAME = "browser_state_prefs"
    private const val KEY_LAST_VISITED_URL = "last_visited_url"
    private const val KEY_DESKTOP_MODE = "is_desktop_mode"

    private var retainedWebView: WebView? = null
    private var savedBundle: Bundle? = null

    private val _navigationRequests = MutableStateFlow<BrowserNavigationRequest?>(null)
    val navigationRequests: StateFlow<BrowserNavigationRequest?> = _navigationRequests.asStateFlow()

    fun clearSavedState() {
        savedBundle = null
    }

    fun forceNavigate(context: Context, url: String) {
        val trimmed = url.trim()
        val formatted = if (!trimmed.startsWith("http://") && !trimmed.startsWith("https://")) {
            "https://$trimmed"
        } else {
            trimmed
        }

        // 1. Forcefully replace last visited URL with the new link
        saveLastVisitedUrl(context, formatted)

        // 2. Forcefully delete any previous saved bundle/history
        savedBundle = null

        // 3. If WebView is already instantiated in memory, forcefully purge previous history and load fresh
        try {
            retainedWebView?.apply {
                stopLoading()
                clearHistory()
                if (this.url == formatted) {
                    reload()
                } else {
                    loadUrl(formatted)
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        // 4. Broadcast navigation request with unique timestamp so FloatingWebViewContent updates UI immediately
        _navigationRequests.value = BrowserNavigationRequest(
            url = formatted,
            forceClearAndRefresh = true,
            requestId = System.currentTimeMillis()
        )
    }

    fun getLastVisitedUrl(context: Context): String {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val saved = prefs.getString(KEY_LAST_VISITED_URL, null)
        return if (!saved.isNullOrBlank()) {
            saved
        } else {
            SearchEnginePreferences.getSelectedEngine(context).homeUrl
        }
    }

    fun saveLastVisitedUrl(context: Context, url: String) {
        if (url.isBlank() || url == "about:blank") return
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(KEY_LAST_VISITED_URL, url).apply()
    }

    fun getDesktopMode(context: Context): Boolean {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getBoolean(KEY_DESKTOP_MODE, false)
    }

    fun setDesktopMode(context: Context, isDesktop: Boolean) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putBoolean(KEY_DESKTOP_MODE, isDesktop).apply()
    }

    fun getRetainedWebView(): WebView? {
        return retainedWebView
    }

    fun setRetainedWebView(webView: WebView?) {
        retainedWebView = webView
    }

    fun getSavedBundle(): Bundle? = savedBundle

    fun saveState(webView: WebView?) {
        if (webView == null) return
        val bundle = Bundle()
        webView.saveState(bundle)
        savedBundle = bundle
    }

    fun detachFromParent(webView: WebView?) {
        webView?.let {
            (it.parent as? ViewGroup)?.removeView(it)
        }
    }

    fun pauseWebView() {
        try {
            retainedWebView?.onPause()
            retainedWebView?.pauseTimers()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun resumeWebView() {
        try {
            retainedWebView?.onResume()
            retainedWebView?.resumeTimers()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun resetToHome(context: Context, webView: WebView?) {
        val homeUrl = SearchEnginePreferences.getSelectedEngine(context).homeUrl
        saveLastVisitedUrl(context, homeUrl)
        savedBundle = null
        webView?.loadUrl(homeUrl)
    }
}
