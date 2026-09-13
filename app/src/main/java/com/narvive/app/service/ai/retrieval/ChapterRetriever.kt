package com.narvive.app.service.ai.retrieval

/**
 * 模型选块的调用入口。
 *
 * @param overview 已格式化的概览文本（含编号）
 * @param question 当前提问
 * @param itemCount 概览条数，用于校验模型返回的编号是否越界
 * @param maxPick 最多采纳几条
 *
 * 由调用方负责 Provider 遍历与 Fallback 记账，便于单测注入假实现。
 */
typealias ChunkSelector = suspend (
    overview: String,
    question: String,
    itemCount: Int,
    maxPick: Int,
) -> ChunkPick

/** 概览里线索标签的本地化文案（由 `AiText` 提供，保证跟随界面语言） */
class CueLabels(val namesLabel: String, val timeLabel: String) {
    companion object {
        val NEUTRAL = CueLabels("names", "time")
    }
}

enum class RetrievalMode {
    /** 正文未超预算，原样注入 */
    FULL_TEXT,

    /** 模型从概览中选中块 */
    MODEL_PICK,

    /** 零 token 快路径：问题命中原文独有词，跳过模型 rerank */
    FAST_PATH,

    /** 模型调用失败/输出不可解析，用本地词法兜底 */
    LEXICAL_FALLBACK,

    /** 模型判定「概览即可回答」或本地无任何信号 */
    OVERVIEW_ONLY,

    /** 覆盖式压缩（总结 / 关系图 / 时间线） */
    COVERAGE,
}

class RetrievalStats(
    val chapterChars: Int,
    val estChapterTokens: Int,
    val chunkCount: Int,
    val groupCount: Int,
    val overviewCount: Int,
    val injectedTokens: Int,
    val lexicalMatchedTerms: Int,
    val lexicalTotalTerms: Int,
) {
    val injectedRatio: Double
        get() = if (estChapterTokens == 0) 1.0 else injectedTokens.toDouble() / estChapterTokens

    /** 仅调试用；不进入任何用户可见文案 */
    fun debugLine(mode: RetrievalMode): String =
        "mode=$mode chars=$chapterChars est=${estChapterTokens}tok chunks=$chunkCount groups=$groupCount " +
            "overview=$overviewCount injected=${injectedTokens}tok ratio=${(injectedRatio * 100).toInt()}% " +
            "lex=${lexicalMatchedTerms}/${lexicalTotalTerms}"
}

class QaRetrieval(
    val mode: RetrievalMode,
    /** 概览行（"[3] 摘要 ｜ 人物：A、B"），已含块号 */
    val overviewLines: List<String>,
    /** 注入块的编号（1 基，与正文顺序一致） */
    val pickedLabels: List<Int>,
    /** 与 [pickedLabels] 一一对应的正文片段 */
    val pickedTexts: List<String>,
    val stats: RetrievalStats,
)

/**
 * 章节检索编排：把「分块 → 概览 → 词法 → 模型精排（或快路径）→ 物化正文」串成一条链路。
 *
 * 设计要点：
 * - 纯逻辑类，不依赖 Android/Hilt，模型调用以 [ChunkSelector] 注入 → 所有异常分支都能单测；
 * - 短章节路径**不进入检索**，由调用方直接用全文，保证与旧实现逐字节一致；
 * - 任何一步失败都有明确兜底，且**绝不把故障伪装成「问题较泛」**。
 */
class ChapterRetriever(private val config: ChunkConfig = ChunkConfig()) {

    /** 概览行：单层给子块摘要，多层给父块摘要 + 块号区间 */
    fun overviewLines(text: CharSequence, index: ChapterIndex, labels: CueLabels): List<String> {
        val sb = StringBuilder(256)
        val lines = ArrayList<String>(minOf(index.groups.size, index.chunks.size))
        if (index.singleLevel) {
            index.chunks.forEachIndexed { i, c ->
                sb.setLength(0)
                sb.append('[').append(i + 1).append("] ").append(c.digest)
                appendCue(sb, c, labels)
                lines.add(sb.toString())
            }
        } else {
            index.groups.forEachIndexed { i, g ->
                sb.setLength(0)
                sb.append('[').append(i + 1).append("] (")
                    .append(g.firstIndex + 1).append('-').append(g.lastIndex + 1).append(") ")
                    .append(g.digest)
                appendCue(sb, index.chunks[g.firstIndex], labels, from = g.firstIndex, to = g.lastIndex, all = index.chunks)
                lines.add(sb.toString())
            }
        }
        return lines
    }

