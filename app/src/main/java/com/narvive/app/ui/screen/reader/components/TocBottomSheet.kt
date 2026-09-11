package com.narvive.app.ui.screen.reader.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bookmark
import androidx.compose.material.icons.automirrored.rounded.List
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.narvive.app.R
import com.narvive.app.domain.model.Annotation
import com.narvive.app.domain.model.AnnotationType
import com.narvive.app.domain.model.Bookmark
import com.narvive.app.service.reader.TocItem
import com.narvive.app.ui.theme.AnnotationPalette
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun TocBottomSheet(
    chapters: List<TocItem>,
    bookmarks: List<Bookmark>,
    onChapterClick: (String) -> Unit,
    onBookmarkClick: (String) -> Unit,
    /** G2.6：本书笔记列表 + 点击跳转 */
    annotations: List<Annotation> = emptyList(),
    onAnnotationClick: ((String) -> Unit)? = null,
    /** 当前所在章索引（目录面板高亮当前章，-1 表示未知） */
    currentChapterIndex: Int = -1,
    progressDisplayMode: String = "percentage",
    pageTotal: Int = 0,
    ink: Color = Color.Unspecified,
    modifier: Modifier = Modifier,
) {
    var selectedTab by remember { mutableIntStateOf(0) }
    val tabs = listOf(
        stringResource(R.string.reader_toc),
        stringResource(R.string.reader_bookmarks),
        stringResource(R.string.reader_notes),
    )

    Column(modifier.fillMaxSize().padding(bottom = 16.dp)) {
        HorizontalDivider(color = ink.copy(alpha = 0.15f))
        Row(Modifier.fillMaxWidth()) {
            tabs.forEachIndexed { i, label ->
                val selected = selectedTab == i
                Column(
                    Modifier
                        .weight(1f)
                        .clickable { selectedTab = i },
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        label,
                        color = if (selected) ink else ink.copy(alpha = 0.5f),
                        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                        modifier = Modifier.padding(vertical = 12.dp),
                    )
                    Box(
                        Modifier
                            .fillMaxWidth(if (selected) 0.5f else 0f)
                            .height(2.dp)
                            .background(ink),
                    )
                }
            }
        }
        HorizontalDivider(color = ink.copy(alpha = 0.15f))
        when (selectedTab) {
            0 -> ChapterList(chapters, onChapterClick, currentChapterIndex, ink, Modifier.weight(1f))
            1 -> BookmarkList(bookmarks, onBookmarkClick, progressDisplayMode, pageTotal, ink, Modifier.weight(1f))
            2 -> NoteList(annotations, onAnnotationClick ?: {}, progressDisplayMode, pageTotal, ink, Modifier.weight(1f))
        }
    }
}

@Composable
private fun ChapterList(chapters: List<TocItem>, onChapterClick: (String) -> Unit, currentChapterIndex: Int, ink: Color, modifier: Modifier = Modifier) {
    if (chapters.isEmpty()) {
        EmptyTab(stringResource(R.string.reader_empty_toc), ink)
    } else {
        LazyColumn(modifier) {
            itemsIndexed(chapters) { index, item ->
                val isCurrent = index == currentChapterIndex
                Row(
                    Modifier.fillMaxWidth().clickable { onChapterClick(item.locatorJson) }.padding(horizontal = 20.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.AutoMirrored.Rounded.List, null, tint = if (isCurrent) MaterialTheme.colorScheme.primary else ink.copy(alpha = 0.55f))
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            item.title,
                            style = MaterialTheme.typography.bodyLarge,
                            color = if (isCurrent) MaterialTheme.colorScheme.primary else ink,
                            fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Normal,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                        if (item.pageLabel.isNotEmpty()) Text(item.pageLabel, style = MaterialTheme.typography.bodySmall, color = ink.copy(alpha = 0.55f))
                    }
                    if (isCurrent) {
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.reader_current), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                    }
                }
                HorizontalDivider(Modifier.padding(horizontal = 20.dp), color = ink.copy(alpha = 0.15f))
            }
        }
    }
}

