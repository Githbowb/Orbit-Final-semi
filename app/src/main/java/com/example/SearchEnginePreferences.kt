package com.example

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class SearchEngine(
    val id: String,
    val name: String,
    val homeUrl: String,
    val searchUrlTemplate: String
)

object SearchEnginePreferences {
    private const val PREFS_NAME = "search_engine_prefs"
    private const val KEY_SELECTED_ENGINE = "selected_search_engine"

    val ENGINES = listOf(
        SearchEngine("google", "Google", "https://www.google.com", "https://www.google.com/search?q=%s"),
        SearchEngine("duckduckgo", "DuckDuckGo", "https://duckduckgo.com", "https://duckduckgo.com/?q=%s"),
        SearchEngine("bing", "Bing", "https://www.bing.com", "https://www.bing.com/search?q=%s"),
        SearchEngine("brave", "Brave", "https://search.brave.com", "https://search.brave.com/search?q=%s"),
        SearchEngine("ecosia", "Ecosia", "https://www.ecosia.org", "https://www.ecosia.org/search?q=%s"),
        SearchEngine("yahoo", "Yahoo", "https://search.yahoo.com", "https://search.yahoo.com/search?p=%s"),
        SearchEngine("startpage", "Startpage", "https://www.startpage.com", "https://www.startpage.com/sp/search?query=%s"),
        SearchEngine("yandex", "Yandex", "https://yandex.com", "https://yandex.com/search/?text=%s")
    )

    private val _engineChanges = MutableStateFlow(0L)
    val engineChanges: StateFlow<Long> = _engineChanges.asStateFlow()

    fun getSelectedEngine(context: Context): SearchEngine {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val engineId = prefs.getString(KEY_SELECTED_ENGINE, "google") ?: "google"
        return ENGINES.firstOrNull { it.id == engineId } ?: ENGINES.first()
    }

    fun setSelectedEngine(context: Context, engineId: String) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(KEY_SELECTED_ENGINE, engineId).apply()
        val engine = ENGINES.firstOrNull { it.id == engineId } ?: ENGINES.first()
        BrowserStateManager.resetToHome(context, BrowserStateManager.getRetainedWebView())
        _engineChanges.value = System.currentTimeMillis()
    }
}
