package com.narvive.app.ui.screen.reader

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.ParagraphStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.rememberTextMeasurer
import com.narvive.app.R
import com.narvive.app.domain.model.Annotation
import com.narvive.app.domain.model.AnnotationType
import com.narvive.app.service.reader.ReadSettings
import com.narvive.app.service.reader.TxtReaderController
import com.narvive.app.ui.screen.reader.selection.SelectionTarget
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/** 分页模式下的一屏页：仅存渲染片段 + 预计算命中 span，不持有 chunkRender（测完即释放，省内存）。
 *  [origOffsets]：页内渲染下标 → 章内原文偏移映射（SelectionTarget 命中/几何换算用）。 */
internal data class TxtPage(
    val chapterIndex: Int,
    val chapterTitle: String,
    val rendered: AnnotatedString,
    val isFirstOfChapter: Boolean,
    val origStartInChapter: Int,
    val origEndInChapter: Int,
    val origOffsets: IntArray,
    val translationSpans: List<MarkSpan>,  // 翻译命中（页内渲染坐标）
    val highlightSpans: List<MarkSpan>,    // 高亮命中（页内渲染坐标）
)

/** 页命中条目（分页模式 SelectionTarget 注册表数据源） */
internal class PageHitEntry(
    val page: TxtPage,
    val layout: TextLayoutResult,
    val coords: LayoutCoordinates,
)

/** 非降序数组下界：origOffsets 中第一个 >= v 的下标 */
private fun lowerBound(a: IntArray, v: Int): Int {
    var lo = 0
    var hi = a.size
    while (lo < hi) {
        val mid = (lo + hi) ushr 1
        if (a[mid] < v) lo = mid + 1 else hi = mid
    }
    return lo.coerceIn(0, a.lastIndex)
}

/** 分块窗口坐标：章内第几个 chunk（0 起） */
private data class ChunkKey(
    val chapterIndex: Int,
    val chunkIndex: Int,
)

private const val PREFETCH_CHUNKS = 4
private const val PREFETCH_THRESHOLD_PAGES = 4
private const val PAGE_TAP_EDGE_RATIO = 0.22f

/**
 * 把单个 chunk 的渲染结果按屏高切成页。
 * TextMeasurer 只能在创建它的主线程调用，因此本函数必须运行在 Main；
 * 耗时的 buildChunkRender 由调用方放到 Dispatchers.Default。
 */
