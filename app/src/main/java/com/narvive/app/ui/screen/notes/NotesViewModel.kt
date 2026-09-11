package com.narvive.app.ui.screen.notes

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.narvive.app.R
import com.narvive.app.domain.model.Annotation
import com.narvive.app.domain.model.AnnotationType
import com.narvive.app.domain.model.Book
import com.narvive.app.domain.repository.AnnotationRepository
import com.narvive.app.domain.repository.BookshelfRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class NotesUiState(
    val annotations: List<NoteItem> = emptyList(),
    val typeFilter: AnnotationType? = null,
    val bookFilter: String? = null, // null=全部
    val searchQuery: String = "",
    val typeCounts: Map<AnnotationType, Int> = emptyMap(),
    val bookChips: List<BookChip> = emptyList(),
)

data class NoteItem(
    val annotation: Annotation,
    val bookTitle: String,
)

data class BookChip(val id: String, val title: String, val count: Int)

@HiltViewModel
class NotesViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val annotationRepo: AnnotationRepository,
    private val bookshelfRepo: BookshelfRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(NotesUiState())
    val uiState: StateFlow<NotesUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            combine(
                annotationRepo.observeAll(),
                bookshelfRepo.observeAllBooks(),
                _uiState,
            ) { annotations, books, state ->
                val bookMap = books.associateBy { it.id }
                val withTitle = annotations.map { NoteItem(it, bookMap[it.bookId]?.title ?: context.getString(R.string.notes_unknown_book)) }

                val typeCounts = annotations.groupBy { it.type }.mapValues { it.value.size }
                val bookChips = annotations.groupBy { it.bookId }
                    .map { (id, list) -> BookChip(id, bookMap[id]?.title ?: context.getString(R.string.notes_unknown_book), list.size) }
                    .sortedByDescending { it.count }

                val filtered = withTitle
                    .filter { state.typeFilter == null || it.annotation.type == state.typeFilter }
                    .filter { state.bookFilter == null || it.annotation.bookId == state.bookFilter }
                    .filter { state.searchQuery.isBlank() || containsText(it.annotation, state.searchQuery) }
                    .sortedByDescending { it.annotation.createdAt }

                state.copy(annotations = filtered, typeCounts = typeCounts, bookChips = bookChips)
            }.collect { _uiState.value = it }
        }
    }

    private fun containsText(ann: Annotation, q: String): Boolean {
        val text = listOf(ann.selectedText, ann.note, ann.translation, ann.rewrittenText).filterNotNull().joinToString(" ")
        return text.contains(q, ignoreCase = true)
    }

    fun setTypeFilter(type: AnnotationType?) { _uiState.update { it.copy(typeFilter = type) } }
    fun setBookFilter(bookId: String?) { _uiState.update { it.copy(bookFilter = bookId) } }
    fun onSearchQueryChange(q: String) { _uiState.update { it.copy(searchQuery = q) } }

    /** 删除单条笔记 */
    fun deleteAnnotation(annotation: Annotation) {
        viewModelScope.launch { annotationRepo.delete(annotation.id) }
    }

    /** 导出单条笔记为 Markdown 并系统分享 */
    fun exportAnnotation(annotation: Annotation) {
        viewModelScope.launch {
            val bookTitle = _uiState.value.annotations.firstOrNull { it.annotation.id == annotation.id }?.bookTitle ?: ""
            val md = buildString {
                appendLine("# $bookTitle")
                appendLine()
                appendLine("## ${label(annotation.type)}")
                if (annotation.chapterTitle.isNotBlank()) appendLine("章：${annotation.chapterTitle}")
                annotation.progress?.let { appendLine("进度：%.1f%%".format(it * 100)) }
                appendLine("时间：${java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", java.util.Locale.getDefault()).format(java.util.Date(annotation.createdAt))}")
                if (annotation.selectedText.isNotBlank()) appendLine("> ${annotation.selectedText}")
                annotation.note?.let { appendLine("\n$it") }
                annotation.translation?.let { appendLine("\n译文：$it") }
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
            context.startActivity(Intent.createChooser(intent, context.getString(R.string.notes_export_chooser)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
    }

    fun exportMarkdown() {
        val items = _uiState.value.annotations
        if (items.isEmpty()) return
        viewModelScope.launch {
            val md = buildString {
                appendLine("# 笔记导出")
                appendLine()
                items.groupBy { it.bookTitle }.forEach { (bookTitle, list) ->
                    appendLine("## $bookTitle")
                    list.forEach { item ->
                        appendLine("- **${label(item.annotation.type)}**：${item.annotation.note ?: item.annotation.translation ?: item.annotation.rewrittenText ?: ""}")
                        if (item.annotation.selectedText.isNotBlank()) appendLine("  > ${item.annotation.selectedText.take(200)}")
                    }
                    appendLine()
                }
            }
            val file = java.io.File(context.cacheDir, "NarviveNotes.md")
            file.writeText(md, Charsets.UTF_8)
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "text/markdown"
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(Intent.createChooser(intent, context.getString(R.string.notes_export_chooser)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
    }

    private fun label(type: AnnotationType) = when (type) {
        AnnotationType.HIGHLIGHT -> "高亮"
        AnnotationType.NOTE -> "笔记"
        AnnotationType.TRANSLATION -> "翻译"
        AnnotationType.AI_ANSWER -> "AI 保存"
        AnnotationType.ROLEPLAY -> "角色对话"
        AnnotationType.REWRITE -> "改写"
    }
}
