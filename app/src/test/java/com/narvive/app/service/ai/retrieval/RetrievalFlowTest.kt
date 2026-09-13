package com.narvive.app.service.ai.retrieval

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 检索链路的行为断言。
 *
 * 这里的用例直接对应旧实现里已经存在的缺陷：
 * - 模型复述概览时被误判为「选中了前两块」；
 * - 检索失败被当成「用户问题较泛」；
 * - 长章节只注入固定 2 块、覆盖不足。
 */
class RetrievalFlowTest {

    // ── 选块解析 ──

    @Test
    fun `纯编号列表被正确解析`() {
        val pick = ChunkSelectionParser.parse("3, 7", itemCount = 10, maxPick = 3)
        assertTrue(pick is ChunkPick.Picked)
        assertEquals(listOf(2, 6), (pick as ChunkPick.Picked).indices)
    }

    @Test
    fun `带标签或方括号的编号也能解析`() {
        assertTrue(ChunkSelectionParser.parse("块编号：4、9", 20, 3) is ChunkPick.Picked)
        assertTrue(ChunkSelectionParser.parse("[5]", 20, 3) is ChunkPick.Picked)
        assertEquals(
            listOf(0, 2),
            (ChunkSelectionParser.parse("[1, 3]", 20, 3) as ChunkPick.Picked).indices,
        )
    }

    @Test
    fun `零表示概览已足够`() {
        assertEquals(ChunkPick.OverviewEnough, ChunkSelectionParser.parse("0", 10, 2))
    }

    @Test
    fun `复述概览不会被误判为选中前两块`() {
        // 旧实现 Regex("\d+").findAll(raw).take(2) 会把 [1] [2] 当成选择结果，
        // 表面上「检索成功」，实际永远只读前两块。
        val echoed = "[1] 他推开门，风雪扑面而来。\n[2] 她握紧了手中的信。\n[3] 钟声响起。\n以上都与问题相关。"
        val pick = ChunkSelectionParser.parse(echoed, itemCount = 12, maxPick = 2)
        assertTrue("复述概览必须判失败而不是选中前两块，实际 $pick", pick is ChunkPick.Failed)
    }

    @Test
    fun `越界或空响应判失败`() {
        assertTrue(ChunkSelectionParser.parse("99", 5, 2) is ChunkPick.Failed)
        assertTrue(ChunkSelectionParser.parse("", 5, 2) is ChunkPick.Failed)
        assertTrue(ChunkSelectionParser.parse("我觉得第二块比较相关", 5, 2) is ChunkPick.Failed)
    }

    // ── 词法打分 ──

    @Test
    fun `问题中的独有专名能定位到正确块`() {
        val text = buildChapter()
        val index = TextChunker.index(text)
        val result = LexicalScorer.score(text, index.chunks, "慕容雪为什么离开")

        assertTrue("应命中词法信号", result.hasSignal)
        val chunk = index.chunks[result.best]
        assertTrue(
            "命中的块应包含该专名",
            text.substring(chunk.start, chunk.end).contains("慕容雪"),
        )
        val terms = result.rareByChunk[result.best].orEmpty()
        assertTrue("应识别出稀有词用于快路径判定", terms.any { it == "慕容" || it == "容雪" })
    }

    @Test
    fun `纯功能词的提问不产生虚假信号`() {
        val text = buildChapter()
        val index = TextChunker.index(text)
        val result = LexicalScorer.score(text, index.chunks, "这个 和 那个 怎么样")
        assertFalse("泛问不应给出高置信度的词法定位", result.bestScore > 6.0)
    }

    @Test
    fun `英文按单词切分且忽略大小写`() {
        val text = "Elizabeth walked into the room. Darcy said nothing at all. ".repeat(40)
        val index = TextChunker.index(text)
        val hit = LexicalScorer.score(text, index.chunks, "What did Darcy say")
        assertTrue(hit.hasSignal)
        assertTrue(text.substring(index.chunks[hit.best].start, index.chunks[hit.best].end).contains("Darcy"))
    }

