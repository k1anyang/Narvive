package com.narvive.app.ui.screen.library

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.MenuBook
import androidx.compose.material.icons.automirrored.rounded.Sort
import androidx.compose.material.icons.automirrored.rounded.ViewList
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.GridView
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SearchBar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.rememberTopAppBarState
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import coil.request.CachePolicy
import coil.request.ImageRequest
import androidx.compose.ui.platform.LocalContext
import com.narvive.app.R
import com.narvive.app.domain.model.Book
import com.narvive.app.ui.components.BookCoverFallback
import com.narvive.app.ui.components.TabPageScaffold
import com.narvive.app.ui.theme.LocalElevation
import com.narvive.app.ui.theme.NarviveShape

private const val NEW_BOOK_THRESHOLD_MS = 3L * 24 * 60 * 60 * 1000

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryScreen(
    onBookClick: (String) -> Unit = {},
    viewModel: LibraryViewModel = hiltViewModel(),
    modifier: Modifier = Modifier,
) {
    val uiState by viewModel.uiState.collectAsState()
    var showMoreMenu by remember { mutableStateOf(false) }
    var showSortMenu by remember { mutableStateOf(false) }
    var isSearchActive by remember { mutableStateOf(false) }
    var batchDeleteCache by remember { mutableStateOf(true) }
    val snackbarHostState = remember { SnackbarHostState() }

    val filePickerLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenMultipleDocuments()
    ) { uris: List<Uri> -> viewModel.onFilesPicked(uris) }

    LaunchedEffect(uiState.error) {
        uiState.error?.let { snackbarHostState.showSnackbar(it); viewModel.clearError() }
    }
    LaunchedEffect(uiState.showBatchDeleteConfirm) {
        if (uiState.showBatchDeleteConfirm) batchDeleteCache = true
    }

    TabPageScaffold(
        title = if (uiState.selectionMode) stringResource(R.string.library_selected_count, uiState.selectedIds.size) else stringResource(R.string.library_title),
        modifier = modifier,
        navigationIcon = {
            if (uiState.selectionMode) {
                IconButton(onClick = viewModel::exitSelectionMode) { Icon(Icons.Rounded.Close, stringResource(R.string.library_close)) }
            }
        },
        actions = {
            if (uiState.selectionMode) {
                TextButton(onClick = viewModel::selectAll) { Text(stringResource(R.string.library_select_all), fontWeight = FontWeight.SemiBold) }
            } else {
                IconButton(onClick = { filePickerLauncher.launch(arrayOf("application/epub+zip", "application/pdf", "text/plain")) }) {
                    if (uiState.isImporting) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                    else Icon(Icons.Rounded.Add, stringResource(R.string.library_import_books))
                }
                IconButton(onClick = { isSearchActive = !isSearchActive }) { Icon(Icons.Rounded.Search, stringResource(R.string.library_search)) }
                Box {
                    IconButton(onClick = { showSortMenu = true }) { Icon(Icons.AutoMirrored.Rounded.Sort, stringResource(R.string.library_sort)) }
                    DropdownMenu(expanded = showSortMenu, onDismissRequest = { showSortMenu = false }) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.library_sort_asc)) },
                            onClick = { viewModel.onSortDirectionChange(SortDirection.ASC) },
                            trailingIcon = { if (uiState.sortDirection == SortDirection.ASC) Icon(Icons.Rounded.Check, null) },
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.library_sort_desc)) },
                            onClick = { viewModel.onSortDirectionChange(SortDirection.DESC) },
                            trailingIcon = { if (uiState.sortDirection == SortDirection.DESC) Icon(Icons.Rounded.Check, null) },
                        )
                        HorizontalDivider()
                        SortOrder.entries.forEach { order ->
                            DropdownMenuItem(
                                text = { Text(sortOrderLabel(order)) },
                                onClick = { viewModel.onSortOrderChange(order); showSortMenu = false },
                                trailingIcon = { if (uiState.sortOrder == order) Icon(Icons.Rounded.Check, null) },
                            )
                        }
                    }
                }
                Box {
                    IconButton(onClick = { showMoreMenu = true }) { Icon(Icons.Rounded.MoreVert, stringResource(R.string.library_more)) }
                    DropdownMenu(expanded = showMoreMenu, onDismissRequest = { showMoreMenu = false }) {
                        DropdownMenuItem(text = { Text(stringResource(R.string.library_collections_manage)) }, leadingIcon = { Icon(Icons.Rounded.Folder, null) }, onClick = { showMoreMenu = false; viewModel.toggleCollectionDialog() })
                        DropdownMenuItem(
                            text = { Text(if (uiState.viewMode == ViewMode.GRID) stringResource(R.string.library_switch_to_list) else stringResource(R.string.library_switch_to_grid)) },
                            leadingIcon = { Icon(if (uiState.viewMode == ViewMode.GRID) Icons.AutoMirrored.Rounded.ViewList else Icons.Rounded.GridView, null) },
                            onClick = { showMoreMenu = false; viewModel.onViewModeChange(if (uiState.viewMode == ViewMode.GRID) ViewMode.LIST else ViewMode.GRID) },
                        )
                    }
                }
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            Column(Modifier.fillMaxSize()) {
                AnimatedVisibility(isSearchActive && !uiState.selectionMode, enter = fadeIn() + expandVertically(), exit = fadeOut() + shrinkVertically()) {
                    SearchBar(
                        query = uiState.searchQuery, onQueryChange = viewModel::onSearchQueryChange, onSearch = {}, active = false, onActiveChange = {},
                        placeholder = { Text(stringResource(R.string.library_search_hint)) },
                        leadingIcon = { Icon(Icons.Rounded.Search, null) },
                        windowInsets = WindowInsets(0, 0, 0, 0),
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(top = 0.dp, bottom = 8.dp),
                    ) {}
                }
                if (uiState.isImporting) LinearProgressIndicator(Modifier.fillMaxWidth())

                // 继续阅读 · 单张大卡（最近一本）
                val recent = uiState.recentBooks.firstOrNull()
                if (recent != null && uiState.searchQuery.isBlank() && uiState.collectionFilter == null && !uiState.isLoading) {
                    ContinueReadingCard(
                        recent,
                        onClick = { onBookClick(recent.id) },
                        onLongClick = { viewModel.onBookLongPress(recent) },
                        enabled = !uiState.selectionMode,
                    )
                }

                // 合集筛选 chips — 始终显示（固定分组不受合集数量影响）
                CollectionChips(
                    uiState.collections,
                    uiState.collectionFilter,
                    viewModel::onCollectionFilterChange,
                    enabled = !uiState.selectionMode,
                )

                Box(Modifier.weight(1f)) {
                    when {
                        uiState.isLoading -> ShimmerGrid()
                        uiState.books.isEmpty() && uiState.collectionFilter == null -> EmptyLibrary(onImport = { filePickerLauncher.launch(arrayOf("application/epub+zip", "application/pdf", "text/plain")) })
                        uiState.books.isEmpty() -> EmptyCollection()
                        else -> Crossfade(uiState.viewMode, label = "view") { mode ->
                            if (mode == ViewMode.GRID) BookGrid(uiState.books, uiState.selectionMode, uiState.selectedIds, { onBookClick(it.id) }, viewModel::onBookLongPress, viewModel::toggleSelection, uiState.gridColumns)
                            else BookList(uiState.books, uiState.selectionMode, uiState.selectedIds, { onBookClick(it.id) }, viewModel::onBookLongPress, viewModel::toggleSelection)
                        }
                    }
                }
            }

            // 批量操作 · 底部悬浮胶囊栏
            if (uiState.selectionMode) {
                BatchActionBar(
                    modifier = Modifier.align(Alignment.BottomCenter).padding(horizontal = 20.dp, vertical = 16.dp),
                    onAddToCollection = viewModel::showBatchCollectionDialog,
                    onDelete = viewModel::requestBatchDelete,
                )
            }
        }
    }

    // 批量删除二次确认（F4）
    if (uiState.showBatchDeleteConfirm) {
        AlertDialog(
            onDismissRequest = viewModel::dismissBatchDelete,
            title = { Text(stringResource(R.string.library_batch_delete_title, uiState.selectedIds.size)) },
            text = {
                Column {
                    Text(stringResource(R.string.library_batch_delete_message, uiState.selectedIds.size))
                    Spacer(Modifier.height(12.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = batchDeleteCache, onCheckedChange = { batchDeleteCache = it })
                        Text(stringResource(R.string.library_delete_cache_too), style = MaterialTheme.typography.bodyMedium)
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { viewModel.confirmBatchDelete(batchDeleteCache) }) {
                    Text(stringResource(R.string.library_delete), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = viewModel::dismissBatchDelete) { Text(stringResource(R.string.library_cancel)) } },
        )
    }

    if (uiState.showImportConfirm) {
        ImportConfirmDialog(
            candidates = uiState.importCandidates,
            onToggle = viewModel::toggleImportCandidate,
            onConfirm = viewModel::confirmImport,
            onDismiss = viewModel::dismissImportConfirm,
        )
    }

    if (uiState.showCollectionDialog) {
        CollectionSheet(
            collections = uiState.collections.filter { it.name != "我喜欢" },
            onDismiss = viewModel::toggleCollectionDialog,
            onCreate = viewModel::onCreateCollection,
            onRename = viewModel::onRenameCollection,
            onDelete = viewModel::onDeleteCollection,
        )
    }

    if (uiState.showBatchCollectionDialog) {
        AlertDialog(
            onDismissRequest = viewModel::dismissBatchCollectionDialog,
            title = { Text(stringResource(R.string.library_add_to_collection)) },
            text = {
                Column {
                    if (uiState.collections.none { it.name != "我喜欢" }) {
                        Text(stringResource(R.string.library_no_collections_hint), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    } else {
                        uiState.collections.filter { it.name != "我喜欢" }.forEach { c ->
                            TextButton(onClick = { viewModel.batchAddToCollection(c.id) }, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.library_collection_with_count, c.name, c.bookCount)) }
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = viewModel::dismissBatchCollectionDialog) { Text(stringResource(R.string.library_cancel)) } },
        )
    }
}

// ── 导入确认弹窗 ──

@Composable
private fun ImportConfirmDialog(
    candidates: List<ImportCandidate>,
    onToggle: (Int) -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    val checking = candidates.any { it.checking }
    val selectedCount = candidates.count { !it.checking && !it.duplicate && it.error == null && it.selected }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.library_import_confirm_title)) },
        text = {
            Column {
                Text(stringResource(R.string.library_import_selected_files, candidates.size), style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.height(10.dp))
                LazyColumn(Modifier.heightIn(max = 300.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    itemsIndexed(candidates) { index, item ->
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(
                                checked = item.selected,
                                enabled = !item.checking && !item.duplicate && item.error == null,
                                onCheckedChange = { onToggle(index) },
                            )
                            Column(Modifier.weight(1f)) {
                                Text(
                                    item.name,
                                    style = MaterialTheme.typography.bodyMedium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    color = if (item.duplicate || item.error != null) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                                )
                                Text(
                                    when {
                                        item.checking -> stringResource(R.string.library_checking)
                                        item.duplicate -> stringResource(R.string.library_already_in_library_skip)
                                        item.error != null -> item.error
                                        else -> stringResource(
                                            R.string.library_format_and_size,
                                            item.format ?: stringResource(R.string.library_unknown_format),
                                            formatSize(item.size),
                                        )
                                    },
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (item.duplicate) MaterialTheme.colorScheme.outline else MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    stringResource(R.string.library_import_skip_note),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onConfirm, enabled = selectedCount > 0) {
                Text(if (checking) stringResource(R.string.library_import) else stringResource(R.string.library_import_with_count, selectedCount))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.library_cancel)) } },
    )
}

@Composable
private fun formatSize(bytes: Long): String = when {
    bytes <= 0 -> stringResource(R.string.library_unknown_size)
    bytes < 1024 -> "$bytes B"
    bytes < 1024 * 1024 -> "%.1f KB".format(bytes / 1024.0)
    else -> "%.1f MB".format(bytes / 1024.0 / 1024.0)
}

/** 排序方式显示名（枚举 label 属于其他文件，这里做本地化映射） */
@Composable
private fun sortOrderLabel(order: SortOrder): String = stringResource(
    when (order) {
        SortOrder.TITLE -> R.string.library_sort_by_title
        SortOrder.AUTHOR -> R.string.library_sort_by_author
        SortOrder.LAST_READ -> R.string.library_sort_by_last_read
        SortOrder.DATE_ADDED -> R.string.library_sort_by_date_added
        SortOrder.PROGRESS -> R.string.library_sort_by_progress
    }
)

// ── 顶栏 ──

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BatchTopBar(count: Int, onClose: () -> Unit, onSelectAll: () -> Unit) {
    TopAppBar(
        title = { Text(stringResource(R.string.library_selected_count, count), fontWeight = FontWeight.Bold) },
        navigationIcon = { IconButton(onClick = onClose) { Icon(Icons.Rounded.Close, stringResource(R.string.library_close)) } },
        actions = { TextButton(onClick = onSelectAll) { Text(stringResource(R.string.library_select_all), fontWeight = FontWeight.SemiBold) } },
        colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface),
    )
}

