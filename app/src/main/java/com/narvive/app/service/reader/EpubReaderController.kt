package com.narvive.app.service.reader

import com.narvive.app.domain.model.Annotation
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import org.json.JSONObject
import org.readium.r2.navigator.Decoration
import org.readium.r2.navigator.Selection as ReadiumSelection
import org.readium.r2.shared.ExperimentalReadiumApi
import org.readium.r2.shared.publication.Link
import org.readium.r2.shared.publication.Locator
import org.readium.r2.shared.publication.Publication

/**
 * EPUB 阅读器 — 封装 Readium NavigatorFragment。
 *
 * Navigator/Publication 由 EpubViewer（UI 层）持有并注入：
 * - attachNavigator：翻页/跳转/搜索落地
 * - attachPublication：目录提取、全文字符统计、自研书内搜索、高亮装饰
 */
@OptIn(ExperimentalReadiumApi::class)
class EpubReaderController : ReaderController {

    companion object {
        /** Readium 装饰分组名（高亮） */
        const val HIGHLIGHT_DECORATION_GROUP = "annotations"
        /** Readium 装饰分组名（翻译红下划线） */
        const val TRANSLATION_DECORATION_GROUP = "translations"
    }

    private val _progress = MutableStateFlow(0f)
    override val progress: StateFlow<Float> = _progress.asStateFlow()

    private val _currentChapter = MutableStateFlow("")
    override val currentChapter: StateFlow<String> = _currentChapter.asStateFlow()

    private val _currentLocator = MutableStateFlow<String?>(null)
    override val currentLocator: StateFlow<String?> = _currentLocator.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    override val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    /** 全书文本字符数（异步统计完成前为 0），搜索前懒加载文本缓存用 */
    private val _totalChars = MutableStateFlow(0)
    val totalChars: StateFlow<Int> = _totalChars.asStateFlow()

    /** total pages (known + estimated) */
    private val _globalTotalPages = MutableStateFlow(0)
    val globalTotalPages: StateFlow<Int> = _globalTotalPages.asStateFlow()

    /** current global page (1-based) */
    private val _globalCurrentPage = MutableStateFlow(1)
    val globalCurrentPage: StateFlow<Int> = _globalCurrentPage.asStateFlow()

    /** 目录条目（G1.3：attachPublication 时从 tableOfContents 提取） */
    private val _tocItems = MutableStateFlow<List<TocItem>>(emptyList())
    val tocItems: StateFlow<List<TocItem>> = _tocItems.asStateFlow()

    /** 待应用的高亮标注（VM 侧变更后 EpubViewer 收集并调用 applyHighlightDecorations） */
    private val _pendingHighlights = MutableStateFlow<List<Annotation>>(emptyList())
    val pendingHighlights: StateFlow<List<Annotation>> = _pendingHighlights.asStateFlow()

    /** 待应用的翻译标注（红下划线；VM 侧变更后 EpubViewer 收集并调用 applyTranslationDecorations） */
    private val _pendingTranslations = MutableStateFlow<List<Annotation>>(emptyList())
    val pendingTranslations: StateFlow<List<Annotation>> = _pendingTranslations.asStateFlow()

    /** 当前章节正在显示的 WebView，由 EpubViewer 在重绑时更新；上下滚动/音量键直接读它，避免视图树轮询 */
    var currentWebView: android.webkit.WebView? = null

    private var currentFilePath: String? = null
    private var currentSettings = ReadSettings()
    private var currentPageIndex = 0
    private var currentHref: String? = null

    /** UI 层注入的 Readium 对象（Any 避免 controller 依赖 fragment 类，强转在使用点） */
    private var navigator: Any? = null
    private var publication: Publication? = null

    /** 章节文本缓存：href -> 纯文本（搜索懒加载） */
    private val chapterTextCache = mutableMapOf<String, String>()

    /** spine item position index for character-weighted seeking */
    data class SpineItemInfo(
        val href: String,
        val title: String?,
        val charCount: Int,
        val cumulativeChars: Long,
        var pageCount: Int = 0,  // lazily populated from onPageChanged; 0 = unknown
    )

    private val spineItems = mutableListOf<SpineItemInfo>()

    fun attachNavigator(nav: Any) {
        navigator = nav
        // 首次挂载/重建后短暂抑制滚底自动续章，避免恢复位置（章尾/短章）被误推进下一章
        lastUserJumpAt = System.currentTimeMillis()
    }

