package com.example

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.app.DownloadManager
import android.content.ClipData
import android.content.ClipboardManager
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.DownloadListener
import android.webkit.JavascriptInterface
import android.webkit.MimeTypeMap
import android.webkit.PermissionRequest
import android.webkit.URLUtil
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import java.io.File
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.browser.customtabs.CustomTabsIntent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.ui.theme.TextSecondary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun FloatingWebViewContent(
    initialUrl: String = "",
    accentColor: Color,
    isMaximized: Boolean = false,
    onToggleMaximize: (() -> Unit)? = null,
    onClose: (() -> Unit)? = null,
    onOpenExternal: ((String) -> Unit)? = null,
    onUrlNavigated: ((String) -> Unit)? = null
) {
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current
    val defaultHomeUrl = remember(context) { SearchEnginePreferences.getSelectedEngine(context).homeUrl }

    // Retrieve last visited URL from persistent store if initialUrl is blank
    val startingUrl = remember(initialUrl) {
        if (initialUrl.isNotBlank()) {
            initialUrl
        } else {
            BrowserStateManager.getLastVisitedUrl(context)
        }
    }

    var currentUrl by rememberSaveable { mutableStateOf(startingUrl) }
    var inputUrl by rememberSaveable { mutableStateOf(startingUrl) }
    var pageTitle by rememberSaveable { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }
    var loadingProgress by remember { mutableStateOf(0) }
    var canGoBack by remember { mutableStateOf(false) }
    var canGoForward by remember { mutableStateOf(false) }
    var isDesktopMode by rememberSaveable { mutableStateOf(BrowserStateManager.getDesktopMode(context)) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var isAddressBarFocused by remember { mutableStateOf(false) }

    var webViewInstance by remember { mutableStateOf<WebView?>(null) }

    // Native Web file upload & microphone permissions for in-page actions (ChatGPT, DuckDuckGo, etc.)
    var fileChooserCallback by remember { mutableStateOf<ValueCallback<Array<Uri>>?>(null) }
    var pendingWebPermissionRequest by remember { mutableStateOf<PermissionRequest?>(null) }
    var hoveredLink by remember { mutableStateOf<String?>(null) }
    var showEngineMenu by remember { mutableStateOf(false) }

    // AI & Web media downloader states
    val coroutineScope = rememberCoroutineScope()
    var lastTouchX by remember { mutableFloatStateOf(0f) }
    var lastTouchY by remember { mutableFloatStateOf(0f) }

    // Generic file chooser launcher for <input type="file"> on web pages
    val fileChooserLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val intentData = result.data
            val uris: Array<Uri>? = when {
                intentData?.clipData != null -> {
                    val count = intentData.clipData!!.itemCount
                    (0 until count).map { intentData.clipData!!.getItemAt(it).uri }.toTypedArray()
                }
                intentData?.data != null -> arrayOf(intentData.data!!)
                else -> null
            }
            fileChooserCallback?.onReceiveValue(uris)
        } else {
            fileChooserCallback?.onReceiveValue(null)
        }
        fileChooserCallback = null
    }

    // Audio recording runtime permission launcher for in-page WebRTC voice chat (e.g. ChatGPT Voice)
    val audioPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            pendingWebPermissionRequest?.grant(pendingWebPermissionRequest?.resources)
        } else {
            pendingWebPermissionRequest?.deny()
        }
        pendingWebPermissionRequest = null
    }

    fun normalizeUrl(input: String): String {
        val trimmed = input.trim()
        val defaultEngine = SearchEnginePreferences.getSelectedEngine(context)
        if (trimmed.isEmpty()) return defaultEngine.homeUrl
        if (URLUtil.isValidUrl(trimmed) && (trimmed.startsWith("http://") || trimmed.startsWith("https://"))) {
            return trimmed
        }
        if (trimmed.contains(".") && !trimmed.contains(" ") && !trimmed.startsWith("http")) {
            return "https://$trimmed"
        }
        val encodedQuery = Uri.encode(trimmed)
        return defaultEngine.searchUrlTemplate.replace("%s", encodedQuery)
    }

    fun navigateTo(url: String) {
        val target = normalizeUrl(url)
        currentUrl = target
        inputUrl = target
        errorMessage = null
        BrowserStateManager.saveLastVisitedUrl(context, target)
        onUrlNavigated?.invoke(target)
        webViewInstance?.loadUrl(target)
        focusManager.clearFocus()
    }

    LaunchedEffect(initialUrl) {
        if (initialUrl.isNotBlank() && initialUrl != currentUrl) {
            navigateTo(initialUrl)
        }
    }

    // Auto-refresh to the new engine's homepage whenever the user selects a new search engine
    val engineChangeTrigger by SearchEnginePreferences.engineChanges.collectAsState()
    LaunchedEffect(engineChangeTrigger) {
        if (engineChangeTrigger > 0L) {
            val newHome = SearchEnginePreferences.getSelectedEngine(context).homeUrl
            currentUrl = newHome
            inputUrl = newHome
            errorMessage = null
            onUrlNavigated?.invoke(newHome)
            webViewInstance?.loadUrl(newHome)
        }
    }

    fun openExternally(targetUrl: String) {
        val formattedUrl = normalizeUrl(targetUrl)
        if (onOpenExternal != null) {
            onOpenExternal(formattedUrl)
        } else {
            try {
                val uri = Uri.parse(formattedUrl)
                val customTabsIntent = CustomTabsIntent.Builder()
                    .setShowTitle(true)
                    .build()
                customTabsIntent.launchUrl(context, uri)
            } catch (e: Exception) {
                try {
                    val fallbackIntent = Intent(Intent.ACTION_VIEW, Uri.parse(formattedUrl)).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    context.startActivity(fallbackIntent)
                } catch (ex: Exception) {
                    Toast.makeText(context, "No browser found to open link.", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 2.dp, vertical = 2.dp)
    ) {
        // Loading Progress Indicator
        val animatedProgress by animateFloatAsState(
            targetValue = loadingProgress / 100f,
            label = "WebViewProgress"
        )
        AnimatedVisibility(
            visible = isLoading && animatedProgress < 1f,
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            LinearProgressIndicator(
                progress = { animatedProgress },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(2.5.dp)
                    .padding(bottom = 2.dp),
                color = accentColor,
                trackColor = Color.Transparent
            )
        }

        // Top Address & Search Bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 4.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(Color(0xFF111422))
                .border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(10.dp))
                .padding(horizontal = 8.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.Search,
                contentDescription = "Search",
                tint = accentColor,
                modifier = Modifier.size(15.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            BasicTextField(
                value = inputUrl,
                onValueChange = { inputUrl = it },
                singleLine = true,
                textStyle = LocalTextStyle.current.copy(
                    color = Color.White,
                    fontSize = 12.sp
                ),
                keyboardOptions = KeyboardOptions(
                    imeAction = ImeAction.Go,
                    keyboardType = KeyboardType.Uri
                ),
                keyboardActions = KeyboardActions(
                    onGo = { navigateTo(inputUrl) }
                ),
                modifier = Modifier
                    .weight(1f)
                    .padding(vertical = 5.dp)
            )
            if (inputUrl.isNotBlank()) {
                IconButton(
                    onClick = { inputUrl = "" },
                    modifier = Modifier.size(24.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Clear",
                        tint = TextSecondary.copy(alpha = 0.6f),
                        modifier = Modifier.size(14.dp)
                    )
                }
            }
            if (onToggleMaximize != null) {
                Spacer(modifier = Modifier.width(2.dp))
                IconButton(
                    onClick = { onToggleMaximize() },
                    modifier = Modifier.size(26.dp)
                ) {
                    Icon(
                        imageVector = if (isMaximized) Icons.Default.FullscreenExit else Icons.Default.Fullscreen,
                        contentDescription = if (isMaximized) stringResource(R.string.overlay_restore) else stringResource(R.string.overlay_maximize),
                        tint = accentColor,
                        modifier = Modifier.size(17.dp)
                    )
                }
            }
            if (onClose != null) {
                Spacer(modifier = Modifier.width(2.dp))
                IconButton(
                    onClick = { onClose() },
                    modifier = Modifier.size(26.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = stringResource(R.string.overlay_close),
                        tint = Color.White.copy(alpha = 0.85f),
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }

        // Main WebView Viewport Area
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(Color(0xFF0C0F1A))
                .border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(14.dp))
        ) {
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { ctx ->
                    val existingWebView = BrowserStateManager.getRetainedWebView()
                    val webView = if (existingWebView != null) {
                        BrowserStateManager.detachFromParent(existingWebView)
                        existingWebView
                    } else {
                        WebView(ctx).apply {
                            layoutParams = ViewGroup.LayoutParams(
                                ViewGroup.LayoutParams.MATCH_PARENT,
                                ViewGroup.LayoutParams.MATCH_PARENT
                            )
                            setBackgroundColor(android.graphics.Color.TRANSPARENT)
                            isFocusable = true
                            isFocusableInTouchMode = true

                            settings.apply {
                                javaScriptEnabled = true
                                domStorageEnabled = true
                                databaseEnabled = true
                                allowFileAccess = true
                                allowContentAccess = true
                                setSupportZoom(true)
                                builtInZoomControls = true
                                displayZoomControls = false
                                useWideViewPort = true
                                loadWithOverviewMode = true
                                layoutAlgorithm = WebSettings.LayoutAlgorithm.TEXT_AUTOSIZING
                                defaultTextEncodingName = "utf-8"
                                cacheMode = WebSettings.LOAD_DEFAULT
                                mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
                                setSupportMultipleWindows(true)
                                javaScriptCanOpenWindowsAutomatically = true
                            }
                            BrowserStateManager.setRetainedWebView(this)
                        }
                    }

                    // Enable JavaScript interface for blob downloads and AI picture extraction
                    webView.addJavascriptInterface(
                        AndroidDownloadBridge(
                            onBlobReceived = { dataUrl, mimeType, suggestedFilename ->
                                saveDataUrlToDownloads(ctx, dataUrl, mimeType, suggestedFilename)
                            },
                            onInterceptDownload = { url, filename ->
                                (ctx as? Activity)?.runOnUiThread {
                                    val clean = url.substringBefore('?').lowercase()
                                    if (url.startsWith("data:") || url.startsWith("blob:") ||
                                        clean.endsWith(".png") || clean.endsWith(".jpg") || clean.endsWith(".jpeg") ||
                                        clean.endsWith(".webp") || clean.endsWith(".gif") || clean.endsWith(".svg") ||
                                        clean.endsWith(".bmp")
                                    ) {
                                        downloadAndSaveImage(ctx, webView, url, filename)
                                    } else {
                                        downloadHttpFile(ctx, url, webView.settings.userAgentString, null, null)
                                    }
                                }
                            },
                            onImageDetectedAtPoint = { json ->
                                (ctx as? Activity)?.runOnUiThread {
                                    try {
                                        val obj = JSONObject(json)
                                        val url = obj.getString("url")
                                        val alt = obj.optString("alt", "")
                                        downloadAndSaveImage(ctx, webView, url, alt)
                                    } catch (e: Exception) {
                                        e.printStackTrace()
                                    }
                                }
                            },
                            onFallbackHttpDownload = { url, filename ->
                                coroutineScope.launch {
                                    val ok = downloadHttpImageOkHttp(ctx, url, webView.settings.userAgentString, webView.url, filename)
                                    if (!ok) {
                                        downloadHttpFile(ctx, url, webView.settings.userAgentString, null, "image/*")
                                    }
                                }
                            }
                        ),
                        "AndroidDownloadBridge"
                    )

                    // Download Listener for direct downloads and Content-Disposition headers
                    webView.setDownloadListener { url, userAgent, contentDisposition, mimetype, _ ->
                        if (url.isNullOrBlank()) return@setDownloadListener
                        downloadAndSaveImage(ctx, webView, url, mimetype = mimetype)
                    }

                    webView.isFocusable = true
                    webView.isFocusableInTouchMode = true
                    webView.setOnTouchListener { v, event ->
                        if (event.action == android.view.MotionEvent.ACTION_DOWN) {
                            lastTouchX = event.x
                            lastTouchY = event.y
                        }
                        if (!v.hasFocus()) {
                            v.requestFocus()
                        }
                        false
                    }
                    webView.setOnHoverListener { v, _ ->
                        val hitResult = (v as? WebView)?.hitTestResult
                        val link = when (hitResult?.type) {
                            WebView.HitTestResult.SRC_ANCHOR_TYPE,
                            WebView.HitTestResult.SRC_IMAGE_ANCHOR_TYPE -> hitResult.extra
                            else -> null
                        }
                        hoveredLink = link
                        false
                    }

                    // Configure Clients
                    webView.webViewClient = object : WebViewClient() {
                        override fun shouldOverrideUrlLoading(
                            view: WebView?,
                            request: WebResourceRequest?
                        ): Boolean {
                            val url = request?.url?.toString() ?: return false
                            if (url.startsWith("http://") || url.startsWith("https://")) {
                                val cleanUrl = url.substringBefore('?').lowercase()
                                if (cleanUrl.endsWith(".apk") || cleanUrl.endsWith(".zip") || cleanUrl.endsWith(".pdf") ||
                                    cleanUrl.endsWith(".rar") || cleanUrl.endsWith(".7z") || cleanUrl.endsWith(".tar.gz") ||
                                    cleanUrl.endsWith(".iso") || cleanUrl.endsWith(".dmg") || cleanUrl.endsWith(".bin") ||
                                    cleanUrl.endsWith(".epub") || cleanUrl.endsWith(".csv") || cleanUrl.endsWith(".mp3") ||
                                    cleanUrl.endsWith(".mp4") || cleanUrl.endsWith(".docx") || cleanUrl.endsWith(".xlsx")
                                ) {
                                    downloadHttpFile(ctx, url, view?.settings?.userAgentString, null, null)
                                    return true
                                }
                                if (cleanUrl.endsWith(".png") || cleanUrl.endsWith(".jpg") || cleanUrl.endsWith(".jpeg") ||
                                    cleanUrl.endsWith(".webp") || cleanUrl.endsWith(".gif") || cleanUrl.endsWith(".svg")
                                ) {
                                    downloadAndSaveImage(ctx, webView, url)
                                    return true
                                }
                                return false
                            }
                            if (url.startsWith("blob:")) {
                                downloadAndSaveImage(ctx, webView, url)
                                return true
                            }
                            if (url.startsWith("data:")) {
                                saveDataUrlToDownloads(ctx, url, null)
                                return true
                            }
                            try {
                                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
                                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                }
                                ctx.startActivity(intent)
                            } catch (e: Exception) {
                                e.printStackTrace()
                            }
                            return true
                        }

                        override fun doUpdateVisitedHistory(view: WebView?, url: String?, isReload: Boolean) {
                            super.doUpdateVisitedHistory(view, url, isReload)
                            url?.let {
                                if (it.isNotBlank() && it != "about:blank") {
                                    currentUrl = it
                                    inputUrl = it
                                }
                            }
                        }

                        override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                            super.onPageStarted(view, url, favicon)
                            isLoading = true
                            errorMessage = null
                            url?.let {
                                if (it.isNotBlank() && it != "about:blank") {
                                    currentUrl = it
                                    inputUrl = it
                                    BrowserStateManager.saveLastVisitedUrl(ctx, it)
                                    onUrlNavigated?.invoke(it)
                                }
                            }
                            canGoBack = view?.canGoBack() == true
                            canGoForward = view?.canGoForward() == true
                            view?.evaluateJavascript(INJECTED_DOWNLOAD_HOOK, null)
                        }

                        override fun onPageFinished(view: WebView?, url: String?) {
                            super.onPageFinished(view, url)
                            isLoading = false
                            url?.let {
                                if (it.isNotBlank() && it != "about:blank") {
                                    currentUrl = it
                                    inputUrl = it
                                    BrowserStateManager.saveLastVisitedUrl(ctx, it)
                                    onUrlNavigated?.invoke(it)
                                }
                            }
                            pageTitle = view?.title ?: ""
                            canGoBack = view?.canGoBack() == true
                            canGoForward = view?.canGoForward() == true

                            // Persist full WebBackForwardList history into state bundle
                            BrowserStateManager.saveState(view)

                            view?.evaluateJavascript(
                                """
                                (function() {
                                    if (!document.querySelector('meta[name="viewport"]')) {
                                        var meta = document.createElement('meta');
                                        meta.name = 'viewport';
                                        meta.content = 'width=device-width, initial-scale=1.0, maximum-scale=5.0, user-scalable=yes';
                                        document.getElementsByTagName('head')[0].appendChild(meta);
                                    }
                                })();
                                """.trimIndent(),
                                null
                            )
                            view?.evaluateJavascript(INJECTED_DOWNLOAD_HOOK, null)
                        }

                        override fun onReceivedError(
                            view: WebView?,
                            request: WebResourceRequest?,
                            error: WebResourceError?
                        ) {
                            super.onReceivedError(view, request, error)
                            if (request?.isForMainFrame == true) {
                                isLoading = false
                                errorMessage = error?.description?.toString() ?: "Network error"
                            }
                        }
                    }

                    webView.webChromeClient = object : WebChromeClient() {
                        override fun onProgressChanged(view: WebView?, newProgress: Int) {
                            super.onProgressChanged(view, newProgress)
                            loadingProgress = newProgress
                            if (newProgress == 100) {
                                isLoading = false
                            }
                            canGoBack = view?.canGoBack() == true
                            canGoForward = view?.canGoForward() == true
                            if (newProgress >= 25) {
                                view?.evaluateJavascript(INJECTED_DOWNLOAD_HOOK, null)
                            }
                        }

                        override fun onReceivedTitle(view: WebView?, title: String?) {
                            super.onReceivedTitle(view, title)
                            if (!title.isNullOrBlank()) {
                                pageTitle = title
                            }
                        }

                        override fun onShowFileChooser(
                            view: WebView?,
                            filePathCallback: ValueCallback<Array<Uri>>?,
                            fileChooserParams: FileChooserParams?
                        ): Boolean {
                            fileChooserCallback?.onReceiveValue(null)
                            fileChooserCallback = filePathCallback

                            val isMultiple = fileChooserParams?.mode == WebChromeClient.FileChooserParams.MODE_OPEN_MULTIPLE
                            val acceptTypes = fileChooserParams?.acceptTypes?.filter { it.isNotBlank() } ?: emptyList()
                            val isImageUpload = acceptTypes.isEmpty() ||
                                acceptTypes.any { it.contains("image", ignoreCase = true) || it == "*/*" }

                            val intent = if (isImageUpload) {
                                // Open Gallery instead of file explorer
                                Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI).apply {
                                    type = "image/*"
                                    if (isMultiple) {
                                        putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
                                    }
                                }
                            } else {
                                try {
                                    fileChooserParams?.createIntent() ?: Intent(Intent.ACTION_GET_CONTENT).apply {
                                        type = "*/*"
                                        addCategory(Intent.CATEGORY_OPENABLE)
                                    }
                                } catch (e: Exception) {
                                    Intent(Intent.ACTION_GET_CONTENT).apply {
                                        type = "*/*"
                                        addCategory(Intent.CATEGORY_OPENABLE)
                                    }
                                }
                            }

                            try {
                                fileChooserLauncher.launch(intent)
                                return true
                            } catch (e: Exception) {
                                val fallback = if (isImageUpload) {
                                    Intent(Intent.ACTION_GET_CONTENT).apply {
                                        type = "image/*"
                                        addCategory(Intent.CATEGORY_OPENABLE)
                                        if (isMultiple) {
                                            putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
                                        }
                                    }
                                } else {
                                    Intent(Intent.ACTION_GET_CONTENT).apply {
                                        type = "*/*"
                                        addCategory(Intent.CATEGORY_OPENABLE)
                                    }
                                }
                                try {
                                    fileChooserLauncher.launch(fallback)
                                    return true
                                } catch (ex: Exception) {
                                    fileChooserCallback?.onReceiveValue(null)
                                    fileChooserCallback = null
                                    return false
                                }
                            }
                        }

                        override fun onPermissionRequest(request: PermissionRequest?) {
                            if (request == null) return
                            val resources = request.resources
                            if (resources.contains(PermissionRequest.RESOURCE_AUDIO_CAPTURE)) {
                                if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
                                    request.grant(resources)
                                } else {
                                    pendingWebPermissionRequest = request
                                    audioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                                }
                            } else {
                                request.grant(resources)
                            }
                        }

                        override fun onCreateWindow(
                            view: WebView?,
                            isDialog: Boolean,
                            isUserGesture: Boolean,
                            resultMsg: android.os.Message?
                        ): Boolean {
                            if (resultMsg == null) return false
                            val transport = resultMsg.obj as? WebView.WebViewTransport ?: return false
                            val tempWebView = WebView(view?.context ?: return false).apply {
                                webViewClient = object : WebViewClient() {
                                    override fun shouldOverrideUrlLoading(
                                        v: WebView?,
                                        request: WebResourceRequest?
                                    ): Boolean {
                                        val targetUrl = request?.url?.toString() ?: return false
                                        val clean = targetUrl.substringBefore('?').lowercase()
                                        if (targetUrl.startsWith("blob:") || targetUrl.startsWith("data:") ||
                                            clean.endsWith(".png") || clean.endsWith(".jpg") || clean.endsWith(".jpeg") ||
                                            clean.endsWith(".webp") || clean.endsWith(".gif") || clean.endsWith(".svg") ||
                                            clean.endsWith(".zip") || clean.endsWith(".pdf") || clean.endsWith(".apk")
                                        ) {
                                            downloadAndSaveImage(ctx, webView, targetUrl)
                                            return true
                                        }
                                        view?.loadUrl(targetUrl)
                                        return true
                                    }
                                }
                            }
                            transport.webView = tempWebView
                            resultMsg.sendToTarget()
                            return true
                        }
                    }

                    webView.setOnLongClickListener {
                        val hitResult = webView.hitTestResult
                        val target = hitResult.extra
                        when (hitResult.type) {
                            WebView.HitTestResult.IMAGE_TYPE,
                            WebView.HitTestResult.SRC_IMAGE_ANCHOR_TYPE -> {
                                if (!target.isNullOrBlank()) {
                                    Toast.makeText(ctx, "Saving picture to Gallery...", Toast.LENGTH_SHORT).show()
                                    downloadAndSaveImage(ctx, webView, target)
                                    true
                                } else false
                            }
                            WebView.HitTestResult.SRC_ANCHOR_TYPE -> {
                                if (!target.isNullOrBlank()) {
                                    val cleanTarget = target.substringBefore('?').lowercase()
                                    if (cleanTarget.endsWith(".apk") || cleanTarget.endsWith(".zip") || cleanTarget.endsWith(".pdf") ||
                                        cleanTarget.endsWith(".rar") || cleanTarget.endsWith(".7z") || cleanTarget.endsWith(".tar.gz") ||
                                        cleanTarget.endsWith(".iso") || cleanTarget.endsWith(".dmg") || cleanTarget.endsWith(".bin") ||
                                        cleanTarget.endsWith(".epub") || cleanTarget.endsWith(".csv") || cleanTarget.endsWith(".mp3") ||
                                        cleanTarget.endsWith(".mp4") || cleanTarget.endsWith(".docx") || cleanTarget.endsWith(".xlsx")
                                    ) {
                                        downloadHttpFile(ctx, target, webView.settings.userAgentString, null, null)
                                    } else if (cleanTarget.endsWith(".png") || cleanTarget.endsWith(".jpg") || cleanTarget.endsWith(".jpeg") ||
                                        cleanTarget.endsWith(".webp") || cleanTarget.endsWith(".gif") || cleanTarget.endsWith(".svg")
                                    ) {
                                        Toast.makeText(ctx, "Saving picture to Gallery...", Toast.LENGTH_SHORT).show()
                                        downloadAndSaveImage(ctx, webView, target)
                                    } else {
                                        openExternally(target)
                                    }
                                    true
                                } else false
                            }
                            else -> {
                                // For complex AI generation pages (Midjourney, ChatGPT, Copilot, Leonardo, Canvas renders)
                                val density = ctx.resources.displayMetrics.density
                                val cssX = (lastTouchX / density).toInt()
                                val cssY = (lastTouchY / density).toInt()
                                val js = """
                                    (function(x, y) {
                                        function findNode(n) {
                                            if (!n) return null;
                                            if (n.tagName === 'IMG' && (n.currentSrc || n.src)) {
                                                return { url: n.currentSrc || n.src, width: n.naturalWidth || 0, height: n.naturalHeight || 0, alt: n.alt || 'AI Picture', type: 'img' };
                                            }
                                            if (n.tagName === 'CANVAS') {
                                                try {
                                                    return { url: n.toDataURL('image/png'), width: n.width || 0, height: n.height || 0, alt: 'Canvas Picture', type: 'canvas' };
                                                } catch(e) {}
                                            }
                                            var img = n.querySelector && n.querySelector('img');
                                            if (img && (img.currentSrc || img.src)) {
                                                return { url: img.currentSrc || img.src, width: img.naturalWidth || 0, height: img.naturalHeight || 0, alt: img.alt || 'AI Picture', type: 'img' };
                                            }
                                            var cv = n.querySelector && n.querySelector('canvas');
                                            if (cv) {
                                                try {
                                                    return { url: cv.toDataURL('image/png'), width: cv.width || 0, height: cv.height || 0, alt: 'Canvas Picture', type: 'canvas' };
                                                } catch(e) {}
                                            }
                                            var p = n.parentElement;
                                            for (var i = 0; i < 5 && p; i++) {
                                                if (p.tagName === 'IMG' && (p.currentSrc || p.src)) {
                                                    return { url: p.currentSrc || p.src, width: p.naturalWidth || 0, height: p.naturalHeight || 0, alt: p.alt || 'AI Picture', type: 'img' };
                                                }
                                                var pImg = p.querySelector && p.querySelector('img');
                                                if (pImg && (pImg.currentSrc || pImg.src)) {
                                                    return { url: pImg.currentSrc || pImg.src, width: pImg.naturalWidth || 0, height: pImg.naturalHeight || 0, alt: pImg.alt || 'AI Picture', type: 'img' };
                                                }
                                                p = p.parentElement;
                                            }
                                            return null;
                                        }
                                        var el = document.elementFromPoint(x, y);
                                        var item = findNode(el);
                                        if (item && window.AndroidDownloadBridge) {
                                            window.AndroidDownloadBridge.onImageDetectedAtPoint(JSON.stringify(item));
                                        }
                                    })($cssX, $cssY);
                                """.trimIndent()
                                webView.evaluateJavascript(js, null)
                                true
                            }
                        }
                    }

                    // Restore or initialize URL/State
                    val savedBundle = BrowserStateManager.getSavedBundle()
                    if (initialUrl.isNotBlank() && initialUrl != webView.url) {
                        val target = normalizeUrl(initialUrl)
                        currentUrl = target
                        inputUrl = target
                        webView.loadUrl(target)
                    } else if (webView.url.isNullOrBlank() || webView.url == "about:blank") {
                        if (savedBundle != null) {
                            webView.restoreState(savedBundle)
                        } else {
                            val startUrl = BrowserStateManager.getLastVisitedUrl(ctx)
                            currentUrl = startUrl
                            inputUrl = startUrl
                            webView.loadUrl(normalizeUrl(startUrl))
                        }
                    } else {
                        // Already active and retained!
                        currentUrl = webView.url ?: BrowserStateManager.getLastVisitedUrl(ctx)
                        inputUrl = currentUrl
                        pageTitle = webView.title ?: ""
                        canGoBack = webView.canGoBack()
                        canGoForward = webView.canGoForward()
                    }

                    BrowserStateManager.resumeWebView()
                    webViewInstance = webView
                    webView
                },
                update = { webView ->
                    webViewInstance = webView
                    val targetUserAgent = if (isDesktopMode) {
                        "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"
                    } else {
                        WebSettings.getDefaultUserAgent(context)
                    }
                    if (webView.settings.userAgentString != targetUserAgent) {
                        webView.settings.userAgentString = targetUserAgent
                        webView.reload()
                    }
                }
            )

            // Offline / Error Overlay
            if (errorMessage != null) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color(0xFF0F1017))
                        .padding(20.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.WifiOff,
                            contentDescription = "Offline",
                            tint = Color(0xFFFF5252),
                            modifier = Modifier.size(40.dp)
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = stringResource(id = R.string.browser_load_error),
                            color = Color.White,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = errorMessage ?: "Unable to load page.",
                            color = TextSecondary,
                            fontSize = 11.sp,
                            maxLines = 2
                        )
                        Spacer(modifier = Modifier.height(14.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(
                                onClick = {
                                    errorMessage = null
                                    webViewInstance?.reload()
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = accentColor),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Refresh,
                                    contentDescription = "Retry",
                                    tint = Color.Black,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(text = "Retry", color = Color.Black, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }

                            Button(
                                onClick = {
                                    val homeUrl = SearchEnginePreferences.getSelectedEngine(context).homeUrl
                                    errorMessage = null
                                    navigateTo(homeUrl)
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF222840)),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Home,
                                    contentDescription = "Home",
                                    tint = Color.White,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(text = "Home", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        // Bottom Navigation Bar Controls
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(36.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(Color(0xFF101322))
                .border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(12.dp))
                .padding(horizontal = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                // Home / Reset Button
                IconButton(
                    onClick = {
                        val homeUrl = SearchEnginePreferences.getSelectedEngine(context).homeUrl
                        errorMessage = null
                        currentUrl = homeUrl
                        inputUrl = homeUrl
                        BrowserStateManager.resetToHome(context, webViewInstance)
                        onUrlNavigated?.invoke(homeUrl)
                        Toast.makeText(context, context.getString(R.string.browser_reset_home), Toast.LENGTH_SHORT).show()
                    },
                    modifier = Modifier
                        .size(28.dp)
                        .clip(CircleShape)
                        .background(accentColor.copy(alpha = 0.12f))
                ) {
                    Icon(
                        imageVector = Icons.Default.Home,
                        contentDescription = stringResource(id = R.string.browser_home),
                        tint = accentColor,
                        modifier = Modifier.size(16.dp)
                    )
                }

                // Back Button
                IconButton(
                    onClick = { webViewInstance?.goBack() },
                    enabled = canGoBack,
                    modifier = Modifier.size(28.dp)
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = if (canGoBack) Color.White else TextSecondary.copy(alpha = 0.35f),
                        modifier = Modifier.size(16.dp)
                    )
                }

                // Forward Button
                IconButton(
                    onClick = { webViewInstance?.goForward() },
                    enabled = canGoForward,
                    modifier = Modifier.size(28.dp)
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                        contentDescription = "Forward",
                        tint = if (canGoForward) Color.White else TextSecondary.copy(alpha = 0.35f),
                        modifier = Modifier.size(16.dp)
                    )
                }

                // Refresh / Stop Button
                IconButton(
                    onClick = {
                        if (isLoading) webViewInstance?.stopLoading() else webViewInstance?.reload()
                    },
                    modifier = Modifier.size(28.dp)
                ) {
                    Icon(
                        imageVector = if (isLoading) Icons.Default.Close else Icons.Default.Refresh,
                        contentDescription = if (isLoading) "Stop" else "Refresh",
                        tint = accentColor,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }

            val standingOnUrl = hoveredLink?.takeIf { it.isNotBlank() } ?: currentUrl
            val currentEngine = remember(context, engineChangeTrigger) {
                SearchEnginePreferences.getSelectedEngine(context)
            }

            // Middle Status: Current link user is standing on / viewing
            Row(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 6.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(Color.White.copy(alpha = 0.05f))
                    .clickable {
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? android.content.ClipboardManager
                        val clip = android.content.ClipData.newPlainText("URL", standingOnUrl)
                        clipboard?.setPrimaryClip(clip)
                        Toast.makeText(context, "Copied: $standingOnUrl", Toast.LENGTH_SHORT).show()
                    }
                    .padding(horizontal = 6.dp, vertical = 3.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Link,
                    contentDescription = stringResource(id = R.string.browser_standing_on_link),
                    tint = accentColor.copy(alpha = 0.85f),
                    modifier = Modifier.size(12.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = standingOnUrl.ifBlank { "about:blank" },
                    color = TextSecondary.copy(alpha = 0.95f),
                    fontSize = 10.5.sp,
                    fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                // Small Search Engine Switcher Button with Dropdown Menu
                Box {
                    IconButton(
                        onClick = { showEngineMenu = true },
                        modifier = Modifier
                            .size(28.dp)
                            .clip(CircleShape)
                            .background(accentColor.copy(alpha = 0.15f))
                    ) {
                        Icon(
                            imageVector = Icons.Default.Language,
                            contentDescription = stringResource(id = R.string.browser_switch_engine),
                            tint = accentColor,
                            modifier = Modifier.size(15.dp)
                        )
                    }

                    DropdownMenu(
                        expanded = showEngineMenu,
                        onDismissRequest = { showEngineMenu = false },
                        modifier = Modifier
                            .background(Color(0xFF131728))
                            .border(1.dp, Color.White.copy(alpha = 0.1f), RoundedCornerShape(8.dp))
                    ) {
                        SearchEnginePreferences.ENGINES.forEach { engine ->
                            val isSelected = engine.id == currentEngine.id
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        text = engine.name,
                                        color = if (isSelected) accentColor else Color.White,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                        fontSize = 12.5.sp
                                    )
                                },
                                leadingIcon = {
                                    if (isSelected) {
                                        Icon(
                                            imageVector = Icons.Default.Check,
                                            contentDescription = null,
                                            tint = accentColor,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    } else {
                                        Spacer(modifier = Modifier.size(16.dp))
                                    }
                                },
                                onClick = {
                                    showEngineMenu = false
                                    SearchEnginePreferences.setSelectedEngine(context, engine.id)
                                    navigateTo(engine.homeUrl)
                                    Toast.makeText(context, "Engine: ${engine.name}", Toast.LENGTH_SHORT).show()
                                }
                            )
                        }
                    }
                }

                // Desktop Mode Toggle Button
                IconButton(
                    onClick = {
                        val nextMode = !isDesktopMode
                        isDesktopMode = nextMode
                        BrowserStateManager.setDesktopMode(context, nextMode)
                    },
                    modifier = Modifier
                        .size(28.dp)
                        .background(if (isDesktopMode) accentColor.copy(alpha = 0.25f) else Color.Transparent, CircleShape)
                ) {
                    Icon(
                        imageVector = if (isDesktopMode) Icons.Default.DesktopWindows else Icons.Default.PhoneAndroid,
                        contentDescription = "Toggle Desktop Mode",
                        tint = if (isDesktopMode) accentColor else Color.White.copy(alpha = 0.8f),
                        modifier = Modifier.size(15.dp)
                    )
                }

                // Downloads Folder Quick View
                IconButton(
                    onClick = {
                        try {
                            val intent = Intent(DownloadManager.ACTION_VIEW_DOWNLOADS).apply {
                                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            }
                            context.startActivity(intent)
                        } catch (e: Exception) {
                            Toast.makeText(context, "Downloads saved in Downloads folder", Toast.LENGTH_SHORT).show()
                        }
                    },
                    modifier = Modifier.size(28.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Download,
                        contentDescription = "Downloads Folder",
                        tint = accentColor,
                        modifier = Modifier.size(15.dp)
                    )
                }

                // Open in External Default Browser Button
                IconButton(
                    onClick = { openExternally(currentUrl) },
                    modifier = Modifier.size(28.dp)
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.OpenInNew,
                        contentDescription = "Open in Default Browser",
                        tint = accentColor,
                        modifier = Modifier.size(15.dp)
                    )
                }
            }
        }
    }

    // State Preservation on Composable disposal (Keep history and state alive!)
    DisposableEffect(Unit) {
        onDispose {
            webViewInstance?.let { wv ->
                BrowserStateManager.saveState(wv)
                wv.url?.let { url ->
                    if (url.isNotBlank() && url != "about:blank") {
                        BrowserStateManager.saveLastVisitedUrl(context, url)
                    }
                }
                BrowserStateManager.pauseWebView()
                BrowserStateManager.detachFromParent(wv)
            }
            webViewInstance = null
        }
    }
}

