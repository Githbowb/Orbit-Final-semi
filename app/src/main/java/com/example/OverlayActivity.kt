package com.example

import android.app.Activity
import android.app.AppOpsManager
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Rect
import android.graphics.drawable.Drawable
import android.os.Build
import android.os.Bundle
import android.widget.ImageView
import android.widget.Toast
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.enableEdgeToEdge
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.ActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.Spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.FullscreenExit
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.example.ui.theme.MyApplicationTheme
import androidx.compose.ui.res.stringResource
import com.example.R
import com.example.capture.ScreenCaptureManager
import com.example.capture.ScreenCaptureOverlay
import com.example.ui.theme.CardDark
import com.example.ui.theme.TextSecondary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.*
import com.example.data.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.compose.runtime.saveable.rememberSaveable

class OverlayActivity : ComponentActivity() {

    companion object {
        val activeTabFlow = kotlinx.coroutines.flow.MutableStateFlow<String?>(null)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        activeTabFlow.value = intent.getStringExtra("launch_tab")

        val currentLang = ThemePreferences.getLanguage(this)
        val locale = java.util.Locale(currentLang)
        java.util.Locale.setDefault(locale)
        val config = resources.configuration
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1) {
            config.setLocale(locale)
            config.setLayoutDirection(locale)
        }

