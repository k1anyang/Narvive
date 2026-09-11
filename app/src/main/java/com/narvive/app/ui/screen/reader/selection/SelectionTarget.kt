package com.narvive.app.ui.screen.reader.selection

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect

/**
 * 选区容器接口（P1）：渲染容器只提供命中测试与几何信息，不持有任何手势逻辑。
 * 坐标约定：入参 [Offset]/出参 [Rect] 一律为「阅读根容器坐标 px」（与 Text.localToRoot 一致）。
 * 偏移约定：chapter 章内字符偏移；区间一律半开 [start, end)。
 */
interface SelectionTarget {

    /**
     * 根坐标 → (章索引, 章内字符偏移)；命中正文之外的空白/边距/章节头返回 null。
     * 命中插入内容（段距空段/译文）时按现有 renderedToOriginal 语义折回原文最近位置。
     */
    fun charOffsetAt(rootPos: Offset): Pair<Int, Int>?

    /** 章内字符偏移 → 该字符包围矩形（root px）；offset == end 时返回末字符右侧零宽位置 */
    fun rectForCharOffset(chapter: Int, offset: Int): Rect?

    /**
     * 选区 [start, end) 的逐行背景矩形（root px）。
     * 跨块/跨页由容器内部聚合：每行一段，段首/段尾按选区边界截断。
     */
    fun selectionRects(chapter: Int, start: Int, end: Int): List<Rect>

    /** 章内字符偏移所在行的矩形（root px；气泡基准定位用） */
    fun lineRectAt(chapter: Int, offset: Int): Rect?

    /**
     * 选区拖动边缘自动滚动：deltaPx > 0 向下/向后，< 0 向上/向前。
     * 滚动模式按像素滚动；分页模式实现为翻页（容器内部节流，避免每帧连翻）。
     */
    suspend fun scrollByForSelection(deltaPx: Float)

    /** 选区原文片段（SelectionState.text 用） */
    fun originalText(chapter: Int, start: Int, end: Int): String
}
