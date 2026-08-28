package com.example

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import android.view.ViewGroup
import android.webkit.URLUtil
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.browser.customtabs.CustomTabsIntent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.example.ui.theme.TextSecondary

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun FloatingWebViewContent(
    initialUrl: String = "",
    accentColor: Color,
    isMaximized: Boolean = false,
    onToggleMaximize: (() -> Unit)? = null,
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

                            settings.apply {
                                javaScriptEnabled = true
                                domStorageEnabled = true
                                databaseEnabled = true
                                setSupportZoom(true)
                                builtInZoomControls = true
                                displayZoomControls = false
                                useWideViewPort = true
                                loadWithOverviewMode = true
                                layoutAlgorithm = WebSettings.LayoutAlgorithm.TEXT_AUTOSIZING
                                defaultTextEncodingName = "utf-8"
                                cacheMode = WebSettings.LOAD_DEFAULT
                                mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
                            }
                            BrowserStateManager.setRetainedWebView(this)
                        }
                    }

                    // Configure Clients
                    webView.webViewClient = object : WebViewClient() {
                        override fun shouldOverrideUrlLoading(
                            view: WebView?,
                            request: WebResourceRequest?
                        ): Boolean {
                            val url = request?.url?.toString() ?: return false
                            if (url.startsWith("http://") || url.startsWith("https://")) {
                                return false
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
                        }

                        override fun onReceivedTitle(view: WebView?, title: String?) {
                            super.onReceivedTitle(view, title)
                            if (!title.isNullOrBlank()) {
                                pageTitle = title
                            }
                        }
                    }

                    webView.setOnLongClickListener {
                        val hitResult = webView.hitTestResult
                        val target = when (hitResult.type) {
                            WebView.HitTestResult.SRC_ANCHOR_TYPE,
                            WebView.HitTestResult.SRC_IMAGE_ANCHOR_TYPE,
                            WebView.HitTestResult.IMAGE_TYPE -> hitResult.extra
                            else -> null
                        }
                        if (!target.isNullOrBlank()) {
                            Toast.makeText(ctx, "Opening link externally in default browser...", Toast.LENGTH_SHORT).show()
                            openExternally(target)
                            true
                        } else {
                            false
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

            // Middle Status (Domain / Title)
            Text(
                text = pageTitle.ifBlank {
                    try {
                        Uri.parse(currentUrl).host ?: "Orbit Web"
                    } catch (e: Exception) {
                        "Orbit Web"
                    }
                },
                color = TextSecondary.copy(alpha = 0.9f),
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 6.dp)
            )

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(2.dp)
            ) {
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
