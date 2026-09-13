package com.narvive.app.service.ai.retrieval

/** 覆盖型任务的用途：决定「哪些句子值得留」 */
enum class CoveragePurpose { SUMMARY, GRAPH, TIMELINE }

class CoverageResult(
    val text: String,
    val keptSentences: Int,
    val totalSentences: Int,
    val keptChars: Int,
    val originalChars: Int,
    /** true = 已压到预算上限（说明仍有内容被丢弃） */
    val budgetHit: Boolean,
    /** true = 本地时间预算用尽，走了「每块首句」的轻量模式（覆盖性仍在，句子挑选变粗） */
    val lightweight: Boolean = false,
) {
    val keptRatio: Double
        get() = if (originalChars == 0) 1.0 else keptChars.toDouble() / originalChars
}

/**
 * 覆盖式本地压缩：把整章压到 token 预算内，**但每一块都至少留一句**。
 *
 * 与「top-k 选择」的本质区别：选择会丢掉 88% 的内容并让模型假装读完了；
 * 压缩是等比例稀释——全章情节主干都在，只是句子被抽稀，模型一次调用即可看到全弧线。
 *
 * 与「map-reduce 逐块摘要」的区别：调用次数从 N+1 降到 1，速度与 token 都显著更优，
 * 代价是抽取式启发会丢掉一部分句子（因此问答链路仍走精读，不依赖压缩质量）。
 *
 * 纯本地计算：两次线性扫描 + 一次排序，20 万字章节约 30~120ms（中端机），可取消。
 */
object CoverageCompressor {

    /** 省略处标记：中英皆可读 */
    private const val OMIT = "\n……\n"

    private const val DIALOGUE_CHARS = "“”「」『』\""

    private val TIME_EXPR = Regex(
        "(\\d{2,4}\\s*年|\\d{1,2}\\s*月\\d{1,2}\\s*日|第[零一二三四五六七八九十百千0-9]+[天日夜年月]|" +
            "十年[前后]|[一二三四五六七八九十]年[前后]|翌日|次日|当晚|第二天|次日清晨|" +
            "\\b(\\d{4}|\\d{1,2}(st|nd|rd|th))\\b|\\b(morning|evening|night|dawn|dusk|years? later|next day|that night)\\b)",
        RegexOption.IGNORE_CASE,
    )

    fun compress(
        text: CharSequence,
        index: ChapterIndex,
        budgetTokens: Int,
        purpose: CoveragePurpose,
        isActive: () -> Boolean = { true },
        budget: LocalBudget = LocalBudget.UNLIMITED,
    ): CoverageResult {
        val ranges = index.sentenceRanges
        val sentenceCount = ranges.size / 2
        if (sentenceCount == 0) {
            return CoverageResult(text.toString(), 0, 0, text.length, text.length, false)
        }
        val perChar = TokenEstimator.tokensPerChar(index.cjkRatio)
        val totalTokens = (text.length * perChar).toInt().coerceAtLeast(1)
        if (totalTokens <= budgetTokens) {
            return CoverageResult(text.toString(), sentenceCount, sentenceCount, text.length, text.length, false)
        }

        // ── 1. 逐句打分（本地时间预算用尽则转入轻量模式） ──
        val scores = DoubleArray(sentenceCount)
        val tokens = IntArray(sentenceCount)
        val isChunkFirst = BooleanArray(sentenceCount)
        markChunkFirsts(ranges, index, isChunkFirst)

        val names = index.nameCandidates
        var lightweight = false
        var i = 0
        while (i < sentenceCount) {
            if (i % 512 == 0) {
                if (!isActive()) {
                    // 协程被取消（用户已离开）：返回原文，结果不会被使用
                    return CoverageResult(text.toString(), sentenceCount, sentenceCount, text.length, text.length, true)
                }
                if (budget.expired()) {
                    lightweight = true
                    break
                }
            }
            val s = ranges[i * 2]
            val e = ranges[i * 2 + 1]
            val len = e - s
            tokens[i] = maxOf(1, (len * perChar).toInt())
            val view = SubText(text, s, e)
            var sc = 0.0
            if (isChunkFirst[i]) sc += 1.0
            if (hasDialogue(view)) sc += 1.0
            var nameHits = 0
            for (n in names) {
                if (contains(view, n)) nameHits++
                if (nameHits >= 3) break
            }
            if (nameHits > 0) sc += 2.5 * minOf(nameHits, 2) / 2.0 + 1.25
            if (TIME_EXPR.containsMatchIn(view)) sc += 1.5
            if (len in 14..160) sc += 0.5
            sc += minOf(len, 200) * 0.002
            scores[i] = sc
            i++
        }

        // ── 2. 用途过滤 ──
        val eligible = BooleanArray(sentenceCount) { true }
        when (purpose) {
            CoveragePurpose.SUMMARY -> Unit
            CoveragePurpose.GRAPH -> {
                var kept = 0L
                for (k in 0 until sentenceCount) {
                    val v = SubText(text, ranges[k * 2], ranges[k * 2 + 1])
                    val hit = hasDialogue(v) || names.any { contains(v, it) }
                    eligible[k] = hit
                    if (hit) kept += tokens[k]
                }
                // 过滤后剩得太少（例如全篇第一人称叙述）则退回总结策略，避免注入空内容
                if (kept < budgetTokens * 0.3) eligible.fill(true)
            }
            CoveragePurpose.TIMELINE -> {
                var kept = 0L
                for (k in 0 until sentenceCount) {
                    val v = SubText(text, ranges[k * 2], ranges[k * 2 + 1])
                    val hit = TIME_EXPR.containsMatchIn(v)
                    eligible[k] = hit
                    if (hit) kept += tokens[k]
                }
                if (kept < budgetTokens * 0.2) eligible.fill(true)
            }
        }

        // ── 3. 选取：先保每块一句，再按分数填满预算 ──
        val chosen = BooleanArray(sentenceCount)
        var used = 0
        if (lightweight) {
            // 轻量模式：跳过用途过滤与逐句打分，只保留每块的**首句**。
            // 覆盖性仍然成立（每块都有内容进上下文），代价是句子挑选变粗；
            // 这条路径只在低配机处理超长章节、本地预算用尽时才会走到。
            for (k in 0 until sentenceCount) {
                if (!isChunkFirst[k]) continue
                val t = sentenceTokens(ranges, k, perChar)
                if (used > 0 && used + t > budgetTokens) continue
                chosen[k] = true
                used += t
            }
            return finish(text, ranges, chosen, sentenceCount, used, budgetHit = used >= budgetTokens, lightweight = true)
        }

        // 块与句都按位置有序，用移动游标避免「每块从头扫一遍句界」
        var cursor = 0
        for (c in index.chunks.indices) {
            if (used >= budgetTokens) break
            val chunk = index.chunks[c]
            while (cursor < sentenceCount && ranges[cursor * 2] < chunk.start) cursor++
            var best = -1
            var bestScore = -1.0
            var k = cursor
            while (k < sentenceCount && ranges[k * 2] < chunk.end) {
                if (eligible[k] && !chosen[k] && scores[k] > bestScore) {
                    bestScore = scores[k]
                    best = k
                }
                k++
            }
            if (best >= 0) {
                chosen[best] = true
                used += tokens[best]
            }
        }

        val order = (0 until sentenceCount)
            .filter { eligible[it] && !chosen[it] }
            .sortedByDescending { scores[it] }
        for (k in order) {
            if (used >= budgetTokens) break
            chosen[k] = true
            used += tokens[k]
        }

        // ── 4. 邻句扩展：让片段读起来连贯（仅在预算尚有余量时） ──
        val softBudget = (budgetTokens * 0.9).toInt()
        for (k in 0 until sentenceCount - 1) {
            if (used >= softBudget) break
            if (chosen[k] && !chosen[k + 1] && eligible[k + 1]) {
                chosen[k + 1] = true
                used += tokens[k + 1]
            }
        }

        // ── 5. 按原文顺序拼接，省略处插入标记 ──
        return finish(text, ranges, chosen, sentenceCount, used, budgetHit = used >= budgetTokens, lightweight = false)
    }