    private fun appendCue(
        sb: StringBuilder,
        chunk: TextChunk,
        labels: CueLabels,
        from: Int = -1,
        to: Int = -1,
        all: List<TextChunk>? = null,
    ) {
        val names: List<String>
        val hasTime: Boolean
        if (all == null) {
            names = chunk.names
            hasTime = chunk.hasTime
        } else {
            val set = LinkedHashSet<String>(4)
            var time = false
            for (i in from..to) {
                set.addAll(all[i].names)
                if (all[i].hasTime) time = true
            }
            names = set.toList()
            hasTime = time
        }
        if (names.isEmpty() && !hasTime) return
        sb.append(" ｜ ")
        if (names.isNotEmpty()) {
            sb.append(labels.namesLabel).append('：')
            names.take(4).forEachIndexed { i, n ->
                if (i > 0) sb.append('、')
                sb.append(n)
            }
        }
        if (hasTime) {
            if (names.isNotEmpty()) sb.append('、')
            sb.append(labels.timeLabel)
        }
    }

    /**
     * 问答检索。
     *
     * @param question 当前用户提问（用于词法打分与模型精排）
     */
    suspend fun retrieveQa(
        text: String,
        index: ChapterIndex,
        question: String,
        budget: RetrievalBudget,
        labels: CueLabels,
        isActive: () -> Boolean = { true },
        selector: ChunkSelector,
    ): QaRetrieval {
        val overview = overviewLines(text, index, labels)
        // 词法扫描也有本地预算：超时就放弃词法信号（退化为纯模型 rerank，即旧行为）
        val local = LocalBudget.ofMillis()
        val lexical = LexicalScorer.score(text, index.chunks, question) { isActive() && !local.expired() }
        val maxChunks = chunkLimit(index, budget.qaChunkTokens)

        var mode: RetrievalMode
        var chunkIndices: List<Int>

        val fastPick = fastPathPick(index, lexical, maxChunks)
        if (fastPick != null) {
            mode = RetrievalMode.FAST_PATH
            chunkIndices = fastPick
        } else {
            // 概览可能很长（父块模式），但仍在选择器输入的可接受范围内
            val pickLimit = if (index.singleLevel) maxChunks else maxOf(1, minOf(3, maxChunks / 2))
            val pick = selector(overview.joinToString("\n"), question, overview.size, pickLimit)
            when (pick) {
                is ChunkPick.OverviewEnough -> {
                    mode = RetrievalMode.OVERVIEW_ONLY
                    chunkIndices = emptyList()
                }
                is ChunkPick.Picked -> {
                    mode = RetrievalMode.MODEL_PICK
                    chunkIndices = expandPicks(index, pick.indices, lexical, budget.qaChunkTokens)
                }
                is ChunkPick.Failed -> {
                    // 关键：失败 ≠ 问题较泛。用本地词法兜底，并如实标记降级。
                    val fallback = lexical.topIndices(maxChunks)
                    mode = if (fallback.isEmpty()) RetrievalMode.OVERVIEW_ONLY else RetrievalMode.LEXICAL_FALLBACK
                    chunkIndices = fallback
                }
            }
        }

        // 补块：模型漏选而词法高度可疑时补一块，避免「因为漏选所以答错」
        if (mode == RetrievalMode.MODEL_PICK && lexical.hasSignal) {
            val best = lexical.best
            if (best >= 0 && chunkIndices.none { it == best }) {
                val augmented = (chunkIndices + best).distinct().sorted()
                if (augmented.size <= maxChunks) chunkIndices = augmented
            }
        }

        val materialized = materialize(text, index, chunkIndices, budget.qaChunkTokens)
        val injectedTokens = materialized.sumOf { TokenEstimator.estimate(it.text) }
        val stats = RetrievalStats(
            chapterChars = index.charCount,
            estChapterTokens = index.estTokens,
            chunkCount = index.chunks.size,
            groupCount = index.groups.size,
            overviewCount = overview.size,
            injectedTokens = injectedTokens + TokenEstimator.estimate(overview.joinToString("\n")),
            lexicalMatchedTerms = lexical.matchedTermCount,
            lexicalTotalTerms = lexical.totalTermCount,
        )
        return QaRetrieval(
            mode = mode,
            overviewLines = overview,
            pickedLabels = materialized.map { it.label },
            pickedTexts = materialized.map { it.text },
            stats = stats,
        )
    }

