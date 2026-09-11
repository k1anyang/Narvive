package com.narvive.app.service.reader

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import java.nio.charset.Charset

/**
 * TXT 阅读器 — 自研，按正则识别章节标题切章，Compose 滚动模式渲染（D3 决策，废弃估算分页）。
 * 进度按字符偏移精确计算：progress = (章节起始偏移 + 章内字符偏移) / 全文长度。
 * Locator 格式：{"chapter":i,"offset":n}（章内字符偏移）；
 * 兼容旧格式 {"chapter":i,"page":p}（page 忽略，落章首）与搜索格式 {"offset":n}（全文偏移）。
 */
class TxtReaderController : ReaderController {

    companion object {
        /** 每块字符数上限：LazyColumn 内单个 Text 的 AnnotatedString 超过此值有 OOM 风险 */
        const val CHUNK_SIZE = 2000
    }

    data class Chapter(val title: String, val startOffset: Int, val endOffset: Int) {
        val length get() = endOffset - startOffset
    }

    /** 扁平分块信息 */
    data class ChunkInfo(
        val chapterIndex: Int,
        val chunkIndexInChapter: Int,
        val startOffset: Int,  // 章内字符偏移（含）
        val endOffset: Int,    // 章内字符偏移（不含）
    )

    // ── 扁平分块索引 ──

    /** 全书分块总数 */
    fun totalChunks(): Int {
        var sum = 0
        for (ch in _chapters.value) sum += (ch.length + CHUNK_SIZE - 1) / CHUNK_SIZE
        return sum.coerceAtLeast(1)
    }

    /** 扁平索引 → 分块信息 */
    fun chunkAt(flatIndex: Int): ChunkInfo {
        var remaining = flatIndex.coerceAtLeast(0)
        for ((ci, ch) in _chapters.value.withIndex()) {
            val cnt = (ch.length + CHUNK_SIZE - 1) / CHUNK_SIZE
            if (remaining < cnt) {
                return ChunkInfo(
                    chapterIndex = ci,
                    chunkIndexInChapter = remaining,
                    startOffset = remaining * CHUNK_SIZE,
                    endOffset = minOf((remaining + 1) * CHUNK_SIZE, ch.length),
                )
            }
            remaining -= cnt
        }
        return ChunkInfo(0, 0, 0, 0)
    }

    /** 章 + 章内偏移 → 扁平分块索引 */
    fun chunkIndexOf(chapterIndex: Int, offsetInChapter: Int): Int {
        var base = 0
        val chList = _chapters.value
        for (i in 0 until chapterIndex.coerceIn(0, chList.lastIndex.coerceAtLeast(0))) {
            val ch = chList.getOrNull(i) ?: continue
            base += (ch.length + CHUNK_SIZE - 1) / CHUNK_SIZE
        }
        return base + offsetInChapter.coerceAtLeast(0) / CHUNK_SIZE
    }

    private val _progress = MutableStateFlow(0f)
    override val progress: StateFlow<Float> = _progress.asStateFlow()

    private val _currentChapter = MutableStateFlow("")
    override val currentChapter: StateFlow<String> = _currentChapter.asStateFlow()

    private val _currentLocator = MutableStateFlow<String?>(null)
    override val currentLocator: StateFlow<String?> = _currentLocator.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    override val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    /** 章节列表（StateFlow，保证解析完成后触发 UI 重组） */
    private val _chapters = MutableStateFlow<List<Chapter>>(emptyList())
    val chaptersFlow: StateFlow<List<Chapter>> = _chapters.asStateFlow()
    val chapters: List<Chapter> get() = _chapters.value

    /** 当前章节索引（UI 据此同步，目录跳转/滚动上报共用同一状态源） */
    private val _currentChapterIndex = MutableStateFlow(0)
    val currentChapterIndex: StateFlow<Int> = _currentChapterIndex.asStateFlow()

    /** 翻页/滚动次数（会话统计用） */
    var pageTurns = 0
        private set

    val fullText: String get() = fullTextInternal
    private var fullTextInternal: String = ""
    private var currentSettings = ReadSettings()

    private var lastPositionKey = -1

    /** 滚动上报折算结果（纯计算，不写状态；供 TxtViewer 在写 currentLocator 前预登记去重键） */
    data class ScrollPosition(
        val chapterIndex: Int,
        val offsetInChapter: Int,
        val locator: String,
    )

