package com.narvive.app.service.ai.retrieval

import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 本地检索工作的耗时基准（非严格性能测试，但可复现）。
 *
 * 目的：给「低配机适配」提供依据。此前只有静态推算，这里在 JVM 上量出真实数字，
 * 再按手机 CPU 通常比桌面慢 2~4 倍折算，判断 400ms 本地预算是否合理。
 *
 * 断言刻意宽松（放 CI 波动），真正的产出是 `system-out` 里的那几行测量结果：
 * 见 `app/build/test-results/**/TEST-*RetrievalBenchmarkTest.xml`。
 */
class RetrievalBenchmarkTest {

    @Test
    fun `二十万字与五十万字章节的本地耗时`() {
        for (target in listOf(200_000, 500_000)) {
            val text = buildChapter(target)
            println("── 章节规模：${text.length} 字符 ──")

            val indexMs = measure { TextChunker.index(text) }
            val index = TextChunker.index(text)
            println("索引构建：${indexMs}ms ｜ 块数=${index.chunks.size} 组数=${index.groups.size} " +
                "估算 token=${index.estTokens} 人物候选=${index.nameCandidates.size}")

            val query = "慕容雪后来为什么离开"  // 专名提问，最坏情况下词法要扫全文
            val lexicalMs = measure { LexicalScorer.score(text, index.chunks, query) }
            println("词法扫描：${lexicalMs}ms ｜ 查询词=${LexicalScorer.tokenizeQuery(query).size}")

            val budget = RetrievalBudgetConfig.of()
            val compressMs = measure {
                CoverageCompressor.compress(text, index, budget.coverageTokens, CoveragePurpose.SUMMARY)
            }
            val compressed = CoverageCompressor.compress(text, index, budget.coverageTokens, CoveragePurpose.SUMMARY)
            println(
                "覆盖压缩：${compressMs}ms ｜ 保留 ${compressed.keptSentences}/${compressed.totalSentences} 句 " +
                    "(${(compressed.keptRatio * 100).toInt()}%) ｜ 轻量模式=${compressed.lightweight}",
            )

            val graphMs = measure {
                CoverageCompressor.compress(text, index, budget.coverageTokens, CoveragePurpose.GRAPH)
            }
            println("关系图压缩：${graphMs}ms")

            // 宽松护栏：桌面 JVM 上单段本地工作不应超过 3 秒（远超 400ms 预算即说明有明显退化）
            assertTrue("索引构建过慢：${indexMs}ms", indexMs < 3_000)
            assertTrue("词法扫描过慢：${lexicalMs}ms", lexicalMs < 3_000)
            assertTrue("覆盖压缩过慢：${compressMs}ms", compressMs < 5_000)
            println()
        }
    }

    @Test
    fun `预算充足的索引与超时降级的索引块数一致`() {
        val text = buildChapter(120_000)
        val normal = TextChunker.index(text, budget = LocalBudget.ofMillis(2_000))
        val starved = TextChunker.index(text, budget = LocalBudget.EXPIRED)
        assertTrue("降级不应改变分块结果", normal.chunks.size == starved.chunks.size)
    }

    /** 合成一段「有对话、有人名、有段落」的中文小说章节 */
    private fun buildChapter(targetChars: Int): String {
        val sb = StringBuilder(targetChars + 256)
        val names = listOf("慕容雪", "林昭", "沈砚", "苏九娘", "陆行舟")
        var p = 0
        while (sb.length < targetChars) {
            p++
            val a = names[p % names.size]
            val b = names[(p + 2) % names.size]
            sb.append("第").append(p).append("节。")
            sb.append("夜色压下来的时候，").append(a).append("推开了那扇半朽的木门，门轴发出细长的呻吟。")
            sb.append("屋里只点着一盏豆大的油灯，").append(b).append("坐在桌边，手里的茶盏已经凉透了。")
            sb.append("“你终于来了。”").append(b).append("说道，声音里听不出喜怒。")
            sb.append(a).append("没有回答，只是把怀里的布包放在桌上，布包上还沾着未干的血迹。")
            sb.append("三年前的旧事像潮水一样涌上来，两个人都沉默了很久。")
            sb.append("“城外的烽火已经烧了三天。”").append(a).append("低声道，“再不走就来不及了。”")
            sb.append("窗外传来马蹄声，由远及近，最后停在院门口。")
            sb.append('\n')
        }
        return sb.toString()
    }

    private fun measure(block: () -> Unit): Long {
        val start = System.nanoTime()
        block()
        return (System.nanoTime() - start) / 1_000_000
    }
}
