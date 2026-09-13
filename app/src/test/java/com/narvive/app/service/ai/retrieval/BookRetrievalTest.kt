package com.narvive.app.service.ai.retrieval

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 全书检索（在已读章节摘要上做两阶段检索）。
 *
 * 摘要来自增量缓存，因此这里的重点是三件事：
 * 没有摘要时不越权、模型选中时正确注入、模型失手时仍能兜住。
 */
class BookRetrievalTest {

    private val summaries = listOf(
        BookSummary("第 1 章 雪夜", "林昭在雪夜推开木门，见到了等候多时的沈砚。两人谈及三年前的旧案。"),
        BookSummary("第 2 章 旧信", "沈砚交出一封没有署名的信，信中提到了城外的烽火与慕容雪的名字。"),
        BookSummary("第 3 章 出城", "林昭与沈砚连夜出城，在驿站遇到了自称商队的陆行舟。"),
        BookSummary("第 4 章 烽火", "慕容雪独自站在城墙上，望着远方的烽火，想起母亲临终前的话。"),
        BookSummary("第 5 章 归途", "三人分道扬镳，林昭独自返回城中，旧案的真凶浮出水面。"),
    )

    @Test
    fun `没有摘要时返回 null 由调用方保持原有行为`() = runBlocking {
        val result = ChapterRetriever().retrieveBook(emptyList(), "这本书讲了什么") { _, _, _, _ ->
            ChunkPick.Picked(listOf(1))
        }
        assertNull("没有摘要就不应该编造检索结果", result)
    }

    @Test
    fun `模型选中的章节会被注入`() = runBlocking {
        val result = ChapterRetriever().retrieveBook(summaries, "慕容雪是谁") { _, _, itemCount, _ ->
            assertEquals("应把章数告诉解析器", summaries.size, itemCount)
            // 编号是 0 基（解析器已把模型的 1 基编号换算过）
            ChunkPick.Picked(listOf(3))
        }
        assertEquals(RetrievalMode.MODEL_PICK, result!!.mode)
        assertTrue("选中的章节应在结果里", result.pickedIndices.contains(3))
        assertTrue(result.pickedSummaries.any { it.text.contains("慕容雪") })
        assertTrue("注入章数不应超过上限", result.pickedSummaries.size <= 4)
    }

    @Test
    fun `模型调用失败时用词法兜底命中正确章节`() = runBlocking {
        val result = ChapterRetriever().retrieveBook(summaries, "慕容雪后来怎么样了") { _, _, _, _ ->
            ChunkPick.Failed("http 500")
        }
        assertEquals(RetrievalMode.LEXICAL_FALLBACK, result!!.mode)
        assertTrue(
            "兜底也必须命中提到该人物的章节，实际 ${result.pickedIndices}",
            result.pickedIndices.contains(3),
        )
    }

    @Test
    fun `问题含独有专名时走零 token 快路径`() = runBlocking {
        var selectorCalled = false
        val result = ChapterRetriever().retrieveBook(summaries, "陆行舟是什么人") { _, _, _, _ ->
            selectorCalled = true
            ChunkPick.Picked(listOf(1))
        }
        assertEquals(RetrievalMode.FAST_PATH, result!!.mode)
        assertTrue("快路径不应再调用模型", !selectorCalled)
        assertTrue(result.pickedSummaries.any { it.text.contains("陆行舟") })
    }

    @Test
    fun `模型认为无需细看时按开头与最近章节兜底`() = runBlocking {
        val result = ChapterRetriever().retrieveBook(summaries, "这本书讲什么") { _, _, _, _ ->
            ChunkPick.OverviewEnough
        }
        assertEquals(RetrievalMode.RECENT_FALLBACK, result!!.mode)
        assertTrue("必须给出真实内容而不是空手而归", result.pickedSummaries.isNotEmpty())
        assertTrue("应覆盖开头", result.pickedIndices.contains(0))
        assertTrue("应覆盖最近读到的部分", result.pickedIndices.contains(summaries.lastIndex))
    }

    @Test
    fun `越界编号被忽略且不会取到错误章节`() = runBlocking {
        val result = ChapterRetriever().retrieveBook(summaries, "随便问问") { _, _, _, _ ->
            ChunkPick.Picked(listOf(99))
        }
        // 越界 → 解析器判失败 → 无词法信号 → 兜底到开头/结尾，绝不会取到不存在的章
        assertTrue(result!!.pickedIndices.all { it in summaries.indices })
    }
}
