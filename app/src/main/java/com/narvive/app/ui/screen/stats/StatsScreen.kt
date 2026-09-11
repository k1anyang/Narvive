package com.narvive.app.ui.screen.stats

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.BarChart
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.MenuBook
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import com.narvive.app.R
import com.narvive.app.domain.model.Book
import com.narvive.app.ui.components.TabPageScaffold
import com.narvive.app.ui.message.text
import com.narvive.app.ui.theme.NarviveShape
import com.narvive.app.ui.theme.SemanticColors
import com.narvive.app.ui.screen.library.BookCoverImage
import kotlinx.coroutines.delay

@Composable
fun StatsScreen(
    viewModel: StatsViewModel = hiltViewModel(),
    modifier: Modifier = Modifier,
) {
    val uiState by viewModel.uiState.collectAsState()
    var selectedIdx by remember { mutableIntStateOf(-1) }

    // 切换范围时清除选中（防止越界）
    LaunchedEffect(uiState.chartRange) { selectedIdx = -1 }
    // 3 秒无操作自动消失
    if (selectedIdx >= 0) {
        LaunchedEffect(selectedIdx) {
            delay(3000)
            selectedIdx = -1
        }
    }

    TabPageScaffold(title = stringResource(R.string.stats_title), modifier = modifier) { padding ->
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(padding).padding(horizontal = 20.dp, vertical = 20.dp)) {

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            StatCard(uiState.todayMinutes.toString(), stringResource(R.string.stats_today_reading), stringResource(R.string.stats_unit_minutes), Modifier.weight(1f))
            StatCard(uiState.weekMinutes.toString(), stringResource(R.string.stats_week_reading), stringResource(R.string.stats_unit_minutes), Modifier.weight(1f))
        }
        Spacer(Modifier.height(10.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            StatCard(uiState.consecutiveDays.toString(), stringResource(R.string.stats_streak), stringResource(R.string.stats_unit_days), Modifier.weight(1f))
            StatCard(uiState.booksFinished.toString(), stringResource(R.string.stats_books_finished), "", Modifier.weight(1f))
        }

        Spacer(Modifier.height(20.dp))
        // 图表标题 + 导航 / 选中提示
        if (selectedIdx >= 0 && selectedIdx < uiState.chartPoints.size) {
            // 选中态：提示框覆盖标题栏位，点击关闭
            val p = uiState.chartPoints[selectedIdx]
            Row(
                Modifier.fillMaxWidth().height(32.dp).clip(NarviveShape.Sm)
                    .background(MaterialTheme.colorScheme.surfaceVariant).clickable { selectedIdx = -1 }
                    .padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    stringResource(R.string.stats_tooltip_point, p.label, p.minutes.toInt()),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    stringResource(R.string.stats_tooltip_dismiss),
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                    style = MaterialTheme.typography.labelSmall,
                )
            }
        } else {
            // 正常态：< > 标签 日 周 月
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(
                    onClick = {
                        when (uiState.chartRange) {
                            ChartRange.DAY -> viewModel.navigateDay(-1)
                            ChartRange.WEEK -> viewModel.navigateWeek(-1)
                            ChartRange.MONTH -> viewModel.navigateMonth(-1)
                        }
                    },
                    enabled = uiState.canGoBack,
                    modifier = Modifier.size(32.dp),
                ) {
                    Icon(Icons.AutoMirrored.Rounded.KeyboardArrowLeft, stringResource(R.string.stats_prev), modifier = Modifier.size(22.dp),
                        tint = if (uiState.canGoBack) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f))
                }
                Text(uiState.chartLabel.text(), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                IconButton(
                    onClick = {
                        when (uiState.chartRange) {
                            ChartRange.DAY -> viewModel.navigateDay(1)
                            ChartRange.WEEK -> viewModel.navigateWeek(1)
                            ChartRange.MONTH -> viewModel.navigateMonth(1)
                        }
                    },
                    enabled = uiState.canGoForward,
                    modifier = Modifier.size(32.dp),
                ) {
                    Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, stringResource(R.string.stats_next), modifier = Modifier.size(22.dp),
                        tint = if (uiState.canGoForward) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f))
                }
                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(ChartRange.entries) { range ->
                        FilterChip(
                            selected = uiState.chartRange == range,
                            onClick = { viewModel.setChartRange(range) },
                            label = { Text(stringResource(range.labelRes), style = MaterialTheme.typography.labelSmall) },
                            modifier = Modifier.height(26.dp),
                        )
                    }
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        Card(Modifier.fillMaxWidth(), shape = NarviveShape.Md) {
            if (uiState.chartPoints.isEmpty()) {
                Box(Modifier.fillMaxWidth().height(180.dp), contentAlignment = Alignment.Center) {
                    Text(stringResource(R.string.stats_chart_empty), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            } else {
                val points = uiState.chartPoints
                Column(Modifier.padding(16.dp)) {
                    Box {
                        val primaryColor = MaterialTheme.colorScheme.primary
                        val primaryContainerColor = MaterialTheme.colorScheme.primaryContainer
                        val surfaceVariantColor = MaterialTheme.colorScheme.surfaceVariant
                        Canvas(Modifier.fillMaxWidth().height(150.dp).graphicsLayer { clip = false }) {
                            val maxVal = (points.maxOfOrNull { it.minutes } ?: 1f).coerceAtLeast(1f)
                            val barWidth = size.width / points.size
                            val chartHeight = size.height

                            points.forEachIndexed { i, p ->
                                val barHeight = (p.minutes / maxVal) * chartHeight
                                val x = i * barWidth
                                val brush = if (p.isCurrent)
                                    Brush.verticalGradient(listOf(primaryContainerColor, primaryColor))
                                else
                                    Brush.verticalGradient(listOf(primaryColor, primaryColor))
                                if (p.isCurrent) {
                                    drawRoundRect(
                                        primaryColor.copy(alpha = 0.18f),
                                        topLeft = Offset(x - 2f, 0f), size = Size(barWidth + 0f, chartHeight),
                                        cornerRadius = androidx.compose.ui.geometry.CornerRadius(3f),
                                    )
                                }
                                drawRoundRect(
                                    brush,
                                    topLeft = Offset(x + 2f, chartHeight - barHeight.coerceAtLeast(4f)),
                                    size = Size(barWidth - 6f, barHeight.coerceAtLeast(4f)),
                                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(3f, 3f),
                                )
                            }

                            // 选中竖线：顶层绘制，固定长度（极限柱高 + 上下各 14px）
                            if (selectedIdx in points.indices) {
                                val centerX = selectedIdx * barWidth + barWidth / 2f
                                drawLine(
                                    surfaceVariantColor,
                                    Offset(centerX, -14f),
                                    Offset(centerX, chartHeight + 14f),
                                    strokeWidth = 2.5f,
                                )
                            }
                        }
                        // 触控层：拖拽滑动选柱 + 单击点选
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .height(150.dp)
                                .pointerInput(points.size) {
                                    detectDragGestures(
                                        onDragStart = { offset ->
                                            val barW = size.width.toFloat() / points.size
                                            selectedIdx = (offset.x / barW).toInt().coerceIn(0, points.lastIndex)
                                        },
                                        onDrag = { change, _ ->
                                            change.consume()
                                            val barW = size.width.toFloat() / points.size
                                            selectedIdx = (change.position.x / barW).toInt().coerceIn(0, points.lastIndex)
                                        },
                                    )
                                }
                                .pointerInput(points.size) {
                                    detectTapGestures { offset ->
                                        val barW = size.width.toFloat() / points.size
                                        val idx = (offset.x / barW).toInt().coerceIn(0, points.lastIndex)
                                        selectedIdx = idx
                                    }
                                },
                        )
                    }
                    // 日期刻度
                    Spacer(Modifier.height(6.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(points.first().label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        if (points.size > 2) Text(points[points.size / 2].label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(points.last().label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }

        Spacer(Modifier.height(20.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.stats_recent_finished), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            if (uiState.finishedBooks.size > 3) {
                TextButton(onClick = viewModel::toggleShowAllFinished) { Text(if (uiState.showAllFinished) stringResource(R.string.stats_collapse) else stringResource(R.string.stats_view_all), style = MaterialTheme.typography.labelMedium) }
            }
        }
        Spacer(Modifier.height(12.dp))
        if (uiState.finishedBooks.isEmpty()) {
            Text(stringResource(R.string.stats_no_finished_books), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        val finishedBooks = if (uiState.showAllFinished) uiState.finishedBooks else uiState.finishedBooks.take(3)
        finishedBooks.forEach { fb ->
            FinishedBookCard(fb)
            Spacer(Modifier.height(10.dp))
        }
        }
    }
}

@Composable
private fun StatCard(value: String, label: String, unit: String, modifier: Modifier) {
    Card(modifier, shape = NarviveShape.Md, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.Bottom) {
                Text(value, style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                if (unit.isNotEmpty()) {
                    Spacer(Modifier.width(2.dp))
                    Text(unit, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(bottom = 3.dp))
                }
            }
            Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 2.dp))
        }
    }
}

@Composable
private fun FinishedBookCard(fb: FinishedBook) {
    Card(Modifier.fillMaxWidth(), shape = NarviveShape.Md) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(40.dp, 56.dp).clip(NarviveShape.Xs).background(MaterialTheme.colorScheme.surfaceVariant)) {
                if (fb.coverPath != null) {
                    AsyncImage(model = fb.coverPath, contentDescription = fb.title, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                } else {
                    Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(MaterialTheme.colorScheme.primaryContainer, MaterialTheme.colorScheme.primary))))
                }
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(fb.title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, maxLines = 1)
                Text(stringResource(R.string.stats_book_meta, fb.author, fb.dateLabel.text()), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Icon(Icons.Rounded.CheckCircle, null, tint = SemanticColors.Success, modifier = Modifier.size(22.dp))
        }
    }
}
