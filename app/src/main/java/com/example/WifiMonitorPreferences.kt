package com.example

import android.content.Context
import android.net.TrafficStats
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object WifiMonitorPreferences {
    private const val PREFS_NAME = "orbit_wifi_monitor_prefs"

    const val MODE_RAM = "RAM"
    const val MODE_WIFI = "WIFI"

    const val DISPLAY_ALL = "ALL"
    const val DISPLAY_SPEED_ONLY = "SPEED_ONLY"
    const val DISPLAY_USAGE_ONLY = "USAGE_ONLY"

    private const val KEY_SELECTED_MODE = "key_selected_mode"
    private const val KEY_DAILY_LIMIT_MB = "key_daily_limit_mb"
    private const val KEY_DISPLAY_MODE = "key_display_mode"
    private const val KEY_PING_ENABLED = "key_ping_enabled"
    private const val KEY_ADAPTIVE_REFRESH = "key_adaptive_refresh"
    private const val KEY_SPEED_UNIT_BITS = "key_speed_unit_bits"

    private const val KEY_USAGE_TRACK_DATE = "key_usage_track_date"
    private const val KEY_ACCUMULATED_TODAY_BYTES = "key_accumulated_today_bytes"
    private const val KEY_LAST_WIFI_BOOT_BYTES = "key_last_wifi_boot_bytes"

    private fun getTodayDateString(): String {
        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        return sdf.format(Date())
    }

    fun getSelectedMode(context: Context): String {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getString(KEY_SELECTED_MODE, MODE_RAM) ?: MODE_RAM
    }

    fun setSelectedMode(context: Context, mode: String) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(KEY_SELECTED_MODE, mode).apply()
    }

    /**
     * Daily WiFi limit in MB. 0f represents No Limit (Disabled).
     */
    fun getDailyLimitMb(context: Context): Float {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getFloat(KEY_DAILY_LIMIT_MB, 0f)
    }

    fun setDailyLimitMb(context: Context, limitMb: Float) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putFloat(KEY_DAILY_LIMIT_MB, limitMb).apply()
    }

    fun getDisplayMode(context: Context): String {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getString(KEY_DISPLAY_MODE, DISPLAY_ALL) ?: DISPLAY_ALL
    }

    fun setDisplayMode(context: Context, mode: String) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(KEY_DISPLAY_MODE, mode).apply()
    }

    fun isPingEnabled(context: Context): Boolean {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getBoolean(KEY_PING_ENABLED, true)
    }

    fun setPingEnabled(context: Context, enabled: Boolean) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putBoolean(KEY_PING_ENABLED, enabled).apply()
    }

    fun isAdaptiveRefreshEnabled(context: Context): Boolean {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getBoolean(KEY_ADAPTIVE_REFRESH, true)
    }

    fun setAdaptiveRefreshEnabled(context: Context, enabled: Boolean) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putBoolean(KEY_ADAPTIVE_REFRESH, enabled).apply()
    }

    fun isSpeedUnitBits(context: Context): Boolean {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getBoolean(KEY_SPEED_UNIT_BITS, false)
    }

    fun setSpeedUnitBits(context: Context, useBits: Boolean) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putBoolean(KEY_SPEED_UNIT_BITS, useBits).apply()
    }

    /**
     * Updates cumulative Wi-Fi traffic delta using system kernel counters.
     * Accurately tracks downloads across all apps (Google Play, browser, games)
     * even when Orbit is in background or device reboots.
     */
    @Synchronized
    fun updateTrafficDelta(context: Context): Long {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val todayStr = getTodayDateString()
        val savedDate = prefs.getString(KEY_USAGE_TRACK_DATE, "")

        val totalRx = TrafficStats.getTotalRxBytes()
        val totalTx = TrafficStats.getTotalTxBytes()
        val mobileRx = TrafficStats.getMobileRxBytes()
        val mobileTx = TrafficStats.getMobileTxBytes()

        if (totalRx < 0 || totalTx < 0) {
            return getAccumulatedTodayBytes(context)
        }

        // Isolate Wi-Fi traffic from mobile cellular
        val wifiRx = if (mobileRx >= 0) (totalRx - mobileRx).coerceAtLeast(0L) else totalRx
        val wifiTx = if (mobileTx >= 0) (totalTx - mobileTx).coerceAtLeast(0L) else totalTx
        val currentWifiBoot = wifiRx + wifiTx

        val lastBootBytes = prefs.getLong(KEY_LAST_WIFI_BOOT_BYTES, -1L)
        var accumulatedToday = prefs.getLong(KEY_ACCUMULATED_TODAY_BYTES, 0L)

        if (savedDate != todayStr) {
            // New calendar day: archive previous day if valid
            if (!savedDate.isNullOrEmpty() && accumulatedToday > 0L) {
                prefs.edit().putLong("hist_day_$savedDate", accumulatedToday).apply()
            }
            accumulatedToday = 0L
            prefs.edit()
                .putString(KEY_USAGE_TRACK_DATE, todayStr)
                .putLong(KEY_ACCUMULATED_TODAY_BYTES, 0L)
                .putLong(KEY_LAST_WIFI_BOOT_BYTES, currentWifiBoot)
                .apply()
            return 0L
        }

        if (lastBootBytes < 0L) {
            // Baseline initialization on first run of the day
            prefs.edit()
                .putString(KEY_USAGE_TRACK_DATE, todayStr)
                .putLong(KEY_LAST_WIFI_BOOT_BYTES, currentWifiBoot)
                .apply()
            return accumulatedToday
        }

        val delta = if (currentWifiBoot >= lastBootBytes) {
            currentWifiBoot - lastBootBytes
        } else {
            // Device rebooted: currentWifiBoot began from 0
            currentWifiBoot
        }

        if (delta > 0L) {
            accumulatedToday += delta
            prefs.edit()
                .putLong(KEY_ACCUMULATED_TODAY_BYTES, accumulatedToday)
                .putLong(KEY_LAST_WIFI_BOOT_BYTES, currentWifiBoot)
                .apply()
        }

        return accumulatedToday
    }

    /**
     * Backward-compatible helper for adding an explicit delta.
     */
    @Synchronized
    fun addTrafficDelta(context: Context, deltaBytes: Long): Long {
        if (deltaBytes <= 0) return getAccumulatedTodayBytes(context)
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val todayStr = getTodayDateString()
        val savedDate = prefs.getString(KEY_USAGE_TRACK_DATE, "")

        val newTotal = if (savedDate == todayStr) {
            prefs.getLong(KEY_ACCUMULATED_TODAY_BYTES, 0L) + deltaBytes
        } else {
            deltaBytes
        }

        prefs.edit()
            .putString(KEY_USAGE_TRACK_DATE, todayStr)
            .putLong(KEY_ACCUMULATED_TODAY_BYTES, newTotal)
            .apply()

        return newTotal
    }

    fun setAccumulatedTodayBytes(context: Context, bytes: Long) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val todayStr = getTodayDateString()
        prefs.edit()
            .putString(KEY_USAGE_TRACK_DATE, todayStr)
            .putLong(KEY_ACCUMULATED_TODAY_BYTES, bytes)
            .putLong("hist_day_$todayStr", bytes)
            .apply()
    }

    fun getAccumulatedTodayBytes(context: Context): Long {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val todayStr = getTodayDateString()
        val savedDate = prefs.getString(KEY_USAGE_TRACK_DATE, "")
        return if (savedDate == todayStr) {
            prefs.getLong(KEY_ACCUMULATED_TODAY_BYTES, 0L)
        } else {
            0L
        }
    }

    fun recordDayBytes(context: Context, dateStr: String, bytes: Long) {
        if (bytes <= 0L) return
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putLong("hist_day_$dateStr", bytes).apply()
    }

    fun getHistoricalDayBytes(context: Context, year: Int, month: Int, day: Int): Long {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val dateStr = String.format(Locale.US, "%04d-%02d-%02d", year, month + 1, day)
        return prefs.getLong("hist_day_$dateStr", 0L)
    }
}
