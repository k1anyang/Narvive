package com.narvive.app.ui.screen.notes

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.rounded.IosShare
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.EditNote
import androidx.compose.material.icons.rounded.FormatPaint
import androidx.compose.material.icons.rounded.PushPin
import androidx.compose.material.icons.automirrored.rounded.StickyNote2
import androidx.compose.material.icons.rounded.TheaterComedy
import androidx.compose.material.icons.rounded.Translate
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SearchBar
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.narvive.app.R
import com.narvive.app.domain.model.AnnotationType
import com.narvive.app.ui.components.SwipeRevealRow
import com.narvive.app.ui.components.TabPageScaffold
import com.narvive.app.ui.theme.NarviveShape
import com.narvive.app.ui.theme.annotationTypeColor
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotesScreen(
    onNoteClick: (bookId: String, locatorJson: String) -> Unit = { _, _ -> },
    viewModel: NotesViewModel = hiltViewModel(),
    modifier: Modifier = Modifier,
) {
    val uiState by viewModel.uiState.collectAsState()
    var isSearchActive by remember { mutableStateOf(false) }
    var detailItem by remember { mutableStateOf<NoteItem?>(null) }
    var deleteTarget by remember { mutableStateOf<NoteItem?>(null) }

    deleteTarget?.let { item ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text(stringResource(R.string.notes_delete_title)) },
            text = { Text(stringResource(R.string.notes_delete_message, typeLabel(item.annotation.type))) },
            confirmButton = {
                TextButton(onClick = { viewModel.deleteAnnotation(item.annotation); deleteTarget = null }) {
                    Text(stringResource(R.string.common_delete), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { deleteTarget = null }) { Text(stringResource(R.string.common_cancel)) } },
        )
    }

    TabPageScaffold(
        title = stringResource(R.string.notes_title),
        modifier = modifier,
        actions = {
            IconButton(onClick = { isSearchActive = !isSearchActive }) { Icon(Icons.Rounded.Search, stringResource(R.string.notes_search)) }
            IconButton(onClick = viewModel::exportMarkdown, enabled = uiState.annotations.isNotEmpty()) { Icon(Icons.Rounded.IosShare, stringResource(R.string.common_export)) }
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            AnimatedVisibility(isSearchActive, enter = fadeIn() + expandVertically(), exit = fadeOut() + shrinkVertically()) {
                SearchBar(
                    query = uiState.searchQuery, onQueryChange = viewModel::onSearchQueryChange, onSearch = {}, active = false, onActiveChange = {},
                    placeholder = { Text(stringResource(R.string.notes_search_hint)) },
                    leadingIcon = { Icon(Icons.Rounded.Search, null) },
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                ) {}
            }

            // 类型筛选 chips（带计数）
            LazyRow(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                item {
                    FilterChip(
                        selected = uiState.typeFilter == null,
                        onClick = { viewModel.setTypeFilter(null) },
                        label = { Text(stringResource(R.string.notes_filter_all_count, uiState.typeCounts.values.sum())) },
                    )
                }
                items(typeChipOrder) { type ->
                    val count = uiState.typeCounts[type] ?: 0
                    if (count > 0) {
                        FilterChip(
                            selected = uiState.typeFilter == type,
                            onClick = { viewModel.setTypeFilter(if (uiState.typeFilter == type) null else type) },
                            label = { Text(stringResource(R.string.notes_filter_count, typeLabel(type), count)) },
                        )
                    }
                }
            }

            // 按书筛选 chips
            if (uiState.bookChips.size > 1) {
                LazyRow(
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    item {
                        FilterChip(selected = uiState.bookFilter == null, onClick = { viewModel.setBookFilter(null) }, label = { Text(stringResource(R.string.notes_filter_all_books)) })
                    }
                    items(uiState.bookChips) { chip ->
                        FilterChip(
                            selected = uiState.bookFilter == chip.id,
                            onClick = { viewModel.setBookFilter(if (uiState.bookFilter == chip.id) null else chip.id) },
                            label = { Text(stringResource(R.string.notes_filter_count, chip.title ?: stringResource(R.string.notes_unknown_book), chip.count)) },
                        )
                    }
                }
            }

            if (uiState.annotations.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        // 空状态给足左右留白并让文案居中：英文提示比中文长得多，
                        // 不加边距时会贴到屏幕边缘，视觉上像排版错误。
                        modifier = Modifier.padding(horizontal = 40.dp),
                    ) {
                        Icon(Icons.AutoMirrored.Rounded.StickyNote2, null, Modifier.size(64.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f))
                        Spacer(Modifier.height(16.dp))
                        Text(
                            stringResource(R.string.notes_empty_title),
                            style = MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            stringResource(R.string.notes_empty_hint_highlight),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                            textAlign = TextAlign.Center,
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            stringResource(R.string.notes_empty_hint_ai),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                            textAlign = TextAlign.Center,
                        )
                    }
                }
            } else {
                LazyColumn(
                    Modifier.fillMaxSize(),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(uiState.annotations, key = { it.annotation.id }) { item ->
                        SwipeRevealRow(
                            onDelete = { deleteTarget = item },
                            onExport = { viewModel.exportAnnotation(item.annotation) },
                        ) {
                            AnnotationCard(item) {
                                if (item.annotation.type == AnnotationType.AI_ANSWER || item.annotation.type == AnnotationType.REWRITE) {
                                    detailItem = item
                                } else {
                                    onNoteClick(item.annotation.bookId, item.annotation.locatorJson)
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    detailItem?.let { item ->
        NoteDetailSheet(
            item = item,
            onJump = { onNoteClick(item.annotation.bookId, item.annotation.locatorJson); detailItem = null },
            onDismiss = { detailItem = null },
        )
    }
}

@Composable
private fun AnnotationCard(item: NoteItem, onClick: () -> Unit) {
    val ann = item.annotation
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        shape = NarviveShape.Md,
        colors = CardDefaults.cardColors(
            containerColor = if (ann.type == AnnotationType.REWRITE) {
                                MaterialTheme.colorScheme.primaryContainer
                            } else {
                                MaterialTheme.colorScheme.surface
                            },
        ),
    ) {
        Row {
            Box(Modifier.width(3.dp).height(96.dp).background(cardBorderColor(ann)))
            Column(Modifier.padding(start = 14.dp, end = 16.dp, top = 12.dp, bottom = 12.dp).weight(1f)) {
                // 副标题：书名独立一行。
                // 为什么不挤进下面的 meta 行：那一行要放 类型/章名/进度/时间，且 maxLines=1 从
                // 末尾截断，书名排在前半段会被先截掉；另外拼接分隔符与 leftLabel 内部的「·」
                // 相同，书名即使在屏幕上也无法一眼认出是书名。
                Text(
                    item.bookTitle ?: stringResource(R.string.notes_unknown_book),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(4.dp))
                // serif 引文
                if (ann.selectedText.isNotBlank()) {
                    Text(
                        ann.selectedText.take(120),
                        style = MaterialTheme.typography.bodyMedium,
                        lineHeight = 22.sp,
                        maxLines = 3, overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(Modifier.height(6.dp))
                }
                // 笔记/译文/改写
                ann.note?.takeIf { it.isNotBlank() }?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 3, overflow = TextOverflow.Ellipsis)
                    Spacer(Modifier.height(6.dp))
                }
                ann.translation?.let {
                    Text(stringResource(R.string.notes_translation_prefix, it), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Spacer(Modifier.height(6.dp))
                }
                ann.rewrittenText?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary, maxLines = 3, overflow = TextOverflow.Ellipsis, modifier = Modifier.background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f), NarviveShape.Xs).padding(6.dp))
                    Spacer(Modifier.height(6.dp))
                }
                // meta 行：类型·章名 + 进度 + 相对时间（书名已提为上方副标题，此处不再重复）
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Icon(typeIcon(ann.type), null, Modifier.size(13.dp), tint = annotationTypeColor(ann.type))
                    Spacer(Modifier.width(4.dp))
                    val chapter = ann.chapterTitle.ifBlank { null }
                    val leftLabel = listOfNotNull(typeLabel(ann.type), chapter).joinToString(" · ")
                    Text(
                        leftLabel,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    ann.progress?.let { p ->
                        Spacer(Modifier.width(6.dp))
                        Text("%.1f%%".format(p * 100), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f))
                    }
                    Spacer(Modifier.width(6.dp))
                    Text(relativeTime(ann.createdAt), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f))
                }
            }
        }
    }
}

