package com.narvive.app.service.ai.retrieval

import javax.inject.Inject
import javax.inject.Singleton

/**
 * 章节索引缓存（按正文内容哈希）。
 *
 * 只缓存**派生数据**（块区间、块摘要、句界、人物候选），不缓存正文副本，
 * 单章约几十 KB，LRU 上限 2 章——低配机上也不会成为内存负担。
 *
 * 索引是正文的纯函数，因此用内容哈希做键天然正确：换章、换书、正文变化都会自动失效。
 */
@Singleton
class RetrievalCache @Inject constructor() {

    private val lock = Any()
    private val entries = LinkedHashMap<Long, ChapterIndex>(4, 0.75f, true)

    /**
     * 命中/构建章节索引。构建是 CPU 密集的，调用方应确保在后台线程执行。
     *
     * 索引构建自带本地时间预算：超时后放弃人名候选这类「锦上添花」的工作，
     * 保证低配机在超长章节上也不会长时间占着 CPU。
     */
    fun indexOf(text: String, config: ChunkConfig = ChunkConfig()): ChapterIndex {
        val key = hashOf(text, config)
        synchronized(lock) {
            entries[key]?.let { return it }
        }
        val built = TextChunker.index(text, config, LocalBudget.ofMillis())
        synchronized(lock) {
            entries[key] = built
            while (entries.size > MAX_ENTRIES) {
                val oldest = entries.entries.firstOrNull() ?: break
                entries.remove(oldest.key)
            }
        }
        return built
    }

    fun clear() {
        synchronized(lock) { entries.clear() }
    }

    /** FNV-1a 64：一次线性扫描，20 万字符约 0.2ms */
    private fun hashOf(text: String, config: ChunkConfig): Long {
        var h = -0x340d631b7bdddcdbL // FNV offset basis
        var i = 0
        val n = text.length
        while (i < n) {
            h = h xor text[i].code.toLong()
            h *= 0x100000001b3L
            i++
        }
        h = h * 31 + config.targetTokens
        h = h * 31 + config.maxTokens
        h = h * 31 + config.maxGroups
        return h
    }

    private companion object {
        const val MAX_ENTRIES = 2
    }
}