class AndroidDownloadBridge(
    private val onBlobReceived: (String, String, String) -> Unit,
    private val onInterceptDownload: (String, String) -> Unit,
    private val onImageDetectedAtPoint: (String) -> Unit,
    private val onFallbackHttpDownload: (String, String) -> Unit
) {
    @JavascriptInterface
    fun onBlobData(dataUrl: String, mimeType: String, suggestedFilename: String) {
        onBlobReceived(dataUrl, mimeType, suggestedFilename)
    }

    @JavascriptInterface
    fun onInterceptDownload(url: String, filename: String) {
        onInterceptDownload(url, filename)
    }

    @JavascriptInterface
    fun onImageDetected(url: String, filename: String) {
        onInterceptDownload(url, filename)
    }

    @JavascriptInterface
    fun onImageDetectedAtPoint(json: String) {
        onImageDetectedAtPoint(json)
    }

    @JavascriptInterface
    fun onFallbackHttpDownload(url: String, filename: String) {
        onFallbackHttpDownload(url, filename)
    }
}

const val INJECTED_DOWNLOAD_HOOK = """
(function() {
    if (window.__orbit_download_hooked) return;
    window.__orbit_download_hooked = true;

    // Prevent immediate revocation of blob URLs so async reader can process them
    var origRevoke = URL.revokeObjectURL;
    URL.revokeObjectURL = function(url) {
        setTimeout(function() {
            try { origRevoke(url); } catch(e) {}
        }, 60000);
    };

    function triggerNativeDownload(href, filename) {
        if (!href || !window.AndroidDownloadBridge) return;
        if (href.startsWith('data:')) {
            window.AndroidDownloadBridge.onBlobData(href, '', filename || '');
            return;
        }
        if (href.startsWith('blob:')) {
            fetch(href)
                .then(function(res) { return res.blob(); })
                .then(function(blob) {
                    var reader = new FileReader();
                    reader.onloadend = function() {
                        if (window.AndroidDownloadBridge) {
                            window.AndroidDownloadBridge.onBlobData(reader.result, blob.type || 'application/octet-stream', filename || '');
                        }
                    };
                    reader.readAsDataURL(blob);
                })
                .catch(function() {
                    if (window.AndroidDownloadBridge) {
                        window.AndroidDownloadBridge.onInterceptDownload(href, filename || '');
                    }
                });
            return;
        }
        window.AndroidDownloadBridge.onInterceptDownload(href, filename || '');
    }

    // Helper: Find image or canvas in or near element
    function extractMediaFromElement(el) {
        if (!el) return null;
        if (el.tagName === 'IMG' && (el.currentSrc || el.src)) {
            return { url: el.currentSrc || el.src, name: el.alt || 'image' };
        }
        if (el.tagName === 'CANVAS') {
            try {
                return { url: el.toDataURL('image/png'), name: 'canvas_image' };
            } catch(e) {}
        }
        var img = el.querySelector && el.querySelector('img');
        if (img && (img.currentSrc || img.src)) {
            return { url: img.currentSrc || img.src, name: img.alt || 'image' };
        }
        var cv = el.querySelector && el.querySelector('canvas');
        if (cv) {
            try {
                return { url: cv.toDataURL('image/png'), name: 'canvas_image' };
            } catch(e) {}
        }
        return null;
    }

    // 1. Intercept programmatic anchor click (used by ChatGPT, Claude, Midjourney, etc.)
    var origClick = HTMLAnchorElement.prototype.click;
    HTMLAnchorElement.prototype.click = function() {
        try {
            var href = this.href || this.getAttribute('href');
            var download = this.download || this.getAttribute('download');
            var isBlobOrData = href && (href.startsWith('blob:') || href.startsWith('data:'));
            var isMediaExt = href && /\.(png|jpe?g|webp|gif|svg|bmp|pdf|zip|apk|tar|gz|mp3|mp4|csv|txt|json)(\?.*)?$/i.test(href);
            if (isBlobOrData || (download !== null && download !== undefined) || isMediaExt) {
                if (href && !href.startsWith('#') && !href.startsWith('javascript:')) {
                    triggerNativeDownload(href, download || '');
                    return;
                }
            }
        } catch(e) {}
        return origClick.apply(this, arguments);
    };

    // 2. Intercept dispatchEvent on anchor (used by React/Vue in modern AI chats)
    var origDispatch = EventTarget.prototype.dispatchEvent;
    EventTarget.prototype.dispatchEvent = function(event) {
        try {
            if (this instanceof HTMLAnchorElement && event && event.type === 'click') {
                var href = this.href || this.getAttribute('href');
                var download = this.download || this.getAttribute('download');
                var isBlobOrData = href && (href.startsWith('blob:') || href.startsWith('data:'));
                var isMediaExt = href && /\.(png|jpe?g|webp|gif|svg|bmp|pdf|zip|apk|tar|gz|mp3|mp4|csv|txt|json)(\?.*)?$/i.test(href);
                if (isBlobOrData || (download !== null && download !== undefined) || isMediaExt) {
                    if (href && !href.startsWith('#') && !href.startsWith('javascript:')) {
                        triggerNativeDownload(href, download || '');
                        return false;
                    }
                }
            }
        } catch(e) {}
        return origDispatch.apply(this, arguments);
    };

    // 3. Intercept user clicks on download links, download buttons, and image download triggers
    document.addEventListener('click', function(e) {
        try {
            var el = e.target;
            while (el && el !== document.body && el !== document.documentElement) {
                var href = el.href || (el.getAttribute && el.getAttribute('href'));
                var download = el.hasAttribute && el.getAttribute('download');
                var isBlobOrData = href && (href.startsWith('blob:') || href.startsWith('data:'));
                var isMediaExt = href && /\.(png|jpe?g|webp|gif|svg|bmp|pdf|zip|apk|tar|gz|mp3|mp4|csv|txt|json)(\?.*)?$/i.test(href);
                
                if (isBlobOrData || (download !== null && href && !href.startsWith('#') && !href.startsWith('javascript:')) || isMediaExt) {
                    triggerNativeDownload(href, download || '');
                    e.preventDefault();
                    e.stopPropagation();
                    return;
                }

                // Check for download buttons in AI chat interfaces (ChatGPT, Claude, Poe, Leonardo, Midjourney web)
                var ariaLabel = (el.getAttribute && (el.getAttribute('aria-label') || el.getAttribute('title') || '')) || '';
                var testId = (el.getAttribute && el.getAttribute('data-testid') || '') || '';
                var className = (typeof el.className === 'string' ? el.className : '') || '';
                var isDownloadBtn = /download|save image|save picture|export/i.test(ariaLabel) ||
                                    /download|save/i.test(testId) ||
                                    /download-btn|download-button|btn-download/i.test(className);

                if (isDownloadBtn) {
                    // Search for associated image in neighboring containers or parent card
                    var searchContainer = el.closest('[data-message-author-role], .message, .group, article, .card, [role="region"], div') || el.parentElement;
                    if (searchContainer) {
                        var media = extractMediaFromElement(searchContainer);
                        if (media && media.url) {
                            triggerNativeDownload(media.url, media.name || '');
                            e.preventDefault();
                            e.stopPropagation();
                            return;
                        }
                    }
                }

                el = el.parentElement;
            }
        } catch(err) {}
    }, true);

    // 4. Intercept window.open
    var origOpen = window.open;
    window.open = function(url) {
        if (url && (url.startsWith('blob:') || url.startsWith('data:') || /\.(png|jpe?g|webp|gif|svg|bmp|pdf|zip|apk)(\?.*)?$/i.test(url))) {
            triggerNativeDownload(url, '');
            return null;
        }
        return origOpen.apply(this, arguments);
    };
})();
"""

