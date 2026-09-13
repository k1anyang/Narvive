package com.narvive.app.service.ai.retrieval

/**
 * 轻量 token 估算：不引入 tokenizer 依赖，按字符类别分权。
 *
 * 依据：CJK 字符在主流 BPE 词表下约 0.6~1.0 token/字，拉丁字母约 0.25~0.35 token/字符。
 * 这与 `ui/screen/rewrite/RewriteScreen.kt` 里既有的「中文字符≈0.67 token」口径一致，
 * 只是把它从界面层收敛到一处，供检索预算判断复用。
 *
 * 只用于「放不放得下」的判断，**不用于计费**，因此宁可保守（高估）。
 */
object TokenEstimator {

    /** CJK 字符权重 */
    const val CJK_WEIGHT = 0.7

    /** 拉丁字母/数字/标点权重 */
    const val LATIN_WEIGHT = 0.28

    fun isCjk(ch: Char): Boolean {
        val c = ch.code
        return (c in 0x4E00..0x9FFF) || // CJK 统一汉字
            (c in 0x3400..0x4DBF) ||    // 扩展 A
            (c in 0xF900..0xFAFF) ||    // 兼容汉字
            (c in 0x3040..0x30FF) ||    // 日文假名
            (c in 0xAC00..0xD7AF)       // 谚文
    }

    /** 估算一段文本的 token 数（至少 1，便于直接做预算比较） */
    fun estimate(text: CharSequence): Int {
        var cjk = 0
        var other = 0
        for (i in text.indices) {
            if (isCjk(text[i])) cjk++ else other++
        }
        return tokensOf(cjk, other)
    }

    fun tokensOf(cjkChars: Int, otherChars: Int): Int =
        (cjkChars * CJK_WEIGHT + otherChars * LATIN_WEIGHT).toInt().coerceAtLeast(1)

    /** 每个字符的平均 token 数（给定 CJK 占比），用于「只数一遍字符」的快速估算 */
    fun tokensPerChar(cjkRatio: Double): Double =
        cjkRatio * CJK_WEIGHT + (1.0 - cjkRatio) * LATIN_WEIGHT

    /** 在给定 token 预算下最多能放多少字符 */
    fun charsForTokens(tokens: Int, cjkRatio: Double): Int {
        val perChar = tokensPerChar(cjkRatio)
        return if (perChar <= 0.0) tokens else (tokens / perChar).toInt()
    }

    /**
     * 采样估算 CJK 占比（长文本只采样首尾各 [sample] 字符，避免全量扫描）。
     * 用于把「字符数」近似折算成 token，避免逐句调用 [estimate]。
     */
    fun cjkRatio(text: CharSequence, sample: Int = 4_000): Double {
        if (text.isEmpty()) return 1.0
        val head = minOf(sample, text.length)
        val tailStart = maxOf(head, text.length - sample)
        var cjk = 0
        var total = 0
        for (i in 0 until head) {
            total++
            if (isCjk(text[i])) cjk++
        }
        for (i in tailStart until text.length) {
            total++
            if (isCjk(text[i])) cjk++
        }
        return if (total == 0) 1.0 else cjk.toDouble() / total
    }
}
