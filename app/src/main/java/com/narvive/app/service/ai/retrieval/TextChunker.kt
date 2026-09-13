package com.narvive.app.service.ai.retrieval

/**
 * 零拷贝的正文区间视图：分块、概览、抽取都只持有 (start, end)，不复制正文字符串。
 *
 * 动机：长章节正文可达数十万字符，若为每块都 `substring` 一份，内存直接翻倍，
 * 低配机上会明显吃紧。只有在真正要注入模型时才把选中的块 `toString()`。
 */
class SubText(
    private val base: CharSequence,
    private val from: Int,
    private val to: Int,
) : CharSequence {
    override val length: Int get() = (to - from).coerceAtLeast(0)
    override fun get(index: Int): Char = base[from + index]
    override fun subSequence(startIndex: Int, endIndex: Int): CharSequence =
        SubText(base, from + startIndex, from + endIndex)

    override fun toString(): String =
        if (length == 0) "" else base.subSequence(from, to).toString()
}

/**
 * 子块：检索与注入的最小单位。**只存区间**，正文留在原字符串里。
 *
 * @param digest 本地生成的块摘要（首句 + 收尾片段），用于概览；零 token 成本
 * @param names 本块出现的候选人物（引用共享字符串，不额外占内存）
 * @param hasTime 本块是否含时间表达
 *
 * 线索只存**数据**不存文案：这里的词会进入提示词，必须跟随界面语言，
 * 因此拼装交给 `AiText` 一侧完成。
 */
class TextChunk(
    val start: Int,
    val end: Int,
    val digest: String,
    val names: List<String>,
    val hasTime: Boolean,
) {
    val length: Int get() = end - start
}

/** 父块：一组连续子块，用于把「给模型看的概览条数」控制在上限内 */
class ChunkGroup(
    val firstIndex: Int,
    val lastIndex: Int,
    val digest: String,
) {
    val size: Int get() = lastIndex - firstIndex + 1
}

/** 章节索引：分块、概览、句界、词法所需的全部本地结构，可按正文哈希缓存 */
class ChapterIndex(
    val charCount: Int,
    val estTokens: Int,
    val cjkRatio: Double,
    val chunks: List<TextChunk>,
    val groups: List<ChunkGroup>,
    /** 句界（扁平数组 [s0,e0,s1,e1,…]），覆盖式压缩复用，避免二次切句 */
    val sentenceRanges: IntArray,
    /** 本地抽出的人物候选（供关系图压缩与线索标签使用） */
    val nameCandidates: List<String>,
) {
    /** 单块平均 token，用于把 token 预算换算成块数 */
    val avgChunkTokens: Int =
        if (chunks.isEmpty()) 1 else (estTokens / chunks.size).coerceAtLeast(1)

    /** 是否单层：块数已在上限内，模型直接看子块概览 */
    val singleLevel: Boolean get() = groups.size == chunks.size
}

/** 分块参数。默认值面向「章节正文」这一粒度，可按用途覆盖 */
data class ChunkConfig(
    /** 目标块大小（token）：对齐主流 RAG 的 1000~2000 token 区间 */
    val targetTokens: Int = 1_500,
    /** 硬上限（token）：单块不得超过，避免一段超长正文撑爆预算 */
    val maxTokens: Int = 2_200,
    /** 注入时的上下文重叠比例（按句对齐），避免答案正好被切断在块边界 */
    val overlapRatio: Double = 0.12,
    /** 给模型看的概览条数上限；超过则启用父块分组 */
    val maxGroups: Int = 40,
    /** 父块最少包含几个子块 */
    val minGroupSize: Int = 4,
    /** 子块摘要字符数上限 */
    val digestChars: Int = 88,
)

/**
 * 分块器：**不依赖换行**，按「段落 → 句末标点 → 硬切」对齐语义边界。
 *
 * 这一点是必须的：EPUB 取出的正文在阅读器里已被压成单个空格
 * （`EpubReaderController.extractChapterText` 的 `\s+ → " "`），
 * 旧实现按 `\n` 切段会让整章退化成 1 块，检索直接失效。
 * 这里改为按句切分，因此对 TXT / EPUB / 未来格式的表现一致，
 * 同时**不需要改动阅读器的取文逻辑**（不影响阅读进度、搜索与字数统计）。
 */
object TextChunker {

    fun index(
        text: CharSequence,
        config: ChunkConfig = ChunkConfig(),
        budget: LocalBudget = LocalBudget.UNLIMITED,
    ): ChapterIndex {
        val cjkRatio = TokenEstimator.cjkRatio(text)
        val perChar = TokenEstimator.tokensPerChar(cjkRatio)
        val sentences = SentenceSplitter.ranges(text)
        val cores = buildCores(sentences, config, perChar)
        // 人名候选只影响概览线索与压缩取舍，超时就放弃，不拖慢整体
        val names = if (budget.expired()) emptyList() else NameCandidates.top(text, budget = budget)

        val chunks = cores.map { (s, e) ->
            val (hitNames, hasTime) = cuesOf(text, s, e, names)
            TextChunk(
                start = s,
                end = e,
                digest = digestOf(text, s, e, config.digestChars),
                names = hitNames,
                hasTime = hasTime,
            )
        }
        val groups = buildGroups(chunks, config)
        val tokens = (text.length * perChar).toInt().coerceAtLeast(1)
        return ChapterIndex(
            charCount = text.length,
            estTokens = tokens,
            cjkRatio = cjkRatio,
            chunks = chunks,
            groups = groups,
            sentenceRanges = sentences,
            nameCandidates = names,
        )
    }