fun saveImageBytesToStorage(
    context: Context,
    bytes: ByteArray,
    mimeType: String?,
    suggestedName: String? = null
): Uri? {
    try {
        val detectedMime = mimeType?.takeIf { it.isNotBlank() && it != "application/octet-stream" }
            ?: when {
                bytes.size > 8 && bytes[0] == 0x89.toByte() && bytes[1] == 0x50.toByte() -> "image/png"
                bytes.size > 2 && bytes[0] == 0xFF.toByte() && bytes[1] == 0xD8.toByte() -> "image/jpeg"
                bytes.size > 4 && bytes[0] == 'R'.code.toByte() && bytes[1] == 'I'.code.toByte() -> "image/webp"
                bytes.size > 4 && bytes[0] == 'G'.code.toByte() && bytes[1] == 'I'.code.toByte() -> "image/gif"
                else -> "image/png"
            }

        val ext = when {
            detectedMime.contains("png") -> "png"
            detectedMime.contains("jpeg") || detectedMime.contains("jpg") -> "jpg"
            detectedMime.contains("webp") -> "webp"
            detectedMime.contains("gif") -> "gif"
            detectedMime.contains("svg") -> "svg"
            else -> "png"
        }

        val filename = if (!suggestedName.isNullOrBlank()) {
            val clean = suggestedName.replace(Regex("[^a-zA-Z0-9._-]"), "_")
            if (clean.contains(".")) clean else "$clean.$ext"
        } else {
            "AI_Image_${System.currentTimeMillis()}.$ext"
        }

        var savedImageUri: Uri? = null

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            // Save to Pictures/Orbit (so it shows in Gallery)
            val imageValues = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, filename)
                put(MediaStore.Images.Media.MIME_TYPE, detectedMime)
                put(MediaStore.Images.Media.RELATIVE_PATH, "${Environment.DIRECTORY_PICTURES}/Orbit")
                put(MediaStore.Images.Media.IS_PENDING, 1)
            }
            val imgUri = context.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, imageValues)
            if (imgUri != null) {
                context.contentResolver.openOutputStream(imgUri)?.use { os ->
                    os.write(bytes)
                }
                imageValues.clear()
                imageValues.put(MediaStore.Images.Media.IS_PENDING, 0)
                context.contentResolver.update(imgUri, imageValues, null, null)
                savedImageUri = imgUri
            }

            // Also save to Downloads/Orbit
            try {
                val dlValues = ContentValues().apply {
                    put(MediaStore.Downloads.DISPLAY_NAME, filename)
                    put(MediaStore.Downloads.MIME_TYPE, detectedMime)
                    put(MediaStore.Downloads.RELATIVE_PATH, "${Environment.DIRECTORY_DOWNLOADS}/Orbit")
                    put(MediaStore.Downloads.IS_PENDING, 1)
                }
                val dlUri = context.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, dlValues)
                if (dlUri != null) {
                    context.contentResolver.openOutputStream(dlUri)?.use { os ->
                        os.write(bytes)
                    }
                    dlValues.clear()
                    dlValues.put(MediaStore.Downloads.IS_PENDING, 0)
                    context.contentResolver.update(dlUri, dlValues, null, null)
                }
            } catch (e: Exception) {
                // Secondary copy is best-effort
            }
        } else {
            val picturesDir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES), "Orbit").apply { mkdirs() }
            val file = File(picturesDir, filename)
            file.writeBytes(bytes)
            savedImageUri = Uri.fromFile(file)
            MediaScannerConnection.scanFile(context, arrayOf(file.absolutePath), arrayOf(detectedMime), null)
        }

        android.os.Handler(android.os.Looper.getMainLooper()).post {
            Toast.makeText(context.applicationContext, "Saved $filename to Gallery & Downloads", Toast.LENGTH_LONG).show()
        }
        return savedImageUri
    } catch (e: Exception) {
        android.os.Handler(android.os.Looper.getMainLooper()).post {
            Toast.makeText(context.applicationContext, "Save error: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
        }
        return null
    }
}