    /** 当前可见分块索引 + 块内像素偏移 → 章内字符偏移（纯计算，不修改任何状态） */
    fun computeScrollPosition(chunkIndex: Int, chunkOffsetPx: Int, chunkSizePx: Int): ScrollPosition? {
        if (_chapters.value.isEmpty() || chunkSizePx <= 0) return null
        val chunk = chunkAt(chunkIndex)
        val chapter = _chapters.value.getOrNull(chunk.chapterIndex) ?: return null
        val frac = (chunkOffsetPx.toFloat() / chunkSizePx).coerceIn(0f, 1f)
        val chunkLen = chunk.endOffset - chunk.startOffset
        val offset = chunk.startOffset + (chunkLen * frac).toInt()
        val safeOffset = offset.coerceIn(0, chapter.length.coerceAtLeast(0))
        return ScrollPosition(chunk.chapterIndex, safeOffset, locatorFor(chunk.chapterIndex, safeOffset))
    }

    /** 滚动模式下由 TxtViewer 上报：当前可见分块索引 + 块内像素偏移 → 折算章内字符偏移 */
    fun updateScrollPosition(chunkIndex: Int, chunkOffsetPx: Int, chunkSizePx: Int) {
        val sp = computeScrollPosition(chunkIndex, chunkOffsetPx, chunkSizePx) ?: return
        val chapter = _chapters.value.getOrNull(sp.chapterIndex) ?: return
        _currentChapterIndex.value = sp.chapterIndex
        _currentChapter.value = chapter.title
        val total = fullTextInternal.length.coerceAtLeast(1)
        _progress.value = ((chapter.startOffset + sp.offsetInChapter).toFloat() / total).coerceIn(0f, 1f)
        _currentLocator.value = sp.locator
        val key = sp.chapterIndex * 10_000_000 + sp.offsetInChapter / 2000
        if (key != lastPositionKey) { lastPositionKey = key; pageTurns++ }
    }

    fun locatorFor(chapterIndex: Int, offsetInChapter: Int): String =
        """{"chapter":$chapterIndex,"offset":${offsetInChapter.coerceAtLeast(0)}}"""

    /** 由 locator 实时计算全局进度（书签进度匹配用；TXT 基于字符偏移，与排版无关，天然稳定） */
    override fun progressOfLocator(locatorJson: String): Float? = runCatching {
        val obj = org.json.JSONObject(locatorJson)
        val idx = obj.optInt("chapter", 0)
        val offset = obj.optInt("offset", obj.optInt("start", 0))
        positionToProgress(idx, offset)
    }.getOrNull()

    /** 书签位置匹配：同章且同块（CHUNK_SIZE ≈ 一屏），滚动离开该块后不再显示书签 */
    override fun isNearBookmark(currentLocator: String?, bookmarkLocator: String?): Boolean {
        if (currentLocator.isNullOrBlank() || bookmarkLocator.isNullOrBlank()) return false
        return runCatching {
            val cur = org.json.JSONObject(currentLocator)
            val bm = org.json.JSONObject(bookmarkLocator)
            val curChapter = cur.optInt("chapter", -1)
            val bmChapter = bm.optInt("chapter", -1)
            if (curChapter != bmChapter || curChapter < 0) return@runCatching false
            val curOff = cur.optInt("offset", cur.optInt("start", 0)).coerceAtLeast(0)
            val bmOff = bm.optInt("offset", bm.optInt("start", 0)).coerceAtLeast(0)
            curOff / CHUNK_SIZE == bmOff / CHUNK_SIZE
        }.getOrDefault(false)
    }

    /** 进度 → 所属章节标题（拖动进度条提示用；按真实章节字符区间匹配，而非等分目录） */
    override fun chapterTitleAtProgress(progress: Float): String? {
        val chs = _chapters.value
        if (chs.isEmpty()) return null
        val (idx, _) = progressToPosition(progress.coerceIn(0f, 1f))
        return chs.getOrNull(idx)?.title
    }

    override fun currentSpineIndex(): Int = _currentChapterIndex.value

    override fun spineItemCount(): Int = _chapters.value.size

    override fun locatorForSpineItem(spineIndex: Int): String? {
        if (spineIndex < 0 || spineIndex >= _chapters.value.size) return null
        return locatorFor(spineIndex, 0)
    }

    override fun chapterTitleForSpineItem(spineIndex: Int): String? =
        _chapters.value.getOrNull(spineIndex)?.title

    override fun chapterTitleAtChapterStart(progress: Float): String? {
        val chs = _chapters.value
        if (chs.isEmpty()) return null
        val total = fullTextInternal.length.coerceAtLeast(1)
        val charPos = (progress.coerceIn(0f, 1f) * total).toInt()
        val idx = chs.indexOfLast { charPos >= it.startOffset }.coerceAtLeast(0)
        val chapter = chs.getOrNull(idx) ?: return null
        val offsetInChapter = charPos - chapter.startOffset
        // TXT: first CHUNK_SIZE characters ~= title page area
        if (offsetInChapter in 0 until CHUNK_SIZE.coerceAtMost(chapter.length)) {
            return chapter.title
        }
        return null
    }

