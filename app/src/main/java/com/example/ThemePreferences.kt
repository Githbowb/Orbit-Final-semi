package com.example

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.ui.graphics.Color
import java.io.File

data class NeonTheme(
    val id: String,
    val displayName: String,
    val colorValue: Long,
    val colorHexStr: String
) {
    fun getColor(): Color = Color(colorValue)
}

object ThemePreferences {
    private const val PREFS_NAME = "floating_launcher_theme_prefs"
    private const val KEY_COLOR_ID = "neon_color_id"
    private const val KEY_BUBBLE_SIZE = "bubble_size_dp"
    private const val KEY_LANGUAGE = "app_language"
    private const val KEY_FIRST_RUN = "is_first_run"
    private const val KEY_USAGE_PERMISSION_SKIPPED = "usage_permission_skipped"
    private const val KEY_INTRO_SEEN = "intro_seen"
    private const val KEY_LANG_SELECTED = "lang_selected_v2"
    private const val KEY_USERNAME = "user_profile_name"

    fun getUsername(context: Context): String {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getString(KEY_USERNAME, "") ?: ""
    }

    fun setUsername(context: Context, username: String) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(KEY_USERNAME, username.trim()).apply()
    }

    fun hasUsername(context: Context): Boolean {
        return getUsername(context).trim().isNotEmpty()
    }

    fun isLangSelected(context: Context): Boolean {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getBoolean(KEY_LANG_SELECTED, false)
    }

    fun setLangSelected(context: Context, selected: Boolean) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putBoolean(KEY_LANG_SELECTED, selected).apply()
    }
    const val DEFAULT_BUBBLE_SIZE = 56

    fun isIntroSeen(context: Context): Boolean {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getBoolean(KEY_INTRO_SEEN, false)
    }

    fun setIntroSeen(context: Context, seen: Boolean) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putBoolean(KEY_INTRO_SEEN, seen).apply()
    }

    fun isUsagePermissionSkipped(context: Context): Boolean {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getBoolean(KEY_USAGE_PERMISSION_SKIPPED, false)
    }

    fun setUsagePermissionSkipped(context: Context, skipped: Boolean) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putBoolean(KEY_USAGE_PERMISSION_SKIPPED, skipped).apply()
    }

    val themes = listOf(
        NeonTheme("cyan", "Neon Cyan", 0xFF00E5FF, "#00E5FF"),
        NeonTheme("pink", "Neon Pink", 0xFFFF2A85, "#FF2A85"),
        NeonTheme("green", "Neon Green", 0xFF00E676, "#00E676"),
        NeonTheme("purple", "Neon Purple", 0xFFD500F9, "#D500F9"),
        NeonTheme("yellow", "Neon Yellow", 0xFFFFD600, "#FFD600")
    )

    fun getLanguage(context: Context): String {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getString(KEY_LANGUAGE, "en") ?: "en"
    }

    fun setLanguage(context: Context, langCode: String) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(KEY_LANGUAGE, langCode).apply()
    }

    fun isFirstRun(context: Context): Boolean {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getBoolean(KEY_FIRST_RUN, true)
    }

    fun setFirstRun(context: Context, isFirstRun: Boolean) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putBoolean(KEY_FIRST_RUN, isFirstRun).apply()
    }

    fun getSelectedTheme(context: Context): NeonTheme {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val id = prefs.getString(KEY_COLOR_ID, "cyan") ?: "cyan"
        return themes.find { it.id == id } ?: themes[0]
    }

    fun setSelectedTheme(context: Context, themeId: String) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(KEY_COLOR_ID, themeId).apply()
    }

    fun getBubbleSize(context: Context): Int {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getInt(KEY_BUBBLE_SIZE, DEFAULT_BUBBLE_SIZE)
    }

    fun setBubbleSize(context: Context, sizeDp: Int) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putInt(KEY_BUBBLE_SIZE, sizeDp).apply()
    }

    private const val KEY_BUBBLE_SYMBOL = "bubble_symbol"
    private const val KEY_GLOW_INTENSITY = "glow_intensity"
    private const val KEY_BUBBLE_OPACITY = "bubble_opacity"
    private const val KEY_BORDER_STROKE = "border_stroke_dp"
    private const val KEY_BUBBLE_LABEL = "bubble_label"

    data class BubbleIconItem(
        val id: String,
        val name: String,
        val drawableRes: Int
    )

    fun getBubbleIconDrawableRes(symbolKey: String): Int {
        return when (symbolKey) {
            "crescent_moon", "🪐", "🌙" -> R.drawable.ic_bubble_crescent_moon
            "orbit_ring", "⚡" -> R.drawable.ic_bubble_orbit_ring
            "atom_core", "🌀" -> R.drawable.ic_bubble_atom_core
            "cyber_spark", "🚀" -> R.drawable.ic_bubble_cyber_spark
            "energy_matrix", "💎" -> R.drawable.ic_bubble_energy_matrix
            "galaxy_vortex", "🌐" -> R.drawable.ic_bubble_galaxy_vortex
            "crystal_hex", "🔮" -> R.drawable.ic_bubble_crystal_hex
            "terminal_code" -> R.drawable.ic_bubble_terminal_code
            else -> R.drawable.ic_bubble_crescent_moon
        }
    }

    val bubbleIcons = listOf(
        BubbleIconItem("crescent_moon", "Crescent Moon", R.drawable.ic_bubble_crescent_moon),
        BubbleIconItem("orbit_ring", "Orbit Ring", R.drawable.ic_bubble_orbit_ring),
        BubbleIconItem("atom_core", "Atom Core", R.drawable.ic_bubble_atom_core),
        BubbleIconItem("cyber_spark", "Cyber Spark", R.drawable.ic_bubble_cyber_spark),
        BubbleIconItem("energy_matrix", "Energy Matrix", R.drawable.ic_bubble_energy_matrix),
        BubbleIconItem("galaxy_vortex", "Galaxy Vortex", R.drawable.ic_bubble_galaxy_vortex),
        BubbleIconItem("crystal_hex", "Crystal Hex", R.drawable.ic_bubble_crystal_hex),
        BubbleIconItem("terminal_code", "Terminal Code", R.drawable.ic_bubble_terminal_code)
    )

    fun loadCustomBubbleBitmap(context: Context): Bitmap? {
        val file = File(context.filesDir, "custom_bubble_icon.png")
        return if (file.exists()) {
            try {
                BitmapFactory.decodeFile(file.absolutePath)
            } catch (e: Exception) {
                null
            }
        } else null
    }

    fun isCustomBubbleImageAvailable(context: Context): Boolean {
        return File(context.filesDir, "custom_bubble_icon.png").exists()
    }

    fun getBubbleSymbol(context: Context): String {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val symbol = prefs.getString(KEY_BUBBLE_SYMBOL, "crescent_moon") ?: "crescent_moon"
        return if (symbol in listOf("🪐", "🌙", "⚡", "🌀", "🚀", "💎", "🌐", "🔮")) "crescent_moon" else symbol
    }

    fun setBubbleSymbol(context: Context, symbol: String) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(KEY_BUBBLE_SYMBOL, symbol).apply()
    }

    fun getGlowIntensity(context: Context): Float {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getFloat(KEY_GLOW_INTENSITY, 0.7f)
    }

    fun setGlowIntensity(context: Context, intensity: Float) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putFloat(KEY_GLOW_INTENSITY, intensity).apply()
    }

    fun getBubbleOpacity(context: Context): Float {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getFloat(KEY_BUBBLE_OPACITY, 1.0f)
    }

    fun setBubbleOpacity(context: Context, opacity: Float) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putFloat(KEY_BUBBLE_OPACITY, opacity).apply()
    }

    fun getBorderStrokeDp(context: Context): Int {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getInt(KEY_BORDER_STROKE, 2)
    }

    fun setBorderStrokeDp(context: Context, strokeDp: Int) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putInt(KEY_BORDER_STROKE, strokeDp).apply()
    }

    private const val KEY_INNER_TINT_COLOR = "inner_tint_color"

    fun getInnerTintColor(context: Context): Long {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getLong(KEY_INNER_TINT_COLOR, 0L)
    }

    fun setInnerTintColor(context: Context, colorValue: Long) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putLong(KEY_INNER_TINT_COLOR, colorValue).apply()
    }
}