    /**
     * 把句子聚成块。保证：
     * 1. 每块至少一句；
     * 2. 每块不超过 [ChunkConfig.maxTokens]（句本身已被 [SentenceSplitter] 限长）；
     * 3. 只有一个句子的超长输入（如整章无标点）也能被切成多块。
     */
    private fun buildCores(
        sentences: IntArray,
        config: ChunkConfig,
        perChar: Double,
    ): List<Pair<Int, Int>> {
        if (sentences.isEmpty()) return emptyList()
        val sentenceCount = sentences.size / 2
        val out = ArrayList<Pair<Int, Int>>(sentenceCount / 3 + 4)
        val targetChars = (config.targetTokens / perChar).toInt().coerceAtLeast(200)
        val maxChars = (config.maxTokens / perChar).toInt().coerceAtLeast(targetChars)

        var blockStart = -1
        var blockEnd = -1
        var i = 0
        while (i < sentenceCount) {
            val s = sentences[i * 2]
            val e = sentences[i * 2 + 1]
            if (blockStart < 0) {
                blockStart = s
                blockEnd = e
            } else {
                val wouldBe = e - blockStart
                // 超过硬上限：先收一块，再以当前句开新块
                if (wouldBe > maxChars && blockEnd > blockStart) {
                    out.add(blockStart to blockEnd)
                    blockStart = s
                    blockEnd = e
                } else {
                    blockEnd = e
                }
            }
            i++
            // 到达目标大小即收块（下一句开新块）
            if (blockEnd - blockStart >= targetChars && i < sentenceCount) {
                out.add(blockStart to blockEnd)
                blockStart = -1
                blockEnd = -1
            }
        }
        if (blockStart in 0 until blockEnd) out.add(blockStart to blockEnd)
        return out
    }

    private fun buildGroups(chunks: List<TextChunk>, config: ChunkConfig): List<ChunkGroup> {
        if (chunks.isEmpty()) return emptyList()
        if (chunks.size <= config.maxGroups) {
            return chunks.indices.map { i ->
                ChunkGroup(i, i, chunks[i].digest)
            }
        }
        val size = maxOf(
            config.minGroupSize,
            (chunks.size + config.maxGroups - 1) / config.maxGroups,
        )
        val groups = ArrayList<ChunkGroup>(chunks.size / size + 1)
        var i = 0
        while (i < chunks.size) {
            val last = minOf(i + size - 1, chunks.lastIndex)
            groups.add(ChunkGroup(i, last, groupDigest(chunks, i, last)))
            i = last + 1
        }
        return groups
    }

    /**
     * 父块摘要：拼接成员块摘要的前若干字。
     *
     * 只取每个成员的前 36 字——父块摘要的作用是「让模型判断这一段要不要读」，
     * 不是代替子块摘要；过长会把选择器的输入 token 又推回去。
     */
    private fun groupDigest(chunks: List<TextChunk>, first: Int, last: Int): String {
        val sb = StringBuilder(256)
        val shown = minOf(last, first + MAX_GROUP_MEMBERS - 1)
        for (i in first..shown) {
            if (sb.isNotEmpty()) sb.append(" / ")
            sb.append(chunks[i].digest.take(GROUP_MEMBER_CHARS))
        }
        if (shown < last) sb.append(" / …")
        return sb.toString()
    }

    /** 块摘要：首句（截断）+ 末句片段，均为纯本地计算 */
    private fun digestOf(text: CharSequence, start: Int, end: Int, limit: Int): String {
        val head = SubText(text, start, minOf(end, start + limit * 2))
        val first = SentenceSplitter.firstSentenceEnd(head)
        val headText = if (first > 0) head.subSequence(0, minOf(first, limit)) else head.subSequence(0, minOf(head.length, limit))
        var s = headText.toString().replace(WHITESPACE_RUN, " ").trim()
        if (s.length >= limit) s = s.take(limit)
        // 末句只在还剩空间时补，避免摘要过长
        if (s.length < limit - 24 && end - start > limit * 3) {
            val tailLen = minOf(end - start, limit)
            val tail = SubText(text, maxOf(start, end - tailLen), end)
                .toString().replace(WHITESPACE_RUN, " ").trim()
            if (tail.isNotEmpty() && !s.endsWith(tail.take(12))) {
                val room = limit - s.length - 3
                if (room > 8) s = s + " … " + tail.takeLast(room)
            }
        }
        return s
    }

