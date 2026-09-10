package com.example

import android.content.Context
import android.content.Intent
import android.provider.Settings
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.launch
import java.util.Calendar

@Composable
fun WifiMonthlyTrackerDialog(
    context: Context,
    onDismiss: () -> Unit
) {
    val neonCyan = Color(0xFF00F0FF)
    val emeraldGreen = Color(0xFF00E676)
    val alertPink = Color(0xFFFF3366)
    val trackBlue = Color(0xFF38BDF8)
    val inkLight = Color(0xFFEEF0F6)
    val inkDim = Color(0xFF8E95AA)

    val currentCal = remember { Calendar.getInstance() }
    val currentYear = remember { currentCal.get(Calendar.YEAR) }
    val currentMonth = remember { currentCal.get(Calendar.MONTH) }

    var selectedYear by remember { mutableStateOf(currentYear) }
    var selectedMonth by remember { mutableStateOf(currentMonth) }

    var summary by remember { mutableStateOf<MonthWifiSummary?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var selectedDayIndex by remember { mutableStateOf<Int?>(null) }

    val coroutineScope = rememberCoroutineScope()
    val listState = rememberLazyListState()

    fun loadMonthData() {
        isLoading = true
        coroutineScope.launch {
            summary = WifiMonitorManager.getMonthDailyWifiUsage(context, selectedYear, selectedMonth)
            isLoading = false
        }
    }

    LaunchedEffect(selectedYear, selectedMonth) {
        loadMonthData()
    }

    val isCurrentMonth = (selectedYear == currentYear && selectedMonth == currentMonth)

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .fillMaxHeight(0.90f)
                .padding(vertical = 12.dp)
                .border(1.dp, neonCyan.copy(alpha = 0.25f), RoundedCornerShape(24.dp)),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF0D1322)),
            shape = RoundedCornerShape(24.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(18.dp)
            ) {
                // Top Navigation Bar
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(38.dp)
                                .clip(CircleShape)
                                .background(neonCyan.copy(alpha = 0.15f))
                                .border(1.dp, neonCyan.copy(alpha = 0.35f), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.CalendarMonth,
                                contentDescription = null,
                                tint = neonCyan,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Column {
                            Text(
                                text = stringResource(id = R.string.monthly_tracker_title),
                                color = inkLight,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = stringResource(id = R.string.monthly_tracker_subtitle),
                                color = inkDim,
                                fontSize = 11.sp
                            )
                        }
                    }

                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close",
                            tint = inkDim,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Month Selector Bar (Previous, Label, Next)
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(14.dp)),
                    colors = CardDefaults.cardColors(containerColor = Color(0x18FFFFFF)),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        IconButton(
                            onClick = {
                                if (selectedMonth == 0) {
                                    selectedMonth = 11
                                    selectedYear -= 1
                                } else {
                                    selectedMonth -= 1
                                }
                            },
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Previous Month",
                                tint = neonCyan,
                                modifier = Modifier.size(18.dp)
                            )
                        }

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                text = summary?.monthLabel ?: "Loading…",
                                color = inkLight,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.ExtraBold
                            )
                            if (isCurrentMonth) {
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(4.dp))
                                        .background(emeraldGreen.copy(alpha = 0.2f))
                                        .padding(horizontal = 5.dp, vertical = 2.dp)
                                ) {
                                    Text(
                                        text = "CURRENT",
                                        color = emeraldGreen,
                                        fontSize = 8.sp,
                                        fontWeight = FontWeight.ExtraBold
                                    )
                                }
                            }
                        }

                        IconButton(
                            onClick = {
                                if (!isCurrentMonth) {
                                    if (selectedMonth == 11) {
                                        selectedMonth = 0
                                        selectedYear += 1
                                    } else {
                                        selectedMonth += 1
                                    }
                                }
                            },
                            enabled = !isCurrentMonth,
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                                contentDescription = "Next Month",
                                tint = if (!isCurrentMonth) neonCyan else inkDim.copy(alpha = 0.3f),
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                if (isLoading) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(
                            color = neonCyan,
                            strokeWidth = 2.5.dp,
                            modifier = Modifier.size(32.dp)
                        )
                    }
                } else if (summary != null) {
                    val s = summary!!

                    // 1. Overview Metric Pods (Total, Daily Average, Peak Day)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // Total Usage Pod
                        Card(
                            modifier = Modifier
                                .weight(1f)
                                .border(1.dp, neonCyan.copy(alpha = 0.25f), RoundedCornerShape(12.dp)),
                            colors = CardDefaults.cardColors(containerColor = Color(0x12FFFFFF)),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Column(modifier = Modifier.padding(9.dp)) {
                                Text(
                                    text = stringResource(id = R.string.monthly_total_usage),
                                    color = inkDim,
                                    fontSize = 8.5.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Spacer(modifier = Modifier.height(3.dp))
                                Text(
                                    text = s.totalFormatted,
                                    color = neonCyan,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Black
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = "↓ ${s.rxFormatted} • ↑ ${s.txFormatted}",
                                    color = inkDim,
                                    fontSize = 8.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }

                        // Daily Average Pod
                        Card(
                            modifier = Modifier
                                .weight(1f)
                                .border(1.dp, trackBlue.copy(alpha = 0.25f), RoundedCornerShape(12.dp)),
                            colors = CardDefaults.cardColors(containerColor = Color(0x12FFFFFF)),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Column(modifier = Modifier.padding(9.dp)) {
                                Text(
                                    text = stringResource(id = R.string.monthly_daily_average),
                                    color = inkDim,
                                    fontSize = 8.5.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Spacer(modifier = Modifier.height(3.dp))
                                Text(
                                    text = s.dailyAverageFormatted,
                                    color = trackBlue,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Black
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = "Per active day",
                                    color = inkDim,
                                    fontSize = 8.sp
                                )
                            }
                        }

                        // Peak Usage Pod
                        Card(
                            modifier = Modifier
                                .weight(1f)
                                .border(1.dp, emeraldGreen.copy(alpha = 0.25f), RoundedCornerShape(12.dp)),
                            colors = CardDefaults.cardColors(containerColor = Color(0x12FFFFFF)),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Column(modifier = Modifier.padding(9.dp)) {
                                Text(
                                    text = stringResource(id = R.string.monthly_peak_day),
                                    color = inkDim,
                                    fontSize = 8.5.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Spacer(modifier = Modifier.height(3.dp))
                                Text(
                                    text = s.peakDay?.totalFormatted ?: "0 B",
                                    color = emeraldGreen,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Black
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = s.peakDay?.dateLabel ?: "No activity",
                                    color = inkDim,
                                    fontSize = 8.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // OS Sync Permission reminder (if not granted)
                    if (!s.hasUsagePermission) {
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .border(1.dp, emeraldGreen.copy(alpha = 0.35f), RoundedCornerShape(12.dp)),
                            colors = CardDefaults.cardColors(containerColor = emeraldGreen.copy(alpha = 0.08f)),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Sync,
                                        contentDescription = null,
                                        tint = emeraldGreen,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Text(
                                        text = stringResource(id = R.string.monthly_permission_hint),
                                        color = inkLight,
                                        fontSize = 10.sp,
                                        lineHeight = 13.sp
                                    )
                                }
                                Spacer(modifier = Modifier.width(8.dp))
                                Button(
                                    onClick = {
                                        try {
                                            val intent = Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)
                                            context.startActivity(intent)
                                        } catch (_: Throwable) {}
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = emeraldGreen),
                                    shape = RoundedCornerShape(8.dp),
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                    modifier = Modifier.height(28.dp)
                                ) {
                                    Text(
                                        text = "SYNC",
                                        color = Color.Black,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.ExtraBold
                                    )
                                }
                            }
                        }
                        Spacer(modifier = Modifier.height(10.dp))
                    }

                    // 2. Visual Daily Bar Chart / Activity Strip
                    val maxBytesInMonth = s.days.maxOfOrNull { it.totalBytes }?.coerceAtLeast(1024L * 1024L) ?: (1024L * 1024L)
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(14.dp)),
                        colors = CardDefaults.cardColors(containerColor = Color(0x10FFFFFF)),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = stringResource(id = R.string.monthly_chart_title),
                                    color = inkLight,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "${s.days.size} DAYS",
                                    color = inkDim,
                                    fontSize = 9.sp
                                )
                            }

                            Spacer(modifier = Modifier.height(10.dp))

                            // Horizontal Bar Chart of all days
                            val chartScrollState = rememberScrollState()
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(72.dp)
                                    .horizontalScroll(chartScrollState),
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                                verticalAlignment = Alignment.Bottom
                            ) {
                                s.days.forEachIndexed { index, day ->
                                    val barRatio = (day.totalBytes.toFloat() / maxBytesInMonth.toFloat()).coerceIn(0.04f, 1f)
                                    val barHeight = (barRatio * 44f).dp
                                    val isSelected = selectedDayIndex == index
                                    val barColor = when {
                                        day.isToday -> neonCyan
                                        day.isExceededLimit -> alertPink
                                        day.totalBytes > 0 -> trackBlue
                                        else -> Color.White.copy(alpha = 0.12f)
                                    }

                                    Column(
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        modifier = Modifier
                                            .width(18.dp)
                                            .clickable {
                                                selectedDayIndex = if (isSelected) null else index
                                                coroutineScope.launch {
                                                    val reverseIndex = s.days.size - 1 - index
                                                    listState.animateScrollToItem(reverseIndex.coerceAtLeast(0))
                                                }
                                            }
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .width(if (isSelected) 10.dp else 7.dp)
                                                .height(barHeight)
                                                .clip(RoundedCornerShape(topStart = 3.dp, topEnd = 3.dp))
                                                .background(if (isSelected) neonCyan else barColor)
                                        )
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(
                                            text = "${day.dayOfMonth}",
                                            color = if (day.isToday || isSelected) neonCyan else inkDim,
                                            fontSize = 8.sp,
                                            fontWeight = if (day.isToday || isSelected) FontWeight.Bold else FontWeight.Normal,
                                            textAlign = TextAlign.Center
                                        )
                                    }
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // 3. Everyday Usage Log (List of all days)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = stringResource(id = R.string.monthly_days_title),
                            color = inkLight,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Daily Breakdown",
                            color = inkDim,
                            fontSize = 10.sp
                        )
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    // Show days in reverse order (most recent first)
                    val reversedDays = remember(s.days) { s.days.reversed() }

                    LazyColumn(
                        state = listState,
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        items(reversedDays, key = { it.dayOfMonth }) { day ->
                            val isFocused = selectedDayIndex != null && s.days.getOrNull(selectedDayIndex!!)?.dayOfMonth == day.dayOfMonth

                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .border(
                                        width = if (day.isToday || isFocused) 1.5.dp else 1.dp,
                                        color = when {
                                            isFocused -> neonCyan
                                            day.isToday -> neonCyan.copy(alpha = 0.5f)
                                            day.isExceededLimit -> alertPink.copy(alpha = 0.4f)
                                            else -> Color.White.copy(alpha = 0.06f)
                                        },
                                        shape = RoundedCornerShape(12.dp)
                                    ),
                                colors = CardDefaults.cardColors(
                                    containerColor = if (day.isToday) Color(0x1800F0FF) else Color(0x0CFFFFFF)
                                ),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 10.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    // Day Badge
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(34.dp)
                                                .clip(RoundedCornerShape(8.dp))
                                                .background(
                                                    if (day.isToday) neonCyan.copy(alpha = 0.2f) else Color.White.copy(alpha = 0.07f)
                                                ),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                                Text(
                                                    text = "${day.dayOfMonth}",
                                                    color = if (day.isToday) neonCyan else inkLight,
                                                    fontSize = 11.sp,
                                                    fontWeight = FontWeight.Bold
                                                )
                                                Text(
                                                    text = day.dayOfWeek.take(3),
                                                    color = if (day.isToday) neonCyan else inkDim,
                                                    fontSize = 7.sp,
                                                    fontWeight = FontWeight.SemiBold
                                                )
                                            }
                                        }

                                        Column {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                                            ) {
                                                Text(
                                                    text = day.dateLabel,
                                                    color = inkLight,
                                                    fontSize = 11.sp,
                                                    fontWeight = FontWeight.SemiBold
                                                )
                                                if (day.isToday) {
                                                    Box(
                                                        modifier = Modifier
                                                            .clip(RoundedCornerShape(3.dp))
                                                            .background(neonCyan.copy(alpha = 0.2f))
                                                            .padding(horizontal = 4.dp, vertical = 1.dp)
                                                    ) {
                                                        Text(
                                                            text = stringResource(id = R.string.monthly_day_today),
                                                            color = neonCyan,
                                                            fontSize = 7.sp,
                                                            fontWeight = FontWeight.ExtraBold
                                                        )
                                                    }
                                                } else if (day.isExceededLimit) {
                                                    Box(
                                                        modifier = Modifier
                                                            .clip(RoundedCornerShape(3.dp))
                                                            .background(alertPink.copy(alpha = 0.2f))
                                                            .padding(horizontal = 4.dp, vertical = 1.dp)
                                                    ) {
                                                        Text(
                                                            text = stringResource(id = R.string.monthly_day_exceeded),
                                                            color = alertPink,
                                                            fontSize = 7.sp,
                                                            fontWeight = FontWeight.ExtraBold
                                                        )
                                                    }
                                                } else if (day.isFuture) {
                                                    Box(
                                                        modifier = Modifier
                                                            .clip(RoundedCornerShape(3.dp))
                                                            .background(Color.White.copy(alpha = 0.08f))
                                                            .padding(horizontal = 4.dp, vertical = 1.dp)
                                                    ) {
                                                        Text(
                                                            text = stringResource(id = R.string.monthly_day_upcoming),
                                                            color = inkDim,
                                                            fontSize = 7.sp,
                                                            fontWeight = FontWeight.Normal
                                                        )
                                                    }
                                                }
                                            }
                                            Spacer(modifier = Modifier.height(2.dp))
                                            Text(
                                                text = if (day.isFuture) "—" else "↓ ${day.rxFormatted}  •  ↑ ${day.txFormatted}",
                                                color = inkDim,
                                                fontSize = 9.sp
                                            )
                                        }
                                    }

                                    // Right usage text
                                    Column(horizontalAlignment = Alignment.End) {
                                        Text(
                                            text = if (day.isFuture) "0 B" else day.totalFormatted,
                                            color = when {
                                                day.isExceededLimit -> alertPink
                                                day.isToday -> neonCyan
                                                day.totalBytes > 0 -> inkLight
                                                else -> inkDim
                                            },
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                        if (s.dailyLimitMb > 0f && !day.isFuture && day.totalBytes > 0) {
                                            val pct = (day.limitPercent * 100).toInt()
                                            Text(
                                                text = "$pct% of limit",
                                                color = if (day.isExceededLimit) alertPink else inkDim,
                                                fontSize = 8.sp
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Bottom Close Button
                Button(
                    onClick = onDismiss,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(42.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0x22FFFFFF)),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(
                        text = "CLOSE",
                        color = inkLight,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}
