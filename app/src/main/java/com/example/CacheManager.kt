package com.example

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.webkit.WebView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * CacheManager for Orbit.
 *
 * Responsibilities:
 * 1. Monitor total application cache size (internal cacheDir, externalCacheDir, and WebView disk caches).
 * 2. If the cache reaches or exceeds the threshold (default: 100 MB), automatically purge temporary files
 *    and invoke WebView.clearCache(true) without touching user data (Room database, SharedPreferences, Vault, etc.).
 * 3. Provide convenience functions to inspect cache size (in MB / formatted String) for UI / Settings.
 * 4. Log detailed timestamps and freed space metrics whenever a clear operation occurs.
 */
object CacheManager {

    private const val TAG = "OrbitCacheManager"

    /**
     * Default threshold in Megabytes (100 MB) as requested.
     */
    const val DEFAULT_THRESHOLD_MB = 100.0

    private val scope = CoroutineScope(Dispatchers.IO)

    /**
     * Calculates the total cache size in bytes.
     * Includes context.cacheDir and all externalCacheDirs.
     * Completely avoids user databases, shared preferences, or filesDir.
     */
    fun getCacheSizeBytes(context: Context): Long {
        var totalBytes = 0L
        try {
            val appContext = context.applicationContext
            appContext.cacheDir?.let { totalBytes += getFolderSizeBytes(it) }
            appContext.externalCacheDir?.let { totalBytes += getFolderSizeBytes(it) }
            appContext.externalCacheDirs?.forEach { dir ->
                if (dir != null && dir != appContext.externalCacheDir) {
                    totalBytes += getFolderSizeBytes(dir)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error measuring cache size", e)
        }
        return totalBytes
    }

    /**
     * Returns the current cache size in Megabytes (MB).
     */
    fun getCacheSizeMb(context: Context): Double {
        val bytes = getCacheSizeBytes(context)
        return bytes / (1024.0 * 1024.0)
    }

    /**
     * Returns a human-readable formatted string of the current cache size (e.g. "12.4 MB" or "320 KB").
     * Ideal for displaying in the Settings screen.
     */
    fun getFormattedCacheSize(context: Context): String {
        val mb = getCacheSizeMb(context)
        return if (mb < 1.0) {
            val kb = getCacheSizeBytes(context) / 1024.0
            String.format(Locale.US, "%.1f KB", kb)
        } else {
            String.format(Locale.US, "%.1f MB", mb)
        }
    }

    /**
     * Asynchronously checks the cache size. If it exceeds [thresholdMb] (default 100 MB),
     * it automatically triggers a cache clear on a background thread and logs the outcome.
     *
     * Safe to call anywhere in the application lifecycle (e.g. OrbitApp.onCreate or MainActivity.onCreate).
     */
    fun checkAndAutoClearCache(
        context: Context,
        thresholdMb: Double = DEFAULT_THRESHOLD_MB,
        onComplete: ((clearedMb: Double) -> Unit)? = null
    ) {
        val appContext = context.applicationContext
        scope.launch {
            try {
                val currentSizeMb = getCacheSizeMb(appContext)
                if (currentSizeMb >= thresholdMb) {
                    Log.i(TAG, "[CacheManager] Cache size (${String.format(Locale.US, "%.2f", currentSizeMb)} MB) exceeds threshold (${thresholdMb} MB). Initiating auto-clear...")
                    val freedMb = performClear(appContext)
                    withContext(Dispatchers.Main) {
                        onComplete?.invoke(freedMb)
                    }
                } else {
                    Log.d(TAG, "[CacheManager] Cache size is ${String.format(Locale.US, "%.2f", currentSizeMb)} MB (below ${thresholdMb} MB threshold). No action needed.")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed during checkAndAutoClearCache", e)
            }
        }
    }

    /**
     * Manually forces a complete cache clear on demand (e.g. user taps "Clear Cache" in Settings).
     */
    fun clearCacheNow(
        context: Context,
        onComplete: ((clearedMb: Double) -> Unit)? = null
    ) {
        val appContext = context.applicationContext
        scope.launch {
            try {
                val freedMb = performClear(appContext)
                withContext(Dispatchers.Main) {
                    onComplete?.invoke(freedMb)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed during clearCacheNow", e)
            }
        }
    }

    /**
     * Core routine that performs the clearing operation.
     * Must be called from a worker thread.
     */
    private suspend fun performClear(context: Context): Double {
        val sizeBeforeMb = getCacheSizeMb(context)
        val startTime = System.currentTimeMillis()

        // 1. Clear WebView cache via WebView instance on the Main Looper
        withContext(Dispatchers.Main) {
            try {
                val tempWebView = WebView(context)
                tempWebView.clearCache(true)
                tempWebView.destroy()
            } catch (t: Throwable) {
                Log.w(TAG, "WebView.clearCache encountered an issue: ${t.message}")
            }
        }

        // 2. Clear contents of context.cacheDir
        context.cacheDir?.let { clearDirectoryContents(it) }

        // 3. Clear contents of context.externalCacheDir and secondary external cache dirs
        context.externalCacheDir?.let { clearDirectoryContents(it) }
        context.externalCacheDirs?.forEach { dir ->
            if (dir != null && dir != context.externalCacheDir) {
                clearDirectoryContents(dir)
            }
        }

        val sizeAfterMb = getCacheSizeMb(context)
        val freedMb = (sizeBeforeMb - sizeAfterMb).coerceAtLeast(0.0)
        val timeFormatted = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())

        Log.i(
            TAG,
            "✅ [CacheManager Auto-Clear] Completed at $timeFormatted in ${System.currentTimeMillis() - startTime}ms. " +
                "Freed: ${String.format(Locale.US, "%.2f", freedMb)} MB " +
                "(Before: ${String.format(Locale.US, "%.2f", sizeBeforeMb)} MB, After: ${String.format(Locale.US, "%.2f", sizeAfterMb)} MB)"
        )

        return freedMb
    }

    /**
     * Recursively computes the byte size of a file or directory.
     */
    private fun getFolderSizeBytes(file: File?): Long {
        if (file == null || !file.exists()) return 0L
        var length = 0L
        if (file.isDirectory) {
            file.listFiles()?.forEach { child ->
                length += getFolderSizeBytes(child)
            }
        } else if (file.isFile) {
            length += file.length()
        }
        return length
    }

    /**
     * Recursively deletes all files and folders inside a given directory,
     * leaving the parent directory itself intact so system directory handles remain valid.
     */
    private fun clearDirectoryContents(dir: File?) {
        if (dir == null || !dir.exists() || !dir.isDirectory) return
        dir.listFiles()?.forEach { child ->
            deleteRecursive(child)
        }
    }

    private fun deleteRecursive(fileOrDir: File): Boolean {
        if (fileOrDir.isDirectory) {
            fileOrDir.listFiles()?.forEach { child ->
                deleteRecursive(child)
            }
        }
        return fileOrDir.delete()
    }
}
