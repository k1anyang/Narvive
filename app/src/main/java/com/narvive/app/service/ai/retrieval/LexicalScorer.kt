package com.narvive.app.service.ai.retrieval

/**
 * 词法打分（BM25-lite）：**查询驱动、单遍扫描、不建持久索引**。
 *
 * 这是为「低配手机 + 超长章节」专门选的形态：
 * - 不构建全章倒排索引（20 万字的中文 bigram 索引在手机上要几十 MB），
 *   而是只统计**查询词**在每块里的词频：内存 O(查询词数 × 块数)，通常几十 KB；
 * - 时间 O(章节字符数) 一次线性扫描，20 万字约 10~30ms（中端机），可随时取消；
 * - 结果提供两个用途：① 模型 rerank 失败时的兜底；② 判断能否走「零 token 快路径」。
 *
 * 中文按字符 bigram 切分（无需分词器，对未登录的人名/专名同样有效），
 * 拉丁按小写单词切分。查询与正文使用完全相同的切分规则，保证可比。
 */
object LexicalScorer {

    private const val K1 = 1.2
    private const val B = 0.75
    private const val MAX_QUERY_TERMS = 128
    private const val RARE_DF_RATIO = 0.15
    private const val MAX_RARE_PER_CHUNK = 3

    class Result(
        private val scores: DoubleArray,
        /** 块索引按分数降序 */
        val order: IntArray,
        /** 命中「稀有词」的块 → 稀有词列表（快路径判定用） */
        val rareByChunk: Map<Int, List<String>>,
        val matchedTermCount: Int,
        val totalTermCount: Int,
    ) {
        val best: Int get() = if (order.isEmpty()) -1 else order[0]
        val bestScore: Double get() = if (best < 0) 0.0 else scores[best]

        fun scoreOf(index: Int): Double = if (index in scores.indices) scores[index] else 0.0

        /** 第二高分，用于判断「是否一枝独秀」 */
        fun secondScore(): Double {
            for (i in 1 until order.size) {
                val s = scores[order[i]]
                if (s > 0.0) return s
            }
            return 0.0
        }

        fun topIndices(limit: Int): List<Int> =
            order.asSequence().filter { scores[it] > 0.0 }.take(limit).toList()

        val hasSignal: Boolean get() = best >= 0 && bestScore > 0.0

        companion object {
            val EMPTY = Result(DoubleArray(0), IntArray(0), emptyMap(), 0, 0)
        }
    }

    fun score(
        text: CharSequence,
        chunks: List<TextChunk>,
        query: String,
        isActive: () -> Boolean = { true },
    ): Result {
        if (text.isEmpty() || chunks.isEmpty() || query.isBlank()) return Result.EMPTY
        val terms = tokenizeQuery(query)
        if (terms.isEmpty()) return Result.EMPTY

        val termIndex = HashMap<String, Int>(terms.size * 2)
        terms.forEachIndexed { i, t -> termIndex[t] = i }

        val n = chunks.size
        val tf = Array(terms.size) { IntArray(n) }
        val docLen = IntArray(n)
        val queryHits = BooleanArray(terms.size)

        var chunkIdx = 0
        var i = 0
        val len = text.length
        var sinceCheck = 0
        while (i < len) {
            if (++sinceCheck >= CHECK_INTERVAL) {
                sinceCheck = 0
                if (!isActive()) return Result.EMPTY
            }
            // 块是互不重叠的核心区间，按位置推进指针即可，无需二分
            while (chunkIdx < n && i >= chunks[chunkIdx].end) chunkIdx++
            if (chunkIdx >= n) break
            if (i < chunks[chunkIdx].start) {
                i = chunks[chunkIdx].start
                continue
            }
            val ch = text[i]
            val cjk = TokenEstimator.isCjk(ch)
            if (cjk) {
                docLen[chunkIdx]++
                if (i + 1 < len && TokenEstimator.isCjk(text[i + 1])) {
                    val t = termIndex[bigramKey(ch, text[i + 1])]
                    if (t != null) {
                        tf[t][chunkIdx]++
                        queryHits[t] = true
                    }
                }
                i++
            } else if (ch.isLetterOrDigit()) {
                var j = i
                while (j < len && text[j].isLetterOrDigit() && !TokenEstimator.isCjk(text[j])) j++
                if (j - i >= 2) {
                    docLen[chunkIdx]++
                    val t = termIndex[wordKey(text, i, j)]
                    if (t != null) {
                        tf[t][chunkIdx]++
                        queryHits[t] = true
                    }
                }
                i = j
            } else {
                i++
            }
        }

        var totalLen = 0
        for (l in docLen) totalLen += l
        val avgLen = if (n == 0) 1.0 else (totalLen.toDouble() / n).coerceAtLeast(1.0)

        val scores = DoubleArray(n)
        var matchedTerms = 0
        for (t in terms.indices) {
            if (!queryHits[t]) continue
            var df = 0
            for (c in 0 until n) if (tf[t][c] > 0) df++
            if (df == 0) continue
            matchedTerms++
            val idf = Math.log(1.0 + (n - df + 0.5) / (df + 0.5))
            val row = tf[t]
            for (c in 0 until n) {
                val f = row[c]
                if (f == 0) continue
                val norm = 1.0 - B + B * (docLen[c] / avgLen)
                scores[c] += idf * (f * (K1 + 1.0)) / (f + K1 * norm)
            }
        }

        val order = scores.indices.filter { scores[it] > 0.0 }.sortedByDescending { scores[it] }.toIntArray()
        if (order.isEmpty()) return Result.EMPTY

        // 稀有词：只出现在极少数块里的查询词，是「原文独有词」的最好近似
        val rareDfLimit = maxOf(1, (n * RARE_DF_RATIO).toInt())
        val rareByChunk = HashMap<Int, MutableList<String>>()
        for (t in terms.indices) {
            if (!queryHits[t]) continue
            var df = 0
            var only = -1
            for (c in 0 until n) {
                if (tf[t][c] > 0) {
                    df++
                    only = c
                    if (df > rareDfLimit) break
                }
            }
            if (df in 1..rareDfLimit && only >= 0) {
                val list = rareByChunk.getOrPut(only) { ArrayList(2) }
                if (list.size < MAX_RARE_PER_CHUNK) list.add(terms[t])
            }
        }

        return Result(
            scores = scores,
            order = order,
            rareByChunk = rareByChunk,
            matchedTermCount = matchedTerms,
            totalTermCount = terms.size,
        )
    }

    /** 查询切分：与扫描侧规则一致 */
    fun tokenizeQuery(query: String): List<String> {
        val out = LinkedHashSet<String>(32)
        var i = 0
        val n = query.length
        while (i < n && out.size < MAX_QUERY_TERMS) {
            val ch = query[i]
            if (TokenEstimator.isCjk(ch)) {
                if (i + 1 < n && TokenEstimator.isCjk(query[i + 1])) out.add(bigramKey(ch, query[i + 1]))
                i++
            } else if (ch.isLetterOrDigit()) {
                var j = i
                while (j < n && query[j].isLetterOrDigit() && !TokenEstimator.isCjk(query[j])) j++
                if (j - i >= 2) out.add(wordKey(query, i, j))
                i = j
            } else {
                i++
            }
        }
        return out.toList()
    }

    private fun bigramKey(a: Char, b: Char): String = buildString(2) { append(a); append(b) }

    private fun wordKey(cs: CharSequence, from: Int, to: Int): String =
        buildString(to - from) {
            for (k in from until to) append(cs[k].lowercaseChar())
        }

    private const val CHECK_INTERVAL = 16_384
}
