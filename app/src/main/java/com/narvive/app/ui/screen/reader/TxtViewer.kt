package com.narvive.app.ui.screen.reader

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.ParagraphStyle
import androidx.compose.ui.text.PlatformParagraphStyle
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextIndent
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.narvive.app.R
import com.narvive.app.domain.model.Annotation
import com.narvive.app.domain.model.AnnotationType
import com.narvive.app.service.reader.ReadSettings
import com.narvive.app.service.reader.TxtReaderController
import com.narvive.app.ui.screen.reader.selection.SelectionTarget
import kotlinx.coroutines.launch
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/** 渲染文本相对原文的插入记录（段距空段 / 翻译红字），用于 原文↔渲染 坐标双向映射 */
internal data class Insertion(
    val origPos: Int,       // 原文中的插入点（块内坐标）
    val renderedStart: Int, // 渲染文本中的起点
    val renderedLen: Int,
    val paragraphGap: Boolean = false, // 是否为段距空段标记
)

/** 渲染文本相对原文的删除记录（首行缩进开启时移除的段首原有空白），用于 原文↔渲染 坐标双向映射 */
internal data class Deletion(
    val origPos: Int,       // 原文中删除起点的块内坐标
    val count: Int,         // 删除的字符数（>0）
    val renderedStart: Int, // 删除点对应的渲染坐标（被删字符在渲染文本中不占位，后续内容从此处开始）
)

/** TXT 段首需归一化的空白字符：半角空格 / 制表符 / 全角空格 / 不换行空格 */
internal fun Char.isTxtLeadingWhitespace(): Boolean =
    this == ' ' || this == '\t' || this == '\u3000' || this == '\u00A0'

/** 点按命中的渲染区间 → 标注 */
internal data class MarkSpan(val start: Int, val end: Int, val annotation: Annotation)

/** 分块命中条目（SelectionTarget 注册表数据源） */
internal class ChunkHitEntry(
    val chapterIndex: Int,
    val chunkStart: Int,
    val chunkText: String,
    val render: ChunkRender,
    val layout: TextLayoutResult,
    val coords: LayoutCoordinates,
)

/** 一个分块的渲染产物 */
internal class ChunkRender(
    val rendered: AnnotatedString,
    val plainLength: Int,
    val insertions: List<Insertion>,
    val deletions: List<Deletion>,
    val translationSpans: List<MarkSpan>,
) {
    fun originalToRendered(o: Int, atEnd: Boolean = false): Int {
        // 按原坐标合并遍历插入/删除：插入整体计入，删除只截断 o 之前被移除的部分
        var r = o
        var i = 0
        var d = 0
        while (i < insertions.size || d < deletions.size) {
            val ins = insertions.getOrNull(i)
            val del = deletions.getOrNull(d)
            val insPos = ins?.origPos ?: Int.MAX_VALUE
            val delPos = del?.origPos ?: Int.MAX_VALUE
            if (del != null && (ins == null || delPos < insPos)) {
                if (delPos >= o) break
                r -= minOf(del.count, o - delPos)
                d++
            } else if (ins != null) {
                val include = if (atEnd) ins.origPos < o else ins.origPos <= o
                if (!include) break
                r += ins.renderedLen
                i++
                // 同点删除：删除起点在 o 之前，则被删字符同样不占渲染位置
                if (del != null && delPos == insPos && delPos < o) {
                    r -= minOf(del.count, o - delPos)
                    d++
                }
            }
        }
        return r
    }

    fun renderedToOriginal(r: Int, originalLength: Int): Int {
        // 按渲染坐标合并遍历；同坐标先插后删（插入文本优先命中，删除点之后补上被删字数）
        var orig = r
        var i = 0
        var d = 0
        while (i < insertions.size || d < deletions.size) {
            val ins = insertions.getOrNull(i)
            val del = deletions.getOrNull(d)
            val insStart = ins?.renderedStart ?: Int.MAX_VALUE
            val delStart = del?.renderedStart ?: Int.MAX_VALUE
            if (ins != null && (del == null || insStart <= delStart)) {
                if (r < insStart) break
                if (r < insStart + ins.renderedLen) return ins.origPos.coerceIn(0, originalLength)
                orig -= ins.renderedLen
                i++
            } else if (del != null) {
                if (r < delStart) break
                orig += del.count
                d++
            }
        }
        return orig.coerceIn(0, originalLength)
    }
}