fun shareImage(context: Context, bytes: ByteArray, mimeType: String?, filename: String?) {
    try {
        val ext = when {
            mimeType?.contains("png") == true -> "png"
            mimeType?.contains("jpeg") == true || mimeType?.contains("jpg") == true -> "jpg"
            mimeType?.contains("webp") == true -> "webp"
            else -> "png"
        }
        val safeName = "share_${System.currentTimeMillis()}.$ext"
        val cacheFile = File(context.cacheDir, safeName)
        cacheFile.writeBytes(bytes)
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", cacheFile)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = mimeType?.takeIf { it.isNotBlank() } ?: "image/png"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(Intent.createChooser(intent, "Share Picture").apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        })
    } catch (e: Exception) {
        Toast.makeText(context, "Share failed: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
    }
}

fun downloadHttpFile(
    context: Context,
    url: String,
    userAgent: String? = null,
    contentDisposition: String? = null,
    mimetype: String? = null
) {
    try {
        val uri = Uri.parse(url)
        val filename = try {
            val guessed = URLUtil.guessFileName(url, contentDisposition, mimetype)
            if (guessed.isNullOrBlank() || guessed == "downloadfile.bin") {
                val lastSegment = uri.lastPathSegment
                if (!lastSegment.isNullOrBlank() && lastSegment.contains(".")) lastSegment else guessed
            } else guessed
        } catch (e: Exception) {
            "download_${System.currentTimeMillis()}"
        }

        val request = DownloadManager.Request(uri).apply {
            val ext = MimeTypeMap.getFileExtensionFromUrl(url)
            val effectiveMime = mimetype?.takeIf { it.isNotBlank() && it != "application/octet-stream" }
                ?: MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext)
                ?: "application/octet-stream"
            setMimeType(effectiveMime)

            try {
                val cookies = CookieManager.getInstance().getCookie(url)
                if (!cookies.isNullOrBlank()) {
                    addRequestHeader("Cookie", cookies)
                }
            } catch (e: Exception) {
                // Ignore
            }

            if (!userAgent.isNullOrBlank()) {
                addRequestHeader("User-Agent", userAgent)
            }

            setDescription("Downloading file via Orbit")
            setTitle(filename)
            setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, filename)
        }

        val dm = context.getSystemService(Context.DOWNLOAD_SERVICE) as? DownloadManager
        dm?.enqueue(request)
        Toast.makeText(context, "Downloading $filename...", Toast.LENGTH_SHORT).show()
    } catch (e: Exception) {
        try {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            Toast.makeText(context, "Starting download...", Toast.LENGTH_SHORT).show()
        } catch (ex: Exception) {
            Toast.makeText(context, "Download failed: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
        }
    }
}

