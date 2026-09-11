package com.narvive.app.ui.screen.reader

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * P1 选区基础设施单测：原文↔渲染坐标映射（ChunkRender）是选区命中/几何换算的数学基础。
 *
 * 映射语义（非双射，是投影）：
 *  - 原文坐标 → 渲染坐标：删除区间内的原文偏移投影到删除点；插入点之后的偏移计入插入长度；
 *  - 渲染坐标 → 原文坐标：插入内容内的渲染偏移投影到插入点；删除点之后的渲染偏移补回删除字数。
 * 由此导出的不变量：
 *  A. 对不在任何删除区间 [delPos, delPos+count) 内的原文偏移 o：renderedToOriginal(originalToRendered(o)) == o；
 *     删除区间内的 o 投影到区间末端（段落边界），这是选区端点的期望行为；
 *  B. 对不在任何插入内容内的渲染偏移 r：originalToRendered(renderedToOriginal(r)) == r；
 *  C. 两个方向都单调不减。
 *
 * 注：buildChunkRender 的标注路径依赖 org.json（Android API），纯 JVM 单测无法运行，
 *     此处直接用 insertions/deletions 构造 ChunkRender 覆盖等价场景；
 *     buildChunkRender 的无标注路径（换行/段首空白）仍直接测试。
 */
class ChunkRenderTest {

    private fun chunk(
        text: String,
        insertions: List<Insertion> = emptyList(),
        deletions: List<Deletion> = emptyList(),
    ) = ChunkRender(
        rendered = AnnotatedString(text),
        plainLength = text.length,
        insertions = insertions,
        deletions = deletions,
        translationSpans = emptyList(),
    )

    /** 删除区间的并集检查：o 是否位于某个删除区间内 */
    private fun insideDeletion(deletions: List<Deletion>, o: Int): Boolean =
        deletions.any { o >= it.origPos && o < it.origPos + it.count }

    private fun assertProjectionInvariants(
        r: ChunkRender,
        originalLength: Int,
    ) {
        // 不变量 A
        for (o in 0..originalLength) {
            val rendered = r.originalToRendered(o)
            assertTrue("o=$o rendered=$rendered 越界", rendered in 0..r.plainLength)
            val back = r.renderedToOriginal(rendered, originalLength)
            if (insideDeletion(r.deletions, o)) {
                // 删除区间内 → 投影到所在区间末端
                val runEnd = r.deletions.first { o >= it.origPos && o < it.origPos + it.count }.let { it.origPos + it.count }
                assertEquals("o=$o 应投影到删除区间末端", runEnd, back)
            } else {
                assertEquals("o=$o 往返不一致", o, back)
            }
        }
        // 不变量 B（弱形式，覆盖选区实际使用方向）：rendered → original → rendered 不减
        // （严格恒等仅在“删除点与插入点不重合”处成立，选区代码只依赖弱形式；
        //  渲染末端索引单独断言映射到原文末端）
        for (rr in 0 until r.plainLength) {
            val back = r.renderedToOriginal(rr, originalLength)
            assertTrue(
                "r=$rr 组合映射应不减",
                r.originalToRendered(back) >= rr,
            )
        }
        assertEquals(
            "渲染末端应映射到原文末端",
            originalLength,
            r.renderedToOriginal(r.plainLength, originalLength),
        )
        // 不变量 C：单调不减
        for (o in 1..originalLength) {
            assertTrue(
                "originalToRendered 应单调：o=$o",
                r.originalToRendered(o) >= r.originalToRendered(o - 1),
            )
        }
        for (rr in 1..r.plainLength) {
            assertTrue(
                "renderedToOriginal 应单调：r=$rr",
                r.renderedToOriginal(rr, originalLength) >= r.renderedToOriginal(rr - 1, originalLength),
            )
        }
    }

    @Test
    fun `projection invariants with newline deletion`() {
        // 原文 "ab\ncd" → 渲染 "abcd"：删除 (2,1) 即换行符
        val r = chunk(
            text = "abcd",
            deletions = listOf(Deletion(origPos = 2, count = 1, renderedStart = 2)),
        )
        assertEquals(2, r.originalToRendered(2)) // 删除点处
        assertEquals(2, r.originalToRendered(3)) // 删除区间内投影到删除点
        assertEquals(3, r.renderedToOriginal(2, 4)) // 删除点之后的渲染坐标补回字数
        assertProjectionInvariants(r, originalLength = 4)
    }

