package com.narvive.app.service.ai

/**
 * 提问里是否提到了某本书。
 *
 * 抽成纯函数是为了可单测——这里曾经有个不好复现的缺陷：
 * 旧实现用「书名任意 2 字连续命中」判断，任何含 `the`/`th`/`he` 的英文句子
 * 都会命中一本英文书名的书，把无关书籍的元数据注入上下文。
 */
object IntentTitles {

    /**
     * 精确命中。
     *
     * @param quoted 提问中《》里的片段（无则 null）：可做前缀/包含匹配
     */
    fun hit(text: String, title: String, quoted: String?): Boolean {
        if (title.isBlank()) return false
        if (quoted != null && quoted.isNotBlank()) {
            return title.equals(quoted, ignoreCase = true) ||
                title.contains(quoted, ignoreCase = true) ||
                quoted.contains(title, ignoreCase = true)
        }
        return title.length >= MIN_EXACT_CHARS && text.contains(title, ignoreCase = true)
    }

    /**
     * 模糊命中：书名中足够长的连续片段出现，且覆盖书名一半以上。
     *
     * 门槛刻意偏高——宁可让模型反问用户指哪本书，也不要注入错书的上下文。
     */
    fun fuzzyHit(text: String, title: String): Boolean {
        if (title.length < MIN_FUZZY_CHARS) return false
        val segment = if (title.any { it.code > CJK_START }) CJK_SEGMENT else LATIN_SEGMENT
        if (title.length < segment) return false
        val windows = title.windowed(segment)
        val hits = windows.count { text.contains(it, ignoreCase = true) }
        return hits > 0 && hits * 2 >= windows.size
    }

    private const val MIN_EXACT_CHARS = 2
    private const val MIN_FUZZY_CHARS = 3
    private const val CJK_START = 0x2E80
    private const val CJK_SEGMENT = 3
    private const val LATIN_SEGMENT = 4
}