        setContent {
            val context = LocalContext.current
            val activeTheme = remember { ThemePreferences.getSelectedTheme(context) }
            val currentLanguage = remember { ThemePreferences.getLanguage(context) }
            val layoutDirection = if (currentLanguage == "ar" || currentLanguage == "fa") LayoutDirection.Rtl else LayoutDirection.Ltr

            CompositionLocalProvider(LocalLayoutDirection provides layoutDirection) {
                MyApplicationTheme(accentColor = activeTheme.getColor()) {
                    OverlayScreen(onDismiss = { finish() })
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        activeTabFlow.value = intent.getStringExtra("launch_tab")
    }
}

@Composable
fun OverlayScreen(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val activeTheme = remember { ThemePreferences.getSelectedTheme(context) }
    val accentColor = activeTheme.getColor()

    val coroutineScope = rememberCoroutineScope()

    // Spring scale and alpha animations for elastic bouncy effect
    var animatePlay by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        animatePlay = true
    }

    val scale by androidx.compose.animation.core.animateFloatAsState(
        targetValue = if (animatePlay) 1f else 0.8f,
        animationSpec = androidx.compose.animation.core.spring(
            dampingRatio = androidx.compose.animation.core.Spring.DampingRatioMediumBouncy,
            stiffness = androidx.compose.animation.core.Spring.StiffnessLow
        )
    )
    val alpha by androidx.compose.animation.core.animateFloatAsState(
        targetValue = if (animatePlay) 1f else 0f,
        animationSpec = androidx.compose.animation.core.spring(
            dampingRatio = androidx.compose.animation.core.Spring.DampingRatioNoBouncy,
            stiffness = androidx.compose.animation.core.Spring.StiffnessLow
        )
    )

    fun animateDismiss() {
        animatePlay = false
    }

    LaunchedEffect(animatePlay) {
        if (!animatePlay) {
            delay(220)
            onDismiss()
        }
    }

    // Intercept back button to dismiss the overlay
    BackHandler {
        animateDismiss()
    }

    // Database and repository
    val database = remember { OrbitDatabase.getDatabase(context) }
    val repository = remember { OrbitRepository(database) }

    // Tab Selection state
    var selectedTab by rememberSaveable { mutableStateOf(OrbitTab.LAUNCHER) }

    // Launcher tab states
    var appsList by remember { mutableStateOf<List<AppInfo>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var sortedAlphabetically by remember { mutableStateOf(false) }
    var explanationReason by remember { mutableStateOf("") }
    var searchQuery by remember { mutableStateOf("") }
    var favoritePackageNames by remember { mutableStateOf(FavoritesPreferences.getFavorites(context)) }

    // Vault tab states
    var vaultInputText by rememberSaveable { mutableStateOf("") }
    val savedVaultEntries by repository.vaultEntries.collectAsState(initial = emptyList())
    val filteredVaultEntries = remember(savedVaultEntries) {
        savedVaultEntries.filter { it.id != -1L }
    }
    var currentFolderId by rememberSaveable { mutableStateOf<Long?>(null) }
    var editingEntry by remember { mutableStateOf<VaultEntry?>(null) }
    val savedVaultFolders by repository.vaultFolders.collectAsState(initial = emptyList())

    // Scan Screen (OCR) flow state.
    //   - isScanOverlayVisible: when true, the full-screen translucent
    //     ScreenCaptureOverlay is shown INSTEAD of the regular Vault UI.
    // ----------------------------------------------------------------
    var isScanOverlayVisible by rememberSaveable { mutableStateOf(false) }

    val activity = context as? Activity
    LaunchedEffect(isScanOverlayVisible) {
        activity?.window?.let { win ->
            if (isScanOverlayVisible) {
                // Clear dim behind so the user can see everything clearly
                win.clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
            } else {
                // Restore dim behind when returning to normal menu
                win.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
                win.setDimAmount(0.6f)
            }
        }
    }

    // MediaProjection consent launcher. The Activity Result API handles the
    // startActivityForResult dance for us. On success we initialize the MediaProjection
    // in the service immediately, and show the ScreenCaptureOverlay.
    val mediaProjectionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result: ActivityResult ->
        if (result.resultCode == Activity.RESULT_OK && result.data != null) {
            isScanOverlayVisible = true

            // Close the floating bubble and initialize MediaProjection immediately
            val serviceIntent = Intent(context, FloatingLauncherService::class.java).apply {
                action = FloatingLauncherService.ACTION_START_PROJECTION
                putExtra(FloatingLauncherService.EXTRA_RESULT_CODE, result.resultCode)
                putExtra(FloatingLauncherService.EXTRA_RESULT_DATA, result.data)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(serviceIntent)
            } else {
                context.startService(serviceIntent)
            }
        } else {
            Toast.makeText(
                context,
                "Screen capture permission denied.",
                Toast.LENGTH_SHORT
            ).show()
            isScanOverlayVisible = false
        }
    }

    // Calculator tab states
    var calculatorInputText by rememberSaveable { mutableStateOf("") }

    // Speed Dial tab states
    var speedDialLabelText by rememberSaveable { mutableStateOf("") }
    var speedDialUrlText by rememberSaveable { mutableStateOf("") }
    val speedDialEntries by repository.speedDialEntries.collectAsState(initial = emptyList())

    // Floating Browser tab states
    var activeBrowserUrl by rememberSaveable { mutableStateOf(BrowserStateManager.getLastVisitedUrl(context)) }

    val engineChangeTrigger by SearchEnginePreferences.engineChanges.collectAsState()
    LaunchedEffect(engineChangeTrigger) {
        if (engineChangeTrigger > 0L) {
            activeBrowserUrl = SearchEnginePreferences.getSelectedEngine(context).homeUrl
        }
    }

    // Dynamic Window Resizing & Maximize states (specifically for Browser tab)
    var isMaximized by rememberSaveable { mutableStateOf(false) }

    // Auto-collapse maximize mode whenever switching away from Browser tab
    LaunchedEffect(selectedTab) {
        if (selectedTab != OrbitTab.BROWSER) {
            isMaximized = false
        }
    }

    val animatedWidthFraction by animateFloatAsState(
        targetValue = if (isMaximized && selectedTab == OrbitTab.BROWSER) 0.96f else 0.92f,
        animationSpec = androidx.compose.animation.core.spring(
            dampingRatio = androidx.compose.animation.core.Spring.DampingRatioNoBouncy,
            stiffness = androidx.compose.animation.core.Spring.StiffnessMediumLow
        ),
        label = "windowWidth"
    )
    val animatedHeightFraction by animateFloatAsState(
        targetValue = if (isMaximized && selectedTab == OrbitTab.BROWSER) 0.88f else 0.82f,
        animationSpec = androidx.compose.animation.core.spring(
            dampingRatio = androidx.compose.animation.core.Spring.DampingRatioNoBouncy,
            stiffness = androidx.compose.animation.core.Spring.StiffnessMediumLow
        ),
        label = "windowHeight"
    )

    // Observe activeTabFlow to switch tabs dynamically
    val activeTabExtra by OverlayActivity.activeTabFlow.collectAsState()
    LaunchedEffect(activeTabExtra) {
        if (activeTabExtra == "vault") {
            selectedTab = OrbitTab.VAULT
            ToolsPreferences.incrementLaunchCount(context, ToolsPreferences.KEY_VAULT)
            OverlayActivity.activeTabFlow.value = null // Consume extra
        } else if (activeTabExtra == "browser") {
            selectedTab = OrbitTab.BROWSER
            ToolsPreferences.incrementLaunchCount(context, ToolsPreferences.KEY_BROWSER)
            OverlayActivity.activeTabFlow.value = null
        }
    }

    // Load Vault active composition draft from Room on startup
    LaunchedEffect(Unit) {
        val draft = withContext(Dispatchers.IO) {
            database.vaultDao().getDraftEntry()
        }
        if (draft != null) {
            vaultInputText = draft.content
        }
    }

    // Debounce active Quick Vault composition draft to Room database
    LaunchedEffect(vaultInputText) {
        delay(400) // 400ms debounce
        withContext(Dispatchers.IO) {
            if (vaultInputText.isBlank()) {
                database.vaultDao().deleteDraft()
            } else {
                database.vaultDao().insert(VaultEntry(id = -1L, content = vaultInputText))
            }
        }
    }

    // Launcher tab initialization (loading apps)
    LaunchedEffect(Unit) {
        isLoading = true
        val list = withContext(Dispatchers.IO) {
            AppCache.getApps(context)
        }

        // Determine if we have usage stats data
        val totalUsage = list.sumOf { it.usageTimeMs }
        val hasUsageStats = hasUsagePermission(context)
        val isSkipped = ThemePreferences.isUsagePermissionSkipped(context)

        val collator = java.text.Collator.getInstance(Locale.getDefault())

        val sortedList = if (hasUsageStats && totalUsage > 0L) {
            sortedAlphabetically = false
            explanationReason = ""
            list.sortedByDescending { it.usageTimeMs }
        } else {
            sortedAlphabetically = true
            explanationReason = if (isSkipped) {
                "" // Do not show any error if the user skipped this permission
            } else if (!hasUsageStats) {
                context.getString(R.string.overlay_no_usage_permission)
            } else {
                context.getString(R.string.overlay_no_usage_stats)
            }
            list.sortedWith { app1, app2 -> collator.compare(app1.label, app2.label) }
        }

        appsList = sortedList
        isLoading = false
    }

    // Dynamic list calculations based on favorites and search filter
    val filteredApps = remember(appsList, searchQuery) {
        if (searchQuery.isBlank()) {
            appsList
        } else {
            appsList.filter { it.label.contains(searchQuery, ignoreCase = true) }
        }
    }

    val favoriteApps = remember(filteredApps, favoritePackageNames) {
        filteredApps.filter { favoritePackageNames.contains(it.packageName) }
    }

    val regularApps = remember(filteredApps, favoritePackageNames) {
        filteredApps.filter { !favoritePackageNames.contains(it.packageName) }
    }

    // Toggle Favorite Action
    val onToggleFavorite: (AppInfo) -> Unit = { app ->
        if (favoritePackageNames.contains(app.packageName)) {
            FavoritesPreferences.removeFavorite(context, app.packageName)
        } else {
            FavoritesPreferences.addFavorite(context, app.packageName)
        }
        favoritePackageNames = FavoritesPreferences.getFavorites(context)
    }

    // Fullscreen backdrop
    Box(
        modifier = Modifier
            .fillMaxSize()
            .then(if (!isScanOverlayVisible) Modifier.systemBarsPadding() else Modifier)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) {
                if (!isScanOverlayVisible) {
                    animateDismiss() // Dismiss when tapping outside the card
                }
            },
        contentAlignment = Alignment.Center
    ) {
        if (!isScanOverlayVisible) {
            // App Grid Container Card with Dynamic Sizing and Maximize support
            Card(
                modifier = Modifier
                    .graphicsLayer {
                        scaleX = scale
                        scaleY = scale
                        this.alpha = alpha
                    }
                    .fillMaxWidth(animatedWidthFraction)
                    .fillMaxHeight(animatedHeightFraction)
                    .widthIn(max = if (isMaximized && selectedTab == OrbitTab.BROWSER) 850.dp else 600.dp)
                    .border(
                        width = 1.5.dp,
                        brush = Brush.verticalGradient(
                            listOf(
                                accentColor.copy(alpha = if (isMaximized) 0.85f else 0.95f),
                                accentColor.copy(alpha = 0.3f),
                                Color.White.copy(alpha = 0.06f)
                            )
                        ),
                        shape = RoundedCornerShape(if (isMaximized && selectedTab == OrbitTab.BROWSER) 22.dp else 24.dp)
                    )
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) {
                        // Prevent dismissal when clicking inside the card
                    },
                colors = CardDefaults.cardColors(containerColor = Color(0xFF0C0F1A)),
                shape = RoundedCornerShape(if (isMaximized && selectedTab == OrbitTab.BROWSER) 22.dp else 24.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 24.dp)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            brush = Brush.radialGradient(
                                colors = listOf(
                                    accentColor.copy(alpha = 0.08f),
                                    Color.Transparent
                                ),
                                radius = 900f
                            )
                        )
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(
                                horizontal = if (selectedTab == OrbitTab.BROWSER) 14.dp else 18.dp,
                                vertical = if (selectedTab == OrbitTab.BROWSER) 10.dp else 16.dp
                            )
                    ) {
                        // Top Header Bar: Clean Centered Title for standard tabs; Dynamic Controls for Browser tab
                        if (selectedTab == OrbitTab.BROWSER) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(32.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(8.dp)
                                            .clip(CircleShape)
                                            .background(accentColor)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = stringResource(id = R.string.overlay_launchpad),
                                        fontSize = 13.5.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = Color.White.copy(alpha = 0.95f),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        letterSpacing = 0.3.sp
                                    )
                                }

                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    // Maximize / Restore Button
                                    IconButton(
                                        onClick = { isMaximized = !isMaximized },
                                        modifier = Modifier
                                            .size(28.dp)
                                            .clip(CircleShape)
                                            .background(accentColor.copy(alpha = 0.18f))
                                            .border(1.dp, accentColor.copy(alpha = 0.4f), CircleShape)
                                    ) {
                                        Icon(
                                            imageVector = if (isMaximized) Icons.Default.FullscreenExit else Icons.Default.Fullscreen,
                                            contentDescription = if (isMaximized) stringResource(R.string.overlay_restore) else stringResource(R.string.overlay_maximize),
                                            tint = accentColor,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }

                                    // Close Button (X)
                                    IconButton(
                                        onClick = { animateDismiss() },
                                        modifier = Modifier
                                            .size(28.dp)
                                            .clip(CircleShape)
                                            .background(Color.White.copy(alpha = 0.08f))
                                            .border(1.dp, Color.White.copy(alpha = 0.12f), CircleShape)
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
                        } else {
                            // Standard Views: Sleek futuristic Header Pill with Glowing Orbit Dot
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center
                            ) {
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(20.dp))
                                        .background(accentColor.copy(alpha = 0.12f))
                                        .border(
                                            1.dp,
                                            Brush.horizontalGradient(
                                                listOf(
                                                    accentColor.copy(alpha = 0.5f),
                                                    accentColor.copy(alpha = 0.2f),
                                                    accentColor.copy(alpha = 0.5f)
                                                )
                                            ),
                                            RoundedCornerShape(20.dp)
                                        )
                                        .padding(horizontal = 16.dp, vertical = 6.dp)
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(7.dp)
                                                .clip(CircleShape)
                                                .background(accentColor)
                                        )
                                        Text(
                                            text = stringResource(id = R.string.overlay_launchpad).uppercase(),
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color.White,
                                            letterSpacing = 1.6.sp
                                        )
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(6.dp))

                        // Tab switcher bar
                        TabSwitcherBar(
                            selectedTab = selectedTab,
                            onTabSelected = { tab ->
                                if (tab != OrbitTab.BROWSER) {
                                    isMaximized = false
                                }
                                selectedTab = tab
                                val key = when (tab) {
                                    OrbitTab.LAUNCHER -> ToolsPreferences.KEY_LAUNCHPAD
                                    OrbitTab.VAULT -> ToolsPreferences.KEY_VAULT
                                    OrbitTab.CALCULATOR -> ToolsPreferences.KEY_CALCULATOR
                                    OrbitTab.SPEED_DIAL -> ToolsPreferences.KEY_SPEED_DIAL
                                    OrbitTab.BROWSER -> ToolsPreferences.KEY_BROWSER
                                }
                                ToolsPreferences.incrementLaunchCount(context, key)
                            },
                            accentColor = accentColor
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        // Tab Content Switcher with maximum available vertical height
                        Box(modifier = Modifier.weight(1f)) {
                    when (selectedTab) {
                        OrbitTab.LAUNCHER -> {
                            LauncherTabContent(
                                isLoading = isLoading,
                                searchQuery = searchQuery,
                                onSearchQueryChange = { searchQuery = it },
                                filteredApps = filteredApps,
                                favoriteApps = favoriteApps,
                                regularApps = regularApps,
                                onToggleFavorite = onToggleFavorite,
                                onDismiss = { animateDismiss() },
                                sortedAlphabetically = sortedAlphabetically,
                                explanationReason = explanationReason,
                                accentColor = accentColor
                            )
                        }
                        OrbitTab.VAULT -> {
                            val displayEntries = remember(filteredVaultEntries, currentFolderId) {
                                filteredVaultEntries.filter { it.folderId == currentFolderId }
                            }
                            val currentFolderName = remember(savedVaultFolders, currentFolderId) {
                                savedVaultFolders.find { it.id == currentFolderId }?.name ?: "Root"
                            }

                            VaultTabContent(
                                vaultText = vaultInputText,
                                onVaultTextChange = { vaultInputText = it },
                                savedEntries = displayEntries,
                                folders = savedVaultFolders,
                                currentFolderId = currentFolderId,
                                currentFolderName = currentFolderName,
                                onCurrentFolderIdChange = { currentFolderId = it },
                                onCreateFolder = { folderName ->
                                    coroutineScope.launch(Dispatchers.IO) {
                                        repository.insertVaultFolder(VaultFolder(name = folderName))
                                    }
                                },
                                onDeleteFolder = { folder ->
                                    coroutineScope.launch(Dispatchers.IO) {
                                        repository.deleteVaultFolder(folder)
                                        if (currentFolderId == folder.id) {
                                            withContext(Dispatchers.Main) {
                                                currentFolderId = null
                                            }
                                        }
                                    }
                                },
                                onSaveEntry = {
                                    if (vaultInputText.isNotBlank()) {
                                        coroutineScope.launch(Dispatchers.IO) {
                                            val currentEditing = editingEntry
                                            if (currentEditing != null) {
                                                val updated = currentEditing.copy(content = vaultInputText)
                                                repository.updateVaultEntry(updated)
                                                withContext(Dispatchers.Main) {
                                                    editingEntry = null
                                                    vaultInputText = ""
                                                }
                                            } else {
                                                repository.insertVaultEntry(
                                                    VaultEntry(
                                                        content = vaultInputText,
                                                        folderId = currentFolderId
                                                    )
                                                )
                                                withContext(Dispatchers.Main) {
                                                    vaultInputText = ""
                                                }
                                            }
                                        }
                                    }
                                },
                                onDeleteEntry = { entry ->
                                    coroutineScope.launch(Dispatchers.IO) {
                                        repository.deleteVaultEntry(entry)
                                        if (editingEntry?.id == entry.id) {
                                            withContext(Dispatchers.Main) {
                                                editingEntry = null
                                                vaultInputText = ""
                                            }
                                        }
                                    }
                                },
                                editingEntry = editingEntry,
                                onStartEditing = { entry ->
                                    editingEntry = entry
                                    vaultInputText = entry?.content ?: ""
                                },
                                accentColor = accentColor,
                                onScanScreen = {
                                    ToolsPreferences.incrementLaunchCount(context, ToolsPreferences.KEY_OCR)
                                    // Show the full-screen translucent selection overlay.
                                    // The overlay's "Capture" callback handles both the
                                    // "already have a projection" path (skip consent dialog)
                                    // and the "need consent first" path (launch dialog).
                                    if (ScreenCaptureManager.hasActiveProjection()) {
                                        isScanOverlayVisible = true
                                        val serviceIntent = Intent(context, FloatingLauncherService::class.java).apply {
                                            action = FloatingLauncherService.ACTION_HIDE_BUBBLE
                                        }
                                        context.startService(serviceIntent)
                                    } else {
                                        val intent = ScreenCaptureManager.createScreenCaptureIntent(context)
                                        mediaProjectionLauncher.launch(intent)
                                    }
                                }
                            )
                        }
                        OrbitTab.CALCULATOR -> {
                            CalculatorTabContent(
                                calculatorInput = calculatorInputText,
                                onCalculatorInputChange = { calculatorInputText = it },
                                accentColor = accentColor
                            )
                        }
                        OrbitTab.SPEED_DIAL -> {
                            SpeedDialTabContent(
                                speedDialLabel = speedDialLabelText,
                                onSpeedDialLabelChange = { speedDialLabelText = it },
                                speedDialUrl = speedDialUrlText,
                                onSpeedDialUrlChange = { speedDialUrlText = it },
                                speedDialEntries = speedDialEntries,
                                onSaveLink = {
                                    coroutineScope.launch(Dispatchers.IO) {
                                        repository.insertSpeedDialEntry(
                                            SpeedDialEntry(
                                                label = speedDialLabelText,
                                                url = speedDialUrlText,
                                                sortOrder = speedDialEntries.size
                                            )
                                        )
                                        withContext(Dispatchers.Main) {
                                            speedDialLabelText = ""
                                            speedDialUrlText = ""
                                        }
                                    }
                                },
                                onDeleteLink = { entry ->
                                    coroutineScope.launch(Dispatchers.IO) {
                                        repository.deleteSpeedDialEntry(entry)
                                    }
                                },
                                accentColor = accentColor,
                                onOpenInFloatingWebView = { url ->
                                    activeBrowserUrl = url
                                    selectedTab = OrbitTab.BROWSER
                                    ToolsPreferences.incrementLaunchCount(context, ToolsPreferences.KEY_BROWSER)
                                }
                            )
                        }
                        OrbitTab.BROWSER -> {
                            FloatingWebViewContent(
                                initialUrl = activeBrowserUrl,
                                accentColor = accentColor,
                                isMaximized = isMaximized,
                                onToggleMaximize = { isMaximized = !isMaximized },
                                onUrlNavigated = { url -> activeBrowserUrl = url }
                            )
                        }
                    }
                        }

                        // Bottom Action Bar: Fixed full-width Close Button for standard non-Browser tabs
                        if (selectedTab != OrbitTab.BROWSER) {
                            Spacer(modifier = Modifier.height(10.dp))
                            Button(
                                onClick = { animateDismiss() },
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0x10FFFFFF)),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(44.dp)
                                    .border(1.dp, Color.White.copy(alpha = 0.12f), RoundedCornerShape(14.dp)),
                                shape = RoundedCornerShape(14.dp),
                                contentPadding = PaddingValues(0.dp)
                            ) {
                                Text(
                                    stringResource(id = R.string.overlay_close),
                                    color = Color(0xFFC4CBDC),
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Medium,
                                    letterSpacing = 0.5.sp
                                )
                            }
                        }
                    }
                }
            }
        }

        // ----------------------------------------------------------------
        // Scan Screen (OCR) overlay layer.
        // Rendered on top of the launchpad Card when the user taps "Scan Screen".
        // The overlay itself forces LTR internally (so Arabic UI mode does NOT
        // mirror the selection rectangle). On "Capture" we send the region directly
        // to the service since permission is guaranteed to be granted.
        // ----------------------------------------------------------------
        if (isScanOverlayVisible) {
            ScreenCaptureOverlay(
                onCancel = {
                    isScanOverlayVisible = false
                    // Restore the bubble
                    val serviceIntent = Intent(context, FloatingLauncherService::class.java).apply {
                        action = FloatingLauncherService.ACTION_SHOW_BUBBLE
                    }
                    context.startService(serviceIntent)
                },
                onCapture = { composeRect ->
                    // Convert Compose's geometry Rect to android.graphics.Rect (Int),
                    // matching the raw screen pixels MediaProjection will produce.
                    val region = Rect(
                        composeRect.left.toInt(),
                        composeRect.top.toInt(),
                        composeRect.right.toInt(),
                        composeRect.bottom.toInt()
                    )

                    val serviceIntent = Intent(context, FloatingLauncherService::class.java).apply {
                        action = FloatingLauncherService.ACTION_CAPTURE_SCREEN
                        putExtra(FloatingLauncherService.EXTRA_CAPTURE_REGION, region)
                    }
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        context.startForegroundService(serviceIntent)
                    } else {
                        context.startService(serviceIntent)
                    }
                    isScanOverlayVisible = false
                    animateDismiss()
                }
            )
        }
    }
}