    /**
     * 用户主动跳转时间戳（进度条/章节按钮/目录/笔记回跳）。
     * EpubViewer.maybeContinueScroll 在跳转后 1.5s 内不自动续章，避免章尾落点被误推到下一章。
     */
    var lastUserJumpAt = 0L
        private set

    /** 最近一次用户跳转的目标 href（供跳转遮罩判断目标页是否已就位） */
    var lastJumpTargetHref: String? = null
        private set

    /** 用户主动跳转入口：标记时间戳后走通用 goToLocator */
    suspend fun goToLocatorUser(locatorJson: String) {
        lastUserJumpAt = System.currentTimeMillis()
        lastJumpTargetHref = runCatching {
            JSONObject(locatorJson).optString("href").takeIf { it.isNotBlank() }?.substringBefore('#')
        }.getOrNull()
        goToLocator(locatorJson)
    }
    fun attachPublication(pub: Publication) {
        if (publication === pub) return
        publication = pub
        chapterTextCache.clear()
        spineItems.clear()
        _totalChars.value = 0
        extractToc(pub)
    }

    // ---------- 目录提取（G1.3） ----------

    private fun extractToc(pub: Publication) {
        val items = mutableListOf<TocItem>()
        fun walk(links: List<Link>, depth: Int) {
            links.forEach { link ->
                val title = link.title?.trim().orEmpty()
                if (title.isNotEmpty()) {
                    items.add(TocItem(
                        title = title,
                        locatorJson = JSONObject().apply {
                            put("href", link.href.toString())
                            put("type", "application/xhtml+xml")
                            link.title?.let { put("title", it) }
                            put("locations", JSONObject().apply { put("progression", 0.0) })
                        }.toString(),
                        depth = depth,
                    ))
                }
                if (link.children.isNotEmpty()) walk(link.children, depth + 1)
            }
        }
        walk(pub.tableOfContents, 0)
        _tocItems.value = items
    }

    /** 当前章 href（EPUB 选区定位用） */
    fun currentHref(): String? = runCatching {
        _currentLocator.value?.let { JSONObject(it).optString("href").takeIf { h -> h.isNotBlank() } }
    }.getOrNull()

    // ---------- 高亮装饰（P2：Readium DecorableNavigator 官方装饰层） ----------

    /** 重新应用全书标注的高亮渲染（VM 侧 annotations 变化时触发） */
    fun requestHighlightRefresh(annotations: List<Annotation>) {
        // 高亮渲染覆盖「高亮」与「带颜色的笔记」（需求1：加笔记自动附高亮）
        val highlights = annotations.filter { it.color != null && (it.type.name == "HIGHLIGHT" || it.type.name == "NOTE") }
        _pendingHighlights.value = highlights
    }

    /** 把全书高亮提交为 Readium 装饰（CFI 锚定；翻页/章切换/相邻页由 navigator 自动重锚注入） */
    suspend fun applyHighlightDecorations(annotations: List<Annotation>) {
        val nav = navigatorFragment ?: return
        val decorations = annotations
            .filter { it.color != null && (it.type.name == "HIGHLIGHT" || it.type.name == "NOTE") }
            .mapNotNull { a ->
                val loc = runCatching { Locator.fromJSON(JSONObject(a.locatorJson)) }.getOrNull()
                    ?: return@mapNotNull null
                Decoration(
                    id = a.id,
                    locator = loc,
                    style = Decoration.Style.Highlight(tint = a.color!!.toInt()),
                )
            }
        nav.applyDecorations(decorations, HIGHLIGHT_DECORATION_GROUP)
    }

    /** 重新应用翻译标注（红下划线） */
    fun requestTranslationRefresh(annotations: List<Annotation>) {
        _pendingTranslations.value = annotations.filter { it.type.name == "TRANSLATION" && !it.translation.isNullOrBlank() }
    }

    /** 把翻译标注提交为 Readium 红下划线装饰 */
    suspend fun applyTranslationDecorations(annotations: List<Annotation>) {
        val nav = navigatorFragment ?: return
        val decorations = annotations
            .filter { it.type.name == "TRANSLATION" && !it.translation.isNullOrBlank() }
            .mapNotNull { a ->
                val loc = runCatching { Locator.fromJSON(JSONObject(a.locatorJson)) }.getOrNull()
                    ?: return@mapNotNull null
                Decoration(
                    id = a.id,
                    locator = loc,
                    style = Decoration.Style.Underline(tint = 0xFFDC2626.toInt()),
                )
            }
        nav.applyDecorations(decorations, TRANSLATION_DECORATION_GROUP)
    }

