package com.narvive.app.service.reader

/**
 * 章节稳定键：把「随阅读位置变化的 locator」归一化成「章级不变」的键。
 *
 * 为什么需要它：章节摘要缓存原来直接用整个 locator JSON 当键，而 locator 里带着
 * 偏移/progression——同一章在不同位置打开就是不同的键，会重复生成摘要、也让
 * 「全书索引」与「阅读中顺带缓存」两边的键对不上。这里统一成：
 * - TXT：`txt:<章序号>`
 * - EPUB：`epub:<spine href>`
 *
 * 刻意不依赖 `org.json`（Android 桩在 JVM 单测里不可用），只用极小的正则取值——
 * locator 是本项目自己生成的固定格式，取值足够安全。
 */
object ChapterKey {

    private val CHAPTER_FIELD = Regex("\"chapter\"\\s*:\\s*(-?\\d+)")
    private val HREF_FIELD = Regex("\"href\"\\s*:\\s*\"([^\"]*)\"")

    const val PREFIX_TXT = "txt:"
    const val PREFIX_EPUB = "epub:"

    fun txt(index: Int): String = "$PREFIX_TXT$index"

    fun epub(href: String): String = "$PREFIX_EPUB$href"

    /**
     * 由格式与 locator JSON 推导章级键。
     *
     * @return null 表示这个 locator 不足以定位到某一章（此时不应写入摘要缓存，避免脏键）
     */
    fun of(format: String, locatorJson: String?): String? {
        val raw = locatorJson?.trim().orEmpty()
        if (raw.isEmpty() || raw == "{}") return null
        return when (format.uppercase()) {
            "TXT" -> chapterIndexOf(raw)?.let { txt(it) }
            "EPUB" -> hrefOf(raw)?.let { epub(it) }
            else -> hrefOf(raw)?.let { epub(it) } ?: chapterIndexOf(raw)?.let { txt(it) }
        }
    }

    /**
     * 排序用的位置值：TXT 直接取章序号；EPUB 的 href 不含可靠次序，
     * 退化为 [Int.MAX_VALUE] 以便依赖稳定排序保留原有（阅读顺序）插入序。
     */
    fun positionOf(key: String): Int = key.removePrefix(PREFIX_TXT).toIntOrNull() ?: Int.MAX_VALUE

    private fun chapterIndexOf(locatorJson: String): Int? =
        CHAPTER_FIELD.find(locatorJson)?.groupValues?.getOrNull(1)?.toIntOrNull()?.takeIf { it >= 0 }

    private fun hrefOf(locatorJson: String): String? =
        HREF_FIELD.find(locatorJson)?.groupValues?.getOrNull(1)?.takeIf { it.isNotBlank() }
}