    /** 覆盖式压缩（总结 / 关系图 / 时间线）：一次调用看全章主干 */
    fun coverage(
        text: String,
        index: ChapterIndex,
        budget: RetrievalBudget,
        purpose: CoveragePurpose,
        isActive: () -> Boolean = { true },
    ): CoverageResult = CoverageCompressor.compress(
        text = text,
        index = index,
        budgetTokens = budget.coverageTokens,
        purpose = purpose,
        isActive = isActive,
        budget = LocalBudget.ofMillis(),
    )

    // ── 内部 ──

    private fun chunkLimit(index: ChapterIndex, qaChunkTokens: Int): Int =
        (qaChunkTokens / index.avgChunkTokens).coerceIn(2, MAX_PICKED_CHUNKS)

    /**
     * 零 token 快路径：仅当问题命中了**原文独有词**且分数一枝独秀时跳过模型 rerank。
     *
     * 宁可不触发，也不要选错块：要求同时满足
     * 1. 命中至少一个稀有词（全局只出现在极少数块里）；
     * 2. 最好块分数 ≥ 阈值，且明显高于第二块；
     * 3. 稀有词足够长（拉丁词 ≥ 4 字母），排除「he」「of」这类偶然命中。
     */
    private fun fastPathPick(index: ChapterIndex, lexical: LexicalScorer.Result, maxChunks: Int): List<Int>? {
        if (!lexical.hasSignal) return null
        val best = lexical.best
        val rare = lexical.rareByChunk[best] ?: return null
        val strongRare = rare.any { term ->
            val isLatin = term.all { it.code < 128 }
            if (isLatin) term.length >= 4 else term.length >= 2
        }
        if (!strongRare) return null
        val bestScore = lexical.bestScore
        if (bestScore < FAST_PATH_MIN_SCORE) return null
        val second = lexical.secondScore()
        if (second > 0.0 && bestScore < second * FAST_PATH_MARGIN) return null
        val picked = lexical.topIndices(maxChunks)
        return picked.ifEmpty { null }
    }

    /** 单层：pick 就是块号；多层：pick 是父块号，展开为其子块并按预算裁剪 */
    private fun expandPicks(
        index: ChapterIndex,
        picks: List<Int>,
        lexical: LexicalScorer.Result,
        qaChunkTokens: Int,
    ): List<Int> {
        if (index.singleLevel) return picks.distinct().sorted()
        val perGroup = (qaChunkTokens / picks.size.coerceAtLeast(1))
        val out = LinkedHashSet<Int>()
        for (p in picks) {
            val g = index.groups.getOrNull(p) ?: continue
            val quota = (perGroup / index.avgChunkTokens).coerceAtLeast(1)
            val range = g.firstIndex..g.lastIndex
            val anchor = range.maxByOrNull { lexical.scoreOf(it) }?.takeIf { lexical.scoreOf(it) > 0.0 }
                ?: g.firstIndex
            var taken = 0
            var fwd = anchor
            while (taken < quota && fwd <= g.lastIndex) {
                out.add(fwd)
                taken++
                fwd++
            }
            var back = anchor - 1
            while (taken < quota && back >= g.firstIndex) {
                out.add(back)
                taken++
                back--
            }
        }
        return out.sorted()
    }

    private class Materialized(val label: Int, val text: String)

    /**
     * 物化选中的块：按 [ChunkConfig.overlapRatio] 向两侧扩一点上下文（落在句子边界之外也无妨，
     * 因为块本身就是按句切的），并保证扩出来的区间互不重叠、总 token 不超预算。
     */
    private fun materialize(
        text: String,
        index: ChapterIndex,
        indices: List<Int>,
        qaChunkTokens: Int,
    ): List<Materialized> {
        if (indices.isEmpty()) return emptyList()
        val out = ArrayList<Materialized>(indices.size)
        var lastEnd = 0
        var tokensSoFar = 0
        val tokenCap = (qaChunkTokens * 1.25).toInt()
        for (idx in indices.sorted()) {
            val c = index.chunks.getOrNull(idx) ?: continue
            val pad = (c.length * config.overlapRatio).toInt()
            val start = maxOf(c.start - pad, lastEnd).coerceIn(0, text.length)
            val end = minOf(c.end + pad, text.length)
            if (end <= start) continue
            val piece = text.substring(start, end)
            val t = TokenEstimator.estimate(piece)
            if (tokensSoFar + t > tokenCap && out.isNotEmpty()) continue
            tokensSoFar += t
            lastEnd = end
            out.add(Materialized(idx + 1, piece))
        }
        return out
    }

    private companion object {
        const val MAX_PICKED_CHUNKS = 6
        const val FAST_PATH_MIN_SCORE = 1.2
        const val FAST_PATH_MARGIN = 1.6
    }
}