    /** 读取当前 WebView 原生选区（CFI Locator + 视口坐标矩形）；无选区返回 null */
    suspend fun currentSelection(): ReadiumSelection? =
        navigatorFragment?.currentSelection()

    /** 清除 WebView 原生选区（无选区时幂等） */
    fun clearSelectionNow() {
        navigatorFragment?.clearSelection()
    }

    /** UI 层分页回调入口：同步进度/章节/locator 到状态流 */
    fun onPageChanged(locator: Locator, pageIndex: Int, totalPages: Int) {
        _currentLocator.value = locator.toJSON().toString()
        // 进度锚定到“文本位置”（字符），与排版/间距设置无关：
        // 修改行距/段距/边距导致重排时百分比不漂移，书签进度才能实时匹配。
        val itemHref = locator.href.toString()
        val itemAtHref = spineItems.firstOrNull { it.href == itemHref }
        val total = _totalChars.value
        val p = if (itemAtHref != null && total > 0) {
            val prog = locator.locations.progression ?: 0.0
            ((itemAtHref.cumulativeChars + prog * itemAtHref.charCount).toFloat() / total).coerceIn(0f, 1f)
        } else {
            locator.locations.totalProgression?.toFloat() ?: (pageIndex.toFloat() / totalPages.coerceAtLeast(1))
        }
        _progress.value = p.coerceIn(0f, 1f)
        // Title: four-layer fallback (with backward spine walk as last resort).
        // 1. Navigator-provided title (rare, only at chapter start pages).
        // 2. Spine-indexed lookup (fast, works once computeTotalChars finishes).
        // 3. Direct TOC lookup by href (works even when spine is empty).
        // 4. Backward spine walk (finds containing chapter when item has no title).
        val title = locator.title
            ?: itemAtHref?.let { item ->
                val idx = spineItems.indexOf(item)
                if (idx >= 0) chapterTitleForSpineItem(idx) else null
            }
            ?: tocTitleForHref(locator.href.toString())
            ?: itemAtHref?.let { item ->
                val idx = spineItems.indexOf(item)
                if (idx >= 0) findContainingChapterTitle(idx) else null
            }
        if (title != null) _currentChapter.value = title
        currentPageIndex = pageIndex
        currentHref = locator.href.toString()
        // record per-item page count (lazy accumulation)
        val href = currentHref ?: return
        val item = spineItems.firstOrNull { it.href == href }
        if (item != null && totalPages > 0 && item.pageCount != totalPages) {
            item.pageCount = totalPages
            recomputeGlobalPages()
        }
        // update current global page
        var cumPages = 0
        for (si in spineItems) {
            if (si.href == href) {
                _globalCurrentPage.value = cumPages + pageIndex + 1
                break
            }
            cumPages += if (si.pageCount > 0) si.pageCount else estimatedPages(si)
        }
    }

    override suspend fun open(filePath: String) {
        val file = java.io.File(filePath)
        if (!file.exists() || file.length() == 0L) {
            _isLoading.value = false
            throw java.io.FileNotFoundException("EPUB 文件不存在或为空：$filePath")
        }
        currentFilePath = filePath
        _isLoading.value = true
        _isLoading.value = false
    }

    /** Walk backward through spine items from [spineIndex] to find the nearest
     *  item with a non-null title (the chapter that contains this position). */
    private fun findContainingChapterTitle(spineIndex: Int): String? {
        for (i in spineIndex downTo 0) {
            spineItems[i].title?.let { return it }
        }
        return null
    }

    /** look up TOC title for a spine href (strip fragment before matching) */
    private fun tocTitleForHref(href: String): String? {
        val bareHref = href.substringBefore('#')
        return _tocItems.value.firstOrNull { toc ->
            runCatching {
                org.json.JSONObject(toc.locatorJson).optString("href").substringBefore('#') == bareHref
            }.getOrDefault(false)
        }?.title
    }