/** 原型 chip 顺序：高亮 / 笔记 / 翻译 / AI 保存 / 对话 / 改写 */
private val typeChipOrder = listOf(
    AnnotationType.HIGHLIGHT,
    AnnotationType.NOTE,
    AnnotationType.TRANSLATION,
    AnnotationType.AI_ANSWER,
    AnnotationType.ROLEPLAY,
    AnnotationType.REWRITE,
)

private fun typeIcon(type: AnnotationType): ImageVector = when (type) {
    AnnotationType.HIGHLIGHT -> Icons.Rounded.FormatPaint
    AnnotationType.NOTE -> Icons.Rounded.EditNote
    AnnotationType.TRANSLATION -> Icons.Rounded.Translate
    AnnotationType.AI_ANSWER -> Icons.Rounded.PushPin
    AnnotationType.ROLEPLAY -> Icons.Rounded.TheaterComedy
    AnnotationType.REWRITE -> Icons.Rounded.AutoAwesome
}

/** 高亮/笔记卡的左边条跟随用户实际选择的高亮色 */
private fun cardBorderColor(ann: com.narvive.app.domain.model.Annotation): Color =
    if ((ann.type == AnnotationType.HIGHLIGHT || ann.type == AnnotationType.NOTE) && ann.color != null) {
        Color(ann.color)
    } else {
        annotationTypeColor(ann.type)
    }

