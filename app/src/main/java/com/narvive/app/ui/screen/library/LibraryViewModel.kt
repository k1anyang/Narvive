package com.narvive.app.ui.screen.library

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.narvive.app.R
import com.narvive.app.data.datastore.NarviveDataStore
import com.narvive.app.domain.model.Book
import com.narvive.app.domain.repository.BookshelfRepository
import com.narvive.app.service.AppCacheService
import com.narvive.app.service.BookImportService
import com.narvive.app.service.DuplicateBookException
import com.narvive.app.service.ExternalImportBus
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class LibraryViewModel @Inject constructor(
    @ApplicationContext private val appContext: Context,
    private val bookshelfRepo: BookshelfRepository,
    private val importService: BookImportService,
    private val dataStore: NarviveDataStore,
    private val importBus: ExternalImportBus,
    private val cacheService: AppCacheService,
) : ViewModel() {

    private val _uiState = MutableStateFlow(LibraryUiState())
    val uiState: StateFlow<LibraryUiState> = _uiState.asStateFlow()

    /** 用户可变的过滤/排序/搜索偏好（纯输入，不包含派生数据 → 无自反馈） */
    private val filterState = MutableStateFlow(FilterPrefs())

    private data class FilterPrefs(
        val sortOrder: SortOrder = SortOrder.LAST_READ,
        val searchQuery: String = "",
        val collectionFilter: String? = null,
    )

    init {
        // 主数据流：书籍 + 合集 + 成员关系 + 过滤偏好 → 派生状态（无自反馈）
        viewModelScope.launch {
            combine(
                bookshelfRepo.observeAllBooks(),
                bookshelfRepo.observeCollections(),
                bookshelfRepo.observeCollectionMembership(),
                filterState,
                dataStore.librarySortDirection,
            ) { books, collections, membership, filter, storedDirection ->
                val direction = when (storedDirection) {
                    "asc" -> SortDirection.ASC
                    "desc" -> SortDirection.DESC
                    else -> if (filter.sortOrder.defaultDescending) SortDirection.DESC else SortDirection.ASC
                }
                val sorted = sortBooks(books, filter.sortOrder, direction)
                val searched = if (filter.searchQuery.isBlank()) sorted
                else sorted.filter {
                    it.title.contains(filter.searchQuery, ignoreCase = true) ||
                        (it.author?.contains(filter.searchQuery, ignoreCase = true) == true)
                }
                val filtered = when (filter.collectionFilter) {
                    null -> searched
                    "reading" -> searched.filter { !it.isFinished && it.progress > 0f }
                    "favorite" -> {
                        val favId = collections.firstOrNull { it.name == "我喜欢" }?.id
                        if (favId != null) searched.filter { membership[it.id]?.contains(favId) == true }
                        else emptyList()
                    }
                    "ungrouped" -> searched.filter { membership[it.id].isNullOrEmpty() }
                    else -> searched.filter { membership[it.id]?.contains(filter.collectionFilter) == true }
                }
                val recent = books.filter { !it.isFinished && it.progress > 0f }
                    .sortedByDescending { it.lastReadAt }
                    .take(5)
                _uiState.value.copy(
                    books = filtered,
                    recentBooks = recent,
                    collections = collections.map { CollectionItem(it.id, it.name, it.bookCount) },
                    isLoading = false,
                    sortDirection = direction,
                )
            }.catch { e -> _uiState.update { it.copy(isLoading = false, error = e.message) } }
                .collect { _uiState.value = it }
        }

        // 持久化偏好加载
        viewModelScope.launch {
            dataStore.libraryViewMode.first().let { mode ->
                _uiState.update { it.copy(viewMode = if (mode == "list") ViewMode.LIST else ViewMode.GRID) }
            }
        }
        viewModelScope.launch {
            dataStore.librarySortOrder.first().let { order ->
                val so = runCatching { SortOrder.valueOf(order) }.getOrDefault(SortOrder.LAST_READ)
                _uiState.update { it.copy(sortOrder = so) }
                filterState.update { it.copy(sortOrder = so) }
            }
        }
        viewModelScope.launch {
            dataStore.gridColumns.collect { columns ->
                _uiState.update { it.copy(gridColumns = columns.coerceIn(1, 4)) }
            }
        }

        // 系统分享/打开方式导入
        viewModelScope.launch {
            importBus.pendingUri.collect { uri ->
                if (uri != null) { importBook(uri); importBus.consume() }
            }
        }
    }

    private fun sortBooks(books: List<Book>, order: SortOrder, direction: SortDirection): List<Book> {
        val desc = direction == SortDirection.DESC
        val sorted: List<Book> = when (order) {
            SortOrder.TITLE -> books.sortedBy { it.title.lowercase() }
            SortOrder.AUTHOR -> books.sortedBy { (it.author ?: "").lowercase() }
            SortOrder.LAST_READ -> books.sortedBy { it.lastReadAt }
            SortOrder.DATE_ADDED -> books.sortedBy { it.importedAt }
            SortOrder.PROGRESS -> books.sortedBy { it.progress }
        }
        return if (desc) sorted.reversed() else sorted
    }

    fun importBook(uri: Uri) {
        viewModelScope.launch {
            _uiState.update { it.copy(isImporting = true, error = null) }
            importService.importBook(uri)
                .onSuccess { book -> bookshelfRepo.insertOrUpdate(book) }
                .onFailure { e ->
                    val msg = if (e is DuplicateBookException) {
                        e.message ?: appContext.getString(R.string.lib_vm_book_exists)
                    } else {
                        e.message ?: appContext.getString(R.string.lib_vm_import_failed)
                    }
                    _uiState.update { it.copy(error = msg) }
                }
            _uiState.update { it.copy(isImporting = false) }
        }
    }

    fun onSearchQueryChange(query: String) {
        _uiState.update { it.copy(searchQuery = query) }
        filterState.update { it.copy(searchQuery = query) }
    }
    fun onSortOrderChange(order: SortOrder) {
        _uiState.update { it.copy(sortOrder = order) }
        filterState.update { it.copy(sortOrder = order) }
        viewModelScope.launch { dataStore.setLibrarySortOrder(order.name) }
    }
    fun onSortDirectionChange(direction: SortDirection) {
        _uiState.update { it.copy(sortDirection = direction) }
        viewModelScope.launch { dataStore.setLibrarySortDirection(if (direction == SortDirection.ASC) "asc" else "desc") }
    }
    fun onViewModeChange(mode: ViewMode) {
        _uiState.update { it.copy(viewMode = mode) }
        viewModelScope.launch { dataStore.setLibraryViewMode(if (mode == ViewMode.LIST) "list" else "grid") }
    }
    fun onCollectionFilterChange(filter: String?) {
        _uiState.update { it.copy(collectionFilter = filter) }
        filterState.update { it.copy(collectionFilter = filter) }
    }

    fun toggleCollectionDialog() { _uiState.update { it.copy(showCollectionDialog = !it.showCollectionDialog) } }
    fun onCreateCollection(name: String) {
        // 合集重名校验
        if (_uiState.value.collections.any { it.name.equals(name, ignoreCase = true) }) {
            _uiState.update { it.copy(error = appContext.getString(R.string.lib_vm_collection_exists, name)) }
            return
        }
        viewModelScope.launch { bookshelfRepo.createCollection(name) }
    }
    fun onRenameCollection(id: String, name: String) {
        viewModelScope.launch { bookshelfRepo.renameCollection(id, name) }
    }
    fun onDeleteCollection(id: String) {
        viewModelScope.launch { bookshelfRepo.deleteCollection(id) }
    }

    // ── 多选模式 ──
    fun onBookLongPress(book: Book) {
        if (_uiState.value.selectionMode) {
            toggleSelection(book.id)
        } else {
            _uiState.update { it.copy(selectionMode = true, selectedIds = setOf(book.id)) }
        }
    }
    fun toggleSelection(id: String) {
        _uiState.update {
            val ids = if (id in it.selectedIds) it.selectedIds - id else it.selectedIds + id
            it.copy(selectedIds = ids, selectionMode = ids.isNotEmpty())
        }
    }

    // ── 多选导入 + 二级确认 ──

    /** 系统选择器返回多个 Uri：先检测文件信息（格式/重复），确认后才入库 */
    fun onFilesPicked(uris: List<Uri>) {
        if (uris.isEmpty()) return
        val candidates = uris.map { uri ->
            ImportCandidate(
                key = uri.toString(),
                name = uri.lastPathSegment ?: appContext.getString(R.string.lib_vm_unknown_file),
                size = 0L,
                format = null,
            )
        }
        _uiState.update { it.copy(showImportConfirm = true, importCandidates = candidates) }
        viewModelScope.launch {
            uris.forEachIndexed { index, uri ->
                val result = importService.inspectFile(uri)
                _uiState.update { state ->
                    val list = state.importCandidates.toMutableList()
                    if (index in list.indices) {
                        list[index] = list[index].copy(
                            name = result.fileName,
                            size = result.size,
                            format = result.format,
                            checking = false,
                            duplicate = result.duplicate,
                            error = result.error,
                            selected = !result.duplicate && result.error == null,
                        )
                    }
                    state.copy(importCandidates = list)
                }
            }
        }
    }

    fun toggleImportCandidate(index: Int) {
        _uiState.update { state ->
            val list = state.importCandidates.toMutableList()
            if (index in list.indices) {
                val item = list[index]
                if (!item.checking && !item.duplicate && item.error == null) {
                    list[index] = item.copy(selected = !item.selected)
                }
            }
            state.copy(importCandidates = list)
        }
    }

    fun dismissImportConfirm() {
        _uiState.update { it.copy(showImportConfirm = false, importCandidates = emptyList()) }
    }

    fun confirmImport() {
        val uris = _uiState.value.importCandidates
            .filter { it.selected && !it.duplicate && !it.checking && it.error == null }
            .map { Uri.parse(it.key) }
        _uiState.update { it.copy(showImportConfirm = false, importCandidates = emptyList()) }
        if (uris.isEmpty()) return
        viewModelScope.launch {
            _uiState.update { it.copy(isImporting = true, error = null) }
            var ok = 0
            val failures = mutableListOf<String>()
            uris.forEach { uri ->
                importService.importBook(uri)
                    .onSuccess { book ->
                        bookshelfRepo.insertOrUpdate(book)
                        ok++
                    }
                    .onFailure { e ->
                        failures += e.message ?: appContext.getString(R.string.lib_vm_import_failed)
                    }
            }
            _uiState.update {
                it.copy(
                    isImporting = false,
                    error = when {
                        failures.isEmpty() -> null
                        ok > 0 -> appContext.getString(
                            R.string.lib_vm_import_batch_result,
                            ok,
                            failures.size,
                            failures.first(),
                        )
                        else -> failures.first()
                    },
                )
            }
        }
    }
    fun selectAll() {
        _uiState.update { it.copy(selectedIds = it.books.map { b -> b.id }.toSet(), selectionMode = true) }
    }
    fun exitSelectionMode() { _uiState.update { it.copy(selectionMode = false, selectedIds = emptySet()) } }

    fun showBatchCollectionDialog() { _uiState.update { it.copy(showBatchCollectionDialog = true) } }
    fun dismissBatchCollectionDialog() { _uiState.update { it.copy(showBatchCollectionDialog = false) } }
    fun batchAddToCollection(collectionId: String) {
        viewModelScope.launch {
            _uiState.value.selectedIds.forEach { bookshelfRepo.addBookToCollection(it, collectionId) }
            exitSelectionMode()
            _uiState.update { it.copy(showBatchCollectionDialog = false) }
        }
    }
    /** 批量删除：先弹二次确认（F4），确认后由 confirmBatchDelete 执行 */
    fun requestBatchDelete() {
        if (_uiState.value.selectedIds.isEmpty()) return
        _uiState.update { it.copy(showBatchDeleteConfirm = true) }
    }
    fun dismissBatchDelete() { _uiState.update { it.copy(showBatchDeleteConfirm = false) } }
    fun confirmBatchDelete(deleteCache: Boolean) {
        val ids = _uiState.value.selectedIds
        _uiState.update { it.copy(showBatchDeleteConfirm = false) }
        if (ids.isEmpty()) return
        viewModelScope.launch {
            ids.forEach { id ->
                bookshelfRepo.getBook(id)?.let { importService.deleteBookFiles(it) }
                bookshelfRepo.deleteBook(id)
            }
            if (deleteCache) cacheService.clearAppCaches()
            exitSelectionMode()
        }
    }

    fun clearError() { _uiState.update { it.copy(error = null) } }
}