/** 解析标注 locator {"chapter":i,"start":s,"end":e} */
internal fun parseMarkLocator(json: String): Triple<Int, Int, Int>? = runCatching {
    val obj = org.json.JSONObject(json)
    if (obj.has("chapter") && obj.has("start")) {
        Triple(obj.getInt("chapter"), obj.getInt("start"), obj.getInt("end"))
    } else null
}.getOrNull()

/** 解析位置 locator {"chapter":i,"offset":n} → (chapterIndex, offsetInChapter) */
internal fun parsePositionLocator(json: String): Pair<Int, Int>? = runCatching {
    val obj = org.json.JSONObject(json)
    when {
        obj.has("chapter") -> obj.getInt("chapter") to obj.optInt("offset", obj.optInt("start", 0))
        else -> null
    }
}.getOrNull()

// ── 分块渲染 ──

/** 构建单个分块的渲染产物：段距空段 + 翻译红字 + 高亮背景 + 首行缩进。
 *  chunkStartOffset/chunkEndOffset 为此块在章内的起始/结束偏移，
 *  所有 annotations 的 chapter 坐标需要过滤到此范围内并减掉 chunkStartOffset 转为本地偏移。 */
internal fun buildChunkRender(
    chapterText: String,      // 整章原文
    chapterIndex: Int,
    chunkStartOffset: Int,
    chunkEndOffset: Int,
    annotations: List<Annotation>,
    translationColor: Color,
    fontSize: Int,
    paragraphSpacing: Float, // 是否插入段距空行（>0 时插入）
    paragraphGapSp: Float,   // 段距空段高度（sp）= 字号 × 段间距 em
    firstLineIndent: Int, // 首行缩进 em（0=不缩进）
    flashRange: Pair<Int, Int>? = null, // 全文搜索闪烁区间（章内原文 [start, end)）
): ChunkRender {
    val chunkText = chapterText.substring(chunkStartOffset, chunkEndOffset)
    val chunkLen = chunkEndOffset - chunkStartOffset

    // 过滤本章标注，且区间与本块有交集
    val marks = annotations.mapNotNull { a ->
        parseMarkLocator(a.locatorJson)?.let { loc -> a to loc }
    }.filter { it.second.first == chapterIndex }

    // 翻译标注：end 位置（loc.third = 插入点）落在本块内
    val translations = marks
        .filter { it.first.type == AnnotationType.TRANSLATION && !it.first.translation.isNullOrBlank() }
        .filter { (_, loc) -> loc.third in chunkStartOffset until chunkEndOffset }
        .sortedBy { it.second.third }

    // ── 插入列表：段距空段 + 原文换行折叠 + 译文（块内坐标）──
    data class Pending(val origPos: Int, val text: String, val annotation: Annotation?, val paragraphGap: Boolean = false)
    val pendings = mutableListOf<Pending>()
    val deletions = mutableListOf<Deletion>()
    val insertGap = paragraphSpacing > 0.01f
    // 段间距统一控制段落间的垂直空隙：
    //  - 原文换行只作为段落边界，渲染文本中不保留换行字符；
    //  - 段间距 >0 时在边界处插入零宽空段标记，由 lineHeight 精确控制高度；
    //  - 段落分隔由 ParagraphStyle 范围本身产生，避免换行符被 Compose 额外渲染成空行。
    data class Segment(val start: Int, val end: Int)
    val segments = mutableListOf<Segment>()
    var contentStart = 0
    var i = 0
    while (i < chunkText.length) {
        if (chunkText[i] != '\n') { i++; continue }
        val runStart = i
        while (i < chunkText.length && chunkText[i] == '\n') i++
        val runEnd = i
        if (runStart > contentStart) segments += Segment(contentStart, runStart)
        // 原文换行字符全部删除，段落边界改由 ParagraphStyle 表达
        deletions += Deletion(runStart, runEnd - runStart, 0)
        if (insertGap && runEnd < chunkText.length) {
            pendings += Pending(runEnd, "\u200B", null, paragraphGap = true)
        }
        contentStart = runEnd
    }
    if (contentStart < chunkText.length) segments += Segment(contentStart, chunkText.length)

    // 换行 run 被上一块切掉时，由本块在内容前补上段距空段
    if (
        insertGap && chunkStartOffset > 0 && chunkText.firstOrNull() != '\n' &&
            chapterText.getOrNull(chunkStartOffset - 1) == '\n'
    ) {
        pendings += Pending(0, "\u200B", null, paragraphGap = true)
    }
    translations.forEach { (a, loc) ->
        pendings += Pending((loc.third - chunkStartOffset).coerceIn(0, chunkText.length), "（${a.translation}）", a)
    }
    pendings.sortBy { it.origPos }

    // ── 段首空白归一化删除（始终执行）──
    // 原文段首可能自带半角/全角空格，若保留它们，首行缩进设置就成了“叠加在原文本之上”，
    // 且缩进 0 也无法真正归零。这里始终把段首空白从渲染文本中移除（记录 Deletion 供坐标双向映射），
    // 由 TextIndent 统一施加「正好 firstLineIndent em」的缩进（0 = 真无缩进）。
    val skipFirstIndent = chunkStartOffset > 0 && chapterText.getOrNull(chunkStartOffset - 1) != '\n'
    var q = 0
    while (q < chunkText.length) {
        val nl = chunkText.indexOf('\n', q).let { if (it < 0) chunkText.length else it }
        if (nl > q && !(skipFirstIndent && q == 0)) {
            var w = q
            while (w < nl && chunkText[w].isTxtLeadingWhitespace()) w++
            if (w > q) deletions += Deletion(q, w - q, 0)
        }
        q = nl + 1
    }

    // 插入与删除合并为按原坐标排序的编辑列表（同点先插后删）
    data class Op(val origPos: Int, val insert: Pending?, val delCount: Int)
    val ops = mutableListOf<Op>()
    pendings.forEach { ops += Op(it.origPos, it, 0) }
    deletions.forEach { ops += Op(it.origPos, null, it.count) }
    ops.sortWith(compareBy<Op> { it.origPos }.thenBy { if (it.insert != null) 0 else 1 })

    val sb = StringBuilder(chunkText.length + pendings.size * 24)
    val insertions = mutableListOf<Insertion>()
    val deletionsOut = mutableListOf<Deletion>()
    val translationIns = mutableListOf<Pair<Insertion, Annotation>>()
    var cursor = 0
    for (op in ops) {
        if (op.insert != null) {
            sb.append(chunkText.substring(cursor, op.origPos))
            val rStart = sb.length
            sb.append(op.insert.text)
            val ins = Insertion(op.origPos, rStart, op.insert.text.length, paragraphGap = op.insert.paragraphGap)
            insertions += ins
            if (op.insert.annotation != null) translationIns += ins to op.insert.annotation
            cursor = op.origPos
        } else {
            sb.append(chunkText.substring(cursor, op.origPos))
            deletionsOut += Deletion(op.origPos, op.delCount, sb.length)
            cursor = op.origPos + op.delCount
        }
    }
    sb.append(chunkText.substring(cursor))
    val plain = sb.toString()

    val render = ChunkRender(AnnotatedString(plain), plain.length, insertions, deletionsOut, emptyList())
    val builder = AnnotatedString.Builder(plain)

    // ── 段落样式：每个正文段都显式标记为独立段落 ──
    // 若块从段落中间开始（chunkStartOffset>0 且前一个字符不是 \n），则第一段不缩进
    // 章首第一段（章节标题行）不缩进；段首空白仍会被归一化移除
    var isChapterFirstPara = chunkStartOffset == 0
    segments.forEachIndexed { segIdx, seg ->
        var segStart = seg.start
        if (!(skipFirstIndent && segIdx == 0)) {
            var w = seg.start
            while (w < seg.end && chunkText[w].isTxtLeadingWhitespace()) w++
            segStart = w
        }
        val startR = render.originalToRendered(segStart, atEnd = false)
        val endR = render.originalToRendered(seg.end)
        if (endR > startR) {
            val shouldIndent = firstLineIndent > 0 &&
                !(skipFirstIndent && segIdx == 0) &&
                !isChapterFirstPara
            builder.addStyle(
                ParagraphStyle(
                    textIndent = if (shouldIndent) {
                        TextIndent(firstLine = (fontSize * firstLineIndent).sp, restLine = 0.sp)
                    } else null,
                ),
                startR, endR,
            )
            isChapterFirstPara = false
        }
    }

    // 段距空段：用 ParagraphStyle.lineHeight 精确控制空段高度。
    // 旧实现把 \n 当作空行：StaticLayout 会给以换行结尾的空段额外补一个空行，
    // 于是 0→0.1 时先凭空多出一整行，之后 0.1 的增量才线性加到 lineHeight 上。
    // 现在改用单字符零宽空段，并用 Tight + includeFontPadding=false 强制应用 lineHeight。
    insertions.filter { it.paragraphGap }.forEach { ins ->
        builder.addStyle(
            ParagraphStyle(
                lineHeight = paragraphGapSp.sp,
                lineHeightStyle = LineHeightStyle(
                    alignment = LineHeightStyle.Alignment.Center,
                    trim = LineHeightStyle.Trim.Both,
                    mode = LineHeightStyle.Mode.Tight,
                ),
                platformStyle = PlatformParagraphStyle(includeFontPadding = false),
            ),
            ins.renderedStart, ins.renderedStart + ins.renderedLen,
        )
    }

    // ── 高亮背景（含带颜色的笔记：需求1 加笔记自动附高亮） ──
    marks.filter { it.first.color != null && (it.first.type == AnnotationType.HIGHLIGHT || it.first.type == AnnotationType.NOTE) }.forEach { (a, loc) ->
        val hs = (loc.second - chunkStartOffset).coerceIn(0, chunkLen)
        val he = (loc.third - chunkStartOffset).coerceIn(hs, chunkLen)
        if (he > hs) {
            val s = render.originalToRendered(hs)
            val e = render.originalToRendered(he, atEnd = true)
            if (e > s && e <= plain.length) {
                val base = a.color?.let { Color(it.toInt()) } ?: Color(0xFFFACC15)
                builder.addStyle(SpanStyle(background = base.copy(alpha = 0.40f)), s, e)
            }
        }
    }

    // ── 全文搜索闪烁背景（跳转后 3 秒轻微高亮提示） ──
    flashRange?.let { (fs, fe) ->
        val s = render.originalToRendered((fs - chunkStartOffset).coerceIn(0, chunkLen))
        val e = render.originalToRendered((fe - chunkStartOffset).coerceIn(0, chunkLen), atEnd = true)
        if (e > s && e <= plain.length) {
            builder.addStyle(SpanStyle(background = Color(0xAAFFD54F)), s, e)
        }
    }

    // ── 翻译红字 ──
    val tSpans = translationIns.map { (ins, a) ->
        builder.addStyle(
            SpanStyle(color = translationColor, fontSize = (fontSize - 4).coerceAtLeast(10).sp),
            ins.renderedStart, ins.renderedStart + ins.renderedLen,
        )
        MarkSpan(ins.renderedStart, ins.renderedStart + ins.renderedLen, a)
    }

    return ChunkRender(builder.toAnnotatedString(), plain.length, insertions, deletionsOut, tSpans)
}