    private fun sentenceTokens(ranges: IntArray, k: Int, perChar: Double): Int {
        val len = ranges[k * 2 + 1] - ranges[k * 2]
        return maxOf(1, (len * perChar).toInt())
    }

    /** 按原文顺序拼接选中的句子，省略处插入标记 */
    private fun finish(
        text: CharSequence,
        ranges: IntArray,
        chosen: BooleanArray,
        sentenceCount: Int,
        used: Int,
        budgetHit: Boolean,
        lightweight: Boolean,
    ): CoverageResult {
        val sb = StringBuilder(minOf(text.length, used * 2 + 64))
        var kept = 0
        var lastEnd = -1
        for (k in 0 until sentenceCount) {
            if (!chosen[k]) continue
            val s = ranges[k * 2]
            val e = ranges[k * 2 + 1]
            if (lastEnd >= 0 && s > lastEnd) sb.append(OMIT) else if (sb.isNotEmpty()) sb.append('\n')
            sb.append(text, s, e)
            lastEnd = e
            kept++
        }
        val out = sb.toString().trim()
        return CoverageResult(
            text = out,
            keptSentences = kept,
            totalSentences = sentenceCount,
            keptChars = out.length,
            originalChars = text.length,
            budgetHit = budgetHit,
            lightweight = lightweight,
        )
    }

    /**
     * 标记每块的首句。
     *
     * 句界与块都是有序的，因此这里是一次线性归并，而不是对每块都从头扫一遍句界——
     * 后者在 20 万字章节（约 4000 句 × 100 块）上会变成 40 万次比较。
     */
    private fun markChunkFirsts(ranges: IntArray, index: ChapterIndex, out: BooleanArray) {
        val n = ranges.size / 2
        var k = 0
        for (c in index.chunks) {
            while (k < n && ranges[k * 2] < c.start) k++
            if (k < n) out[k] = true
        }
    }

    private fun hasDialogue(view: CharSequence): Boolean {
        for (i in view.indices) {
            if (DIALOGUE_CHARS.indexOf(view[i]) >= 0) return true
        }
        return false
    }

    private fun contains(haystack: CharSequence, needle: String): Boolean {
        if (needle.isEmpty() || haystack.length < needle.length) return false
        val limit = haystack.length - needle.length
        var i = 0
        while (i <= limit) {
            var j = 0
            while (j < needle.length && haystack[i + j] == needle[j]) j++
            if (j == needle.length) return true
            i++
        }
        return false
    }
}
