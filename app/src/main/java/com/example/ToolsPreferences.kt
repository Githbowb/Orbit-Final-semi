package com.example

import android.content.Context

object ToolsPreferences {
    private const val PREFS_NAME = "floating_launcher_tools_prefs"

    const val KEY_LAUNCHPAD = "tool_launcher"
    const val KEY_VAULT = "tool_vault"
    const val KEY_CALCULATOR = "tool_calc"
    const val KEY_OCR = "tool_ocr"
    const val KEY_SPEED_DIAL = "tool_dial"
    const val KEY_SCANNER = "tool_scanner"

    val DEFAULT_TOOLS = listOf(
        KEY_LAUNCHPAD,
        KEY_VAULT,
        KEY_CALCULATOR,
        KEY_OCR,
        KEY_SPEED_DIAL,
        KEY_SCANNER
    )

    fun isToolEnabled(context: Context, toolKey: String): Boolean {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getBoolean(toolKey, true)
    }

    fun setToolEnabled(context: Context, toolKey: String, enabled: Boolean) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putBoolean(toolKey, enabled).apply()
    }

    fun getLaunchCount(context: Context, toolKey: String): Int {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getInt("count_$toolKey", 0)
    }

    fun incrementLaunchCount(context: Context, toolKey: String): Int {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val current = prefs.getInt("count_$toolKey", 0)
        val newCount = current + 1
        prefs.edit().putInt("count_$toolKey", newCount).apply()
        return newCount
    }
}
