package com.narvive.app.service.reader

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.charset.Charset

/**
 * TXT 阅读器核心数学单测：章节切分、分块索引往返、滚动位置折算（computeScrollPosition/updateScrollPosition
 * 一致性——P1 选区/自动滚动/进度上报共用此计算）。
 */
class TxtReaderControllerTest {

    private fun tempTxt(text: String, charset: Charset = Charsets.UTF_8): String {
        val f = File.createTempFile("narvive_txt_test", ".txt")
        f.writeText(text, charset)
        f.deleteOnExit()
        return f.absolutePath
    }

    @Test
    fun `open parses chapters with preamble`() = runBlocking {
        val c = TxtReaderController()
        c.open(tempTxt("序言内容\n第一章 开始\n正文第一段。\n第二章 继续\n正文第二段。"))
        assertEquals(3, c.chapters.size)
        assertEquals("卷首", c.chapters[0].title)
        assertEquals("第一章 开始", c.chapters[1].title)
        assertEquals("第二章 继续", c.chapters[2].title)
        // 章节区间连续且覆盖全文
        assertEquals(0, c.chapters.first().startOffset)
        assertEquals(c.fullText.length, c.chapters.last().endOffset)
        for (i in 1 until c.chapters.size) {
            assertEquals(c.chapters[i - 1].endOffset, c.chapters[i].startOffset)
        }
    }

    @Test
    fun `open detects GBK encoding`() = runBlocking {
        val c = TxtReaderController()
        c.open(tempTxt("第一章 GBK测试\n内容一二三。", Charset.forName("GBK")))
        assertEquals(1, c.chapters.size)
        assertEquals("第一章 GBK测试", c.chapters[0].title)
        assertTrue(c.fullText.contains("内容一二三"))
    }

    @Test
    fun `chunk index and chunkAt round trip`() = runBlocking {
        val c = TxtReaderController()
        val body = "a".repeat(4500)
        c.open(tempTxt("第一章 甲\n$body"))
        // 章长 = 标题行 6 字符 + 4500 = 4506 → ceil(4506/2000) = 3 块
        assertEquals(3, c.totalChunks())
        val last = c.chunkAt(2)
        assertEquals(0, last.chapterIndex)
        assertEquals(2, last.chunkIndexInChapter)
        assertEquals(4000, last.startOffset)
        assertEquals(4506, last.endOffset)
        // 章内任意偏移 → 扁平索引 → 还原块信息包含该偏移
        for (off in listOf(0, 1, 1999, 2000, 3999, 4000, 4499)) {
            val flat = c.chunkIndexOf(0, off)
            val info = c.chunkAt(flat)
            assertTrue(
                "off=$off flat=$flat",
                off >= info.startOffset && off < info.endOffset,
            )
        }
    }

    @Test
    fun `computeScrollPosition is pure and agrees with updateScrollPosition`() = runBlocking {
        val c = TxtReaderController()
        c.open(tempTxt("第一章 测试\n" + "字".repeat(2500)))
        // 块 1 覆盖 [2000, 2500)，长 500；块内滚动 100/1000 → 章内偏移 2000 + 50
        val sp = c.computeScrollPosition(1, 100, 1000)
        assertEquals(2050, sp!!.offsetInChapter)
        assertEquals("""{"chapter":0,"offset":2050}""", sp.locator)

        // 写状态前先调用纯计算不影响状态（去重键预登记的前提）
        assertEquals(0f, c.progress.value)
        c.updateScrollPosition(1, 100, 1000)
        assertEquals(sp.locator, c.currentLocator.value)
        assertEquals("第一章 测试", c.currentChapter.value)
        assertEquals(2050f / c.fullText.length, c.progress.value, 1e-5f)
    }

    @Test
    fun `scroll position clamps and page turns count per block`() = runBlocking {
        val c = TxtReaderController()
        c.open(tempTxt("第一章 计数\n" + "字".repeat(3000)))
        assertEquals(0, c.pageTurns)
        c.updateScrollPosition(0, 0, 1000)
        assertEquals(1, c.pageTurns)
        // 同一 2000 字符块内的不同滚动位置不重复计数
        c.updateScrollPosition(0, 500, 1000)
        assertEquals(1, c.pageTurns)
        // 跨块后计数
        c.updateScrollPosition(1, 0, 1000)
        assertEquals(2, c.pageTurns)
        // 非法入参不更新状态
        c.updateScrollPosition(0, 0, 0)
        assertEquals(2, c.pageTurns)
    }
}