@Composable
private fun CollectionChips(
    collections: List<CollectionItem>,
    selected: String?,
    onSelect: (String?) -> Unit,
    enabled: Boolean = true,
) {
    LazyRow(
        Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item { FilterChip(selected = selected == null, onClick = { onSelect(null) }, enabled = enabled, label = { Text(stringResource(R.string.library_filter_all)) }) }
        item { FilterChip(selected = selected == "reading", onClick = { onSelect("reading") }, enabled = enabled, label = { Text(stringResource(R.string.library_filter_reading)) }) }
        item { FilterChip(selected = selected == "favorite", onClick = { onSelect("favorite") }, enabled = enabled, label = { Text(stringResource(R.string.library_filter_favorite)) }) }
        items(collections.filter { it.name != "我喜欢" }) { c -> FilterChip(selected = selected == c.id, onClick = { onSelect(c.id) }, enabled = enabled, label = { Text(c.name) }) }
        item { FilterChip(selected = selected == "ungrouped", onClick = { onSelect("ungrouped") }, enabled = enabled, label = { Text(stringResource(R.string.library_filter_ungrouped)) }) }
    }
}

// ── 继续阅读 · 单卡 ──

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
private fun ContinueReadingCard(book: Book, onClick: () -> Unit, onLongClick: () -> Unit, enabled: Boolean = true) {
    Card(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .alpha(if (enabled) 1f else 0.55f)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick, enabled = enabled),
        shape = NarviveShape.Md,
        elevation = CardDefaults.cardElevation(LocalElevation.current.level2),
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(74.dp, 106.dp).clip(NarviveShape.BookCover)) { BookCoverImage(book, Modifier.fillMaxSize()) }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.library_continue_reading), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold, letterSpacing = 0.5.sp)
                Spacer(Modifier.height(4.dp))
                Text(book.title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                book.currentChapter?.let {
                    Spacer(Modifier.height(2.dp))
                    Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Spacer(Modifier.height(10.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    LinearProgressIndicator(progress = { book.progress }, modifier = Modifier.weight(1f).height(4.dp).clip(NarviveShape.Xs), color = MaterialTheme.colorScheme.primary, trackColor = MaterialTheme.colorScheme.surfaceVariant)
                    Spacer(Modifier.width(10.dp))
                    Text("${(book.progress * 100).toInt()}%", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

// ── 批量操作 · 底部悬浮胶囊 ──

@Composable
private fun BatchActionBar(modifier: Modifier = Modifier, onAddToCollection: () -> Unit, onDelete: () -> Unit) {
    Surface(
        modifier = modifier.fillMaxWidth().height(56.dp),
        shape = NarviveShape.Xl,
        tonalElevation = LocalElevation.current.level2,
        shadowElevation = LocalElevation.current.level3,
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
    ) {
        Row(Modifier.fillMaxSize().padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextButton(onClick = onAddToCollection, modifier = Modifier.weight(1f)) {
                Icon(Icons.Rounded.Folder, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text(stringResource(R.string.library_add_to_collection))
            }
            TextButton(onClick = onDelete, modifier = Modifier.weight(1f)) {
                Icon(Icons.Rounded.Delete, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.error)
                Spacer(Modifier.width(6.dp)); Text(stringResource(R.string.library_delete), color = MaterialTheme.colorScheme.error)
            }
        }
    }
}

// ── 网格 ──

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
private fun BookGrid(
    books: List<Book>,
    selectionMode: Boolean,
    selectedIds: Set<String>,
    onClick: (Book) -> Unit,
    onLongClick: (Book) -> Unit,
    onToggleSelect: (String) -> Unit,
    columns: Int = 2,
) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(columns.coerceIn(1, 4)),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        items(books, key = { it.id }) { book ->
            BookGridCell(
                book,
                isNew = isFreshImport(book),
                selected = book.id in selectedIds,
                selectionMode = selectionMode,
                onClick = { if (selectionMode) onToggleSelect(book.id) else onClick(book) },
                onLongClick = { onLongClick(book) },
            )
        }
    }
}

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
private fun BookGridCell(book: Book, isNew: Boolean, selected: Boolean, selectionMode: Boolean, onClick: () -> Unit, onLongClick: () -> Unit) {
    Column(Modifier.combinedClickable(onClick = onClick, onLongClick = onLongClick)) {
        Box(
            Modifier.fillMaxWidth().aspectRatio(0.667f).clip(NarviveShape.BookCover)
                .then(if (selected) Modifier.border(3.dp, MaterialTheme.colorScheme.primary, NarviveShape.BookCover) else Modifier),
        ) {
            BookCoverImage(book, Modifier.fillMaxSize())
            if (book.progress > 0f) {
                Box(Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(3.dp).background(Color.Black.copy(alpha = 0.15f))) {
                    Box(Modifier.fillMaxWidth(book.progress).height(3.dp).background(MaterialTheme.colorScheme.primary))
                }
            }
            if (isNew && !selectionMode) {
                Box(Modifier.align(Alignment.TopStart).padding(6.dp).clip(NarviveShape.Xs).background(MaterialTheme.colorScheme.primary).padding(horizontal = 6.dp, vertical = 2.dp)) {
                    Text(stringResource(R.string.library_badge_new), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onPrimary)
                }
            }
            if (selectionMode) {
                Box(
                    Modifier.align(Alignment.TopStart).padding(8.dp).size(22.dp).clip(CircleShape)
                        .background(if (selected) MaterialTheme.colorScheme.primary else Color.White.copy(alpha = 0.7f))
                        .border(2.dp, if (selected) MaterialTheme.colorScheme.primary else Color.Black.copy(alpha = 0.3f), CircleShape),
                    contentAlignment = Alignment.Center,
                ) { if (selected) Icon(Icons.Rounded.Check, null, Modifier.size(13.dp), tint = MaterialTheme.colorScheme.onPrimary) }
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(book.title, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(book.author ?: stringResource(R.string.library_unknown_author), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

// ── 列表 ──

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
private fun BookList(books: List<Book>, selectionMode: Boolean, selectedIds: Set<String>, onClick: (Book) -> Unit, onLongClick: (Book) -> Unit, onToggleSelect: (String) -> Unit) {
    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        items(books, key = { it.id }) { book ->
            Card(
                shape = NarviveShape.Md,
                elevation = CardDefaults.cardElevation(LocalElevation.current.level2),
                colors = CardDefaults.cardColors(containerColor = if (book.id in selectedIds) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface),
                modifier = Modifier.combinedClickable(onClick = { if (selectionMode) onToggleSelect(book.id) else onClick(book) }, onLongClick = { onLongClick(book) }),
            ) {
                Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(60.dp, 90.dp).clip(NarviveShape.BookCover)) {
                        BookCoverImage(book, Modifier.fillMaxSize())
                        if (selectionMode) {
                            Box(
                                Modifier.align(Alignment.TopStart).padding(5.dp).size(20.dp).clip(CircleShape)
                                    .background(if (book.id in selectedIds) MaterialTheme.colorScheme.primary else Color.White.copy(alpha = 0.75f))
                                    .border(2.dp, if (book.id in selectedIds) MaterialTheme.colorScheme.primary else Color.Black.copy(alpha = 0.3f), CircleShape),
                                contentAlignment = Alignment.Center,
                            ) {
                                if (book.id in selectedIds) Icon(Icons.Rounded.Check, null, Modifier.size(12.dp), tint = MaterialTheme.colorScheme.onPrimary)
                            }
                        }
                    }
                    Spacer(Modifier.width(16.dp))
                    Column(Modifier.weight(1f)) {
                        Text(book.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        Spacer(Modifier.height(4.dp))
                        Text(book.author ?: stringResource(R.string.library_unknown_author), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        if (book.progress > 0f) {
                            Spacer(Modifier.height(8.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                LinearProgressIndicator(progress = { book.progress }, modifier = Modifier.weight(1f).height(4.dp).clip(NarviveShape.Xs))
                                Spacer(Modifier.width(8.dp))
                                Text("${(book.progress * 100).toInt()}%", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                            }
                        }
                    }
                    Spacer(Modifier.width(8.dp))
                    FormatBadge(book.format)
                }
            }
        }
    }
}

// ── 合集管理 · 底部弹层 ──

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CollectionSheet(
    collections: List<CollectionItem>,
    onDismiss: () -> Unit,
    onCreate: (String) -> Unit,
    onRename: (String, String) -> Unit,
    onDelete: (String) -> Unit,
) {
    var showCreate by remember { mutableStateOf(false) }
    var renameTarget by remember { mutableStateOf<CollectionItem?>(null) }
    var inputName by remember { mutableStateOf("") }

    if (showCreate) {
        AlertDialog(
            onDismissRequest = { showCreate = false; inputName = "" },
            title = { Text(stringResource(R.string.library_new_collection)) },
            text = { androidx.compose.material3.OutlinedTextField(inputName, { inputName = it }, label = { Text(stringResource(R.string.library_collection_name)) }, singleLine = true, modifier = Modifier.fillMaxWidth()) },
            confirmButton = { TextButton(onClick = { if (inputName.isNotBlank()) { onCreate(inputName.trim()); inputName = ""; showCreate = false } }, enabled = inputName.isNotBlank()) { Text(stringResource(R.string.library_create)) } },
            dismissButton = { TextButton(onClick = { showCreate = false; inputName = "" }) { Text(stringResource(R.string.library_cancel)) } },
        )
    }
    renameTarget?.let { coll ->
        AlertDialog(
            onDismissRequest = { renameTarget = null; inputName = "" },
            title = { Text(stringResource(R.string.library_rename_collection)) },
            text = { androidx.compose.material3.OutlinedTextField(inputName.ifEmpty { coll.name }, { inputName = it }, label = { Text(stringResource(R.string.library_new_name)) }, singleLine = true, modifier = Modifier.fillMaxWidth()) },
            confirmButton = { TextButton(onClick = { if (inputName.isNotBlank()) { onRename(coll.id, inputName.trim()); renameTarget = null; inputName = "" } }, enabled = inputName.isNotBlank()) { Text(stringResource(R.string.library_confirm)) } },
            dismissButton = { TextButton(onClick = { renameTarget = null; inputName = "" }) { Text(stringResource(R.string.library_cancel)) } },
        )
    }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState()) {
        Column(Modifier.padding(bottom = 24.dp)) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.library_collections_manage), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                TextButton(onClick = { showCreate = true }) { Icon(Icons.Rounded.Add, null, Modifier.size(18.dp)); Spacer(Modifier.width(4.dp)); Text(stringResource(R.string.library_new_collection)) }
            }
            if (collections.isEmpty()) {
                Text(stringResource(R.string.library_no_collections_hint_short), Modifier.padding(20.dp), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                LazyColumn(Modifier.heightIn(max = 320.dp)) {
                    items(collections) { coll ->
                        Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.size(14.dp).clip(NarviveShape.Xs).background(collectionColor(coll.id)))
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(coll.name, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                                Text(stringResource(R.string.library_book_count, coll.bookCount), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            IconButton(onClick = { renameTarget = coll }, modifier = Modifier.size(34.dp)) { Icon(Icons.Rounded.Folder, stringResource(R.string.library_rename), tint = MaterialTheme.colorScheme.onSurfaceVariant) }
                            IconButton(onClick = { onDelete(coll.id) }, modifier = Modifier.size(34.dp)) { Icon(Icons.Rounded.Delete, stringResource(R.string.library_delete), tint = MaterialTheme.colorScheme.error) }
                        }
                    }
                }
            }
            Text(
                stringResource(R.string.library_collections_help),
                Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
                style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, lineHeight = 16.sp,
            )
        }
    }
}

private val collectionPalette = listOf(Color(0xFF0284C7), Color(0xFFF472B6), Color(0xFF4ADE80), Color(0xFFA78BFA), Color(0xFFFACC15), Color(0xFF94A3B8))
private fun collectionColor(id: String): Color = collectionPalette[(id.hashCode().and(Int.MAX_VALUE)) % collectionPalette.size]

// ── 共享 ──

@Composable
fun BookCoverImage(book: Book, modifier: Modifier = Modifier) {
    if (book.coverPath != null) {
        AsyncImage(
            model = ImageRequest.Builder(LocalContext.current).data(book.coverPath).crossfade(200).memoryCachePolicy(CachePolicy.ENABLED).diskCachePolicy(CachePolicy.ENABLED).build(),
            contentDescription = book.title, contentScale = ContentScale.Crop, modifier = modifier,
        )
    } else {
        BookCoverFallback(book.title, modifier)
    }
}

@Composable
fun FormatBadge(format: String) {
    Box(Modifier.clip(NarviveShape.Xs).background(MaterialTheme.colorScheme.secondaryContainer).padding(horizontal = 8.dp, vertical = 4.dp)) {
        Text(format, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSecondaryContainer)
    }
}

private fun isFreshImport(book: Book): Boolean =
    book.progress == 0f && System.currentTimeMillis() - book.importedAt < NEW_BOOK_THRESHOLD_MS

@Composable
private fun EmptyLibrary(onImport: () -> Unit) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(32.dp)) {
            Icon(Icons.AutoMirrored.Rounded.MenuBook, null, Modifier.size(72.dp), tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f))
            Spacer(Modifier.height(24.dp))
            Text(stringResource(R.string.library_empty_title), style = MaterialTheme.typography.headlineSmall)
            Spacer(Modifier.height(8.dp))
            Text(stringResource(R.string.library_empty_subtitle), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, lineHeight = 20.sp, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
            Spacer(Modifier.height(20.dp))
            Button(onClick = onImport, modifier = Modifier.height(48.dp)) {
                Icon(Icons.Rounded.Add, null, Modifier.size(20.dp)); Spacer(Modifier.width(6.dp)); Text(stringResource(R.string.library_import_books), style = MaterialTheme.typography.titleSmall)
            }
            Spacer(Modifier.height(16.dp))
            Text(stringResource(R.string.library_empty_share_hint), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun EmptyCollection() {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(Icons.AutoMirrored.Rounded.MenuBook, null, Modifier.size(64.dp), tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.35f))
            Spacer(Modifier.height(16.dp))
            Text(stringResource(R.string.library_empty_collection), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun ShimmerGrid() {
    LazyVerticalGrid(columns = GridCells.Fixed(2), contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp), horizontalArrangement = Arrangement.spacedBy(14.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        items(6) {
            Column {
                Box(Modifier.fillMaxWidth().aspectRatio(0.667f).clip(NarviveShape.BookCover).background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)))
                Spacer(Modifier.height(8.dp))
                Box(Modifier.fillMaxWidth(0.85f).height(14.dp).background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f), NarviveShape.Xs))
                Spacer(Modifier.height(6.dp))
                Box(Modifier.fillMaxWidth(0.55f).height(12.dp).background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f), NarviveShape.Xs))
            }
        }
    }
}