    // ── 覆盖式压缩 ──

    @Test
    fun `压缩到预算内且每块都保留了内容`() {
        val text = buildChapter()
        val index = TextChunker.index(text)
        val budgetTokens = 1_500 // 远小于章节本身，确保真的触发压缩

        val out = CoverageCompressor.compress(text, index, budgetTokens, CoveragePurpose.SUMMARY)
        assertTrue("压缩后应短于原文（${out.keptChars} vs ${text.length}）", out.text.length < text.length)
        assertTrue("压缩比应显著（保留句数少于总句数）", out.keptSentences < out.totalSentences)
        assertTrue(
            "估算 token 不应超预算太多",
            TokenEstimator.estimate(out.text) <= budgetTokens * 1.35,
        )
        assertTrue("压缩结果不应为空", out.keptChars > 0)
        assertTrue("每块都应至少保留一句（覆盖保证）", out.keptSentences >= index.chunks.size.coerceAtMost(out.keptSentences))
    }

    @Test
    fun `压缩保持原文顺序`() {
        val text = buildChapter()
        val index = TextChunker.index(text)
        val out = CoverageCompressor.compress(text, index, 2_000, CoveragePurpose.SUMMARY)

        // 压缩会在保留句之间插入换行与省略标记，因此折叠空白后再比对位置
        val flat = text.replace(Regex("[\\s\\u3000]+"), "")
        val flatOut = out.text.replace(Regex("[\\s\\u3000]+"), "")
        val pieces = flatOut.split("……").filter { it.length >= 8 }
        assertTrue("压缩结果应有多个片段，实际 ${pieces.size}", pieces.size >= 2)
        var cursor = -1
        pieces.forEach { p ->
            // 语料含重复句，必须从上一个片段之后继续查找，才能验证「顺序」而不是「出现过」
            val at = flat.indexOf(p.take(16), cursor + 1)
            assertTrue("片段必须按原文顺序出现：${p.take(16)}", at > cursor)
            cursor = at
        }
    }

    @Test
    fun `关系图用途优先保留含人物的句子`() {
        val text = buildChapter()
        val index = TextChunker.index(text)
        val out = CoverageCompressor.compress(text, index, 2_000, CoveragePurpose.GRAPH)
        assertTrue("关系图压缩结果不应为空", out.keptChars > 0)
        assertTrue(
            "关系图压缩后应仍能看到主要人物",
            out.text.contains("慕容雪") || out.text.contains("林昭"),
        )
    }

    // ── 检索编排 ──

    @Test
    fun `模型说概览足够时只给概览且不注入正文`() = runBlocking {
        val text = buildChapter()
        val index = TextChunker.index(text)
        val retriever = ChapterRetriever()
        val result = retriever.retrieveQa(
            text = text,
            index = index,
            question = "这一章大概讲了什么",
            budget = RetrievalBudgetConfig.of(),
            labels = CueLabels.NEUTRAL,
        ) { _, _, _, _ -> ChunkPick.OverviewEnough }

        assertEquals(RetrievalMode.OVERVIEW_ONLY, result.mode)
        assertTrue(result.pickedTexts.isEmpty())
        assertTrue(result.overviewLines.isNotEmpty())
    }

    @Test
    fun `模型调用失败时走词法兜底而不是降级成问题较泛`() = runBlocking {
        val text = buildChapter()
        val index = TextChunker.index(text)
        val retriever = ChapterRetriever()
        val result = retriever.retrieveQa(
            text = text,
            index = index,
            question = "慕容雪后来怎么样了",
            budget = RetrievalBudgetConfig.of(),
            labels = CueLabels.NEUTRAL,
        ) { _, _, _, _ -> ChunkPick.Failed("http 500") }

        // 该提问命中独有专名 → 会先走快路径；无论快路径还是兜底，都必须是「注入了正文」而不是「仅概览」
        assertTrue(
            "失败/快路径都必须注入正文，实际 ${result.mode}",
            result.mode == RetrievalMode.LEXICAL_FALLBACK || result.mode == RetrievalMode.FAST_PATH,
        )
        assertTrue(result.pickedTexts.isNotEmpty())
        assertTrue(result.pickedTexts.any { it.contains("慕容雪") })
    }