    suspend fun computeTotalChars() {
        if (_totalChars.value > 0) return
        val pub = publication ?: return
        withContext(Dispatchers.IO) {
            var cumSum: Long = 0
            val items = mutableListOf<SpineItemInfo>()
            for (link in pub.readingOrder) {
                val chars = extractChapterText(link.href.toString()).length
                items.add(SpineItemInfo(
                    href = link.href.toString(),
                    title = link.title ?: tocTitleForHref(link.href.toString()),
                    charCount = chars,
                    cumulativeChars = cumSum,
                ))
                cumSum += chars
            }
            spineItems.clear()
            spineItems.addAll(items)
            _totalChars.value = cumSum.toInt().coerceAtMost(Int.MAX_VALUE)
            recomputeGlobalPages()
        }
    }

    /** estimate page count for an unvisited spine item from known items char/page ratio */
    private fun estimatedPages(item: SpineItemInfo): Int {
        val known = spineItems.filter { it.pageCount > 0 }
        return if (known.isNotEmpty()) {
            val ratio = known.sumOf { it.charCount }.toFloat() / known.sumOf { it.pageCount }
            (item.charCount / ratio).toInt().coerceAtLeast(1)
        } else {
            (item.charCount / 800).coerceAtLeast(1)  // rough fallback
        }
    }

    /** recompute global total pages after a new pageCount is recorded */
    private fun recomputeGlobalPages() {
        if (spineItems.isEmpty()) return
        val total = spineItems.sumOf { if (it.pageCount > 0) it.pageCount else estimatedPages(it) }
        _globalTotalPages.value = total
    }

    private suspend fun extractChapterText(href: String): String {
        chapterTextCache[href]?.let { return it }
        val pub = publication ?: return ""
        val link = pub.readingOrder.firstOrNull { it.href.toString() == href } ?: return ""
        val text = runCatching {
            val resource = pub.get(link) ?: return@runCatching ""
            val bytes = resource.read().getOrNull() ?: return@runCatching ""
            val html = String(bytes, Charsets.UTF_8)
            html
                .replace(Regex("<script[\\s\\S]*?</script>"), " ")
                .replace(Regex("<style[\\s\\S]*?</style>"), " ")
                .replace(Regex("<[^>]+>"), " ")
                .replace(Regex("&nbsp;"), " ")
                .replace(Regex("&[a-zA-Z#0-9]+;"), " ")
                .replace(Regex("\\s+"), " ")
                .trim()
        }.getOrDefault("")
        chapterTextCache[href] = text
        return text
    }

    private fun buildLocator(href: String, title: String?, progression: Double, totalProgression: Double): Locator? = runCatching {
        val json = JSONObject().apply {
            put("href", href)
            put("type", "application/xhtml+xml")
            title?.let { put("title", it) }
            put("locations", JSONObject().apply {
                put("progression", progression.coerceIn(0.0, 1.0))
                put("totalProgression", totalProgression.coerceIn(0.0, 1.0))
            })
        }
        Locator.fromJSON(json)
    }.getOrNull()

    // ── Fragment 生命周期检查 ──
    private val navigatorFragment: org.readium.r2.navigator.epub.EpubNavigatorFragment? get() =
        (navigator as? org.readium.r2.navigator.epub.EpubNavigatorFragment)?.takeIf { it.isAdded && !it.isDetached }

    /** 由 locator 实时计算全局进度（书签进度匹配用；基于字符位置，与排版无关） */
    override fun progressOfLocator(locatorJson: String): Float? = runCatching {
        val loc = Locator.fromJSON(JSONObject(locatorJson)) ?: return@runCatching null
        val href = loc.href.toString().substringBefore('#')
        val item = spineItems.firstOrNull { it.href.substringBefore('#') == href } ?: return@runCatching null
        val total = _totalChars.value
        if (total <= 0) return@runCatching null
        val prog = loc.locations.progression ?: 0.0
        ((item.cumulativeChars + prog * item.charCount).toFloat() / total).coerceIn(0f, 1f)
    }.getOrNull()

    /** 书签位置匹配：同资源（href）且进度差 ≤ 半页；翻页离开后不再显示书签，重排后仍可命中同一页 */
    override fun isNearBookmark(currentLocator: String?, bookmarkLocator: String?): Boolean {
        if (currentLocator.isNullOrBlank() || bookmarkLocator.isNullOrBlank()) return false
        return runCatching {
            val cur = Locator.fromJSON(JSONObject(currentLocator)) ?: return@runCatching false
            val bm = Locator.fromJSON(JSONObject(bookmarkLocator)) ?: return@runCatching false
            val curHref = cur.href.toString().substringBefore('#')
            if (curHref.isBlank() || curHref != bm.href.toString().substringBefore('#')) return@runCatching false
            val curProg = cur.locations.progression ?: return@runCatching false
            val bmProg = bm.locations.progression ?: return@runCatching false
            val item = spineItems.firstOrNull { it.href == curHref }
            val pageWidth = if (item != null && item.pageCount > 0) 1.0 / item.pageCount else 0.01
            kotlin.math.abs(curProg - bmProg) <= pageWidth / 2
        }.getOrDefault(false)
    }