private fun sliceChunkPages(
    chapterIndex: Int,
    chapterTitle: String,
    chunkStart: Int,
    chunkText: String,
    chunkRender: ChunkRender,
    textMeasurer: TextMeasurer,
    textStyle: TextStyle,
    constraints: Constraints,
    density: Density,
    chapterMarks: List<Pair<Annotation, Triple<Int, Int, Int>>>,
    textHeightPx: Float,
    headerHeightPx: Float,
    isFirstOfChapter: Boolean,
): List<TxtPage> {
    val layout = textMeasurer.measure(
        text = chunkRender.rendered,
        style = textStyle,
        constraints = constraints,
        density = density,
    )
    val pages = mutableListOf<TxtPage>()
    var lineIdx = 0
    var firstOfChapter = isFirstOfChapter
    val pageSafetyPx = with(density) { 2.dp.toPx() }
    // 切片子串构造：页起始落在段落中间时移除该段首行缩进（与整块渲染一致）
    fun buildPageSub(rStart: Int, rEnd: Int): AnnotatedString {
        val subLen = rEnd - rStart
        if (subLen <= 0) return AnnotatedString("")
        val raw = chunkRender.rendered.subSequence(rStart, rEnd)
        val pageStartsMidParagraph = rStart > 0 &&
            chunkRender.rendered.paragraphStyles.none { it.start == rStart }
        return if (pageStartsMidParagraph) {
            val adjustedParagraphs = raw.paragraphStyles.map { range ->
                if (range.start == 0 && range.item.textIndent != null) {
                    range.copy(item = range.item.copy(textIndent = null))
                } else range
            }
            AnnotatedString(raw.text, raw.spanStyles, adjustedParagraphs)
        } else raw
    }
    while (lineIdx < layout.lineCount) {
        val availHeight = if (firstOfChapter) (textHeightPx - headerHeightPx).coerceAtLeast(1f) else textHeightPx
        val pageStartLine = lineIdx
        // 用绝对行偏移（相对页首行）判断边界，避免逐行累加引入浮点误差
        while (lineIdx < layout.lineCount) {
            val bottom = layout.getLineBottom(lineIdx) - layout.getLineTop(pageStartLine)
            if (bottom > availHeight - pageSafetyPx && lineIdx > pageStartLine) break
            lineIdx++
        }
        var rStart = layout.getLineStart(pageStartLine)
        var rEnd = if (lineIdx > 0) layout.getLineEnd(lineIdx - 1) else rStart
        var subLen = rEnd - rStart
        var sub = buildPageSub(rStart, rEnd)
        // 有界校验：以实际页高复测切片子串，若 didOverflowHeight 则回退一行重切，避免页尾文字被裁
        val verifyConstraints = Constraints(
            maxWidth = constraints.maxWidth,
            maxHeight = availHeight.toInt().coerceAtLeast(1),
        )
        var verify = textMeasurer.measure(text = sub, style = textStyle, constraints = verifyConstraints, density = density)
        var guard = 0
        while (verify.didOverflowHeight && lineIdx > pageStartLine + 1 && guard < layout.lineCount) {
            lineIdx--
            rEnd = layout.getLineEnd(lineIdx - 1)
            subLen = rEnd - rStart
            sub = buildPageSub(rStart, rEnd)
            verify = textMeasurer.measure(text = sub, style = textStyle, constraints = verifyConstraints, density = density)
            guard++
        }
        val origStart = chunkRender.renderedToOriginal(rStart, chunkText.length) + chunkStart
        val origEnd = chunkRender.renderedToOriginal(rEnd, chunkText.length) + chunkStart
        // 页内渲染下标 → 章内原文偏移映射（选区命中/几何换算用）
        val origOffsets = IntArray(subLen + 1) { r ->
            chunkRender.renderedToOriginal(rStart + r, chunkText.length) + chunkStart
        }
        // 预计算翻译 span（页内坐标）
        val tSpans = chunkRender.translationSpans.mapNotNull { ms ->
            val s = ms.start - rStart
            val e = ms.end - rStart
            if (e <= 0 || s >= subLen) null
            else MarkSpan(s.coerceAtLeast(0), e.coerceAtMost(subLen), ms.annotation)
        }
        // 预计算高亮 span（页内坐标；含带颜色的笔记）
        val hSpans = chapterMarks
            .filter { it.first.color != null && (it.first.type == AnnotationType.HIGHLIGHT || it.first.type == AnnotationType.NOTE) }
            .mapNotNull { (a, loc) ->
                val hs = (loc.second - chunkStart).coerceIn(0, chunkText.length)
                val he = (loc.third - chunkStart).coerceIn(0, chunkText.length)
                if (he <= hs) return@mapNotNull null
                val rs = chunkRender.originalToRendered(hs)
                val re = chunkRender.originalToRendered(he, atEnd = true)
                val ps = (rs - rStart).coerceAtLeast(0)
                val pe = (re - rStart).coerceAtMost(subLen)
                if (pe <= ps) null else MarkSpan(ps, pe, a)
            }
        pages.add(
            TxtPage(
                chapterIndex = chapterIndex,
                chapterTitle = chapterTitle,
                rendered = sub,
                isFirstOfChapter = firstOfChapter,
                origStartInChapter = origStart,
                origEndInChapter = origEnd,
                origOffsets = origOffsets,
                translationSpans = tSpans,
                highlightSpans = hSpans,
            ),
        )
        firstOfChapter = false
    }
    return pages
}

/**
 * TXT 分页阅读视图（slide 真跟手 / none 瞬时）。
 * chunk 级滑动窗口增量分页：只测当前位置所在 chunk + 预读后续 chunk，
 * 翻到窗口边缘时按需 append/prepend，避免长章/长文件全量分页卡顿与闪退。
 * HorizontalPager 原生拖动即真跟手；none 模式不跟手，松手按距离/速度瞬时翻页（与 EPUB 一致）。
 */