fun saveDataUrlToDownloads(
    context: Context,
    dataUrl: String,
    mimeType: String?,
    suggestedName: String? = null
) {
    try {
        val commaIndex = dataUrl.indexOf(",")
        if (commaIndex == -1) return
        val metadata = dataUrl.substring(0, commaIndex)
        val base64Data = dataUrl.substring(commaIndex + 1)
        val bytes = android.util.Base64.decode(base64Data, android.util.Base64.DEFAULT)

        val detectedMime = mimeType?.takeIf { it.isNotBlank() && it != "application/octet-stream" }
            ?: if (metadata.contains(";base64")) {
                metadata.removePrefix("data:").removeSuffix(";base64")
            } else {
                "application/octet-stream"
            }

        // If it is an image, save directly to Gallery and Downloads
        if (detectedMime.startsWith("image/")) {
            saveImageBytesToStorage(context, bytes, detectedMime, suggestedName)
            return
        }

        val ext = when {
            detectedMime.contains("pdf") -> "pdf"
            detectedMime.contains("svg") -> "svg"
            detectedMime.contains("json") -> "json"
            detectedMime.contains("text/plain") -> "txt"
            else -> "bin"
        }

        val filename = suggestedName?.takeIf { it.contains(".") }
            ?: (suggestedName ?: "download_${System.currentTimeMillis()}") + ".$ext"

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val contentValues = ContentValues().apply {
                put(MediaStore.Downloads.DISPLAY_NAME, filename)
                put(MediaStore.Downloads.MIME_TYPE, detectedMime)
                put(MediaStore.Downloads.IS_PENDING, 1)
            }
            val uri = context.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, contentValues)
            if (uri != null) {
                context.contentResolver.openOutputStream(uri)?.use { os ->
                    os.write(bytes)
                }
                contentValues.clear()
                contentValues.put(MediaStore.Downloads.IS_PENDING, 0)
                context.contentResolver.update(uri, contentValues, null, null)
                android.os.Handler(android.os.Looper.getMainLooper()).post {
                    Toast.makeText(context.applicationContext, "Saved $filename to Downloads", Toast.LENGTH_SHORT).show()
                }
            }
        } else {
            val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            downloadsDir.mkdirs()
            val file = File(downloadsDir, filename)
            file.writeBytes(bytes)
            android.os.Handler(android.os.Looper.getMainLooper()).post {
                Toast.makeText(context.applicationContext, "Saved $filename to Downloads", Toast.LENGTH_SHORT).show()
            }
        }
    } catch (e: Exception) {
        android.os.Handler(android.os.Looper.getMainLooper()).post {
            Toast.makeText(context.applicationContext, "Download error: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
        }
    }
}

