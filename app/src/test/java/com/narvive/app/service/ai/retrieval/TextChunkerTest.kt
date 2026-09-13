package com.narvive.app.service.ai.retrieval

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** 分块与 token 估算：重点是「EPUB 无换行」与「超长单段」这两类会让检索整体失效的输入。 */
class TextChunkerTest {

    @Test
    fun `无换行的正文仍能被切成多块`() {
        // EPUB 取出的正文在阅读器里已被 \s+ → " " 压成单行，
        // 旧实现按 \n 切段会让整章退化成 1 块，检索随之失效。
        val text = "他推开门，风雪扑面而来。远处传来钟声，像是某种预兆。" +
            "她握紧了手中的信，指尖微微发白。两个人都没有说话。".repeat(400)

        assertFalse("构造的测试文本不应含换行", text.contains('\n'))

        val index = TextChunker.index(text)
        assertTrue("无换行正文必须被切成多块，实际 ${index.chunks.size}", index.chunks.size >= 4)
        assertTrue("块摘要不应为空", index.chunks.all { it.digest.isNotBlank() })
    }

    @Test
    fun `无标点的超长单段也不会撑出超大块`() {
        val text = "あ".repeat(1) + "无标点长文本".repeat(4_000)

        val index = TextChunker.index(text)
        assertTrue(index.chunks.size > 1)
        val maxChars = (ChunkConfig().maxTokens / TokenEstimator.tokensPerChar(index.cjkRatio)).toInt() + 8
        assertTrue(
            "单块字符数应被硬上限约束，实际最大 ${index.chunks.map { it.length }.max()}",
            index.chunks.all { it.length <= maxChars },
        )
    }

    @Test
    fun `块区间按顺序覆盖全文且互不重叠`() {
        val text = buildParagraphs(60)
        val index = TextChunker.index(text)

        var cursor = 0
        index.chunks.forEach { c ->
            assertTrue("块起点应不小于上一块终点", c.start >= cursor)
            assertTrue("块终点应大于起点", c.end > c.start)
            cursor = c.end
        }
        assertTrue("最后一块应覆盖到接近文末", cursor >= text.trimEnd().length - 2)
    }

    @Test
    fun `块数超过上限时按父块分组并给出块号区间`() {
        val text = buildParagraphs(120)
        val index = TextChunker.index(text, ChunkConfig(maxGroups = 2))

        assertTrue("语料应产生多于 2 块，实际 ${index.chunks.size}", index.chunks.size > 2)
        assertFalse("块数超额时应启用分组", index.singleLevel)
        assertTrue(index.groups.size <= 2)
        assertTrue(index.groups.all { it.size >= 1 })
        assertTrue("分组应连续覆盖所有块", index.groups.first().firstIndex == 0)
        assertEquals(index.chunks.lastIndex, index.groups.last().lastIndex)
    }

    @Test
    fun `token 估算对中文与拉丁分别计权且单调`() {
        val zh = TokenEstimator.estimate("中".repeat(1_000))
        val en = TokenEstimator.estimate("a".repeat(1_000))
        assertTrue("同样字符数下中文 token 应明显多于英文（zh=$zh en=$en）", zh > en * 2)
        assertTrue(TokenEstimator.estimate("") >= 1)
        assertTrue(TokenEstimator.estimate("a".repeat(100)) > TokenEstimator.estimate("a".repeat(50)))
    }

    @Test
    fun `默认预算下五万字中文章节仍走全文路径`() {
        // 这是行为兼容的关键断言：默认配置下短章节必须与旧实现一致（直接注入全文）。
        val budget = RetrievalBudgetConfig.of()
        val fiftyThousandZh = TokenEstimator.estimate("字".repeat(50_000))
        assertTrue(
            "5 万中文字符的估算 token（$fiftyThousandZh）应仍小于全文上限（${budget.fullTextTokens}）",
            fiftyThousandZh < budget.fullTextTokens,
        )
    }

    private fun buildParagraphs(n: Int): String = buildString {
        for (i in 1..n) {
            append("第").append(i).append("段开始，主角走进房间，看见桌上放着一封信。")
            append("他拿起信，读到第三行时停住了。")
            append("“这件事不能再拖了。”他低声说。")
            append('\n')
        }
    }
}