@Composable
internal fun TxtPagedViewer(
    controller: TxtReaderController,
    settings: ReadSettings,
    annotations: List<Annotation>,
    fontFamily: FontFamily,
    onToggleHud: () -> Unit,
    onHighlightTap: (Annotation, HighlightMenuAnchor) -> Unit = { _, _ -> },
    onTranslationTap: (Annotation) -> Unit,
    registerScrollBridge: (((Int) -> Unit) -> Unit)? = null,
    registerAutoFlipTurn: ((() -> Boolean) -> Unit)? = null,
    isAutoFlipActive: Boolean = false,
    /** 阅读根容器 LayoutCoordinates（把 root 坐标换算到页本地） */
    rootCoords: LayoutCoordinates? = null,
    /** 选区激活：锁定 HorizontalPager 拖动翻页 */
    selectionActive: Boolean = false,
    /** 上报 SelectionTarget 给上层 SelectionOverlay */
    onTargetReady: (SelectionTarget?) -> Unit = {},
    /** 点击桥（root 坐标）：本 Compose 版本下 pager 内部 pointerInput 不启动，点击由根裁判转发 */
    registerTapBridge: (((Float, Float) -> Unit) -> Unit)? = null,
    /** 横滑翻页桥（none 模式）：方向 -1=上一页 / 1=下一页 */
    registerFlickBridge: (((Int) -> Unit) -> Unit)? = null,
    /** 全文搜索跳转后的闪烁目标（TXT 精确到章内字符区间） */
    searchFlash: SearchFlash? = null,
    modifier: Modifier = Modifier,
) {
    val chapters by controller.chaptersFlow.collectAsState()
    val textMeasurer = rememberTextMeasurer()
    val density = LocalDensity.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val isNone = settings.pageFlipAnimation == "none"

    val safeFontSize = settings.fontSize.coerceIn(12, 26)
    val safeLineHeight = settings.lineHeight.coerceIn(1.0f, 3.0f)
    val safeHorizontalMargin = settings.horizontalMargin.coerceIn(0f, 2f)
    val safeVerticalMargin = settings.verticalMargin.coerceIn(0f, 2f)
    val safeFirstLineIndent = settings.firstLineIndent.coerceIn(0f, 4f).roundToInt().coerceIn(0, 4)
    val paragraphGapSp = safeFontSize * settings.paragraphSpacing.coerceIn(0f, 3f)
    val inkColor = readerInkColor(settings)
    val pageBgColor = readerPageBackground(settings)
    val translationColor = when {
        settings.nightMode || settings.theme == "black" -> inkColor.copy(alpha = 0.65f)
        else -> Color(0xFFDC2626)
    }
    val textAlign = if (settings.alignment == "left") TextAlign.Left else TextAlign.Justify

    var viewportW by remember { mutableFloatStateOf(0f) }
    var viewportH by remember { mutableFloatStateOf(0f) }
    val hMarginDp = (24 * safeHorizontalMargin).dp
    val vMarginDp = (24 * safeVerticalMargin).dp
    val hMarginPx = with(density) { hMarginDp.toPx() }
    val vMarginPx = with(density) { vMarginDp.toPx() }
    val textWidthPx = (viewportW - 2 * hMarginPx).coerceAtLeast(1f)
    val textHeightPx = (viewportH - 2 * vMarginPx).coerceAtLeast(1f)
    val labelTextStyle = MaterialTheme.typography.labelMedium
    val bodyTextStyle = LocalTextStyle.current.merge(
        TextStyle(
            fontSize = safeFontSize.sp,
            lineHeight = (safeFontSize * safeLineHeight).sp,
            fontFamily = fontFamily,
            textAlign = textAlign,
        ),
    )
    val titleTextStyle = LocalTextStyle.current.merge(
        TextStyle(
            fontFamily = fontFamily,
            fontSize = (safeFontSize + 7).sp,
            fontWeight = FontWeight.Bold,
            lineHeight = (safeFontSize + 7).sp * 1.3f,
        ),
    )

    // 分块滑动窗口：pages 只保存窗口内已测 chunk 的页，避免长章/长书全量分页
    var pages by remember { mutableStateOf<List<TxtPage>>(emptyList()) }
    var firstKey by remember { mutableStateOf<ChunkKey?>(null) }
    var lastKey by remember { mutableStateOf<ChunkKey?>(null) }
    var restored by remember { mutableStateOf(false) }
    // 进入遮罩：首屏定位完成后淡出，仅覆盖“进入”这一次
    var enterMaskVisible by remember { mutableStateOf(true) }
    val pagerState = rememberPagerState(pageCount = { pages.size })
    var lastSwipeTurnTime by remember { mutableStateOf(0L) }
    // 所有窗口修改串行化，避免初始测量/翻页预读/外部定位并发重复测量
    val measureMutex = remember { Mutex() }

    // 标注签名并入重测键：新增/改色/删除高亮、翻译标注变化时，窗口内页面即时重测刷新
    val annotationsSig = annotations.joinToString("|") {
        "${it.id}:${it.type}:${it.locatorJson}:${it.color}:${it.translation}"
    }
    val measureKey = "${safeFontSize}_${safeLineHeight}_${safeHorizontalMargin}_${safeVerticalMargin}_" +
        "${safeFirstLineIndent}_${settings.paragraphSpacing}_${textWidthPx.toInt()}_${textHeightPx.toInt()}_" +
        "${chapters.size}_${fontFamily}_${textAlign}_$annotationsSig" +
        "_flash_${searchFlash?.txtChapter}_${searchFlash?.txtStart}_${searchFlash?.txtEnd}"

    // 章文本缓存：整章只 substring 一次；章节结构变化时重建
    val chapterTextCache = remember(chapters) { mutableMapOf<Int, String>() }
    fun chapterTextFor(ci: Int): String? {
        val ch = chapters.getOrNull(ci) ?: return null
        return chapterTextCache.getOrPut(ci) { controller.fullText.substring(ch.startOffset, ch.endOffset) }
    }
    fun chunkCountOf(ci: Int): Int {
        val ch = chapters.getOrNull(ci) ?: return 0
        return ((ch.length + TxtReaderController.CHUNK_SIZE - 1) / TxtReaderController.CHUNK_SIZE).coerceAtLeast(1)
    }
    fun nextChunk(key: ChunkKey): ChunkKey? {
        val count = chunkCountOf(key.chapterIndex)
        return when {
            key.chunkIndex + 1 < count -> ChunkKey(key.chapterIndex, key.chunkIndex + 1)
            key.chapterIndex + 1 < chapters.size -> ChunkKey(key.chapterIndex + 1, 0)
            else -> null
        }
    }
    fun prevChunk(key: ChunkKey): ChunkKey? {
        if (key.chunkIndex > 0) return ChunkKey(key.chapterIndex, key.chunkIndex - 1)
        if (key.chapterIndex > 0) {
            val prevCount = chunkCountOf(key.chapterIndex - 1)
            return ChunkKey(key.chapterIndex - 1, prevCount - 1)
        }
        return null
    }
    fun flatIndexOf(key: ChunkKey): Int {
        var base = 0
        for (i in 0 until key.chapterIndex) base += chunkCountOf(i)
        return base + key.chunkIndex
    }

    fun measureChapterHeaderHeight(chapterLabel: String, chapterTitle: String, widthPx: Float): Float {
        val width = widthPx.toInt().coerceAtLeast(1)
        val labelLayout = textMeasurer.measure(
            text = chapterLabel,
            style = labelTextStyle,
            constraints = Constraints(maxWidth = width),
            density = density,
        )
        val titleLayout = textMeasurer.measure(
            text = chapterTitle,
            style = titleTextStyle,
            constraints = Constraints(maxWidth = width),
            density = density,
        )
        return with(density) {
            labelLayout.size.height.toFloat() +
                6.dp.toPx() +
                titleLayout.size.height.toFloat() +
                14.dp.toPx() +
                12.dp.toPx()
        }
    }

    // 测量单个 chunk：字符串/标注处理放后台，TextMeasurer.measure 必须在主线程
    suspend fun measureChunk(key: ChunkKey): List<TxtPage> {
        val currentWidth = (viewportW - 2 * hMarginPx).coerceAtLeast(1f)
        val currentHeight = (viewportH - 2 * vMarginPx).coerceAtLeast(1f)
        if (viewportW <= 0 || viewportH <= 0 || currentWidth <= 1f || currentHeight <= 1f) return emptyList()
        val chapter = chapters.getOrNull(key.chapterIndex) ?: return emptyList()
        if (chapter.length <= 0) return emptyList()
        val chapterText = chapterTextFor(key.chapterIndex) ?: return emptyList()
        val chunkStart = key.chunkIndex * TxtReaderController.CHUNK_SIZE
        val chunkEnd = minOf(chunkStart + TxtReaderController.CHUNK_SIZE, chapter.length)
        if (chunkEnd <= chunkStart) return emptyList()
        val chunkText = chapterText.substring(chunkStart, chunkEnd)
        val (render, chapterMarks) = withContext(Dispatchers.Default) {
            val marks = annotations.mapNotNull { a -> parseMarkLocator(a.locatorJson)?.let { a to it } }
                .filter { it.second.first == key.chapterIndex }
            val flashRange = searchFlash
                ?.takeIf { it.txtChapter == key.chapterIndex }
                ?.let { it.txtStart to it.txtEnd }
            buildChunkRender(
                chapterText = chapterText,
                chapterIndex = key.chapterIndex,
                chunkStartOffset = chunkStart,
                chunkEndOffset = chunkEnd,
                annotations = annotations,
                translationColor = translationColor,
                fontSize = safeFontSize,
                paragraphSpacing = settings.paragraphSpacing.coerceIn(0f, 3f),
                paragraphGapSp = paragraphGapSp,
                firstLineIndent = safeFirstLineIndent,
                flashRange = flashRange,
            ) to marks
        }
        val headerHeightPx = if (key.chunkIndex == 0) {
            measureChapterHeaderHeight(context.getString(R.string.viewer_chapter_label, key.chapterIndex + 1), chapter.title, currentWidth)
        } else {
            0f
        }
        return sliceChunkPages(
            chapterIndex = key.chapterIndex,
            chapterTitle = chapter.title,
            chunkStart = chunkStart,
            chunkText = chunkText,
            chunkRender = render,
            textMeasurer = textMeasurer,
            textStyle = bodyTextStyle,
            constraints = Constraints(maxWidth = currentWidth.toInt().coerceAtLeast(1)),
            density = density,
            chapterMarks = chapterMarks,
            textHeightPx = currentHeight,
            headerHeightPx = headerHeightPx,
            isFirstOfChapter = key.chunkIndex == 0,
        )
    }

    // 初始测量：只测当前位置所在 chunk，再预读后续几个 chunk
    LaunchedEffect(measureKey) {
        if (chapters.isEmpty() || viewportW <= 0 || viewportH <= 0) return@LaunchedEffect
        measureMutex.withLock {
            val pos = controller.currentLocator.value?.let { parsePositionLocator(it) }
            val chapterIdx = pos?.first ?: 0
            val flat = controller.chunkIndexOf(chapterIdx, pos?.second ?: 0)
            val info = controller.chunkAt(flat)
            var startKey = ChunkKey(info.chapterIndex, info.chunkIndexInChapter)
            var first = measureChunk(startKey)
            var emptyGuard = 0
            while (first.isEmpty() && emptyGuard < 100) {
                val next = nextChunk(startKey) ?: break
                startKey = next
                first = measureChunk(startKey)
                emptyGuard++
            }
            if (first.isEmpty()) {
                restored = true
                enterMaskVisible = false
                return@withLock
            }
            pages = first
            firstKey = startKey
            lastKey = startKey
            val idx = first.indexOfFirst { p -> pos?.second in p.origStartInChapter until p.origEndInChapter }.let { if (it < 0) 0 else it }
            pagerState.scrollToPage(idx)
            restored = true
            enterMaskVisible = false
            var cursor = startKey
            repeat(PREFETCH_CHUNKS) {
                val next = nextChunk(cursor) ?: return@repeat
                val more = measureChunk(next)
                if (more.isNotEmpty()) {
                    pages = pages + more
                    lastKey = next
                    cursor = next
                }
            }
        }
    }

    // 翻页时预读：接近窗口末尾 append 下一 chunk，接近开头 prepend 上一 chunk
    LaunchedEffect(pagerState.currentPage, pages.size, measureKey) {
        if (!restored || pages.isEmpty() || viewportW <= 0 || viewportH <= 0) return@LaunchedEffect
        val p = pages.getOrNull(pagerState.currentPage) ?: return@LaunchedEffect
        controller.setPagedPosition(p.chapterIndex, p.origStartInChapter)
        measureMutex.withLock {
            val currentPage = pagerState.currentPage
            if (currentPage >= pages.size - PREFETCH_THRESHOLD_PAGES) {
                val next = lastKey?.let { nextChunk(it) }
                if (next != null) {
                    val more = measureChunk(next)
                    if (more.isNotEmpty()) {
                        pages = pages + more
                        lastKey = next
                    }
                }
            }
            if (currentPage <= PREFETCH_THRESHOLD_PAGES) {
                val prev = firstKey?.let { prevChunk(it) }
                if (prev != null) {
                    val more = measureChunk(prev)
                    if (more.isNotEmpty()) {
                        pages = more + pages
                        firstKey = prev
                        pagerState.scrollToPage(currentPage + more.size)
                    }
                }
            }
        }
    }

    // 外部定位（目录/搜索/进度条/笔记跳转）— 去重避免循环
    LaunchedEffect(measureKey) {
        controller.currentLocator.collect { loc ->
            if (!restored || loc == null || pages.isEmpty()) return@collect
            if (viewportW <= 0 || viewportH <= 0) {
                var waits = 0
                while ((viewportW <= 0 || viewportH <= 0) && waits < 60) {
                    withFrameNanos { }
                    waits++
                }
                if (viewportW <= 0 || viewportH <= 0) return@collect
            }
            val pos = parsePositionLocator(loc) ?: return@collect
            measureMutex.withLock {
                val cur = pages.getOrNull(pagerState.currentPage)
                if (cur != null && cur.chapterIndex == pos.first &&
                    pos.second in cur.origStartInChapter until cur.origEndInChapter
                ) return@withLock
                val flat = controller.chunkIndexOf(pos.first, pos.second)
                val info = controller.chunkAt(flat)
                val targetKey = ChunkKey(info.chapterIndex, info.chunkIndexInChapter)
                val f = firstKey
                val l = lastKey
                if (f != null && l != null &&
                    flatIndexOf(targetKey) in flatIndexOf(f)..flatIndexOf(l)
                ) {
                    val idx = pages.indexOfFirst { p ->
                        p.chapterIndex == pos.first && pos.second in p.origStartInChapter until p.origEndInChapter
                    }
                    if (idx >= 0 && idx != pagerState.currentPage) pagerState.scrollToPage(idx)
                } else {
                    val targetPages = measureChunk(targetKey)
                    pages = targetPages
                    firstKey = targetKey
                    lastKey = targetKey
                    val idx = targetPages.indexOfFirst { p ->
                        pos.second in p.origStartInChapter until p.origEndInChapter
                    }.let { if (it < 0) 0 else it }
                    pagerState.scrollToPage(idx)
                    var cursor = targetKey
                    repeat(PREFETCH_CHUNKS) {
                        val next = nextChunk(cursor) ?: return@repeat
                        val more = measureChunk(next)
                        if (more.isNotEmpty()) {
                            pages = pages + more
                            lastKey = next
                            cursor = next
                        }
                    }
                }
            }
        }
    }

    /** 翻页：边界时加载相邻 chunk；slide 带动画，none 瞬时 */
    fun turnTo(target: Int, forceInstant: Boolean = false) {
        val animate = !isNone && !forceInstant
        scope.launch {
            if (viewportW <= 0 || viewportH <= 0) return@launch
            measureMutex.withLock {
                when {
                    target < 0 -> {
                        val prev = firstKey?.let { prevChunk(it) } ?: return@withLock
                        val more = measureChunk(prev)
                        if (more.isEmpty()) return@withLock
                        pages = more + pages
                        firstKey = prev
                        if (animate) pagerState.animateScrollToPage(more.size - 1)
                        else pagerState.scrollToPage(more.size - 1)
                    }
                    target >= pages.size -> {
                        val next = lastKey?.let { nextChunk(it) } ?: return@withLock
                        val more = measureChunk(next)
                        if (more.isEmpty()) return@withLock
                        pages = pages + more
                        lastKey = next
                        val start = pages.size - more.size
                        if (animate) pagerState.animateScrollToPage(start) else pagerState.scrollToPage(start)
                    }
                    else -> {
                        if (animate) pagerState.animateScrollToPage(target) else pagerState.scrollToPage(target)
                    }
                }
            }
        }
    }

    // 音量键翻页桥
    LaunchedEffect(measureKey) {
        registerScrollBridge?.invoke { direction ->
            turnTo(pagerState.currentPage + direction)
        }
        registerAutoFlipTurn?.invoke {
            if (pages.isEmpty()) return@invoke false
            val hasNext = pagerState.currentPage < pages.lastIndex ||
                lastKey?.let { nextChunk(it) } != null
            if (hasNext) {
                turnTo(pagerState.currentPage + 1, forceInstant = true)
                true
            } else {
                false
            }
        }
    }

    // ── 选区命中注册表 + SelectionTarget 实现（P1）──
    val hitRegistry = remember { mutableStateMapOf<TxtPage, PageHitEntry>() }
    val latestRootCoords by rememberUpdatedState(rootCoords)

    val selectionTarget = remember(controller) {
        object : SelectionTarget {
            override fun charOffsetAt(rootPos: Offset): Pair<Int, Int>? {
                val rc = latestRootCoords ?: return null
                for (e in hitRegistry.values) {
                    if (!e.coords.isAttached) continue
                    val local = e.coords.localPositionOf(rc, rootPos)
                    if (local.x < 0f || local.y < 0f ||
                        local.x > e.layout.size.width || local.y > e.layout.size.height
                    ) continue
                    val rendered = e.layout.getOffsetForPosition(local)
                        .coerceIn(0, e.page.rendered.length)
                    return e.page.chapterIndex to e.page.origOffsets[rendered]
                }
                return null
            }

            override fun rectForCharOffset(chapter: Int, offset: Int): Rect? {
                val e = hitRegistry.values.firstOrNull {
                    it.page.chapterIndex == chapter &&
                        offset >= it.page.origStartInChapter && offset < it.page.origEndInChapter
                } ?: return null
                if (!e.coords.isAttached) return null
                val rendered = lowerBound(e.page.origOffsets, offset).coerceIn(0, e.page.rendered.length)
                val box = e.layout.getBoundingBox(rendered)
                val p = e.coords.localToRoot(Offset(box.left, box.top))
                return Rect(p.x, p.y, p.x + box.width, p.y + box.height)
            }

            override fun selectionRects(chapter: Int, start: Int, end: Int): List<Rect> {
                val out = mutableListOf<Rect>()
                for (e in hitRegistry.values) {
                    if (e.page.chapterIndex != chapter || !e.coords.isAttached) continue
                    val lo = max(start, e.page.origStartInChapter)
                    val hi = min(end, e.page.origEndInChapter)
                    if (hi <= lo) continue
                    val rS = lowerBound(e.page.origOffsets, lo).coerceIn(0, e.page.rendered.length)
                    val rE = lowerBound(e.page.origOffsets, hi).coerceIn(0, e.page.rendered.length)
                    if (rE <= rS) continue
                    val lineS = e.layout.getLineForOffset(rS)
                    val lineE = e.layout.getLineForOffset(rE - 1)
                    for (line in lineS..lineE) {
                        val ls = max(e.layout.getLineStart(line), rS)
                        val le = min(e.layout.getLineEnd(line, visibleEnd = true), rE)
                        if (le <= ls) continue
                        val top = e.layout.getLineTop(line)
                        val bottom = e.layout.getLineBottom(line)
                        // 选区边界 x 用字符实际包围盒（正确处理首行缩进）；整行用 getLineLeft/Right
                        val left = if (line == lineS && ls > e.layout.getLineStart(line)) {
                            e.layout.getBoundingBox(ls).left
                        } else {
                            e.layout.getLineLeft(line)
                        }
                        val right = if (line == lineE && le < e.layout.getLineEnd(line, visibleEnd = true)) {
                            e.layout.getBoundingBox(le - 1).right
                        } else {
                            e.layout.getLineRight(line)
                        }
                        if (right <= left) continue
                        val p1 = e.coords.localToRoot(Offset(left, top))
                        val p2 = e.coords.localToRoot(Offset(right, bottom))
                        out += Rect(p1.x, p1.y, p2.x, p2.y)
                    }
                }
                return out
            }

            override fun lineRectAt(chapter: Int, offset: Int): Rect? {
                val e = hitRegistry.values.firstOrNull {
                    it.page.chapterIndex == chapter &&
                        offset >= it.page.origStartInChapter && offset < it.page.origEndInChapter
                } ?: return null
                if (!e.coords.isAttached) return null
                val rendered = lowerBound(e.page.origOffsets, offset).coerceIn(0, e.page.rendered.length)
                val line = e.layout.getLineForOffset(rendered)
                val left = e.layout.getLineLeft(line)
                val top = e.layout.getLineTop(line)
                val bottom = e.layout.getLineBottom(line)
                val endX = e.layout.getLineRight(line)
                val p1 = e.coords.localToRoot(Offset(left, top))
                val p2 = e.coords.localToRoot(Offset(endX, bottom))
                return Rect(p1.x, p1.y, p2.x, p2.y)
            }

            override suspend fun scrollByForSelection(deltaPx: Float) {
                // 分页模式：翻页节奏由 SelectionController.flipHold 的进度控制（进度满才调用一次）
                val cur = pagerState.currentPage
                if (deltaPx > 0) {
                    if (cur < pages.lastIndex) turnTo(cur + 1, forceInstant = true)
                    else turnTo(pages.size, forceInstant = true)
                } else {
                    if (cur > 0) turnTo(cur - 1, forceInstant = true)
                    else turnTo(-1, forceInstant = true)
                }
            }

            override fun originalText(chapter: Int, start: Int, end: Int): String {
                val ch = controller.chapters.getOrNull(chapter) ?: return ""
                val from = (ch.startOffset + start).coerceIn(ch.startOffset, ch.endOffset)
                val to = (ch.startOffset + end).coerceIn(from, ch.endOffset)
                return controller.fullText.substring(from, to)
            }
        }
    }
    DisposableEffect(selectionTarget) {
        onTargetReady(selectionTarget)
        onDispose { onTargetReady(null) }
    }

    // ── 点击/横滑桥（root 坐标）：pager 内部 pointerInput 在本 Compose 版本不启动，改由根裁判转发 ──
    /** 点按处理：翻译/高亮命中 → 边缘翻页 → 中央 HUD（与原 pageContent 逻辑一致） */
    fun handleTapAt(pos: Offset) {
        val rc = latestRootCoords
        // 命中正文：翻译/高亮优先
        if (rc != null) {
            for (e in hitRegistry.values) {
                if (!e.coords.isAttached) continue
                val local = e.coords.localPositionOf(rc, pos)
                if (local.x < 0f || local.y < 0f ||
                    local.x > e.layout.size.width || local.y > e.layout.size.height
                ) continue
                val pageOffset = e.layout.getOffsetForPosition(local)
                // 点击点必须落在命中字符的水平包围盒内（避免点在行尾/行首空白也算命中）
                val hitBox = e.layout.getBoundingBox(pageOffset.coerceIn(0, (e.page.rendered.length - 1).coerceAtLeast(0)))
                if (local.x < hitBox.left || local.x > hitBox.right) continue
                val tHit = e.page.translationSpans.firstOrNull { pageOffset in it.start until it.end }
                if (tHit != null) { onTranslationTap(tHit.annotation); return }
                val hHit = e.page.highlightSpans.firstOrNull { pageOffset in it.start until it.end }
                if (hHit != null) {
                    val c = e.coords
                    val anchor = if (c.isAttached) {
                        val line = e.layout.getLineForOffset(pageOffset)
                        val endX = e.layout.getLineRight(line)
                        val p1 = c.localToRoot(Offset(e.layout.getLineLeft(line), e.layout.getLineTop(line)))
                        val p2 = c.localToRoot(Offset(endX, e.layout.getLineBottom(line)))
                        HighlightMenuAnchor(
                            lineRect = Rect(p1.x, p1.y, p2.x, p2.y),
                            lineEnd = Offset(p2.x, (p1.y + p2.y) / 2f),
                        )
                    } else HighlightMenuAnchor()
                    onHighlightTap(hHit.annotation, anchor)
                    return
                }
                break
            }
        }
        // 空白区也响应手势：边缘翻页 / 中间 HUD
        val pageWidth = (rc?.size?.width ?: 0).toFloat()
        when {
            pos.x < pageWidth * PAGE_TAP_EDGE_RATIO -> turnTo(pagerState.currentPage - 1)
            pos.x > pageWidth * (1f - PAGE_TAP_EDGE_RATIO) -> turnTo(pagerState.currentPage + 1)
            else -> onToggleHud()
        }
    }
    LaunchedEffect(measureKey) {
        registerTapBridge?.invoke { x, y -> handleTapAt(Offset(x, y)) }
        registerFlickBridge?.invoke { direction ->
            // none 模式横滑：仅在 pager 未自行跟手（offsetFraction≈0）时补翻页，避免双重翻页
            if (kotlin.math.abs(pagerState.currentPageOffsetFraction) < 0.05f) {
                turnTo(pagerState.currentPage + direction)
            }
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        if (pages.isEmpty()) {
            // 测量完成前只显示同色细进度环，避免“排版中…”文字闪现后再切正文的闪动
            Box(
                Modifier.fillMaxSize().onGloballyPositioned { viewportW = it.size.width.toFloat(); viewportH = it.size.height.toFloat() },
                contentAlignment = Alignment.Center,
            ) {
                androidx.compose.material3.CircularProgressIndicator(
                    color = inkColor.copy(alpha = 0.3f),
                    strokeWidth = 2.dp,
                )
            }
        } else {
            HorizontalPager(
                state = pagerState,
                modifier = Modifier
                    .fillMaxSize()
                    .onGloballyPositioned { viewportW = it.size.width.toFloat(); viewportH = it.size.height.toFloat() },
        userScrollEnabled = !isNone && !isAutoFlipActive && !selectionActive,
        beyondViewportPageCount = 1,
        pageContent = { pageIndex ->
            val page = pages[pageIndex]
            var layoutResult by remember(pageIndex) { mutableStateOf<TextLayoutResult?>(null) }
            var textCoords by remember(pageIndex) { mutableStateOf<LayoutCoordinates?>(null) }
            var pageCoords by remember(pageIndex) { mutableStateOf<LayoutCoordinates?>(null) }

            // ── 选区命中注册（SelectionOverlay 数据源）──
            fun updateRegistry() {
                val lr = layoutResult
                val c = textCoords
                if (lr != null && c != null && c.isAttached) {
                    hitRegistry[page] = PageHitEntry(page, lr, c)
                } else {
                    hitRegistry.remove(page)
                }
            }
            DisposableEffect(page) {
                onDispose { hitRegistry.remove(page) }
            }

            Box(
                Modifier
                    .fillMaxSize()
                    .onGloballyPositioned { pageCoords = it },
            ) {
                Column(
                    Modifier
                        .fillMaxSize()
                        .padding(horizontal = hMarginDp, vertical = vMarginDp),
                ) {
                    if (page.isFirstOfChapter) {
                        Column(Modifier.fillMaxWidth().padding(bottom = 12.dp)) {
                            Text(
                                text = stringResource(R.string.viewer_chapter_label, page.chapterIndex + 1),
                                style = labelTextStyle,
                                color = inkColor.copy(alpha = 0.5f),
                            )
                            Spacer(Modifier.height(6.dp))
                            Text(
                                text = page.chapterTitle,
                                style = titleTextStyle,
                                color = inkColor,
                            )
                            Spacer(Modifier.height(14.dp))
                        }
                    }

                    Text(
                        text = page.rendered,
                        style = bodyTextStyle,
                        color = inkColor,
                        onTextLayout = { result ->
                            layoutResult = result
                            updateRegistry()
                            if (result.didOverflowHeight) {
                                android.util.Log.w(
                                    "TxtPagedViewer",
                                    "page overflow: chapter=${page.chapterIndex}, start=${page.origStartInChapter}",
                                )
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .onGloballyPositioned {
                                textCoords = it
                                updateRegistry()
                            },
                    )
                }
            }
        },
            )
        }
        // 进入遮罩：首屏定位完成后淡出，仅覆盖“进入”这一次
        AnimatedVisibility(
            visible = enterMaskVisible,
            enter = fadeIn(tween(0)),
            exit = fadeOut(tween(240)),
        ) {
            Box(Modifier.fillMaxSize().background(pageBgColor))
        }
    }
}
