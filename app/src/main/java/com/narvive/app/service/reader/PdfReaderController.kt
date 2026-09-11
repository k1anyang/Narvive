package com.narvive.app.service.reader

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * PDF 阅读器 — 封装 androidx.pdf PdfViewer。
 * Phase 5 MVP：骨架编译通过，真机联调在 Phase 5.5 完成。
 */
class PdfReaderController : ReaderController {

    private val _progress = MutableStateFlow(0f)
    override val progress: StateFlow<Float> = _progress.asStateFlow()

    private val _currentChapter = MutableStateFlow("")
    override val currentChapter: StateFlow<String> = _currentChapter.asStateFlow()

    private val _currentLocator = MutableStateFlow<String?>(null)
    override val currentLocator: StateFlow<String?> = _currentLocator.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    override val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private var currentSettings = ReadSettings()
    private var totalPages = 1
    private var currentPage = 0

    override suspend fun open(filePath: String) {
        _isLoading.value = true
        // TODO Phase 5.5: load PDF via androidx.pdf, count pages
        totalPages = 100
        _isLoading.value = false
    }

    override suspend fun goToLocator(locatorJson: String) {
        currentPage = try {
            org.json.JSONObject(locatorJson).optInt("page", 0)
        } catch (_: Exception) { 0 }
        _progress.value = currentPage.toFloat() / totalPages
    }

    override suspend fun previousPage() {
        currentPage = (currentPage - 1).coerceAtLeast(0)
        _progress.value = currentPage.toFloat() / totalPages
    }

    override suspend fun nextPage() {
        currentPage = (currentPage + 1).coerceAtMost(totalPages)
        _progress.value = currentPage.toFloat() / totalPages
    }

    override suspend fun search(query: String, onResult: (List<SearchResult>) -> Unit) {
        onResult(emptyList())
    }

    override suspend fun applySettings(settings: ReadSettings) {
        currentSettings = settings
    }

    override suspend fun currentChapterText(): String? = null // PDF 骨架阶段无章文本

    override suspend fun close() {}
}
