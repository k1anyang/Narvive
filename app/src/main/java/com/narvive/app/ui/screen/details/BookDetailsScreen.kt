package com.narvive.app.ui.screen.details

import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Menu
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.TheaterComedy
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.narvive.app.R
import com.narvive.app.domain.model.Annotation
import com.narvive.app.domain.model.AnnotationType
import com.narvive.app.domain.model.Book
import com.narvive.app.ui.components.SwipeRevealRow
import com.narvive.app.ui.components.TabPageScaffold
import com.narvive.app.ui.navigation.ReaderCoverRect
import com.narvive.app.ui.navigation.ReaderOpenAnimState
import com.narvive.app.ui.screen.library.BookCoverImage
import com.narvive.app.ui.theme.NarviveShape
import com.narvive.app.ui.theme.annotationTypeColor
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BookDetailsScreen(
    bookId: String,
    onBackClick: () -> Unit,
    onOpenBook: (Book) -> Unit,
    onAskAi: (String) -> Unit = {},
    onRoleplay: (String) -> Unit = {},
    /** 目录条目点击：带 locator 打开阅读器并跳转（H2/F5） */
    onOpenBookAt: (String) -> Unit = {},
    viewModel: BookDetailsViewModel = hiltViewModel(),
) {
    LaunchedEffect(bookId) { viewModel.loadBook(bookId) }
    val uiState by viewModel.uiState.collectAsState()
    val book = uiState.book
    var showMoreMenu by remember { mutableStateOf(false) }
    var showNotesSheet by remember { mutableStateOf(false) }
    var coverRect by remember { mutableStateOf<Rect?>(null) }
    var deleteCache by remember { mutableStateOf(true) }

    LaunchedEffect(uiState.showDeleteDialog) {
        if (uiState.showDeleteDialog) deleteCache = true
    }

    if (uiState.showDeleteDialog) {
        AlertDialog(
            onDismissRequest = viewModel::dismissDelete,
            title = { Text(stringResource(R.string.details_delete_book_title)) },
            text = {
                Column {
                    Text(stringResource(R.string.details_delete_book_message, book?.title ?: ""))
                    Spacer(Modifier.height(12.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = deleteCache, onCheckedChange = { deleteCache = it })
                        Text(stringResource(R.string.details_delete_cache_too), style = MaterialTheme.typography.bodyMedium)
                    }
                    Text(
                        stringResource(R.string.details_source_file_note),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 4.dp, top = 2.dp),
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { viewModel.confirmDelete(deleteCache); onBackClick() }) {
                    Text(stringResource(R.string.details_delete), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = viewModel::dismissDelete) { Text(stringResource(R.string.details_cancel)) } },
        )
    }

    if (uiState.showEditDialog && book != null) {
        EditMetadataDialog(book.title, book.author ?: "", book.description ?: "", viewModel::dismissEdit, viewModel::saveMeta)
    }

    if (uiState.showCollectionSheet) {
        ModalBottomSheet(onDismissRequest = viewModel::dismissCollectionSheet, sheetState = rememberModalBottomSheetState()) {
            Column(Modifier.padding(16.dp).padding(bottom = 24.dp)) {
                Text(stringResource(R.string.details_add_to_collection), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(12.dp))
                if (uiState.collections.isEmpty()) {
                    Text(stringResource(R.string.details_no_collections_hint), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    uiState.collections.forEach { c ->
                        Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(c.name, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
                            TextButton(onClick = { viewModel.toggleCollection(c.id) }) {
                                Text(if (c.assigned) stringResource(R.string.details_remove) else stringResource(R.string.details_add), color = if (c.assigned) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary)
                            }
                        }
                    }
                }
            }
        }
    }

    // 本书笔记 · 全部
    if (showNotesSheet && book != null) {
        ModalBottomSheet(onDismissRequest = { showNotesSheet = false }, sheetState = rememberModalBottomSheetState()) {
            Column(Modifier.padding(bottom = 24.dp)) {
                Text(stringResource(R.string.details_notes_sheet_title, uiState.annotations.size), Modifier.padding(horizontal = 20.dp), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(8.dp))
                LazyColumn(Modifier.heightIn(max = 420.dp)) {
                    items(uiState.annotations) { ann ->
                        NoteCard(
                            ann,
                            chapterLabel = viewModel.chapterLabelOf(ann).takeIf { it != null } ?: ann.chapterTitle.ifBlank { null },
                            onDelete = { viewModel.deleteAnnotation(ann) },
                            onExport = { viewModel.exportAnnotation(ann) },
                            modifier = Modifier.padding(horizontal = 20.dp, vertical = 5.dp),
                        )
                    }
                }
            }
        }
    }

    // 目录 BottomSheet（H2/F5）：点击条目带 locator 打开阅读器
    if (uiState.showTocSheet && book != null) {
        ModalBottomSheet(onDismissRequest = viewModel::dismissToc, sheetState = rememberModalBottomSheetState()) {
            Column(Modifier.padding(bottom = 24.dp)) {
                Text(stringResource(R.string.details_toc), Modifier.padding(horizontal = 20.dp), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(8.dp))
                when {
                    uiState.isTocLoading -> Text(stringResource(R.string.details_toc_parsing), Modifier.padding(horizontal = 20.dp, vertical = 20.dp), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    uiState.tocItems.isEmpty() -> Text(stringResource(R.string.details_toc_empty), Modifier.padding(horizontal = 20.dp, vertical = 20.dp), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    else -> LazyColumn(Modifier.heightIn(max = 420.dp)) {
                        items(uiState.tocItems) { item ->
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .clickable { viewModel.dismissToc(); onOpenBookAt(item.locatorJson) }
                                    .padding(start = (20 + item.depth * 20).dp, end = 20.dp, top = 12.dp, bottom = 12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    item.title,
                                    style = if (item.depth == 0) MaterialTheme.typography.bodyLarge else MaterialTheme.typography.bodyMedium,
                                    color = if (item.depth == 0) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                            HorizontalDivider(Modifier.padding(horizontal = 20.dp))
                        }
                    }
                }
            }
        }
    }

    TabPageScaffold(
        title = "",
        navigationIcon = { IconButton(onClick = onBackClick) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, stringResource(R.string.details_back)) } },
        actions = {
            // 喜欢：将书籍加入/移出「我喜欢」合集
            IconButton(onClick = viewModel::toggleFavorite) {
                Icon(
                    if (uiState.isFavorited) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                    stringResource(R.string.details_favorite),
                    tint = if (uiState.isFavorited) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Box {
                IconButton(onClick = { showMoreMenu = true }) { Icon(Icons.Rounded.MoreVert, stringResource(R.string.details_more)) }
                DropdownMenu(expanded = showMoreMenu, onDismissRequest = { showMoreMenu = false }) {
                    DropdownMenuItem(text = { Text(stringResource(R.string.details_edit_info)) }, leadingIcon = { Icon(Icons.Rounded.Edit, null) }, onClick = { showMoreMenu = false; viewModel.showEdit() })
                    DropdownMenuItem(text = { Text(stringResource(R.string.details_export_notes)) }, leadingIcon = { Icon(Icons.Rounded.Description, null) }, onClick = { showMoreMenu = false; viewModel.exportNotes() })
                    DropdownMenuItem(text = { Text(stringResource(R.string.details_add_to_collection)) }, leadingIcon = { Icon(Icons.Rounded.Folder, null) }, onClick = { showMoreMenu = false; viewModel.showCollectionSheet() })
                    DropdownMenuItem(text = { Text(stringResource(R.string.details_delete_book_title), color = MaterialTheme.colorScheme.error) }, leadingIcon = { Icon(Icons.Rounded.Delete, null, tint = MaterialTheme.colorScheme.error) }, onClick = { showMoreMenu = false; viewModel.showDelete() })
                }
            }
        },
    ) { padding ->
        if (book == null) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Text(stringResource(R.string.details_loading), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            Column(
                Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp),
            ) {
                Spacer(Modifier.height(6.dp))
                // 封面 + 信息
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
                    Box(
                        Modifier
                            .size(108.dp, 162.dp)
                            .clip(NarviveShape.Md)
                            .onGloballyPositioned { coords -> coverRect = coords.boundsInWindow() },
                    ) {
                        BookCoverImage(book, Modifier.fillMaxSize())
                    }
                    Spacer(Modifier.width(18.dp))
                    Column(Modifier.weight(1f)) {
                        Text(book.title, style = MaterialTheme.typography.headlineSmall, lineHeight = 25.sp)
                        Spacer(Modifier.height(4.dp))
                        Text(book.author ?: stringResource(R.string.details_unknown_author), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.height(8.dp))
                        FormatChip(book.format)
                        Spacer(Modifier.height(12.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            ProgressRingSmall(book.progress)
                            Spacer(Modifier.width(10.dp))
                            Column {
                                Text("${(book.progress * 100).toInt()}%", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                                Text(stringResource(R.string.details_read_status), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }

                Spacer(Modifier.height(16.dp))
                Button(
                    onClick = {
                        val rect = coverRect
                        if (uiState.bookOpenAnimation && rect != null && rect.width > 0f && rect.height > 0f) {
                            ReaderOpenAnimState.set(
                                coverPath = book.coverPath,
                                title = book.title,
                                rect = ReaderCoverRect(
                                    left = rect.left.roundToInt(),
                                    top = rect.top.roundToInt(),
                                    width = rect.width.roundToInt(),
                                    height = rect.height.roundToInt(),
                                ),
                            )
                        }
                        onOpenBook(book)
                    },
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                    shape = NarviveShape.Md,
                ) {
                    Text(if (book.progress > 0f) stringResource(R.string.details_continue_reading) else stringResource(R.string.details_start_reading), style = MaterialTheme.typography.titleSmall)
                }

                Spacer(Modifier.height(12.dp))
                // 四个 secondary 操作按钮
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    SecondaryAction(stringResource(R.string.details_ask_ai), Icons.Rounded.AutoAwesome, Modifier.weight(1f)) { onAskAi(book.id) }
                    SecondaryAction(stringResource(R.string.details_roleplay), Icons.Rounded.TheaterComedy, Modifier.weight(1f)) { onRoleplay(book.id) }
                    SecondaryAction(stringResource(R.string.details_notes), Icons.Rounded.Description, Modifier.weight(1f)) { showNotesSheet = true }
                    SecondaryAction(stringResource(R.string.details_toc), Icons.Rounded.Menu, Modifier.weight(1f)) { viewModel.showToc() }
                }

                // 简介
                book.description?.takeIf { it.isNotBlank() }?.let {
                    Spacer(Modifier.height(20.dp))
                    Text(stringResource(R.string.details_description), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(8.dp))
                    Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, lineHeight = 22.sp)
                }

                // 本书笔记
                if (uiState.annotations.isNotEmpty()) {
                    Spacer(Modifier.height(20.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(stringResource(R.string.details_notes_section), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                        Text(
                            stringResource(R.string.details_view_all), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.clip(NarviveShape.Xs).clickable { showNotesSheet = true }.padding(4.dp),
                        )
                    }
                    Spacer(Modifier.height(12.dp))
                    uiState.annotations.take(2).forEach { ann ->
                        NoteCard(
                            ann,
                            chapterLabel = viewModel.chapterLabelOf(ann).takeIf { it != null } ?: ann.chapterTitle.ifBlank { null },
                            onDelete = { viewModel.deleteAnnotation(ann) },
                            onExport = { viewModel.exportAnnotation(ann) },
                        )
                        Spacer(Modifier.height(10.dp))
                    }
                }

                // 元数据
                Spacer(Modifier.height(20.dp))
                Card(Modifier.fillMaxWidth(), shape = NarviveShape.Md) {
                    Column(Modifier.padding(16.dp)) {
                        InfoRow(stringResource(R.string.details_info_format), book.format)
                        HorizontalDivider(Modifier.padding(vertical = 8.dp))
                        InfoRow(stringResource(R.string.details_info_imported_at), formatTime(book.importedAt))
                        HorizontalDivider(Modifier.padding(vertical = 8.dp))
                        InfoRow(stringResource(R.string.details_info_last_read), if (book.lastReadAt > 0) formatTime(book.lastReadAt) else stringResource(R.string.details_not_read))
                        book.fileHash?.let {
                            HorizontalDivider(Modifier.padding(vertical = 8.dp))
                            InfoRow(stringResource(R.string.details_info_file_hash), it.take(12) + "…")
                        }
                    }
                }
                Spacer(Modifier.height(24.dp))
            }
        }
    }
}

@Composable
private fun ProgressRingSmall(progress: Float) {
    val trackColor = MaterialTheme.colorScheme.outline
    val progressColor = MaterialTheme.colorScheme.primary
    Box(Modifier.size(48.dp), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val stroke = 3.dp.toPx()
            val inset = stroke / 2
            drawArc(trackColor, -90f, 360f, false, topLeft = Offset(inset, inset), size = Size(size.width - stroke, size.height - stroke), style = Stroke(width = stroke, cap = StrokeCap.Round))
            drawArc(progressColor, -90f, 360f * progress, false, topLeft = Offset(inset, inset), size = Size(size.width - stroke, size.height - stroke), style = Stroke(width = stroke, cap = StrokeCap.Round))
        }
    }
}

@Composable
private fun FormatChip(format: String) {
    Box(Modifier.clip(NarviveShape.Lg).background(MaterialTheme.colorScheme.surfaceVariant).padding(horizontal = 10.dp, vertical = 4.dp)) {
        Text(format, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun SecondaryAction(label: String, icon: androidx.compose.ui.graphics.vector.ImageVector, modifier: Modifier = Modifier, onClick: () -> Unit) {
    OutlinedButton(onClick = onClick, modifier = modifier.height(42.dp), shape = NarviveShape.Md, contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 4.dp)) {
        Icon(icon, null, Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.width(4.dp))
        Text(label, style = MaterialTheme.typography.labelMedium, maxLines = 1, color = MaterialTheme.colorScheme.onSurface)
    }
}

@Composable
private fun NoteCard(ann: Annotation, chapterLabel: String? = null, onDelete: () -> Unit = {}, onExport: () -> Unit = {}, modifier: Modifier = Modifier) {
    SwipeRevealRow(onDelete = onDelete, onExport = onExport, modifier = modifier) {
        Card(Modifier.fillMaxWidth().border(width = 0.dp, color = Color.Transparent, shape = NarviveShape.Md), shape = NarviveShape.Md) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                VerticalTypeLabel(ann.type)
                Column(Modifier.padding(12.dp).weight(1f)) {
                    Text(
                        ann.note ?: ann.translation ?: ann.rewrittenText ?: ann.selectedText,
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 3, overflow = TextOverflow.Ellipsis,
                    )
                    // 类型 · 章名 · 进度 · 时间
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(typeLabelForNotes(ann.type), style = MaterialTheme.typography.labelSmall, color = annotationTypeColor(ann.type))
                        val ch = chapterLabel ?: ann.chapterTitle.ifBlank { null }
                        ch?.let {
                            Text(" · " + it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                        }
                        ann.progress?.let { p ->
                            Text(" · %.1f%%".format(p * 100), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f))
                        }
                        Text(
                            " · " + formatTime(ann.createdAt),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                        )
                    }
                    // 正文摘录
                    if (ann.selectedText.isNotBlank()) {
                        Text(
                            ann.selectedText.take(80),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
    }
}

/** 笔记类型左侧色条（对齐主笔记面板设计） */
@Composable
private fun VerticalTypeLabel(type: AnnotationType) {
    Box(Modifier.width(3.dp).height(56.dp).background(annotationTypeColor(type)))
}

@Composable
private fun typeLabelForNotes(type: AnnotationType): String = when (type) {
    AnnotationType.HIGHLIGHT -> stringResource(R.string.details_type_highlight)
    AnnotationType.TRANSLATION -> stringResource(R.string.details_type_translation)
    AnnotationType.NOTE -> stringResource(R.string.details_type_note)
    AnnotationType.AI_ANSWER -> stringResource(R.string.details_type_ai_answer)
    AnnotationType.ROLEPLAY -> stringResource(R.string.details_type_roleplay)
    AnnotationType.REWRITE -> stringResource(R.string.details_type_rewrite)
}

@Composable
private fun EditMetadataDialog(initialTitle: String, initialAuthor: String, initialDescription: String, onDismiss: () -> Unit, onSave: (String, String?, String?) -> Unit) {
    var title by remember { mutableStateOf(initialTitle) }
    var author by remember { mutableStateOf(initialAuthor) }
    var description by remember { mutableStateOf(initialDescription) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.details_edit_info)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(title, { title = it }, label = { Text(stringResource(R.string.details_field_title)) }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(author, { author = it }, label = { Text(stringResource(R.string.details_field_author)) }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(description, { description = it }, label = { Text(stringResource(R.string.details_description)) }, modifier = Modifier.fillMaxWidth(), maxLines = 4)
            }
        },
        confirmButton = { TextButton(onClick = { onSave(title, author, description) }, enabled = title.isNotBlank()) { Text(stringResource(R.string.details_save)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.details_cancel)) } },
    )
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
    }
}

private fun formatTime(millis: Long): String =
    SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date(millis))