@Composable
private fun BookmarkList(bookmarks: List<Bookmark>, onBookmarkClick: (String) -> Unit, progressDisplayMode: String = "percentage", pageTotal: Int = 0, ink: Color = Color.Unspecified, modifier: Modifier = Modifier) {
    if (bookmarks.isEmpty()) {
        EmptyTab(stringResource(R.string.reader_empty_bookmarks), ink)
    } else {
        LazyColumn(modifier) {
            itemsIndexed(bookmarks) { _, bm ->
                Row(
                    Modifier.fillMaxWidth().clickable { onBookmarkClick(bm.locatorJson) }.padding(start = 20.dp, end = 20.dp, top = 14.dp, bottom = 14.dp),
                    verticalAlignment = Alignment.Top,
                ) {
                    Icon(Icons.Rounded.Bookmark, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(top = 2.dp))
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            bm.chapterTitle ?: "",
                            style = MaterialTheme.typography.bodyMedium,
                            color = ink,
                            fontWeight = FontWeight.Medium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Spacer(Modifier.width(4.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            bm.progress?.let { p ->
                                val bmText = if (progressDisplayMode == "page" && pageTotal > 0) {
                                    val ep = (p * pageTotal).toInt().coerceIn(1, pageTotal)
                                    "${ep}/${pageTotal}"
                                } else {
                                    "%.1f%%".format(p * 100)
                                }
                                Text(bmText, style = MaterialTheme.typography.bodySmall, color = ink.copy(alpha = 0.55f))
                                Text(" | ", style = MaterialTheme.typography.bodySmall, color = ink.copy(alpha = 0.55f))
                            }
                            Text(
                                SimpleDateFormat("yyyy/M/d HH:mm:ss", Locale.getDefault()).format(Date(bm.createdAt)),
                                style = MaterialTheme.typography.bodySmall,
                                color = ink.copy(alpha = 0.55f),
                            )
                        }
                        bm.previewText?.let {
                            Spacer(Modifier.width(4.dp))
                            Text(it, style = MaterialTheme.typography.bodySmall, color = ink.copy(alpha = 0.55f), maxLines = 2, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
                HorizontalDivider(Modifier.padding(horizontal = 20.dp), color = ink.copy(alpha = 0.15f))
            }
        }
    }
}

@Composable
private fun NoteList(annotations: List<Annotation>, onClick: (String) -> Unit, progressDisplayMode: String, pageTotal: Int, ink: Color, modifier: Modifier = Modifier) {
    // 需求3：仅展示 高亮/翻译/笔记 三种
    val filtered = annotations.filter {
        it.type == AnnotationType.HIGHLIGHT || it.type == AnnotationType.TRANSLATION || it.type == AnnotationType.NOTE
    }
    if (filtered.isEmpty()) {
        EmptyTab(stringResource(R.string.reader_empty_notes), ink)
    } else {
        LazyColumn(modifier) {
            itemsIndexed(filtered) { _, ann ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .run { if (ann.locatorJson.isNotBlank()) clickable { onClick(ann.locatorJson) } else this }
                        .padding(horizontal = 20.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.Top,
                ) {
                    // 类型标注（竖排标签样式）
                    NoteTypeTag(ann.type)
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        // 内容（笔记/译文/正文）
                        Text(
                            ann.note ?: ann.translation ?: ann.selectedText.take(60),
                            style = MaterialTheme.typography.bodyMedium,
                            color = ink,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                        // 类型 · 章名 · 进度 · 时间（参考书签逻辑）
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(noteTypeLabel(ann.type), style = MaterialTheme.typography.bodySmall, color = noteTypeColor(ann.type), fontWeight = FontWeight.Medium)
                            if (ann.chapterTitle.isNotBlank()) {
                                Text(" · " + ann.chapterTitle, style = MaterialTheme.typography.bodySmall, color = ink.copy(alpha = 0.55f), maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                            }
                            ann.progress?.let { p ->
                                val pText = if (progressDisplayMode == "page" && pageTotal > 0) {
                                    "${(p * pageTotal).toInt().coerceIn(1, pageTotal)}/$pageTotal"
                                } else {
                                    "%.1f%%".format(p * 100)
                                }
                                Text(" · $pText", style = MaterialTheme.typography.bodySmall, color = ink.copy(alpha = 0.55f))
                            }
                            Text(
                                " · " + SimpleDateFormat("yyyy/M/d HH:mm", Locale.getDefault()).format(Date(ann.createdAt)),
                                style = MaterialTheme.typography.bodySmall,
                                color = ink.copy(alpha = 0.55f),
                            )
                        }
                        // 正文摘录
                        if (ann.selectedText.isNotBlank()) {
                            Text(ann.selectedText.take(80), style = MaterialTheme.typography.bodySmall, color = ink.copy(alpha = 0.5f), maxLines = 2, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
                HorizontalDivider(Modifier.padding(horizontal = 20.dp), color = ink.copy(alpha = 0.15f))
            }
        }
    }
}

/** 笔记类型左侧色条（对齐主笔记面板设计） */
@Composable
private fun NoteTypeTag(type: AnnotationType) {
    Box(Modifier.width(3.dp).height(44.dp).background(noteTypeColor(type)))
}

private fun noteTypeColor(type: AnnotationType): Color = when (type) {
    AnnotationType.HIGHLIGHT -> AnnotationPalette.Highlight
    AnnotationType.TRANSLATION -> AnnotationPalette.Translation
    AnnotationType.NOTE -> AnnotationPalette.Note
    else -> AnnotationPalette.Note
}

@Composable
private fun noteTypeLabel(type: AnnotationType): String = when (type) {
    AnnotationType.HIGHLIGHT -> stringResource(R.string.reader_highlight)
    AnnotationType.TRANSLATION -> stringResource(R.string.reader_translate)
    AnnotationType.NOTE -> stringResource(R.string.reader_note)
    else -> stringResource(R.string.reader_note)
}

@Composable
private fun EmptyTab(message: String, ink: Color) {
    androidx.compose.foundation.layout.Box(
        Modifier.fillMaxWidth().fillMaxSize().padding(32.dp),
        contentAlignment = androidx.compose.ui.Alignment.Center,
    ) {
        Text(message, style = MaterialTheme.typography.bodyMedium, color = ink.copy(alpha = 0.55f))
    }
}