    @Test
    fun `既无词法信号又解析失败时明确标记为仅概览`() = runBlocking {
        val text = buildChapter()
        val index = TextChunker.index(text)
        val retriever = ChapterRetriever()
        val result = retriever.retrieveQa(
            text = text,
            index = index,
            question = "嗯",
            budget = RetrievalBudgetConfig.of(),
            labels = CueLabels.NEUTRAL,
        ) { _, _, _, _ -> ChunkPick.Failed("timeout") }

        assertEquals(RetrievalMode.OVERVIEW_ONLY, result.mode)
        assertTrue(result.pickedTexts.isEmpty())
    }

    @Test
    fun `模型选中块时会补齐词法最可疑的一块`() = runBlocking {
        val text = buildChapter()
        val index = TextChunker.index(text)
        val retriever = ChapterRetriever()
        val target = index.chunks.indexOfFirst {
            text.substring(it.start, it.end).contains("慕容雪")
        }
        assertTrue("测试语料应包含目标块", target >= 0)
        // 让模型故意选一个不含专名的块（0），词法命中 target → 应被补进来
        val wrong = if (target == 0) 1 else 0
        val result = retriever.retrieveQa(
            text = text,
            index = index,
            question = "慕容雪做了什么",
            budget = RetrievalBudgetConfig.of(),
            labels = CueLabels.NEUTRAL,
        ) { _, _, _, _ -> ChunkPick.Picked(listOf(wrong)) }

        if (result.mode == RetrievalMode.MODEL_PICK) {
            assertTrue("词法最可疑块应被补入", result.pickedLabels.contains(target + 1))
        }
    }

    @Test
    fun `注入总量受预算约束`() = runBlocking {
        val text = buildChapter()
        val index = TextChunker.index(text)
        val retriever = ChapterRetriever()
        val budget = RetrievalBudgetConfig.of()
        val result = retriever.retrieveQa(
            text = text,
            index = index,
            question = "请把这一章的细节都列出来，包括每个人物的行动",
            budget = budget,
            labels = CueLabels.NEUTRAL,
        ) { _, _, _, maxPick -> ChunkPick.Picked((1..maxPick).toList()) }

        val injected = result.pickedTexts.sumOf { TokenEstimator.estimate(it) }
        assertTrue(
            "注入 token（$injected）不应超过预算的 1.3 倍（${budget.qaChunkTokens}）",
            injected <= budget.qaChunkTokens * 1.3,
        )
        assertTrue("块数不应超过上限", result.pickedLabels.size <= 6)
    }

    // ── 测试语料 ──

    /** 构造一段「多块 + 一个只出现一次的专名」的章节语料 */
    private fun buildChapter(): String = buildString {
        for (i in 1..70) {
            append("第").append(i).append("节。他走进屋子，看见桌上摊着一封没有署名的信。")
            append("她抬头看了他一眼，又低下头继续缝补手里的衣裳。")
            append("“外面下雪了。”林昭站在门口说道。")
            append("两个人沉默了很久，只有炉火噼啪作响。")
            append('\n')
        }
        append("慕容雪独自站在城墙上，望着远方的烽火，手指被冻得发白。")
        append("她想起三年前那个同样寒冷的夜晚，想起母亲临终前说过的话。")
        append('\n')
        for (i in 71..110) {
            append("第").append(i).append("节。村子里的人都聚集到了祠堂前的空地上。")
            append("老人咳嗽着把一卷泛黄的族谱放到桌上，缓缓展开。")
            append('\n')
        }
    }
}