@Composable
fun AppGridItem(
    app: AppInfo,
    isFavorite: Boolean,
    accentColor: Color,
    onToggleFavorite: () -> Unit,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.88f else 1f,
        animationSpec = spring(dampingRatio = 0.6f, stiffness = 600f),
        label = "AppGridItemScale"
    )

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            )
            .padding(horizontal = 2.dp, vertical = 2.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // High-tech Squircle App Icon Container
            Box(
                modifier = Modifier
                    .size(54.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(
                        brush = Brush.verticalGradient(
                            colors = if (isFavorite) {
                                listOf(
                                    Color(0xFF222840),
                                    Color(0xFF121524)
                                )
                            } else {
                                listOf(
                                    Color(0xFF1B2032),
                                    Color(0xFF101320)
                                )
                            }
                        )
                    )
                    .border(
                        width = if (isFavorite) 1.5.dp else 1.dp,
                        brush = if (isFavorite) {
                            Brush.verticalGradient(
                                listOf(
                                    Color(0xFFFFD600),
                                    Color(0xFFFFB300).copy(alpha = 0.4f)
                                )
                            )
                        } else {
                            Brush.verticalGradient(
                                listOf(
                                    Color.White.copy(alpha = 0.14f),
                                    Color.White.copy(alpha = 0.03f)
                                )
                            )
                        },
                        shape = RoundedCornerShape(16.dp)
                    )
                    .padding(8.dp),
                contentAlignment = Alignment.Center
            ) {
                if (app.icon != null) {
                    AndroidView(
                        factory = { ctx ->
                            ImageView(ctx).apply {
                                scaleType = ImageView.ScaleType.FIT_CENTER
                            }
                        },
                        update = { imageView ->
                            imageView.setImageDrawable(app.icon)
                        },
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.Gray.copy(alpha = 0.3f), RoundedCornerShape(8.dp))
                    )
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            // App Label
            Text(
                text = app.label,
                fontSize = 11.5.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
                color = if (isFavorite) Color.White else Color(0xFFD6DBE8),
                modifier = Modifier.fillMaxWidth()
            )
        }

        // Refined Floating Star Favorite Badge at top-right corner
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .offset(x = 4.dp, y = (-4).dp)
                .size(22.dp)
                .clip(CircleShape)
                .background(
                    if (isFavorite) Color(0xFF262111) else Color(0xFF161A28)
                )
                .border(
                    1.dp,
                    if (isFavorite) Color(0xFFFFD600).copy(alpha = 0.8f) else Color.White.copy(alpha = 0.12f),
                    CircleShape
                )
                .clickable { onToggleFavorite() },
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = if (isFavorite) Icons.Filled.Star else Icons.Outlined.Star,
                contentDescription = "Favorite",
                tint = if (isFavorite) Color(0xFFFFD600) else Color(0xFF7A849C),
                modifier = Modifier.size(13.dp)
            )
        }
    }
}

private fun hasUsagePermission(context: Context): Boolean {
    val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as? AppOpsManager ?: return false
    val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        appOps.unsafeCheckOpNoThrow(
            AppOpsManager.OPSTR_GET_USAGE_STATS,
            android.os.Process.myUid(),
            context.packageName
        )
    } else {
        @Suppress("DEPRECATION")
        appOps.checkOpNoThrow(
            AppOpsManager.OPSTR_GET_USAGE_STATS,
            android.os.Process.myUid(),
            context.packageName
        )
    }
    return mode == AppOpsManager.MODE_ALLOWED
}
