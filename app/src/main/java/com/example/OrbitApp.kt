package com.example

import android.app.Application
import com.example.ocr.OcrManager

/**
 * Application subclass for Orbit.
 *
 * Sole purpose right now: initialize the [OcrManager] singleton with the application
 * context as early as possible so that the model download status is checked early.
 *
 * Registered in AndroidManifest.xml via `android:name=".OrbitApp"` on `<application>`.
 */
class OrbitApp : Application() {
    override fun onCreate() {
        super.onCreate()
        // Hand the application context to OcrManager so it can initialize.
        OcrManager.setApplicationContext(this)
    }
}
