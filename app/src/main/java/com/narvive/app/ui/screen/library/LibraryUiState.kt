package com.narvive.app.ui.screen.library

import androidx.annotation.StringRes
import com.narvive.app.R
import com.narvive.app.domain.model.Book

data class LibraryUiState(
    val books: List<Book> = emptyList(),
    val recentBooks: List<Book> = emptyList(),
    val isLoading: Boolean = true,
    val isImporting: Boolean = false,
    val error: String? = null,
    val searchQuery: String = "",
    val sortOrder: SortOrder = SortOrder.LAST_READ,
    val sortDirection: SortDirection = SortDirection.DESC,
    val viewMode: ViewMode = ViewMode.GRID,
    val gridColumns: Int = 2,
    val selectedBook: Book? = null,
    val showDeleteDialog: Boolean = false,
    val showCollectionDialog: Boolean = false,
    val collections: List<CollectionItem> = emptyList(),
    val collectionFilter: String? = null, // null=全部, "ungrouped"=未分组, 否则为 collectionId
    val selectionMode: Boolean = false,
    val selectedIds: Set<String> = emptySet(),
    val showBatchCollectionDialog: Boolean = false,
    /** 批量删除二次确认（F4：防误删本地文件） */
    val showBatchDeleteConfirm: Boolean = false,
    /** 多选文件后的导入确认弹窗 */
    val showImportConfirm: Boolean = false,
    val importCandidates: List<ImportCandidate> = emptyList(),
)

enum class SortOrder(@StringRes val labelRes: Int, val defaultDescending: Boolean) {
    TITLE(R.string.lib_vm_sort_title, false),
    AUTHOR(R.string.lib_vm_sort_author, false),
    LAST_READ(R.string.lib_vm_sort_last_read, true),
    DATE_ADDED(R.string.lib_vm_sort_date_added, true),
    PROGRESS(R.string.lib_vm_sort_progress, true),
}

enum class SortDirection(@StringRes val labelRes: Int) {
    ASC(R.string.lib_vm_sort_asc),
    DESC(R.string.lib_vm_sort_desc),
}

enum class ViewMode { GRID, LIST }

data class CollectionItem(val id: String, val name: String, val bookCount: Int = 0)

/** 多选导入的候选文件（确认弹窗条目） */
data class ImportCandidate(
    val key: String,          // content uri 字符串
    val name: String,
    val size: Long,
    val format: String?,
    val checking: Boolean = true,
    val duplicate: Boolean = false,
    val error: String? = null,
    val selected: Boolean = true,
)