    /** 全书剩余字符数（HUD"剩余约 x 分钟"用） */
    fun remainingChars(): Int {
        val total = fullTextInternal.length
        val read = (_progress.value * total).toInt()
        return (total - read).coerceAtLeast(0)
    }

    /** 正则匹配章节标题：第X章/节/卷/部/篇 / Chapter X / 楔子/尾声/番外（允许行首空白） */
    private val chapterRegex = Regex(
        "^[\\s　]*(第[零一二三四五六七八九十百千万0-9]+[章节卷部篇回集]|Chapter\\s+\\d+|楔子|尾声|番外|序章|终章).*$",
        setOf(RegexOption.MULTILINE, RegexOption.IGNORE_CASE),
    )

    override suspend fun open(filePath: String) {
        _isLoading.value = true
        try {
            val file = File(filePath)
            if (!file.exists() || file.length() == 0L) {
                fullTextInternal = ""
                _chapters.value = emptyList()
                _currentChapter.value = ""
                _isLoading.value = false
                throw java.io.FileNotFoundException("TXT 文件不存在或为空：$filePath")
            }
            fullTextInternal = readWithDetectedEncoding(file)
            parseChapters()
        } catch (e: Exception) {
            // 上抛给 ReaderViewModel.loadBook 的 try/catch，统一设置 error 态
            fullTextInternal = ""
            _chapters.value = emptyList()
            _currentChapter.value = ""
            _isLoading.value = false
            throw e
        } finally {
            _isLoading.value = false
        }
    }

    private fun readWithDetectedEncoding(file: File): String {
        val bytes = file.readBytes()
        if (bytes.isEmpty()) return ""

        // BOM detection: UTF-8 = EF BB BF, UTF-16LE = FF FE, UTF-16BE = FE FF
        when {
            bytes.size >= 3 && bytes[0] == 0xEF.toByte() && bytes[1] == 0xBB.toByte() && bytes[2] == 0xBF.toByte() ->
                return String(bytes, 3, bytes.size - 3, Charsets.UTF_8)
            bytes.size >= 2 && bytes[0] == 0xFF.toByte() && bytes[1] == 0xFE.toByte() ->
                return String(bytes, 2, bytes.size - 2, Charset.forName("UTF-16LE"))
            bytes.size >= 2 && bytes[0] == 0xFE.toByte() && bytes[1] == 0xFF.toByte() ->
                return String(bytes, 2, bytes.size - 2, Charset.forName("UTF-16BE"))
        }

        // Try UTF-8 first, detect garbled via replacement character ratio
        val utf8 = String(bytes, Charsets.UTF_8)
        val replacementCount = utf8.count { it == '�' }
        val ratio = replacementCount.toFloat() / utf8.length.coerceAtLeast(1)
        if (ratio < 0.01f) return utf8

        // UTF-8 failed, try GBK (most common for Chinese TXT files)
        try {
            val gbk = String(bytes, Charset.forName("GBK"))
            val gbkRatio = gbk.count { it == '�' }.toFloat() / gbk.length.coerceAtLeast(1)
            if (gbkRatio < ratio) return gbk
        } catch (_: Exception) {}

        // Try GB18030 as last resort (superset of GBK)
        try {
            return String(bytes, Charset.forName("GB18030"))
        } catch (_: Exception) {}

        return utf8
    }

    private fun parseChapters() {
        val result = mutableListOf<Chapter>()
        val matches = chapterRegex.findAll(fullTextInternal).toList()
        if (matches.isEmpty()) {
            // No chapter structure — treat as single chapter
            result.add(Chapter("全文", 0, fullTextInternal.length))
        } else {
            // 首个标题前的内容（封面/简介等）归入「卷首」
            val firstStart = matches.first().range.first
            if (firstStart > 0) result.add(Chapter("卷首", 0, firstStart))
            for (i in matches.indices) {
                val start = matches[i].range.first
                val end = if (i + 1 < matches.size) matches[i + 1].range.first else fullTextInternal.length
                result.add(Chapter(matches[i].value.trim(), start, end))
            }
        }
        _chapters.value = result
        if (result.isNotEmpty()) {
            _currentChapter.value = result[0].title
        }
    }

    /** 章节索引 + 章内偏移 → 所属进度；供 UI 恢复/拖动进度条定位 */
    fun positionToProgress(chapterIndex: Int, offsetInChapter: Int): Float {
        val chapter = _chapters.value.getOrNull(chapterIndex) ?: return 0f
        val total = fullTextInternal.length.coerceAtLeast(1)
        return ((chapter.startOffset + offsetInChapter.coerceIn(0, chapter.length)).toFloat() / total).coerceIn(0f, 1f)
    }