    /** 进度 → 所属章节标题（拖动进度条提示用；按 spine 字符区间匹配，标题回退到目录同 href） */
    override fun chapterTitleAtProgress(progress: Float): String? {
        val items = spineItems
        if (items.isEmpty()) return null
        val total = _totalChars.value
        if (total <= 0) return null
        val charPos = (progress.coerceIn(0f, 1f) * total).toLong()
        val item = items.firstOrNull {
            charPos >= it.cumulativeChars && charPos < it.cumulativeChars + it.charCount
        } ?: items.lastOrNull() ?: return null
        // Return the item's own title if it has one; otherwise walk backward
        // through spine items to find the containing chapter title.
        item.title?.let { return it }
        val idx = items.indexOf(item)
        return findContainingChapterTitle(idx)
    }

    override fun currentSpineIndex(): Int {
        val href = currentHref ?: return -1
        return spineItems.indexOfFirst { it.href == href }
    }

    override fun spineItemCount(): Int = spineItems.size

    fun hrefForSpineItem(spineIndex: Int): String? =
        spineItems.getOrNull(spineIndex)?.href

    override fun locatorForSpineItem(spineIndex: Int): String? {
        val item = spineItems.getOrNull(spineIndex) ?: return null
        val total = _totalChars.value
        val tp = if (total > 0) item.cumulativeChars.toDouble() / total else 0.0
        return buildLocator(item.href, item.title, 0.0, tp)?.toJSON()?.toString()
    }

    /** 指定 spine 末尾 locator，供上下滚动模式在章首上拉回退到上一章末尾 */
    fun locatorForSpineItemEnd(spineIndex: Int): String? {
        val item = spineItems.getOrNull(spineIndex) ?: return null
        val total = _totalChars.value
        val endChars = item.cumulativeChars + item.charCount
        val tp = if (total > 0) endChars.toDouble() / total else 0.0
        return buildLocator(item.href, item.title, 1.0, tp)?.toJSON()?.toString()
    }

    override fun chapterTitleForSpineItem(spineIndex: Int): String? =
        spineItems.getOrNull(spineIndex)?.title

    override fun chapterTitleAtChapterStart(progress: Float): String? {
        val items = spineItems
        if (items.isEmpty()) return null
        val total = _totalChars.value
        if (total <= 0) return null
        val charPos = (progress.coerceIn(0f, 1f) * total).toLong()
        val item = items.firstOrNull {
            charPos >= it.cumulativeChars && charPos < it.cumulativeChars + it.charCount
        } ?: return null
        val offsetInChapter = charPos - item.cumulativeChars
        // Only return title within the first page of the chapter (title page).
        // Use known pageCount when available; otherwise estimate ~800 chars/page.
        val charsPerPage = if (item.pageCount > 0) {
            (item.charCount / item.pageCount).coerceAtLeast(1)
        } else {
            800
        }
        if (offsetInChapter in 0 until charsPerPage.coerceAtMost(item.charCount)) {
            return item.title
        }
        return null
    }

    override suspend fun goToLocator(locatorJson: String) {
        val nav = navigatorFragment ?: return
        runCatching {
            val locator = Locator.fromJSON(JSONObject(locatorJson)) ?: return@runCatching
            nav.go(locator, animated = false)
        }
    }