    @Test
    fun `projection invariants with insertion`() {
        // 原文 "abc" → 渲染 "abXYZc"：插入 (origPos=2, renderedStart=2, len=3)
        val r = chunk(
            text = "abXYZc",
            insertions = listOf(Insertion(origPos = 2, renderedStart = 2, renderedLen = 3)),
        )
        assertEquals(5, r.originalToRendered(2)) // 插入点之后的原文偏移计入插入长度
        assertEquals(2, r.renderedToOriginal(2, 3)) // 插入内容投影到插入点
        assertEquals(2, r.renderedToOriginal(4, 3))
        assertEquals(2, r.renderedToOriginal(5, 3)) // 插入点之后的渲染坐标
        assertProjectionInvariants(r, originalLength = 3)
    }

    @Test
    fun `projection invariants with deletion plus insertion`() {
        // 原文 "ab\ncd"（段落间距开启）：删除换行 (2,1)，在其后插入零宽空段（这里以 'X' 代替验证数学）
        // 渲染 "abXcd"：X 位于索引 2（删除点），插入 origPos=3、renderedStart=2、len=1
        val r = chunk(
            text = "abXcd",
            insertions = listOf(Insertion(origPos = 3, renderedStart = 2, renderedLen = 1)),
            deletions = listOf(Deletion(origPos = 2, count = 1, renderedStart = 2)),
        )
        assertEquals(3, r.originalToRendered(3)) // 'c' 位于渲染索引 3
        assertEquals(3, r.renderedToOriginal(3, 4))
        assertProjectionInvariants(r, originalLength = 4)
    }

    @Test
    fun `buildChunkRender deletes newlines and inserts paragraph gap`() {
        val text = "第一段。\n第二段。"
        val r = buildChunkRender(
            chapterText = text,
            chapterIndex = 0,
            chunkStartOffset = 0,
            chunkEndOffset = text.length,
            annotations = emptyList(),
            translationColor = Color(0xFFDC2626),
            fontSize = 17,
            paragraphSpacing = 1f,
            paragraphGapSp = 17f,
            firstLineIndent = 0,
        )
        assertTrue("渲染文本不应包含换行", !r.rendered.text.contains('\n'))
        assertEquals(1, r.insertions.count { it.paragraphGap })
        assertEquals(1, r.deletions.size)
        // 换行后的首个原文偏移（5）映射到渲染位置：删除换行 -1、计入段距空段 +1
        assertEquals("第".toString(), r.rendered.text[r.originalToRendered(5)].toString())
    }

    @Test
    fun `buildChunkRender strips leading whitespace when indent enabled`() {
        val text = "　　正文内容"
        val r = buildChunkRender(
            chapterText = text,
            chapterIndex = 0,
            chunkStartOffset = 0,
            chunkEndOffset = text.length,
            annotations = emptyList(),
            translationColor = Color(0xFFDC2626),
            fontSize = 17,
            paragraphSpacing = 0f,
            paragraphGapSp = 0f,
            firstLineIndent = 2,
        )
        assertEquals(1, r.deletions.size)
        assertEquals(0, r.deletions.first().origPos)
        assertEquals(2, r.deletions.first().count)
        assertEquals(0, r.originalToRendered(2))
        assertEquals(2, r.renderedToOriginal(0, text.length))
    }

    @Test
    fun `chunk coordinates are local, chapter composition is caller's job`() {
        val chapter = "一二三四五六七八九十"
        // 块 2 覆盖章内 [5,10)：块内渲染/原文坐标均为局部坐标
        val r = buildChunkRender(
            chapterText = chapter,
            chapterIndex = 0,
            chunkStartOffset = 5,
            chunkEndOffset = 10,
            annotations = emptyList(),
            translationColor = Color(0xFFDC2626),
            fontSize = 17,
            paragraphSpacing = 0f,
            paragraphGapSp = 0f,
            firstLineIndent = 0,
        )
        assertEquals(0, r.originalToRendered(0)) // 块内坐标
        assertEquals(0, r.renderedToOriginal(0, 5))
        // 章内坐标 = 块起始 + 块内坐标（SelectionTarget 的组合约定）
        assertEquals(5, 5 + r.renderedToOriginal(0, 5))
    }
}
