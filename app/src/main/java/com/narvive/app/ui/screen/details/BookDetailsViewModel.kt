package com.narvive.app.ui.screen.details

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.narvive.app.R
import com.narvive.app.data.datastore.NarviveDataStore
import com.narvive.app.domain.model.Annotation
import com.narvive.app.domain.model.AnnotationType
import com.narvive.app.domain.model.Book
import com.narvive.app.domain.repository.AnnotationRepository
import com.narvive.app.domain.repository.BookshelfRepository
import com.narvive.app.service.AppCacheService
import com.narvive.app.service.BookImportService
import com.narvive.app.service.reader.TocItem
import com.narvive.app.service.reader.TocLoader
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.json.JSONObject
import javax.inject.Inject

data class BookDetailsUiState(
    val book: Book? = null,
    val annotations: List<Annotation> = emptyList(),
    val showDeleteDialog: Boolean = false,
    val showEditDialog: Boolean = false,
    val showCollectionSheet: Boolean = false,
    val collections: List<CollectionAssignment> = emptyList(),
    /** 目录 BottomSheet（H2/F5） */
    val showTocSheet: Boolean = false,
    val tocItems: List<TocItem> = emptyList(),
    val isTocLoading: Boolean = false,
    /** 顶栏喜欢态：当前书籍是否在"我喜欢"合集中 */
    val isFavorited: Boolean = false,
    /** 阅读器打开动画开关（详情页进入阅读页时是否启用封面展开） */
    val bookOpenAnimation: Boolean = false,
)

data class CollectionAssignment(val id: String, val name: String, val assigned: Boolean)

