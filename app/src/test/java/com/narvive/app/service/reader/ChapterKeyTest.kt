package com.narvive.app.service.reader

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 章节稳定键。
 *
 * 关键性质：**同一章在不同阅读位置必须得到同一个键**——否则摘要缓存会重复写入，
 * 而且「阅读中顺带缓存」与「建立全书索引」两边对不上，全书检索等于白建。
 */
class ChapterKeyTest {

    @Test
    fun `TXT 同一章不同偏移得到同一个键`() {
        val a = ChapterKey.of("TXT", """{"chapter":12,"offset":0}""")
        val b = ChapterKey.of("TXT", """{"chapter":12,"offset":3400}""")
        val c = ChapterKey.of("TXT", """{"chapter":12}""")
        assertEquals("txt:12", a)
        assertEquals("同一章不同位置必须是同一个键", a, b)
        assertEquals(a, c)
    }

    @Test
    fun `TXT 不同章得到不同的键`() {
        assertEquals("txt:2", ChapterKey.of("TXT", """{"chapter":2,"offset":0}"""))
        assertEquals("txt:10", ChapterKey.of("TXT", """{"chapter":10,"offset":0}"""))
    }

    @Test
    fun `EPUB 用 href 且忽略 progression`() {
        val a = ChapterKey.of(
            "EPUB",
            """{"href":"OEBPS/ch3.xhtml","type":"application/xhtml+xml","locations":{"progression":0.0,"totalProgression":0.0}}""",
        )
        val b = ChapterKey.of(
            "EPUB",
            """{"href":"OEBPS/ch3.xhtml","type":"application/xhtml+xml","locations":{"progression":0.62,"totalProgression":0.31}}""",
        )
        assertEquals("epub:OEBPS/ch3.xhtml", a)
        assertEquals("翻页后的 locator 必须归一到同一个键", a, b)
    }

    @Test
    fun `无法定位到章时返回 null 而不是脏键`() {
        assertNull(ChapterKey.of("TXT", null))
        assertNull(ChapterKey.of("TXT", ""))
        assertNull(ChapterKey.of("TXT", "{}"))
        assertNull(ChapterKey.of("EPUB", """{"type":"application/xhtml+xml"}"""))
        assertNull(ChapterKey.of("TXT", """{"offset":300}"""))
    }

    @Test
    fun `位置值用于恢复书内顺序`() {
        assertTrue(ChapterKey.positionOf("txt:3") < ChapterKey.positionOf("txt:20"))
        assertEquals(Int.MAX_VALUE, ChapterKey.positionOf("epub:OEBPS/ch1.xhtml"))
    }
}