    /** 全书进度 → (章节索引, 章内偏移)，进度条拖动跳转用 */
    fun progressToPosition(targetProgress: Float): Pair<Int, Int> {
        if (_chapters.value.isEmpty()) return 0 to 0
        val total = fullTextInternal.length.coerceAtLeast(1)
        val charPos = (targetProgress.coerceIn(0f, 1f) * total).toInt()
        val idx = _chapters.value.indexOfLast { charPos >= it.startOffset }.coerceAtLeast(0)
        val chapter = _chapters.value[idx]
        return idx to (charPos - chapter.startOffset).coerceIn(0, chapter.length)
    }

    override suspend fun goToLocator(locatorJson: String) {
        try {
            val obj = org.json.JSONObject(locatorJson)
            when {
                // 搜索结果定位：{"offset":N} → 全文偏移映射到所属章节
                obj.has("offset") && !obj.has("chapter") -> {
                    val offset = obj.optInt("offset", 0).coerceIn(0, fullTextInternal.length.coerceAtLeast(1))
                    val idx = _chapters.value.indexOfLast { offset >= it.startOffset }.coerceAtLeast(0)
                    setPositionInternal(idx, offset - _chapters.value[idx].startOffset)
                }
                else -> {
                    val idx = obj.optInt("chapter", 0).coerceIn(0, _chapters.value.lastIndex.coerceAtLeast(0))
                    // 新格式 offset（章内）；标注跳转 start；旧格式 page 忽略落章首
                    val offset = when {
                        obj.has("offset") -> obj.optInt("offset", 0)
                        obj.has("start") -> obj.optInt("start", 0)
                        else -> 0
                    }
                    setPositionInternal(idx, offset)
                }
            }
        } catch (_: Exception) {}
    }

    private fun setPositionInternal(idx: Int, offsetInChapter: Int) {
        if (_chapters.value.isEmpty()) return
        val safeIdx = idx.coerceIn(0, _chapters.value.lastIndex)
        val chapter = _chapters.value[safeIdx]
        val offset = offsetInChapter.coerceIn(0, chapter.length.coerceAtLeast(0))
        _currentChapterIndex.value = safeIdx
        _currentChapter.value = chapter.title
        val total = fullTextInternal.length.coerceAtLeast(1)
        _progress.value = ((chapter.startOffset + offset).toFloat() / total).coerceIn(0f, 1f)
        _currentLocator.value = locatorFor(safeIdx, offset)
    }

    /** 分页模式（slide/none）由 TxtPagedViewer 翻页后上报位置：直接定位到章+章内偏移 */
    fun setPagedPosition(chapterIndex: Int, offsetInChapter: Int) {
        setPositionInternal(chapterIndex, offsetInChapter)
        val key = chapterIndex * 10_000_000 + offsetInChapter / 2000
        if (key != lastPositionKey) { lastPositionKey = key; pageTurns++ }
    }

    /** 目录跳转：上一章（滚动模式语义） */
    override suspend fun previousPage() {
        val idx = _currentChapterIndex.value
        if (idx > 0) setPositionInternal(idx - 1, 0)
    }

    override suspend fun nextPage() {
        val idx = _currentChapterIndex.value
        if (idx < _chapters.value.lastIndex) setPositionInternal(idx + 1, 0)
    }

    override suspend fun search(query: String, onResult: (List<SearchResult>) -> Unit) {
        if (query.isBlank()) { onResult(emptyList()); return }
        val total = fullTextInternal.length.coerceAtLeast(1)
        val results = mutableListOf<SearchResult>()
        var index = fullTextInternal.indexOf(query, ignoreCase = true)
        while (index >= 0 && results.size < 100) {
            val start = (index - 30).coerceAtLeast(0)
            val end = (index + query.length + 30).coerceAtMost(fullTextInternal.length)
            val chapter = _chapters.value.find { index in it.startOffset until it.endOffset }
            results.add(SearchResult(
                locatorJson = """{"chapter":${_chapters.value.indexOf(chapter).coerceAtLeast(0)},"offset":${index - (chapter?.startOffset ?: 0)}}""",
                excerpt = fullTextInternal.substring(start, end).replace('\n', ' '),
                chapterTitle = chapter?.title,
                progressPercent = index.toFloat() / total,
            ))
            index = fullTextInternal.indexOf(query, index + 1, ignoreCase = true)
        }
        onResult(results)
    }

    /** 当前章纯文本（AI 面板「本章」上下文用） */
    override suspend fun currentChapterText(): String? {
        val text = fullTextInternal
        if (text.isEmpty()) return null
        val ch = _chapters.value.getOrNull(_currentChapterIndex.value) ?: return null
        val from = ch.startOffset.coerceIn(0, text.length)
        val to = ch.endOffset.coerceIn(from, text.length)
        return text.substring(from, to)
    }

    override suspend fun applySettings(settings: ReadSettings) {
        currentSettings = settings
    }

    override suspend fun close() {}
}