@HiltViewModel
class BookDetailsViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val bookshelfRepo: BookshelfRepository,
    private val importService: BookImportService,
    private val annotationRepo: AnnotationRepository,
    private val tocLoader: TocLoader,
    private val dataStore: NarviveDataStore,
    private val cacheService: AppCacheService,
) : ViewModel() {

    private val _uiState = MutableStateFlow(BookDetailsUiState())
    val uiState: StateFlow<BookDetailsUiState> = _uiState.asStateFlow()

    private var loadedBookId: String? = null

    fun loadBook(bookId: String) {
        if (loadedBookId == bookId) return
        loadedBookId = bookId
        viewModelScope.launch {
            combine(
                bookshelfRepo.observeAllBooks(),
                annotationRepo.observeByBook(bookId),
                bookshelfRepo.observeCollections(),
                bookshelfRepo.observeCollectionMembership(),
                dataStore.bookOpenAnimation,
            ) { books, annotations, collections, membership, bookOpenAnimation ->
                val book = books.find { it.id == bookId }
                val assigned = membership[bookId] ?: emptySet()
                BookDetailsUiState(
                    book = book,
                    annotations = annotations.sortedByDescending { it.createdAt },
                    collections = collections.map { CollectionAssignment(it.id, it.name, it.id in assigned) },
                    isFavorited = book != null && collections.any { it.name == "我喜欢" && it.id in assigned },
                    bookOpenAnimation = bookOpenAnimation,
                )
            }.collect {
                _uiState.value = it.copy(
                    showDeleteDialog = _uiState.value.showDeleteDialog,
                    showEditDialog = _uiState.value.showEditDialog,
                    showCollectionSheet = _uiState.value.showCollectionSheet,
                    showTocSheet = _uiState.value.showTocSheet,
                    tocItems = _uiState.value.tocItems,
                    isTocLoading = _uiState.value.isTocLoading,
                )
            }
        }
        // F5/F7：后台预解析目录（详情页目录 BottomSheet + 笔记卡章节名共用）
        viewModelScope.launch {
            val book = bookshelfRepo.getBook(bookId) ?: return@launch
            _uiState.update { it.copy(isTocLoading = true) }
            val toc = tocLoader.load(book.format, book.filePath)
            _uiState.update { it.copy(tocItems = toc, isTocLoading = false) }
        }
    }

    // ---------- 目录 BottomSheet（H2/F5） ----------

    fun showToc() { _uiState.update { it.copy(showTocSheet = true) } }
    fun dismissToc() { _uiState.update { it.copy(showTocSheet = false) } }

    /** 笔记卡章节名（F7）：TXT 按章索引、EPUB 按 href 匹配目录标题 */
    fun chapterLabelOf(annotation: Annotation): String? {
        val toc = _uiState.value.tocItems
        if (toc.isEmpty()) return null
        val locator = annotation.locatorJson
        if (locator.isBlank()) return null
        return runCatching {
            val obj = JSONObject(locator)
            when {
                obj.has("chapter") -> toc.getOrNull(obj.optInt("chapter"))?.title
                obj.has("href") -> {
                    val href = obj.optString("href")
                    toc.firstOrNull { it.locatorJson.contains("\"href\":\"$href\"") }?.title
                }
                else -> null
            }
        }.getOrNull()
    }

    // ---------- 顶栏喜欢 ----------

    fun toggleFavorite() {
        val book = _uiState.value.book ?: return
        viewModelScope.launch {
            val collections = bookshelfRepo.observeCollections().first()
            val fav = collections.firstOrNull { it.name == "我喜欢" }
                ?: run { bookshelfRepo.createCollection("我喜欢"); bookshelfRepo.observeCollections().first().first { it.name == "我喜欢" } }
            val assigned = _uiState.value.collections.firstOrNull { it.id == fav.id }?.assigned == true
            if (assigned) bookshelfRepo.removeBookFromCollection(book.id, fav.id)
            else bookshelfRepo.addBookToCollection(book.id, fav.id)
        }
    }

    fun showDelete() { _uiState.update { it.copy(showDeleteDialog = true) } }
    fun dismissDelete() { _uiState.update { it.copy(showDeleteDialog = false) } }

    fun confirmDelete(deleteCache: Boolean = false) {
        val book = _uiState.value.book ?: return
        viewModelScope.launch {
            importService.deleteBookFiles(book)
            bookshelfRepo.deleteBook(book.id)
            if (deleteCache) cacheService.clearAppCaches()
        }
    }

    fun showEdit() { _uiState.update { it.copy(showEditDialog = true) } }
    fun dismissEdit() { _uiState.update { it.copy(showEditDialog = false) } }
    fun saveMeta(title: String, author: String?, description: String?) {
        val book = _uiState.value.book ?: return
        viewModelScope.launch {
            bookshelfRepo.updateBookMeta(book.id, title.trim(), author?.trim()?.ifBlank { null }, description?.trim()?.ifBlank { null })
            _uiState.update { it.copy(showEditDialog = false) }
        }
    }

    fun showCollectionSheet() { _uiState.update { it.copy(showCollectionSheet = true) } }
    fun dismissCollectionSheet() { _uiState.update { it.copy(showCollectionSheet = false) } }
    fun toggleCollection(collectionId: String) {
        val book = _uiState.value.book ?: return
        val assigned = _uiState.value.collections.firstOrNull { it.id == collectionId }?.assigned == true
        viewModelScope.launch {
            if (assigned) bookshelfRepo.removeBookFromCollection(book.id, collectionId)
            else bookshelfRepo.addBookToCollection(book.id, collectionId)
        }
    }

    /** 导出本书笔记为 Markdown，通过系统分享面板发出 */
    fun exportNotes() {
        val book = _uiState.value.book ?: return
        val annotations = _uiState.value.annotations
        viewModelScope.launch {
            val md = buildMarkdown(book, annotations)
            val file = java.io.File(context.cacheDir, "${book.title}_${context.getString(R.string.lib_vm_export_note_file_suffix)}.md")
            file.writeText(md, Charsets.UTF_8)
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "text/markdown"
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(
                Intent.createChooser(intent, context.getString(R.string.lib_vm_export_notes_chooser))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        }
    }

    /** 删除单条笔记 */
    fun deleteAnnotation(annotation: Annotation) {
        viewModelScope.launch { annotationRepo.delete(annotation.id) }
    }

    /** 导出单条笔记为 Markdown 并系统分享 */
    fun exportAnnotation(annotation: Annotation) {
        viewModelScope.launch {
            val book = _uiState.value.book
            val md = buildString {
                appendLine("# ${book?.title ?: ""}")
                appendLine()
                appendLine("## ${typeLabel(annotation.type)}")
                if (annotation.chapterTitle.isNotBlank())
                    appendLine(context.getString(R.string.lib_vm_md_chapter, annotation.chapterTitle))
                annotation.progress?.let {
                    appendLine(context.getString(R.string.lib_vm_md_progress, "%.1f%%".format(it * 100)))
                }
                appendLine(
                    context.getString(
                        R.string.lib_vm_md_time,
                        java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", java.util.Locale.getDefault()).format(java.util.Date(annotation.createdAt)),
                    )
                )
                if (annotation.selectedText.isNotBlank()) appendLine("> ${annotation.selectedText}")
                annotation.note?.let { appendLine("\n$it") }
                annotation.translation?.let { appendLine("\n" + context.getString(R.string.lib_vm_md_translation, it)) }
                annotation.rewrittenText?.let { appendLine("\n${it}") }
            }
            val file = java.io.File(context.cacheDir, "NarviveNote.md")
            file.writeText(md, Charsets.UTF_8)
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "text/markdown"
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(
                Intent.createChooser(intent, context.getString(R.string.lib_vm_export_notes_chooser))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        }
    }

    private fun buildMarkdown(book: Book, annotations: List<Annotation>): String {
        val sb = StringBuilder()
        sb.appendLine("# ${book.title}")
        book.author?.let { sb.appendLine(context.getString(R.string.lib_vm_md_author, it)) }
        sb.appendLine()
        annotations.forEach { ann ->
            sb.appendLine("## ${typeLabel(ann.type)}")
            sb.appendLine("> ${ann.selectedText.replace("\n", "\n> ")}")
            ann.note?.let { sb.appendLine("\n$it") }
            ann.translation?.let { sb.appendLine("\n" + context.getString(R.string.lib_vm_md_translation, it)) }
            ann.rewrittenText?.let {
                sb.appendLine("\n" + context.getString(R.string.lib_vm_md_rewrite, ann.rewriteInstruction ?: ""))
                sb.appendLine(it)
            }
            sb.appendLine()
        }
        return sb.toString()
    }

    private fun typeLabel(type: AnnotationType) = when (type) {
        AnnotationType.HIGHLIGHT -> context.getString(R.string.lib_vm_type_highlight)
        AnnotationType.NOTE -> context.getString(R.string.lib_vm_type_note)
        AnnotationType.TRANSLATION -> context.getString(R.string.lib_vm_type_translation)
        AnnotationType.AI_ANSWER -> context.getString(R.string.lib_vm_type_ai_answer)
        AnnotationType.ROLEPLAY -> context.getString(R.string.lib_vm_type_roleplay)
        AnnotationType.REWRITE -> context.getString(R.string.lib_vm_type_rewrite)
    }
}