    /** 块线索：本块出现了哪些本地候选人物、是否含时间表达 */
    private fun cuesOf(
        text: CharSequence,
        start: Int,
        end: Int,
        names: List<String>,
    ): Pair<List<String>, Boolean> {
        if (names.isEmpty()) return emptyList<String>() to false
        val view = SubText(text, start, end)
        val hits = ArrayList<String>(2)
        for (n in names) {
            if (indexOf(view, n) >= 0) {
                hits.add(n)
                if (hits.size >= MAX_CUE_NAMES) break
            }
        }
        return hits to TIME_HINT.containsMatchIn(view)
    }

    private fun indexOf(haystack: CharSequence, needle: String): Int {
        if (needle.isEmpty() || haystack.length < needle.length) return -1
        val limit = haystack.length - needle.length
        var i = 0
        while (i <= limit) {
            var j = 0
            while (j < needle.length && haystack[i + j] == needle[j]) j++
            if (j == needle.length) return i
            i++
        }
        return -1
    }

    private val WHITESPACE_RUN = Regex("[\\s\\u3000]+")
    private val TIME_HINT = Regex(
        "(\\d{2,4}\\s*年|\\d{1,2}\\s*月|\\d{1,2}\\s*日|第[零一二三四五六七八九十百千0-9]+[天日夜年月]|" +
            "[春夏秋冬]天|十年前|三年后|翌日|次日|当晚|第二天|" +
            "\\b(\\d{4}|\\d{1,2}(st|nd|rd|th))\\b|\\b(morning|evening|night|dawn|dusk|years? later|next day)\\b)",
        RegexOption.IGNORE_CASE,
    )
    private const val MAX_GROUP_MEMBERS = 8
    private const val GROUP_MEMBER_CHARS = 36
    private const val MAX_CUE_NAMES = 4
}

/**
 * 句子切分：一次线性扫描，返回扁平区间数组（[s0,e0,s1,e1,…]）。
 *
 * 用扁平 IntArray 而不是 Pair 列表：20 万字章节约 4000 句，
 * 对象列表会带来上万次小对象分配，扁平数组只有 32KB。
 */
object SentenceSplitter {

    /** 无标点长句的强制切分长度（字符） */
    private const val MAX_SENTENCE_CHARS = 360

    private const val CLOSERS = "”’」』）)】》〉…"

    fun ranges(text: CharSequence): IntArray {
        val out = ArrayList<Int>(256)
        val n = text.length
        var start = -1
        var i = 0
        while (i < n) {
            val ch = text[i]
            if (start < 0) {
                if (ch.isWhitespace() || ch == '\u3000') {
                    i++
                    continue
                }
                start = i
            }
            if (isTerminator(text, i)) {
                var end = i + 1
                while (end < n && CLOSERS.indexOf(text[end]) >= 0) end++
                out.add(start)
                out.add(end)
                start = -1
                i = end
                continue
            }
            if (i - start >= MAX_SENTENCE_CHARS) {
                val cut = lastBreak(text, start, i)
                out.add(start)
                out.add(cut)
                start = -1
                i = cut
                continue
            }
            i++
        }
        if (start in 0 until n) {
            var e = n
            while (e > start && (text[e - 1].isWhitespace() || text[e - 1] == '\u3000')) e--
            if (e > start) {
                out.add(start)
                out.add(e)
            }
        }
        return out.toIntArray()
    }

    /** 视图内首句的结束位置（相对偏移）；无终止符时返回 0 */
    fun firstSentenceEnd(view: CharSequence): Int {
        for (i in view.indices) {
            if (isTerminator(view, i)) {
                var end = i + 1
                while (end < view.length && CLOSERS.indexOf(view[end]) >= 0) end++
                return end
            }
        }
        return 0
    }

    private fun isTerminator(text: CharSequence, i: Int): Boolean = when (text[i]) {
        '\n', '\r' -> true
        '。', '！', '？', '；', '…', '!', '?', ';' -> true
        '.' -> isLatinPeriod(text, i)
        else -> false
    }

    /** 英文句点：排除小数与常见缩写，且要求后面是空白或结尾 */
    private fun isLatinPeriod(text: CharSequence, i: Int): Boolean {
        val prev = if (i > 0) text[i - 1] else ' '
        val next = if (i + 1 < text.length) text[i + 1] else ' '
        if (prev.isDigit() && next.isDigit()) return false
        if (!(next == ' ' || next == '\n' || next == '\r' || next == '\t' || i + 1 >= text.length)) return false
        // Mr. / Dr. / St. 一类缩写
        if (i >= 2) {
            val c1 = text[i - 1].lowercaseChar()
            val c2 = text[i - 2].lowercaseChar()
            if ((c1 == 'r' && c2 == 'm') || (c1 == 'r' && c2 == 'd') || (c1 == 't' && c2 == 's')) return false
        }
        return true
    }

    private fun lastBreak(text: CharSequence, start: Int, i: Int): Int {
        var k = i - 1
        while (k > start + 20) {
            val c = text[k]
            if (c == '，' || c == ',' || c == '、' || c == ' ' || c == '\u3000') return k + 1
            k--
        }
        return i
    }
}