fun downloadBlob(webView: WebView, blobUrl: String, suggestedFilename: String? = null) {
    val escapedUrl = blobUrl.replace("'", "\\'")
    val filename = suggestedFilename?.replace("'", "\\'") ?: "ai_image_${System.currentTimeMillis()}.png"
    val js = """
        (function() {
            var url = '$escapedUrl';
            var name = '$filename';
            function send(dataUrl, mime) {
                if (window.AndroidDownloadBridge) {
                    window.AndroidDownloadBridge.onBlobData(dataUrl, mime, name);
                }
            }
            fetch(url)
                .then(function(res) { return res.blob(); })
                .then(function(blob) {
                    var reader = new FileReader();
                    reader.onloadend = function() {
                        send(reader.result, blob.type || 'image/png');
                    };
                    reader.readAsDataURL(blob);
                })
                .catch(function() {
                    try {
                        var xhr = new XMLHttpRequest();
                        xhr.open('GET', url, true);
                        xhr.responseType = 'blob';
                        xhr.onload = function() {
                            var reader = new FileReader();
                            reader.onloadend = function() {
                                send(reader.result, xhr.response.type || 'image/png');
                            };
                            reader.readAsDataURL(xhr.response);
                        };
                        xhr.send();
                    } catch(e) {}
                });
        })();
    """.trimIndent()
    webView.evaluateJavascript(js, null)
}

