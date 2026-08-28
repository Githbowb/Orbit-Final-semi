package com.example

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
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
import com.example.ui.theme.CardDark
import com.example.ui.theme.TextSecondary

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun FloatingWebViewContent(
    initialUrl: String = "https://www.google.com",
    accentColor: Color,
    isMaximized: Boolean = false,
    onToggleMaximize: (() -> Unit)? = null,
    onOpenExternal: ((String) -> Unit)? = null
) {
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current

    var currentUrl by rememberSaveable { mutableStateOf(initialUrl.ifBlank { "https://www.google.com" }) }
    var inputUrl by rememberSaveable { mutableStateOf(initialUrl.ifBlank { "https://www.google.com" }) }
    var pageTitle by rememberSaveable { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }
    var loadingProgress by remember { mutableStateOf(0) }
    var canGoBack by remember { mutableStateOf(false) }
    var canGoForward by remember { mutableStateOf(false) }
    var isDesktopMode by rememberSaveable { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    var webViewInstance by remember { mutableStateOf<WebView?>(null) }

    fun normalizeUrl(input: String): String {
        val trimmed = input.trim()
        if (trimmed.isEmpty()) return "https://www.google.com"
        if (URLUtil.isValidUrl(trimmed) && (trimmed.startsWith("http://") || trimmed.startsWith("https://"))) {
            return trimmed
        }
        if (trimmed.contains(".") && !trimmed.contains(" ") && !trimmed.startsWith("http")) {
            return "https://$trimmed"
        }
        val encodedQuery = Uri.encode(trimmed)
        return "https://www.google.com/search?q=$encodedQuery"
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

    // Quick bookmarks / popular shortcuts
    val quickLinks = remember {
        listOf(
            "Google" to "https://www.google.com",
            "Wikipedia" to "https://en.m.wikipedia.org",
            "GitHub" to "https://github.com",
            "Reddit" to "https://www.reddit.com"
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 2.dp, vertical = 2.dp)
    ) {
        // Top URL and Search Bar (Compact & Space-Optimized)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(36.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(Color(0x0CFFFFFF))
                .border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(12.dp))
                .padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.Search,
                contentDescription = "Search",
                tint = accentColor,
                modifier = Modifier.size(15.dp)
            )

            Spacer(modifier = Modifier.width(6.dp))

            TextField(
                value = inputUrl,
                onValueChange = { inputUrl = it },
                placeholder = {
                    Text(
                        text = stringResource(id = R.string.browser_search_placeholder),
                        color = TextSecondary,
                        fontSize = 11.5.sp,
                        maxLines = 1
                    )
                },
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = Color.Transparent,
                    unfocusedContainerColor = Color.Transparent,
                    disabledContainerColor = Color.Transparent,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White
                ),
                textStyle = LocalTextStyle.current.copy(fontSize = 11.5.sp),
                singleLine = true,
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Uri,
                    imeAction = ImeAction.Go
                ),
                keyboardActions = KeyboardActions(
                    onGo = {
                        focusManager.clearFocus()
                        val target = normalizeUrl(inputUrl)
                        currentUrl = target
                        inputUrl = target
                        errorMessage = null
                        webViewInstance?.loadUrl(target)
                    }
                ),
                modifier = Modifier.weight(1f)
            )

            if (inputUrl.isNotEmpty()) {
                IconButton(
                    onClick = { inputUrl = "" },
                    modifier = Modifier.size(24.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Clear",
                        tint = TextSecondary,
                        modifier = Modifier.size(13.dp)
                    )
                }
            }

            IconButton(
                onClick = {
                    focusManager.clearFocus()
                    val target = normalizeUrl(inputUrl)
                    currentUrl = target
                    inputUrl = target
                    errorMessage = null
                    webViewInstance?.loadUrl(target)
                },
                modifier = Modifier
                    .size(26.dp)
                    .background(accentColor.copy(alpha = 0.25f), CircleShape)
            ) {
                Icon(
                    imageVector = Icons.Default.ArrowForward,
                    contentDescription = "Go",
                    tint = accentColor,
                    modifier = Modifier.size(13.dp)
                )
            }
        }

        // Quick Shortcuts Row (Compact)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            quickLinks.forEach { (label, url) ->
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color.White.copy(alpha = 0.05f))
                        .border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(8.dp))
                        .clickable {
                            focusManager.clearFocus()
                            inputUrl = url
                            currentUrl = url
                            errorMessage = null
                            webViewInstance?.loadUrl(url)
                        }
                        .padding(horizontal = 8.dp, vertical = 2.5.dp)
                ) {
                    Text(
                        text = label,
                        color = Color.White.copy(alpha = 0.85f),
                        fontSize = 9.5.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }

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
                    .height(2.dp),
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
                .background(Color(0xFF0F1017))
                .border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(14.dp))
        ) {
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { ctx ->
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
                            userAgentString = if (isDesktopMode) {
                                "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"
                            } else {
                                userAgentString
                            }
                        }

                        // Link Click and Long-Press Handlers
                        webViewClient = object : WebViewClient() {
                            override fun shouldOverrideUrlLoading(
                                view: WebView?,
                                request: WebResourceRequest?
                            ): Boolean {
                                val url = request?.url?.toString() ?: return false
                                if (url.startsWith("http://") || url.startsWith("https://")) {
                                    // Standard Tap: Open directly inside the Floating WebView
                                    return false
                                }
                                // External custom scheme handling (e.g., mailto:, tel:, intent:)
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
                                    currentUrl = it
                                    inputUrl = it
                                }
                                canGoBack = view?.canGoBack() == true
                                canGoForward = view?.canGoForward() == true
                            }

                            override fun onPageFinished(view: WebView?, url: String?) {
                                super.onPageFinished(view, url)
                                isLoading = false
                                url?.let {
                                    currentUrl = it
                                    inputUrl = it
                                }
                                pageTitle = view?.title ?: ""
                                canGoBack = view?.canGoBack() == true
                                canGoForward = view?.canGoForward() == true

                                // Dynamic viewport scaling script injection to ensure mobile responsive rendering
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

                        webChromeClient = object : WebChromeClient() {
                            override fun onProgressChanged(view: WebView?, newProgress: Int) {
                                super.onProgressChanged(view, newProgress)
                                loadingProgress = newProgress
                                if (newProgress == 100) {
                                    isLoading = false
                                }
                            }

                            override fun onReceivedTitle(view: WebView?, title: String?) {
                                super.onReceivedTitle(view, title)
                                if (!title.isNullOrBlank()) {
                                    pageTitle = title
                                }
                            }
                        }

                        // Long-Press on links to open in external browser
                        setOnLongClickListener {
                            val hitResult = hitTestResult
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

                        loadUrl(normalizeUrl(currentUrl))
                        webViewInstance = this
                    }
                },
                update = { webView ->
                    webViewInstance = webView
                    // Update desktop mode user agent if changed
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

            // Friendly Offline / Error Overlay
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
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(4.dp))

        // Bottom Navigation Bar Controls (Compact)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(34.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(Color(0x0CFFFFFF))
                .border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(12.dp))
                .padding(horizontal = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                // Back Button
                IconButton(
                    onClick = { webViewInstance?.goBack() },
                    enabled = canGoBack,
                    modifier = Modifier.size(26.dp)
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = if (canGoBack) Color.White else TextSecondary.copy(alpha = 0.35f),
                        modifier = Modifier.size(15.dp)
                    )
                }

                // Forward Button
                IconButton(
                    onClick = { webViewInstance?.goForward() },
                    enabled = canGoForward,
                    modifier = Modifier.size(26.dp)
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                        contentDescription = "Forward",
                        tint = if (canGoForward) Color.White else TextSecondary.copy(alpha = 0.35f),
                        modifier = Modifier.size(15.dp)
                    )
                }

                // Refresh / Stop Button
                IconButton(
                    onClick = {
                        if (isLoading) webViewInstance?.stopLoading() else webViewInstance?.reload()
                    },
                    modifier = Modifier.size(26.dp)
                ) {
                    Icon(
                        imageVector = if (isLoading) Icons.Default.Close else Icons.Default.Refresh,
                        contentDescription = if (isLoading) "Stop" else "Refresh",
                        tint = accentColor,
                        modifier = Modifier.size(15.dp)
                    )
                }
            }

            // Middle Status (Domain / Title)
            Text(
                text = pageTitle.ifBlank { Uri.parse(currentUrl).host ?: "Orbit Web" },
                color = TextSecondary.copy(alpha = 0.9f),
                fontSize = 10.5.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 8.dp)
            )

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                // Desktop Mode Toggle Button
                IconButton(
                    onClick = { isDesktopMode = !isDesktopMode },
                    modifier = Modifier
                        .size(26.dp)
                        .background(if (isDesktopMode) accentColor.copy(alpha = 0.2f) else Color.Transparent, CircleShape)
                ) {
                    Icon(
                        imageVector = if (isDesktopMode) Icons.Default.DesktopWindows else Icons.Default.PhoneAndroid,
                        contentDescription = "Toggle Desktop Mode",
                        tint = if (isDesktopMode) accentColor else Color.White.copy(alpha = 0.8f),
                        modifier = Modifier.size(14.dp)
                    )
                }

                // Open in External Default Browser Button
                IconButton(
                    onClick = { openExternally(currentUrl) },
                    modifier = Modifier.size(26.dp)
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

    // Safe Lifecycle & Memory Cleanup
    DisposableEffect(Unit) {
        onDispose {
            webViewInstance?.apply {
                stopLoading()
                clearHistory()
                loadUrl("about:blank")
                onPause()
                removeAllViews()
                destroy()
            }
            webViewInstance = null
        }
    }
}