    suspend fun goToProgression(targetProgress: Float) {
        lastUserJumpAt = System.currentTimeMillis()
        val nav = navigatorFragment ?: return
        // 等待 spineItems 就绪（首次打开/重建时 computeTotalChars 仍在 IO 进行），
        // 避免回退到按 readingOrder 等分的线性估计导致落点偏差
        var waited = 0
        while (_totalChars.value <= 0 && waited < 30) {
            delay(100)
            waited++
        }
        val totalChars = _totalChars.value
        val items = spineItems.toList()
        // character-index weighted mapping; fallback to equal spine-item split
        if (totalChars > 0 && items.isNotEmpty()) {
            val p = targetProgress.coerceIn(0f, 1f)
            val targetChar = (p * totalChars).toLong().coerceIn(0, (totalChars - 1).coerceAtLeast(0).toLong())
            val idx = items.indexOfLast { it.cumulativeChars <= targetChar }.coerceAtLeast(0)
            val item = items[idx]
            lastJumpTargetHref = item.href.substringBefore('#')
            val charOffsetInItem = (targetChar - item.cumulativeChars).toInt().coerceIn(0, item.charCount.coerceAtLeast(1) - 1)
            val frac = if (item.charCount > 0) charOffsetInItem.toDouble() / item.charCount else 0.0
            val locator = buildLocator(item.href, item.title, frac.coerceIn(0.0, 1.0), p.toDouble()) ?: return
            runCatching { nav.go(locator, animated = false) }
        } else {
            val pub = publication ?: return
            val order = pub.readingOrder
            if (order.isEmpty()) return
            val p = targetProgress.coerceIn(0f, 1f)
            val idx = (p * order.size).toInt().coerceIn(0, order.size - 1)
            lastJumpTargetHref = order[idx].href.toString().substringBefore('#')
            val frac = (p * order.size - idx).toDouble().coerceIn(0.0, 1.0)
            val locator = buildLocator(order[idx].href.toString(), order[idx].title, frac, p.toDouble()) ?: return
            runCatching { nav.go(locator, animated = false) }
        }
    }

    override suspend fun previousPage() {
        val nav = navigatorFragment ?: return
        // slide 模式沿用 Readium 自带翻页动画；none/updown 均瞬时切换，
        // 避免上下滚动模式万一走到该路径时出现左右滑动动画
        val animated = currentSettings.pageFlipAnimation == "slide"
        runCatching { nav.goBackward(animated = animated) }
    }

    override suspend fun nextPage() {
        val nav = navigatorFragment ?: return
        val animated = currentSettings.pageFlipAnimation == "slide"
        runCatching { nav.goForward(animated = animated) }
    }

    /** 自动翻页下一页：无论手动翻页动画设置为何，均瞬时无动画翻页 */
    suspend fun nextPageInstant() {
        val nav = navigatorFragment ?: return
        runCatching { nav.goForward(animated = false) }
    }

    override suspend fun search(query: String, onResult: (List<SearchResult>) -> Unit) {
        if (query.isBlank()) { onResult(emptyList()); return }
        val pub = publication
        if (pub == null) { onResult(emptyList()); return }
        val results = mutableListOf<SearchResult>()
        withContext(Dispatchers.IO) {
            val order = pub.readingOrder
            for ((i, link) in order.withIndex()) {
                if (results.size >= 100) break
                val text = extractChapterText(link.href.toString())
                var index = text.indexOf(query, ignoreCase = true)
                while (index >= 0 && results.size < 100) {
                    val start = (index - 30).coerceAtLeast(0)
                    val end = (index + query.length + 30).coerceAtMost(text.length)
                    val progression = if (text.isEmpty()) 0.0 else index.toDouble() / text.length
                    val locator = buildLocator(
                        link.href.toString(), link.title, progression,
                        (i + progression) / order.size,
                    ) ?: continue
                    results.add(SearchResult(
                        locatorJson = locator.toJSON().toString(),
                        excerpt = text.substring(start, end),
                        chapterTitle = link.title,
                        progressPercent = (spineItems.getOrNull(i)?.let { (it.cumulativeChars + index).toFloat() / _totalChars.value.coerceAtLeast(1) } ?: (i.toFloat() / order.size)),
                    ))
                    index = text.indexOf(query, index + 1, ignoreCase = true)
                }
            }
        }
        onResult(results)
    }

    override suspend fun currentChapterText(): String? {
        val locatorJson = _currentLocator.value ?: return null
        val href = runCatching { JSONObject(locatorJson).optString("href") }.getOrNull()
            ?.takeIf { it.isNotBlank() } ?: return null
        return extractChapterText(href).takeIf { it.isNotBlank() }
    }

    override suspend fun applySettings(settings: ReadSettings) {
        currentSettings = settings
    }

    fun submitPreferences(prefs: org.readium.r2.navigator.epub.EpubPreferences) {
        navigatorFragment?.submitPreferences(prefs)
    }

    override suspend fun close() {
        navigator = null
        publication = null
        chapterTextCache.clear()
        spineItems.clear()
        _globalTotalPages.value = 0
        _globalCurrentPage.value = 1
        currentWebView = null
    }
}