suspend fun downloadHttpImageOkHttp(
    context: Context,
    url: String,
    userAgent: String?,
    referer: String?,
    suggestedFilename: String?
): Boolean = withContext(Dispatchers.IO) {
    try {
        val client = OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .followRedirects(true)
            .build()

        val reqBuilder = Request.Builder()
            .url(url)
            .addHeader("Accept", "image/avif,image/webp,image/apng,image/svg+xml,image/*,*/*;q=0.8")

        if (!userAgent.isNullOrBlank()) {
            reqBuilder.addHeader("User-Agent", userAgent)
        }
        val cookies = CookieManager.getInstance().getCookie(url)
        if (!cookies.isNullOrBlank()) {
            reqBuilder.addHeader("Cookie", cookies)
        }
        if (!referer.isNullOrBlank()) {
            reqBuilder.addHeader("Referer", referer)
        }

        val response = client.newCall(reqBuilder.build()).execute()
        if (response.isSuccessful) {
            val bytes = response.body?.bytes()
            if (bytes != null && bytes.isNotEmpty()) {
                val contentType = response.header("Content-Type") ?: "image/png"
                saveImageBytesToStorage(context, bytes, contentType, suggestedFilename)
                return@withContext true
            }
        }
    } catch (e: Exception) {
        e.printStackTrace()
    }
    return@withContext false
}

