package com.example

import android.content.Context
import android.content.Intent
import android.widget.ImageView
import android.widget.Toast
import androidx.browser.customtabs.CustomTabsIntent
import android.net.Uri
import androidx.compose.animation.*
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.animateColorAsState
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.VaultEntry
import com.example.data.SpeedDialEntry
import com.example.data.VaultFolder
import com.example.ui.theme.TextSecondary
import com.example.ocr.OcrManager

@Composable
fun TabSwitcherBar(
    selectedTab: OrbitTab,
    onTabSelected: (OrbitTab) -> Unit,
    accentColor: Color
) {
    val context = LocalContext.current
    val allTabs = listOf(
        Tuple4(OrbitTab.LAUNCHER, Icons.Default.Apps, stringResource(id = R.string.tool_launcher_name), ToolsPreferences.KEY_LAUNCHPAD),
        Tuple4(OrbitTab.VAULT, Icons.Default.Lock, stringResource(id = R.string.tool_vault_name), ToolsPreferences.KEY_VAULT),
        Tuple4(OrbitTab.CALCULATOR, Icons.Default.Calculate, stringResource(id = R.string.tool_calc_name), ToolsPreferences.KEY_CALCULATOR),
        Tuple4(OrbitTab.SPEED_DIAL, Icons.Default.Link, stringResource(id = R.string.tool_dial_name), ToolsPreferences.KEY_SPEED_DIAL),
        Tuple4(OrbitTab.BROWSER, Icons.Default.Language, stringResource(id = R.string.tool_browser_name), ToolsPreferences.KEY_BROWSER)
    )

    val activeTabs = allTabs.filter { ToolsPreferences.isToolEnabled(context, it.fourth) }

    // If currently selected tab is disabled, switch to first active tab
    LaunchedEffect(activeTabs) {
        if (activeTabs.isNotEmpty() && activeTabs.none { it.first == selectedTab }) {
            onTabSelected(activeTabs.first().first)
        }
    }

    if (activeTabs.isEmpty()) return

    Row(
        modifier = Modifier
            .fillOuterWidth()
            .height(58.dp)
            .background(Color(0x0CFFFFFF), RoundedCornerShape(14.dp))
            .border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(14.dp))
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        activeTabs.forEach { (tab, icon, label, _) ->
            val isSelected = selectedTab == tab
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(10.dp))
                    .background(if (isSelected) accentColor.copy(alpha = 0.22f) else Color.Transparent)
                    .border(
                        1.dp,
                        if (isSelected) accentColor.copy(alpha = 0.45f) else Color.Transparent,
                        RoundedCornerShape(10.dp)
                    )
                    .clickable { onTabSelected(tab) }
                    .padding(horizontal = 2.dp, vertical = 4.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.fillMaxSize()
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = label,
                        tint = if (isSelected) accentColor else TextSecondary.copy(alpha = 0.85f),
                        modifier = Modifier.size(19.dp)
                    )
                    Spacer(modifier = Modifier.height(3.dp))
                    Text(
                        text = label,
                        fontSize = 10.5.sp,
                        fontWeight = if (isSelected) FontWeight.Medium else FontWeight.Normal,
                        color = if (isSelected) accentColor else TextSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

internal data class Tuple4<A, B, C, D>(val first: A, val second: B, val third: C, val fourth: D)

private fun Modifier.fillOuterWidth(): Modifier = this.fillMaxWidth()

@Composable
fun LauncherTabContent(
    isLoading: Boolean,
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    filteredApps: List<AppInfo>,
    favoriteApps: List<AppInfo>,
    regularApps: List<AppInfo>,
    onToggleFavorite: (AppInfo) -> Unit,
    onDismiss: () -> Unit,
    sortedAlphabetically: Boolean,
    explanationReason: String,
    accentColor: Color
) {
    val context = LocalContext.current

    Column(modifier = Modifier.fillMaxSize()) {
        Spacer(modifier = Modifier.height(8.dp))

        // Sorting Subtitle / Explanation
        if (sortedAlphabetically && searchQuery.isEmpty()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0x11FF1744), RoundedCornerShape(8.dp))
                    .padding(8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Info,
                    contentDescription = "Alert",
                    tint = Color(0xFFFF4D4D),
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = explanationReason,
                    color = Color(0xFFFFB3B3),
                    fontSize = 11.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f)
                )
            }
        } else if (searchQuery.isEmpty()) {
            Text(
                text = stringResource(id = R.string.overlay_most_used),
                color = TextSecondary,
                fontSize = 12.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Search Bar Input Field
        TextField(
            value = searchQuery,
            onValueChange = onSearchQueryChange,
            placeholder = { Text(stringResource(id = R.string.overlay_search_placeholder), color = TextSecondary, fontSize = 14.sp) },
            colors = TextFieldDefaults.colors(
                focusedContainerColor = Color(0x1400E5FF),
                unfocusedContainerColor = Color(0x0AFFFFFF),
                focusedIndicatorColor = accentColor,
                unfocusedIndicatorColor = Color.Transparent,
                focusedTextColor = Color.White,
                unfocusedTextColor = Color.White
            ),
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp)
                .clip(RoundedCornerShape(12.dp)),
            singleLine = true,
            leadingIcon = {
                Icon(
                    imageVector = Icons.Default.Search,
                    contentDescription = "Search",
                    tint = accentColor
                )
            }
        )

        Spacer(modifier = Modifier.height(12.dp))

        if (isLoading) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(color = accentColor)
            }
        } else if (filteredApps.isEmpty()) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = stringResource(id = R.string.overlay_no_matching),
                    color = TextSecondary,
                    fontSize = 14.sp
                )
            }
        } else {
            // Unified Grid using span support to build clean Sections (Favorites vs Regular Apps)
            LazyVerticalGrid(
                columns = GridCells.Fixed(4),
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(bottom = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Section: Favorites (Show only when not searching)
                if (favoriteApps.isNotEmpty() && searchQuery.isEmpty()) {
                    item(span = { GridItemSpan(4) }) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(top = 4.dp, bottom = 4.dp)
                        ) {
                            Text(
                                text = stringResource(id = R.string.overlay_favorites),
                                color = accentColor,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            HorizontalDivider(
                                color = accentColor.copy(alpha = 0.2f),
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }

                    items(favoriteApps) { app ->
                        AppGridItem(
                            app = app,
                            isFavorite = true,
                            accentColor = accentColor,
                            onToggleFavorite = { onToggleFavorite(app) },
                            onClick = {
                                val pm = context.packageManager
                                val launchIntent = pm.getLaunchIntentForPackage(app.packageName)
                                if (launchIntent != null) {
                                    context.startActivity(launchIntent)
                                    onDismiss()
                                } else {
                                    Toast.makeText(context, context.getString(R.string.overlay_cannot_open), Toast.LENGTH_SHORT).show()
                                }
                            }
                        )
                    }

                    item(span = { GridItemSpan(4) }) {
                        Spacer(modifier = Modifier.height(4.dp))
                    }
                }

                // Section: Regular or Filtered Apps
                item(span = { GridItemSpan(4) }) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(bottom = 4.dp)
                    ) {
                        Text(
                            text = if (searchQuery.isNotEmpty()) stringResource(id = R.string.overlay_search_results) else stringResource(id = R.string.overlay_all_apps),
                            color = accentColor,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        HorizontalDivider(
                            color = accentColor.copy(alpha = 0.2f),
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                items(regularApps) { app ->
                    AppGridItem(
                        app = app,
                        isFavorite = false,
                        accentColor = accentColor,
                        onToggleFavorite = { onToggleFavorite(app) },
                        onClick = {
                            val pm = context.packageManager
                            val launchIntent = pm.getLaunchIntentForPackage(app.packageName)
                            if (launchIntent != null) {
                                context.startActivity(launchIntent)
                                onDismiss()
                            } else {
                                Toast.makeText(context, context.getString(R.string.overlay_cannot_open), Toast.LENGTH_SHORT).show()
                            }
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun VaultTabContent(
    vaultText: String,
    onVaultTextChange: (String) -> Unit,
    savedEntries: List<VaultEntry>,
    folders: List<VaultFolder>,
    currentFolderId: Long?,
    currentFolderName: String,
    onCurrentFolderIdChange: (Long?) -> Unit,
    onCreateFolder: (String) -> Unit,
    onDeleteFolder: (VaultFolder) -> Unit,
    onSaveEntry: () -> Unit,
    onDeleteEntry: (VaultEntry) -> Unit,
    editingEntry: com.example.data.VaultEntry?,
    onStartEditing: (com.example.data.VaultEntry?) -> Unit,
    accentColor: Color,
    /**
     * Invoked when the user taps the "Scan Screen" button. The host (OverlayActivity)
     * is responsible for showing the ScreenCaptureOverlay and kicking off the
     * MediaProjection + OCR flow. The Vault tab itself does NOT touch MediaProjection
     * or Tesseract — separation of concerns.
     */
    onScanScreen: () -> Unit = {}
) {
    val context = LocalContext.current
    val isModelDownloaded by OcrManager.isModelDownloaded.collectAsState()
    val isDownloading by OcrManager.isDownloading.collectAsState()

    var isCreatingFolder by remember { mutableStateOf(false) }
    var newFolderName by remember { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(vertical = 4.dp)
    ) {
        // Edit Mode Header Info
        if (editingEntry != null) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(accentColor.copy(alpha = 0.1f), RoundedCornerShape(8.dp))
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = stringResource(id = R.string.editing_saved_note),
                    color = accentColor,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = stringResource(id = R.string.cancel_edit),
                    color = Color(0xFFFF5252),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .clickable { onStartEditing(null) }
                        .padding(horizontal = 8.dp, vertical = 2.dp)
                )
            }
            Spacer(modifier = Modifier.height(6.dp))
        }

        // Premium Redesigned Flagship OCR Action - Scanner Control Deck
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 12.dp)
        ) {
            // Header tag with subtle Live/Ready status
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp, vertical = 4.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .background(accentColor, RoundedCornerShape(50))
                    )
                    Text(
                        text = stringResource(id = R.string.screen_optics),
                        color = accentColor.copy(alpha = 0.85f),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.2.sp
                    )
                }
                
                Text(
                    text = stringResource(id = R.string.latin_only),
                    color = TextSecondary.copy(alpha = 0.5f),
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.5.sp
                )
            }

            // The main asymmetrical premium Scanner Panel
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(Color(0xFF0F1524))
                    .border(
                        width = 1.dp,
                        color = Color.White.copy(alpha = 0.08f),
                        shape = RoundedCornerShape(14.dp)
                    )
                    .padding(14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // Left Accent bar + Description Column
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    // Futuristic Neon Vertical Bar
                    Box(
                        modifier = Modifier
                            .width(3.dp)
                            .height(44.dp)
                            .background(
                                brush = Brush.verticalGradient(
                                    colors = listOf(
                                        accentColor,
                                        accentColor.copy(alpha = 0.2f)
                                    )
                                ),
                                shape = RoundedCornerShape(1.5.dp)
                            )
                    )

                    Spacer(modifier = Modifier.width(12.dp))

                    Column {
                        Text(
                            text = stringResource(id = R.string.ocr_screen_capture),
                            color = Color.White,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.SemiBold,
                            letterSpacing = 0.15.sp
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        val statusText = if (isDownloading) {
                            stringResource(id = R.string.ocr_downloading)
                        } else if (!isModelDownloaded) {
                            stringResource(id = R.string.ocr_not_ready)
                        } else {
                            stringResource(id = R.string.ocr_extract_desc)
                        }
                        val statusColor = if (isDownloading) {
                            accentColor
                        } else if (!isModelDownloaded) {
                            Color(0xFFFFB300)
                        } else {
                            TextSecondary.copy(alpha = 0.85f)
                        }
                        Text(
                            text = statusText,
                            color = statusColor,
                            fontSize = 12.sp,
                            lineHeight = 15.sp
                        )
                    }
                }

                Spacer(modifier = Modifier.width(8.dp))

                // Interactive Optical Scanner Lens trigger
                val interactionSource = remember { MutableInteractionSource() }
                val isPressed by interactionSource.collectIsPressedAsState()
                val shutterScale by animateFloatAsState(
                    targetValue = if (isPressed && !isDownloading) 0.90f else 1.0f,
                    label = "ShutterScaleAnimation"
                )
                val shutterBgColor by animateColorAsState(
                    targetValue = if (isPressed && !isDownloading) accentColor.copy(alpha = 0.35f) else accentColor.copy(alpha = 0.15f),
                    label = "ShutterBgAnimation"
                )
                val shutterBorderColor by animateColorAsState(
                    targetValue = if (isPressed && !isDownloading) accentColor else accentColor.copy(alpha = 0.5f),
                    label = "ShutterBorderAnimation"
                )

                Box(
                    modifier = Modifier
                        .graphicsLayer {
                            scaleX = shutterScale
                            scaleY = shutterScale
                        }
                        .size(52.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(shutterBgColor)
                        .border(
                            width = 1.dp,
                            color = shutterBorderColor,
                            shape = RoundedCornerShape(16.dp)
                        )
                        .clickable(
                            enabled = !isDownloading,
                            interactionSource = interactionSource,
                            indication = androidx.compose.foundation.LocalIndication.current,
                            onClick = {
                                if (isModelDownloaded) {
                                    onScanScreen()
                                } else {
                                    OcrManager.checkModelStatus()
                                }
                            }
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .border(
                                width = 1.dp,
                                color = accentColor.copy(alpha = 0.3f),
                                shape = RoundedCornerShape(10.dp)
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        if (isDownloading) {
                            CircularProgressIndicator(
                                color = accentColor,
                                strokeWidth = 2.dp,
                                modifier = Modifier.size(18.dp)
                            )
                        } else if (!isModelDownloaded) {
                            Icon(
                                imageVector = Icons.Default.Download,
                                contentDescription = "Download OCR Model",
                                tint = accentColor,
                                modifier = Modifier.size(20.dp)
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Default.DocumentScanner,
                                contentDescription = "Trigger Screen Scan",
                                tint = accentColor,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            // Non-intrusive informational notice directly below the OCR action (subtle Card chip)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0xFF0F1524).copy(alpha = 0.5f))
                    .border(0.5.dp, accentColor.copy(alpha = 0.15f), RoundedCornerShape(8.dp))
                    .padding(horizontal = 10.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Info,
                    contentDescription = "Info Icon",
                    tint = accentColor.copy(alpha = 0.6f),
                    modifier = Modifier.size(13.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = stringResource(id = R.string.ocr_english_only),
                    color = TextSecondary.copy(alpha = 0.8f),
                    fontSize = 11.sp,
                    lineHeight = 14.sp
                )
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Compose section
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextField(
                value = vaultText,
                onValueChange = onVaultTextChange,
                placeholder = { Text(stringResource(id = R.string.write_quick_note_hint), color = TextSecondary, fontSize = 13.sp) },
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = Color(0x1400E5FF),
                    unfocusedContainerColor = Color(0x0AFFFFFF),
                    focusedIndicatorColor = accentColor,
                    unfocusedIndicatorColor = Color.Transparent,
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White
                ),
                modifier = Modifier
                    .weight(1f)
                    .height(80.dp)
                    .clip(RoundedCornerShape(12.dp)),
                maxLines = 4
            )
            
            Spacer(modifier = Modifier.width(8.dp))
            
            IconButton(
                onClick = onSaveEntry,
                enabled = vaultText.isNotBlank(),
                modifier = Modifier
                    .size(50.dp)
                    .background(
                        if (vaultText.isNotBlank()) accentColor else Color.White.copy(alpha = 0.05f),
                        RoundedCornerShape(12.dp)
                    )
            ) {
                Icon(
                    imageVector = if (editingEntry != null) Icons.Default.Check else Icons.Default.Add,
                    contentDescription = if (editingEntry != null) stringResource(id = R.string.update_note) else stringResource(id = R.string.save_note),
                    tint = if (vaultText.isNotBlank()) Color.Black else TextSecondary
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Folders Section Header and Controls
        if (currentFolderId == null) {
            // Root View Folder Section
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = stringResource(id = R.string.vault_folders),
                    color = accentColor,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold
                )
                
                IconButton(
                    onClick = { isCreatingFolder = !isCreatingFolder },
                    modifier = Modifier.size(24.dp)
                ) {
                    Icon(
                        imageVector = if (isCreatingFolder) Icons.Default.Close else Icons.Default.CreateNewFolder,
                        contentDescription = "New Folder",
                        tint = accentColor,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            if (isCreatingFolder) {
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    TextField(
                        value = newFolderName,
                        onValueChange = { newFolderName = it },
                        placeholder = { Text(stringResource(id = R.string.folder_name_hint), color = TextSecondary, fontSize = 14.sp) },
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = Color(0x1400E5FF),
                            unfocusedContainerColor = Color(0x0AFFFFFF),
                            focusedIndicatorColor = accentColor,
                            unfocusedIndicatorColor = Color.Transparent,
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White
                        ),
                        textStyle = LocalTextStyle.current.copy(fontSize = 14.sp),
                        modifier = Modifier
                            .weight(1f)
                            .height(52.dp)
                            .clip(RoundedCornerShape(12.dp)),
                        singleLine = true
                    )
                    IconButton(
                        onClick = {
                            if (newFolderName.isNotBlank()) {
                                onCreateFolder(newFolderName)
                                newFolderName = ""
                                isCreatingFolder = false
                            }
                        },
                        enabled = newFolderName.isNotBlank(),
                        modifier = Modifier
                            .size(52.dp)
                            .background(
                                if (newFolderName.isNotBlank()) accentColor else Color.White.copy(alpha = 0.05f),
                                RoundedCornerShape(12.dp)
                            )
                    ) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = "Create Folder",
                            tint = if (newFolderName.isNotBlank()) Color.Black else TextSecondary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.height(14.dp))
            }
        } else {
            // Folder Breadcrumbs Back Navigation Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .clickable { onCurrentFolderIdChange(null) }
                    .background(Color(0x1A00E5FF), RoundedCornerShape(8.dp))
                    .border(0.5.dp, accentColor.copy(alpha = 0.3f), RoundedCornerShape(8.dp))
                    .padding(horizontal = 10.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.ArrowBack,
                    contentDescription = "Back",
                    tint = accentColor,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = stringResource(id = R.string.root_folder_prefix, currentFolderName ?: ""),
                    color = Color.White,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Main List Content (Folders & Notes)
        if (currentFolderId == null && folders.isEmpty() && savedEntries.isEmpty()) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = stringResource(id = R.string.vault_empty_msg),
                    color = TextSecondary,
                    fontSize = 13.sp,
                    textAlign = TextAlign.Center
                )
            }
        } else if (currentFolderId != null && savedEntries.isEmpty()) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = stringResource(id = R.string.folder_empty_msg),
                    color = TextSecondary,
                    fontSize = 13.sp,
                    textAlign = TextAlign.Center
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // 1. Folders List (only shown in Root view)
                if (currentFolderId == null && folders.isNotEmpty()) {
                    items(folders) { folder ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .clickable { onCurrentFolderIdChange(folder.id) }
                                .background(Color(0x0AFFFFFF), RoundedCornerShape(10.dp))
                                .border(1.dp, Color.White.copy(alpha = 0.05f), RoundedCornerShape(10.dp))
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Folder,
                                contentDescription = "Folder",
                                tint = accentColor,
                                modifier = Modifier.size(22.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = folder.name,
                                color = Color.White,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.weight(1f)
                            )
                            IconButton(
                                onClick = { onDeleteFolder(folder) },
                                modifier = Modifier.size(28.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Delete,
                                    contentDescription = "Delete Folder",
                                    tint = Color(0xFFFF4D4D).copy(alpha = 0.8f),
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }
                }

                // Header for Notes when both exist in Root view
                if (currentFolderId == null && folders.isNotEmpty() && savedEntries.isNotEmpty()) {
                    item {
                        Text(
                            text = stringResource(id = R.string.root_notes),
                            color = accentColor,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(top = 4.dp, bottom = 2.dp)
                        )
                    }
                }

                // 2. Notes / Entries List
                items(savedEntries) { entry ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color(0x0AFFFFFF), RoundedCornerShape(10.dp))
                            .border(1.dp, Color.White.copy(alpha = 0.05f), RoundedCornerShape(10.dp))
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            // Source badges
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(bottom = 6.dp)
                            ) {
                                if (entry.source == "OCR") {
                                    Box(
                                        modifier = Modifier
                                            .background(accentColor.copy(alpha = 0.15f), RoundedCornerShape(4.dp))
                                            .border(0.5.dp, accentColor, RoundedCornerShape(4.dp))
                                            .padding(horizontal = 6.dp, vertical = 2.dp)
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.CameraAlt,
                                                contentDescription = "OCR badge icon",
                                                tint = accentColor,
                                                modifier = Modifier.size(10.dp)
                                            )
                                            Text(
                                                text = stringResource(id = R.string.screen_ocr_badge),
                                                color = accentColor,
                                                fontSize = 9.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                    }
                                } else {
                                    Box(
                                        modifier = Modifier
                                            .background(Color.White.copy(alpha = 0.08f), RoundedCornerShape(4.dp))
                                            .border(0.5.dp, Color.White.copy(alpha = 0.15f), RoundedCornerShape(4.dp))
                                            .padding(horizontal = 6.dp, vertical = 2.dp)
                                    ) {
                                        Text(
                                            text = stringResource(id = R.string.manual_badge),
                                            color = Color.White.copy(alpha = 0.6f),
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            }

                            Text(
                                text = entry.content,
                                color = Color.White,
                                fontSize = 13.sp,
                                maxLines = 4,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        
                        Spacer(modifier = Modifier.width(8.dp))

                        // Edit note row button
                        IconButton(
                            onClick = { onStartEditing(entry) },
                            modifier = Modifier.size(30.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Edit,
                                contentDescription = "Edit Note",
                                tint = accentColor.copy(alpha = 0.85f),
                                modifier = Modifier.size(16.dp)
                            )
                        }

                        // Copy to Clipboard Button
                        IconButton(
                            onClick = {
                                val clipboardManager = context.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                                val clipData = android.content.ClipData.newPlainText("Vault Note", entry.content)
                                clipboardManager.setPrimaryClip(clipData)
                                Toast.makeText(context, context.getString(R.string.copied_to_clipboard), Toast.LENGTH_SHORT).show()
                            },
                            modifier = Modifier.size(30.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.ContentCopy,
                                contentDescription = "Copy entry",
                                tint = accentColor,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                        
                        IconButton(
                            onClick = { onDeleteEntry(entry) },
                            modifier = Modifier.size(30.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Delete,
                                contentDescription = "Delete entry",
                                tint = Color(0xFFFF4D4D),
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun CalculatorTabContent(
    calculatorInput: String,
    onCalculatorInputChange: (String) -> Unit,
    accentColor: Color
) {
    val context = LocalContext.current
    var resultText by remember { mutableStateOf("") }
    var errorText by remember { mutableStateOf("") }

    // Synchronously parse and evaluate the live input expression
    LaunchedEffect(calculatorInput) {
        if (calculatorInput.isBlank()) {
            resultText = ""
            errorText = ""
            return@LaunchedEffect
        }
        try {
            val res = ExpressionEvaluator.evaluate(calculatorInput)
            // Format nice result: omit decimal if it's a whole number
            resultText = if (res % 1 == 0.0) {
                res.toLong().toString()
            } else {
                String.format("%.4f", res).trimEnd('0').trimEnd('.')
            }
            errorText = ""
        } catch (e: Exception) {
            resultText = ""
            errorText = e.message ?: context.getString(R.string.invalid_expression)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(vertical = 4.dp),
        verticalArrangement = Arrangement.Top
    ) {
        Text(
            text = stringResource(id = R.string.floating_calculator),
            color = accentColor,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 8.dp)
        )

        // Mathematical input field
        TextField(
            value = calculatorInput,
            onValueChange = onCalculatorInputChange,
            placeholder = { Text(stringResource(id = R.string.calc_hint), color = TextSecondary, fontSize = 13.sp) },
            colors = TextFieldDefaults.colors(
                focusedContainerColor = Color(0x1400E5FF),
                unfocusedContainerColor = Color(0x0AFFFFFF),
                focusedIndicatorColor = accentColor,
                unfocusedIndicatorColor = Color.Transparent,
                focusedTextColor = Color.White,
                unfocusedTextColor = Color.White
            ),
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp)
                .clip(RoundedCornerShape(12.dp)),
            singleLine = true
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Large Output Card for the evaluation result or error
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0x05FFFFFF), RoundedCornerShape(14.dp))
                .border(1.dp, Color.White.copy(alpha = 0.03f), RoundedCornerShape(14.dp))
                .padding(20.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = stringResource(id = R.string.calc_result),
                    color = TextSecondary,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(6.dp))
                if (errorText.isNotEmpty()) {
                    Text(
                        text = errorText,
                        color = Color(0xFFFF4D4D),
                        fontSize = 14.sp,
                        textAlign = TextAlign.Center,
                        fontWeight = FontWeight.SemiBold
                    )
                } else {
                    Text(
                        text = resultText.ifEmpty { "0" },
                        color = if (resultText.isNotEmpty()) accentColor else Color.White.copy(alpha = 0.4f),
                        fontSize = 28.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Help quick buttons/keys representation
        Text(
            text = stringResource(id = R.string.calc_operators),
            color = TextSecondary,
            fontSize = 11.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun SpeedDialTabContent(
    speedDialLabel: String,
    onSpeedDialLabelChange: (String) -> Unit,
    speedDialUrl: String,
    onSpeedDialUrlChange: (String) -> Unit,
    speedDialEntries: List<SpeedDialEntry>,
    onSaveLink: () -> Unit,
    onDeleteLink: (SpeedDialEntry) -> Unit,
    accentColor: Color,
    onOpenInFloatingWebView: (String) -> Unit
) {
    val context = LocalContext.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(vertical = 4.dp)
    ) {
        // Inline layout to Add a link
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                TextField(
                    value = speedDialLabel,
                    onValueChange = onSpeedDialLabelChange,
                    placeholder = { Text(stringResource(id = R.string.label_hint), color = TextSecondary, fontSize = 14.sp) },
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = Color(0x1400E5FF),
                        unfocusedContainerColor = Color(0x0AFFFFFF),
                        focusedIndicatorColor = accentColor,
                        unfocusedIndicatorColor = Color.Transparent,
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White
                    ),
                    textStyle = LocalTextStyle.current.copy(fontSize = 14.sp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                        .clip(RoundedCornerShape(12.dp)),
                    singleLine = true
                )
                Spacer(modifier = Modifier.height(8.dp))
                TextField(
                    value = speedDialUrl,
                    onValueChange = onSpeedDialUrlChange,
                    placeholder = { Text(stringResource(id = R.string.url_hint), color = TextSecondary, fontSize = 14.sp) },
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = Color(0x1400E5FF),
                        unfocusedContainerColor = Color(0x0AFFFFFF),
                        focusedIndicatorColor = accentColor,
                        unfocusedIndicatorColor = Color.Transparent,
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White
                    ),
                    textStyle = LocalTextStyle.current.copy(fontSize = 14.sp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                        .clip(RoundedCornerShape(12.dp)),
                    singleLine = true
                )
            }
            
            Spacer(modifier = Modifier.width(8.dp))
            
            IconButton(
                onClick = onSaveLink,
                enabled = speedDialLabel.isNotBlank() && speedDialUrl.isNotBlank(),
                modifier = Modifier
                    .size(112.dp)
                    .background(
                        if (speedDialLabel.isNotBlank() && speedDialUrl.isNotBlank()) accentColor else Color.White.copy(alpha = 0.05f),
                        RoundedCornerShape(12.dp)
                    )
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = "Add Link",
                    tint = if (speedDialLabel.isNotBlank() && speedDialUrl.isNotBlank()) Color.Black else TextSecondary
                )
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = stringResource(id = R.string.speed_dial_links),
                color = accentColor,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = stringResource(id = R.string.browser_long_press_hint),
                color = TextSecondary,
                fontSize = 9.5.sp,
                maxLines = 1
            )
        }

        Spacer(modifier = Modifier.height(6.dp))

        if (speedDialEntries.isEmpty()) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = stringResource(id = R.string.no_links_yet),
                    color = TextSecondary,
                    fontSize = 13.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 16.dp)
                )
            }
        } else {
            // Display links in 4 columns like app icons
            LazyVerticalGrid(
                columns = GridCells.Fixed(4),
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(bottom = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                items(speedDialEntries) { entry ->
                    val formattedUrl = remember(entry.url) {
                        if (!entry.url.startsWith("http://") && !entry.url.startsWith("https://")) {
                            "https://${entry.url}"
                        } else {
                            entry.url
                        }
                    }

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .combinedClickable(
                                onClick = {
                                    // Single Tap: Open URL inside Floating WebView
                                    onOpenInFloatingWebView(formattedUrl)
                                },
                                onLongClick = {
                                    // Long-Press: Open URL in default external browser
                                    try {
                                        Toast.makeText(context, "Opening in external browser...", Toast.LENGTH_SHORT).show()
                                        val uri = Uri.parse(formattedUrl)
                                        val customTabsIntent = CustomTabsIntent.Builder()
                                            .setShowTitle(true)
                                            .build()
                                        customTabsIntent.launchUrl(context, uri)
                                    } catch (e: Exception) {
                                        try {
                                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(formattedUrl)).apply {
                                                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                            }
                                            context.startActivity(intent)
                                        } catch (ex: Exception) {
                                            Toast.makeText(context, context.getString(R.string.invalid_link, entry.url), Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                }
                            )
                            .padding(4.dp)
                    ) {
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            // Link Icon styling matches app-grid item
                            Box(
                                modifier = Modifier
                                    .size(48.dp)
                                    .background(Color(0x0A00E5FF), RoundedCornerShape(12.dp))
                                    .border(
                                        width = 1.dp,
                                        color = accentColor.copy(alpha = 0.3f),
                                        shape = RoundedCornerShape(12.dp)
                                    )
                                    .padding(4.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                // Dynamic letter-based or globe icon
                                val letter = entry.label.trim().firstOrNull()?.uppercase() ?: "W"
                                Text(
                                    text = letter.toString(),
                                    color = accentColor,
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }

                            Spacer(modifier = Modifier.height(6.dp))

                            // Link label text
                            Text(
                                text = entry.label,
                                fontSize = 11.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                textAlign = TextAlign.Center,
                                color = Color.White,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }

                        // Small close/delete button overlayed on top right
                        Box(
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .offset(x = 6.dp, y = (-6).dp)
                                .size(22.dp)
                                .background(Color(0xFF151D33), RoundedCornerShape(11.dp))
                                .border(1.dp, Color.White.copy(alpha = 0.1f), RoundedCornerShape(11.dp))
                                .clickable { onDeleteLink(entry) },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Delete",
                                tint = Color(0xFFFF4D4D),
                                modifier = Modifier.size(12.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

