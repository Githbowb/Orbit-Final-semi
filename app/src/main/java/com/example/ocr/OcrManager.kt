package com.example.ocr

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import com.google.android.gms.tasks.Tasks
import com.google.mlkit.common.sdkinternal.MlKitContext
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import com.google.android.gms.common.moduleinstall.ModuleInstall
import com.google.android.gms.common.moduleinstall.ModuleInstallRequest
import com.google.android.gms.common.moduleinstall.InstallStatusListener
import com.google.android.gms.common.moduleinstall.ModuleInstallStatusUpdate

/**
 * Singleton wrapper around Google ML Kit Text Recognition.
 *
 * This implementation uses the on-device Latin-script OCR engine.
 */
object OcrManager {

    private const val TAG = "OcrManager"

    private val _isModelDownloaded = MutableStateFlow(false)
    val isModelDownloaded: StateFlow<Boolean> = _isModelDownloaded.asStateFlow()

    private val _isDownloading = MutableStateFlow(false)
    val isDownloading: StateFlow<Boolean> = _isDownloading.asStateFlow()

    @Volatile
    private var appContext: Context? = null

    /**
     * Stores the application context and checks the model status.
     */
    fun setApplicationContext(context: Context) {
        appContext = context.applicationContext
        checkModelStatus()
    }

    /**
     * Checks if the OCR model is already available on the device, or downloads it.
     */
    fun checkModelStatus() {
        val context = appContext ?: return
        try {
            // First initialize MlKit if needed
            MlKitContext.initializeIfNeeded(context)
            
            val client = ModuleInstall.getClient(context)
            val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
            
            client.areModulesAvailable(recognizer)
                .addOnSuccessListener { response ->
                    if (response.areModulesAvailable()) {
                        Log.d(TAG, "ML Kit OCR model is already available on device")
                        _isModelDownloaded.value = true
                        _isDownloading.value = false
                    } else {
                        Log.d(TAG, "ML Kit OCR model is NOT available; triggering download")
                        _isModelDownloaded.value = false
                        downloadModel()
                    }
                }
                .addOnFailureListener { e ->
                    Log.e(TAG, "Failed to check ML Kit OCR model availability", e)
                    downloadModel()
                }
        } catch (e: Exception) {
            Log.e(TAG, "Exception during checkModelStatus", e)
        }
    }

    /**
     * Requests Google Play Services to download the OCR model.
     */
    fun downloadModel() {
        val context = appContext ?: return
        if (_isDownloading.value) return
        _isDownloading.value = true
        
        try {
            val client = ModuleInstall.getClient(context)
            val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
            
            val request = ModuleInstallRequest.newBuilder()
                .addApi(recognizer)
                .setListener(InstallStatusListener { event ->
                    val state = event.installState
                    Log.d(TAG, "Model download progress state update: $state")
                    when (state) {
                        ModuleInstallStatusUpdate.InstallState.STATE_COMPLETED -> {
                            Log.i(TAG, "ML Kit OCR model downloaded successfully")
                            _isModelDownloaded.value = true
                            _isDownloading.value = false
                        }
                        ModuleInstallStatusUpdate.InstallState.STATE_FAILED,
                        ModuleInstallStatusUpdate.InstallState.STATE_CANCELED -> {
                            Log.e(TAG, "ML Kit OCR model download failed or canceled")
                            _isModelDownloaded.value = false
                            _isDownloading.value = false
                        }
                    }
                })
                .build()

            client.installModules(request)
                .addOnSuccessListener { response ->
                    if (response.areModulesAlreadyInstalled()) {
                        Log.i(TAG, "Modules already installed (success listener)")
                        _isModelDownloaded.value = true
                        _isDownloading.value = false
                    } else {
                        Log.d(TAG, "Modules installation started successfully")
                    }
                }
                .addOnFailureListener { e ->
                    Log.e(TAG, "Failed to start modules installation", e)
                    _isDownloading.value = false
                }
        } catch (e: Exception) {
            Log.e(TAG, "Exception in downloadModel", e)
            _isDownloading.value = false
        }
    }

    /**
     * Public entry point. Runs OCR on [bitmap] and returns the recognized text.
     */
    suspend fun recognize(bitmap: Bitmap): String = withContext(Dispatchers.IO) {
        Log.d(TAG, "recognize() called")
        if (bitmap.isRecycled) {
            Log.e(TAG, "OCR received an already RECYCLED bitmap!")
            return@withContext ""
        }

        if (bitmap.width < 4 || bitmap.height < 4) {
            Log.w(TAG, "Bitmap too small for OCR, skipping")
            return@withContext ""
        }

        try {
            val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
            val inputImage = InputImage.fromBitmap(bitmap, 0)
            
            val result = Tasks.await(recognizer.process(inputImage))
            val text = result.text ?: ""
            Log.d(TAG, "OCR recognition complete. Length: ${text.length}")
            text.trim()
        } catch (e: Exception) {
            Log.e(TAG, "ML Kit OCR recognition failed", e)
            ""
        }
    }
}