fun downloadAndSaveImage(
    context: Context,
    webView: WebView?,
    url: String,
    suggestedName: String? = null,
    mimetype: String? = null
) {
    if (url.isBlank()) return
    val cleanName = suggestedName?.takeIf { it.isNotBlank() } ?: "ai_image_${System.currentTimeMillis()}"

    if (url.startsWith("data:")) {
        saveDataUrlToDownloads(context, url, mimetype, cleanName)
        return
    }

    if (url.startsWith("blob:")) {
        if (webView != null) {
            Toast.makeText(context, "Extracting picture...", Toast.LENGTH_SHORT).show()
            downloadBlob(webView, url, cleanName)
        }
        return
    }

    // For HTTP / HTTPS: First try in-page DOM canvas extraction if webView is available
    if (webView != null) {
        val escapedUrl = url.replace("'", "\\'")
        val escapedName = cleanName.replace("'", "\\'")
        val js = """
            (function() {
                var target = '$escapedUrl';
                var name = '$escapedName';
                var img = document.querySelector('img[src="' + target + '"]') ||
                          Array.from(document.querySelectorAll('img')).find(function(i) { return (i.currentSrc === target || i.src === target); });
                if (img && img.complete && img.naturalWidth > 0) {
                    try {
                        var canvas = document.createElement('canvas');
                        canvas.width = img.naturalWidth;
                        canvas.height = img.naturalHeight;
                        var ctx = canvas.getContext('2d');
                        ctx.drawImage(img, 0, 0);
                        var dataUrl = canvas.toDataURL('image/png');
                        if (window.AndroidDownloadBridge) {
                            window.AndroidDownloadBridge.onBlobData(dataUrl, 'image/png', name);
                            return;
                        }
                    } catch(e) {}
                }
                fetch(target, { credentials: 'include' })
                    .then(function(r) { return r.blob(); })
                    .then(function(b) {
                        var reader = new FileReader();
                        reader.onloadend = function() {
                            if (window.AndroidDownloadBridge) {
                                window.AndroidDownloadBridge.onBlobData(reader.result, b.type || 'image/png', name);
                            }
                        };
                        reader.readAsDataURL(b);
                    })
                    .catch(function() {
                        if (window.AndroidDownloadBridge) {
                            window.AndroidDownloadBridge.onFallbackHttpDownload(target, name);
                        }
                    });
            })();
        """.trimIndent()
        Toast.makeText(context, "Saving picture...", Toast.LENGTH_SHORT).show()
        webView.evaluateJavascript(js, null)
        return
    }

    downloadHttpFile(context, url, null, null, mimetype ?: "image/*")
}

