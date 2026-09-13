package com.narvive.app.service.ai.retrieval

/**
 * 本地人物候选抽取（启发式，非 NER）。
 *
 * 用途有三处，都属于「错了也不致命」的场景，因此可以接受启发式的误差：
 * 1. 概览里给每块附「出场人物」线索，提升模型选块的命中率；
 * 2. 关系图压缩时，优先保留含人物的句子（顺带去噪）；
 * 3. 词法快路径判断「问题里是否出现了原文独有词」。
 *
 * 小说文本里最可靠的信号是**说话人归属**（`林黛玉笑道`、`"..." said Darcy`），
 * 因此主模式取「行首或标点/引号之后 + 2~4 字 + 言说动词」，再用出现频次过滤掉
 * 「他知」「难道」这类噪声。频次阈值是关键：真正的角色名会在整章反复出现。
 */
object NameCandidates {

    private val CJK_SPEAKER = Regex(
        "(?:^|[。！？…；\\n”’」』）]\\s*)([\\u4e00-\\u9fa5]{2,4}?)(?:说道|问道|笑道|答道|叫道|喊道|喝道|骂道|叹道|应道|开口道|低声道|轻声道|冷冷道|说|道|问|答|喊|叫)",
    )

    private val LATIN_SPEAKER = Regex(
        "(?:^|[.!?…\";]\\s+)([A-Z][a-zA-Z'’-]{2,15})(?:\\s+(?:said|asked|replied|answered|cried|shouted|whispered|murmured|exclaimed|added|continued|muttered))",
    )

    /** 不太可能作为姓名第二部分出现的常用字（用于剔除「他知」这类误报） */
    private const val BAD_TAIL = "知说道想问答喊叫是的了着过和与在有没有不就没都也还要会能可把被让给对从向到于便才又很太更最再"

    /** 不太可能作为姓名首字的字 */
    private const val BAD_HEAD = "他她它我你您咱这那哪谁什怎为因所但可不没就还只真已正能会要想看听觉得和与在把被让给对从向到于随跟同连每各些有无的了吗呢吧啊"

    /** 英文里高频但不可能作人名的词（句子首字母大写会引入大量噪声） */
    private val LATIN_STOPWORDS = hashSetOf(
        "the", "and", "but", "for", "not", "you", "she", "her", "his", "him", "they", "them",
        "this", "that", "these", "those", "there", "here", "what", "when", "where", "which",
        "who", "why", "how", "with", "from", "into", "onto", "over", "under", "after", "before",
        "then", "than", "now", "yes", "no", "oh", "well", "was", "were", "are", "has", "had",
        "have", "will", "would", "could", "should", "can", "may", "might", "must", "one", "two",
        "all", "any", "some", "more", "most", "very", "just", "only", "also", "even", "still",
        "again", "once", "ever", "never", "always", "because", "though", "while", "about",
    )

    private const val MAX_MATCHES = 4_000

    /**
     * @param limit 最多返回多少个候选
     * @param minCount 最少出现次数；低于此值视为噪声
     */
    fun top(text: CharSequence, limit: Int = 12, minCount: Int = 3): List<String> {
        if (text.isEmpty()) return emptyList()
        val counts = HashMap<String, Int>(64)
        val display = HashMap<String, String>(64)

        var seen = 0
        for (m in CJK_SPEAKER.findAll(text)) {
            if (seen++ >= MAX_MATCHES) break
            val name = m.groupValues.getOrNull(1) ?: continue
            if (!plausibleCjk(name)) continue
            counts[name] = (counts[name] ?: 0) + 1
        }
        for (m in LATIN_SPEAKER.findAll(text)) {
            if (seen++ >= MAX_MATCHES) break
            val raw = m.groupValues.getOrNull(1) ?: continue
            val key = raw.lowercase()
            if (key.length < 3 || key in LATIN_STOPWORDS) continue
            counts[key] = (counts[key] ?: 0) + 1
            display.putIfAbsent(key, raw)
        }
        if (counts.isEmpty()) return emptyList()

        return counts.entries
            .asSequence()
            .filter { it.value >= minCount }
            .sortedWith(compareByDescending<Map.Entry<String, Int>> { it.value }.thenBy { it.key })
            .take(limit)
            .map { display[it.key] ?: it.key }
            .toList()
    }

    private fun plausibleCjk(name: String): Boolean {
        if (name.length < 2) return false
        if (BAD_HEAD.indexOf(name[0]) >= 0) return false
        if (name.length >= 2 && BAD_TAIL.indexOf(name[name.length - 1]) >= 0) return false
        // 首尾同字（「谢谢」「常常」）不是人名
        if (name[0] == name[name.length - 1]) return false
        // 全同字（「哈哈」）
        if (name.all { it == name[0] }) return false
        return true
    }
}