/** 类型名需要随界面语言切换，因此声明为 @Composable（仅在本文件的 Composable 中调用）。 */
@Composable
private fun typeLabel(type: AnnotationType): String = when (type) {
    AnnotationType.HIGHLIGHT -> stringResource(R.string.notes_type_highlight)
    AnnotationType.NOTE -> stringResource(R.string.notes_type_note)
    AnnotationType.TRANSLATION -> stringResource(R.string.notes_type_translation)
    AnnotationType.AI_ANSWER -> stringResource(R.string.notes_type_ai_answer)
    AnnotationType.ROLEPLAY -> stringResource(R.string.notes_type_roleplay)
    AnnotationType.REWRITE -> stringResource(R.string.notes_type_rewrite)
}

/** 相对时间文案需要随界面语言切换，因此声明为 @Composable（仅在本文件的 Composable 中调用）。 */
@Composable
private fun relativeTime(ts: Long): String {
    val now = System.currentTimeMillis()
    val diffDays = (dayStart(now) - dayStart(ts)) / (24L * 60 * 60 * 1000)
    return when {
        diffDays <= 0 -> stringResource(R.string.notes_time_today)
        diffDays == 1L -> stringResource(R.string.notes_time_yesterday)
        diffDays < 7L -> stringResource(R.string.notes_time_days_ago, diffDays)
        diffDays < 14L -> stringResource(R.string.notes_time_last_week)
        else -> SimpleDateFormat("yyyy/MM/dd", Locale.getDefault()).format(Date(ts))
    }
}

private fun dayStart(ts: Long): Long {
    val cal = java.util.Calendar.getInstance()
    cal.timeInMillis = ts
    cal.set(java.util.Calendar.HOUR_OF_DAY, 0); cal.set(java.util.Calendar.MINUTE, 0)
    cal.set(java.util.Calendar.SECOND, 0); cal.set(java.util.Calendar.MILLISECOND, 0)
    return cal.timeInMillis
}

/** AI保存/续写/改写 详情预览：书名/章名/部分原文/内容 + 跳转原文 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun NoteDetailSheet(item: NoteItem, onJump: () -> Unit, onDismiss: () -> Unit) {
    val ann = item.annotation
    val context = LocalContext.current
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState()) {
        Column(
            Modifier
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp)
                .heightIn(max = 560.dp)
                .verticalScroll(rememberScrollState()),
        ) {
            Text(typeLabel(ann.type), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, color = annotationTypeColor(ann.type))
            Spacer(Modifier.height(6.dp))
            Text(item.bookTitle ?: stringResource(R.string.notes_unknown_book), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (ann.chapterTitle.isNotBlank()) {
                Text(ann.chapterTitle, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(Modifier.height(12.dp))
            if (ann.selectedText.isNotBlank()) {
                Text(stringResource(R.string.notes_detail_original), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(ann.selectedText, style = MaterialTheme.typography.bodyMedium, lineHeight = 22.sp)
                Spacer(Modifier.height(12.dp))
            }
            val content = ann.rewrittenText ?: ann.note
            if (!content.isNullOrBlank()) {
                Text(
                    if (ann.type == AnnotationType.AI_ANSWER) stringResource(R.string.notes_detail_ai_content) else stringResource(R.string.notes_detail_rewrite_content),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(4.dp))
                Text(content, style = MaterialTheme.typography.bodyMedium)
            }
            Spacer(Modifier.height(16.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(
                    onClick = {
                        val text = ann.rewrittenText ?: ann.note ?: ann.selectedText
                        (context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager)
                            .setPrimaryClip(ClipData.newPlainText("Narvive", text))
                        onDismiss()
                    },
                ) {
                    Icon(Icons.Rounded.ContentCopy, null, Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(4.dp))
                    Text(stringResource(R.string.common_copy))
                }
                Spacer(Modifier.weight(1f))
                TextButton(
                    onClick = onJump,
                    enabled = ann.locatorJson.isNotBlank(),
                ) { Text(if (ann.locatorJson.isNotBlank()) stringResource(R.string.notes_detail_jump) else stringResource(R.string.notes_detail_no_locator)) }
            }
        }
    }
}