// ── TxtViewer Composable ──

/**
 * TXT 阅读视图 — 滚动模式，按分块渲染 LazyColumn item。
 * 每个分块 ~2000 字符，单次 Text 测量 AnnotatedString 不超过 CHUNK_SIZE，
 * 避免 5万+ 字符全章 AnnotatedString 在测量阶段 OOM。
 */
@Composable
fun TxtViewer(
    controller: TxtReaderController,
    settings: ReadSettings,
    annotations: List<Annotation>,
    onCenterTap: () -> Unit = {},
    onHighlightTap: (Annotation, HighlightMenuAnchor) -> Unit = { _, _ -> },
    onTranslationTap: (Annotation) -> Unit = {},
    registerScrollBridge: (((Int) -> Unit) -> Unit)? = null,
    /** 自定义字体（中英解析后的主字体）；null = 按 settings 旧逻辑映射系统字体 */
    overrideFontFamily: FontFamily? = null,
    /** 阅读根容器 LayoutCoordinates（把 root 坐标换算到 item 本地） */
    rootCoords: LayoutCoordinates? = null,
    /** 选区激活：锁定 LazyColumn 滚动 */
    selectionActive: Boolean = false,
    /** 上报 SelectionTarget 给上层 SelectionOverlay */
    onTargetReady: (SelectionTarget?) -> Unit = {},
    /** 点击桥（root 坐标）：返回 true=已处理（翻译/高亮命中），false=交根裁判（切 HUD） */
    registerTapBridge: (((Float, Float) -> Boolean) -> Unit)? = null,
    /** 全文搜索跳转后的闪烁目标（TXT 精确到章内字符区间） */
    searchFlash: SearchFlash? = null,
    modifier: Modifier = Modifier,
) {
    val chapters by controller.chaptersFlow.collectAsState()
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()

    val safeFontSize = settings.fontSize.coerceIn(12, 26)
    val safeLineHeight = settings.lineHeight.coerceIn(1.0f, 3.0f)
    // 上下/左右边距 = 倍率 × 24dp（0–2.0）；首行缩进 0–4 em（取整）
    val safeHorizontalMargin = settings.horizontalMargin.coerceIn(0f, 2f)
    val safeVerticalMargin = settings.verticalMargin.coerceIn(0f, 2f)
    val safeFirstLineIndent = settings.firstLineIndent.coerceIn(0f, 4f).roundToInt().coerceIn(0, 4)
    // 段距空段高度 = 段间距 em × 字号（1.0 = 一个字高；与行高无关，与 EPUB/CSS 的 em 语义一致）
    val paragraphGapSp = safeFontSize * settings.paragraphSpacing.coerceIn(0f, 3f)
    val inkColor = readerInkColor(settings)
    val pageBgColor = readerPageBackground(settings)
    val translationColor = when {
        settings.nightMode || settings.theme == "black" -> inkColor.copy(alpha = 0.65f)
        else -> Color(0xFFDC2626)
    }
    // 双字体限制说明（TXT/Compose）：
    // Compose 文本引擎把 FontFamily 解析为单个 Typeface（按字重/风格匹配），不做逐字形回退，
    // 因此无法像 EPUB 的 CSS 字体栈那样同时应用中英两个下载字体。策略：
    //  - 选了中文字体 → 以中文体为主（中文体自带拉丁字形，缺字走系统回退）；
    //  - 只选英文字体 → 以英文体为主，中文走系统回退；
    //  - 都没选 → 系统衬线（与历史默认一致）。
    // 英文字体在 EPUB 中通过 CSS 字体栈完整生效。
    val fontFamily = overrideFontFamily ?: when (settings.fontFamily) {
        "source_sans" -> FontFamily.SansSerif
        else -> FontFamily.Serif
    }
    val textAlign = if (settings.alignment == "left") TextAlign.Left else TextAlign.Justify

    // 分块总数
    val totalChunks by remember { derivedStateOf { controller.totalChunks() } }

    var restored by remember { mutableStateOf(false) }
    // 进入遮罩：首屏定位完成后淡出，仅覆盖“进入”这一次
    var enterMaskVisible by remember { mutableStateOf(true) }

    // 发送给 VM 的 locator 去重
    var lastReportedLocator by remember { mutableStateOf<String?>(null) }

    // ── 选区命中注册表 + SelectionTarget 实现（P1）──
    val hitRegistry = remember { mutableStateMapOf<Int, ChunkHitEntry>() }
    val latestRootCoords by rememberUpdatedState(rootCoords)

    val selectionTarget = remember(controller, listState) {
        object : SelectionTarget {
            override fun charOffsetAt(rootPos: Offset): Pair<Int, Int>? {
                val rc = latestRootCoords ?: return null
                for (e in hitRegistry.values) {
                    if (!e.coords.isAttached) continue
                    val local = e.coords.localPositionOf(rc, rootPos)
                    if (local.x < 0f || local.y < 0f ||
                        local.x > e.layout.size.width || local.y > e.layout.size.height
                    ) continue
                    val rendered = e.layout.getOffsetForPosition(local).coerceIn(0, e.render.plainLength)
                    val orig = e.render.renderedToOriginal(rendered, e.chunkText.length)
                    return e.chapterIndex to e.chunkStart + orig
                }
                return null
            }

            override fun rectForCharOffset(chapter: Int, offset: Int): Rect? {
                val e = hitRegistry.values.firstOrNull {
                    it.chapterIndex == chapter && offset in it.chunkStart until (it.chunkStart + it.chunkText.length)
                } ?: return null
                if (!e.coords.isAttached) return null
                val rendered = e.render.originalToRendered(offset - e.chunkStart).coerceIn(0, e.render.plainLength)
                val box = e.layout.getBoundingBox(rendered)
                val p = e.coords.localToRoot(Offset(box.left, box.top))
                return Rect(p.x, p.y, p.x + box.width, p.y + box.height)
            }

            override fun selectionRects(chapter: Int, start: Int, end: Int): List<Rect> {
                val out = mutableListOf<Rect>()
                for (e in hitRegistry.values) {
                    if (e.chapterIndex != chapter || !e.coords.isAttached) continue
                    val cEnd = e.chunkStart + e.chunkText.length
                    val lo = max(start, e.chunkStart)
                    val hi = min(end, cEnd)
                    if (hi <= lo) continue
                    val rS = e.render.originalToRendered(lo - e.chunkStart).coerceIn(0, e.render.plainLength)
                    val rE = e.render.originalToRendered(hi - e.chunkStart, atEnd = true).coerceIn(0, e.render.plainLength)
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
                    it.chapterIndex == chapter && offset in it.chunkStart until (it.chunkStart + it.chunkText.length)
                } ?: return null
                if (!e.coords.isAttached) return null
                val rendered = e.render.originalToRendered(offset - e.chunkStart).coerceIn(0, e.render.plainLength)
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
                // 接口语义：deltaPx > 0 = 阅读前进（显示后文）；Compose scrollBy 正值即内容上移=后文
                listState.scrollBy(deltaPx)
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

    // ── 点击桥（root 坐标）：item 内 detectTapGestures 在本 Compose 版本不启动，改由根裁判转发 ──
    // 注意：桥由 LaunchedEffect(Unit) 注册一次，闭包必须经 rememberUpdatedState 读最新值
    // （否则 annotations 更新后桥仍命中空列表 → 高亮/翻译点按永久失效）
    val latestAnnotations by rememberUpdatedState(annotations)
    fun handleTapAt(pos: Offset): Boolean {
        val rc = latestRootCoords ?: return false
        for (e in hitRegistry.values) {
            if (!e.coords.isAttached) continue
            val local = e.coords.localPositionOf(rc, pos)
            if (local.x < 0f || local.y < 0f ||
                local.x > e.layout.size.width || local.y > e.layout.size.height
            ) continue
            val idx = e.layout.getOffsetForPosition(local)
            // 点击点必须落在命中字符的水平包围盒内（避免点在行尾/行首空白也算命中高亮/翻译）
            val hitBox = e.layout.getBoundingBox(idx.coerceIn(0, (e.render.plainLength - 1).coerceAtLeast(0)))
            if (local.x < hitBox.left || local.x > hitBox.right) return false
            // 翻译命中
            val tHit = e.render.translationSpans.firstOrNull { idx in it.start until it.end }
            if (tHit != null) { onTranslationTap(tHit.annotation); return true }
            // 高亮命中（块内渲染坐标 → 章内坐标）
            val orig = e.render.renderedToOriginal(idx, e.chunkText.length)
            val globalOrig = e.chunkStart + orig
            val hHit = latestAnnotations.firstOrNull { a ->
                if (a.color == null || (a.type != AnnotationType.HIGHLIGHT && a.type != AnnotationType.NOTE)) return@firstOrNull false
                val loc = parseMarkLocator(a.locatorJson) ?: return@firstOrNull false
                loc.first == e.chapterIndex && globalOrig in loc.second until loc.third
            }
            if (hHit != null) {
                val c = e.coords
                val anchor = if (c.isAttached) {
                    val line = e.layout.getLineForOffset(idx)
                    val endX = e.layout.getLineRight(line)
                    val p1 = c.localToRoot(Offset(e.layout.getLineLeft(line), e.layout.getLineTop(line)))
                    val p2 = c.localToRoot(Offset(endX, e.layout.getLineBottom(line)))
                    HighlightMenuAnchor(
                        lineRect = Rect(p1.x, p1.y, p2.x, p2.y),
                        lineEnd = Offset(p2.x, (p1.y + p2.y) / 2f),
                    )
                } else HighlightMenuAnchor()
                onHighlightTap(hHit, anchor)
                return true
            }
            return false
        }
        return false
    }
    LaunchedEffect(Unit) {
        registerTapBridge?.invoke { x, y -> handleTapAt(Offset(x, y)) }
    }

    /** 滚动到指定分块 + 块内像素偏移 */
    suspend fun scrollToChunk(chunkIdx: Int, offsetPx: Int = 0) {
        if (totalChunks == 0) return
        val safeIdx = chunkIdx.coerceIn(0, totalChunks - 1)
        listState.scrollToItem(safeIdx, offsetPx)
        var waits = 0
        while (listState.layoutInfo.visibleItemsInfo.none { it.index == safeIdx } && waits < 20) {
            withFrameNanos { }; waits++
        }
    }

    // ── 初始恢复位置：先瞬时定位到目标块，定位完成后再置 restored（配合遮罩门控渲染，避免首帧显示第1章再跳）──
    LaunchedEffect(chapters.size) {
        if (chapters.isEmpty() || restored) return@LaunchedEffect
        val locator = controller.currentLocator.value
        val pos = locator?.let { parsePositionLocator(it) }
        if (pos != null) {
            val ci = controller.chunkIndexOf(pos.first, pos.second)
            scrollToChunk(ci)
        }
        restored = true
        enterMaskVisible = false
    }

    // ── 外部定位（目录/搜索/进度条/笔记跳转）──
    LaunchedEffect(Unit) {
        controller.currentLocator.collect { loc ->
            if (!restored || loc == null || loc == lastReportedLocator) return@collect
            val pos = parsePositionLocator(loc) ?: return@collect
            val ci = controller.chunkIndexOf(pos.first, pos.second)
            scrollToChunk(ci)
        }
    }

    // ── 滚动 → 折算位置 → 上报 controller ──
    LaunchedEffect(listState) {
        snapshotFlow {
            val firstIdx = listState.firstVisibleItemIndex
            val info = listState.layoutInfo.visibleItemsInfo.firstOrNull { it.index == firstIdx }
            Triple(firstIdx, listState.firstVisibleItemScrollOffset, info?.size ?: 0)
        }.collect { (idx, offsetPx, sizePx) ->
            if (!restored || totalChunks == 0 || sizePx <= 0) return@collect
            // 先预登记本次上报将写入的 locator，再写 currentLocator：
            // currentLocator 收集器可能随 StateFlow 写入在主线程内联恢复，若去重键滞后，
            // 滚动上报（含选区边缘自动滚动）会被误判为外部跳转而重复 scrollToChunk 造成视图跳动。
            controller.computeScrollPosition(idx, offsetPx, sizePx)?.let { lastReportedLocator = it.locator }
            controller.updateScrollPosition(idx, offsetPx, sizePx)
        }
    }

    // ── 音量键滚动桥 ──
    LaunchedEffect(Unit) {
        registerScrollBridge?.invoke { direction ->
            scope.launch {
                val viewport = listState.layoutInfo.viewportSize.height
                if (viewport > 0) listState.animateScrollBy(viewport * 0.92f * direction)
            }
        }
    }

    // ── 分块 LazyColumn（外包 Box：恢复定位完成前覆盖同色遮罩，避免“首帧第1章 → 跳转目标位置”闪动）──
    Box(modifier = modifier.fillMaxSize()) {
        LazyColumn(
            state = listState,
            // 选区激活期间锁定滚动（滚动由 SelectionOverlay 的边缘自动滚动接管）
            userScrollEnabled = !selectionActive,
            // 上下边距作用于滚动视口，而不是 contentPadding。
            // 这样文字在滚入/滚出时以边距边界为消失/出现线，与分页模式的显示区域一致；
            // 若放在 contentPadding，边距会跟着内容一起滚走，文字仍会贴到屏幕顶部/底部。
            modifier = Modifier
                .fillMaxSize()
                .padding(top = (24 * safeVerticalMargin).dp, bottom = (24 * safeVerticalMargin).dp),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(
            start = (24 * safeHorizontalMargin).dp, end = (24 * safeHorizontalMargin).dp,
            top = 0.dp, bottom = 0.dp,
        ),
    ) {
        items(
            count = totalChunks,
            key = { flatIdx ->
                val c = controller.chunkAt(flatIdx)
                "ch_${c.chapterIndex}_${c.chunkIndexInChapter}"
            },
        ) { chunkIdx ->
            val chunk = controller.chunkAt(chunkIdx)
            val chapter = chapters.getOrNull(chunk.chapterIndex) ?: return@items
            val chapterText = remember(chapter) {
                controller.fullText.substring(chapter.startOffset, chapter.endOffset)
            }
            val isFirstChunk = chunk.chunkIndexInChapter == 0

            // 章节头（仅每章第一块）
            if (isFirstChunk) {
                Column(Modifier.fillMaxWidth().padding(bottom = 12.dp)) {
                    Text(
                        text = stringResource(R.string.viewer_chapter_label, chunk.chapterIndex + 1),
                        style = MaterialTheme.typography.labelMedium,
                        color = inkColor.copy(alpha = 0.5f),
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = chapter.title,
                        fontFamily = fontFamily,
                        fontSize = (safeFontSize + 7).sp,
                        fontWeight = FontWeight.Bold,
                        lineHeight = (safeFontSize + 7).sp * 1.3f,
                        color = inkColor,
                    )
                    Spacer(Modifier.height(14.dp))
                }
            }

            val chunkText = remember(chapterText, chunk) {
                chapterText.substring(chunk.startOffset, chunk.endOffset)
            }

            val render = remember(
                chunkText, annotations, searchFlash,
                settings.fontSize, settings.paragraphSpacing, settings.firstLineIndent, chunk.startOffset,
            ) {
                val flashRange = searchFlash
                    ?.takeIf { it.txtChapter == chunk.chapterIndex }
                    ?.let { it.txtStart to it.txtEnd }
                buildChunkRender(
                    chapterText = chapterText,
                    chapterIndex = chunk.chapterIndex,
                    chunkStartOffset = chunk.startOffset,
                    chunkEndOffset = chunk.endOffset,
                    annotations = annotations,
                    translationColor = translationColor,
                    fontSize = safeFontSize,
                    paragraphSpacing = settings.paragraphSpacing.coerceIn(0f, 3f),
                    paragraphGapSp = paragraphGapSp,
                    firstLineIndent = safeFirstLineIndent,
                    flashRange = flashRange,
                )
            }

            var layoutResult by remember { mutableStateOf<TextLayoutResult?>(null) }
            var textCoords by remember { mutableStateOf<LayoutCoordinates?>(null) }

            // 本章所有高亮/带色笔记（点按检测用，需要全局坐标）
            val chapterHighlights = remember(annotations, chunk.chapterIndex) {
                annotations.filter {
                    it.color != null && (it.type == AnnotationType.HIGHLIGHT || it.type == AnnotationType.NOTE) &&
                        parseMarkLocator(it.locatorJson)?.first == chunk.chapterIndex
                }
            }
            val latestRender by rememberUpdatedState(render)
            val latestHighlights by rememberUpdatedState(chapterHighlights)

            // ── 选区命中注册（SelectionOverlay 数据源）──
            fun updateRegistry() {
                val lr = layoutResult
                val c = textCoords
                if (lr != null && c != null && c.isAttached) {
                    hitRegistry[chunkIdx] = ChunkHitEntry(
                        chapterIndex = chunk.chapterIndex,
                        chunkStart = chunk.startOffset,
                        chunkText = chunkText,
                        render = latestRender,
                        layout = lr,
                        coords = c,
                    )
                } else {
                    hitRegistry.remove(chunkIdx)
                }
            }
            DisposableEffect(chunkIdx) {
                onDispose { hitRegistry.remove(chunkIdx) }
            }

            Text(
                text = render.rendered,
                fontFamily = fontFamily,
                fontSize = safeFontSize.sp,
                lineHeight = (safeFontSize * safeLineHeight).sp,
                textAlign = textAlign,
                color = inkColor,
                onTextLayout = {
                    layoutResult = it
                    updateRegistry()
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
        // 进入遮罩：定位完成后淡出，仅覆盖“进入”这一次
        AnimatedVisibility(
            visible = enterMaskVisible,
            enter = fadeIn(tween(0)),
            exit = fadeOut(tween(240)),
        ) {
            Box(Modifier.fillMaxSize().background(pageBgColor))
        }
    }
}
