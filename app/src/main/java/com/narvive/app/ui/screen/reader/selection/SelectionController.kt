package com.narvive.app.ui.screen.reader.selection

import android.os.SystemClock
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import com.narvive.app.ui.screen.reader.SelectionState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * 选区手势状态机（纯逻辑，由 ReaderScreen 根裁判的指针循环驱动）。
 *
 * 本 Compose 版本下，根 Box 之外的 pointerInput 事件流在 down 后中断（pager 内部指针输入
 * 完全失效、overlay 自身 pointerInput 只收到 down），因此手势判定统一收敛到根裁判循环：
 *  - 根裁判检测长按（按住 ≥ longPressMs 且位移 ≤ slop）→ [begin]；
 *  - 拖动/边缘自动滚动 → [drag]/[autoScrollStart]；
 *  - 松手 → [up]（进入 Active：选区保持 + 双柄）；
 *  - Active 态：根裁判把 down 传给 [downInActive] 判定手柄/内部/外部，拖柄经 [beginHandleDrag]+[drag]。
 * 状态用 snapshot state 暴露，渲染层（SelectionOverlay）据此绘制背景与双柄。
 */
class SelectionController(
    val target: SelectionTarget,
    private val onSelectionUpdated: (SelectionState?) -> Unit,
    private val onActiveChanged: (Boolean) -> Unit,
) {
    /** 手柄几何（px；由 SelectionOverlay 注入，绘制与命中判定共用） */
    class HandleMetrics(
        val bodyWidth: Float,
        val bodyHeight: Float,
        val tipHeight: Float,
    )

    /** 手柄命中扩展容差（px）；由宿主按 dp 注入 */
    var handleHitRadiusPx: Float = 0f
    /** 手柄几何；由宿主注入（未注入前命中判定退化，但不会崩溃） */
    var handleMetrics: HandleMetrics? = null

    var active by mutableStateOf(false)
        private set
    var chapter by mutableIntStateOf(-1)
        private set
    var start by mutableIntStateOf(-1)
        private set
    var end by mutableIntStateOf(-1)
        private set
    var focusOff by mutableIntStateOf(-1)
        private set
    var dragging by mutableStateOf(false)
        private set
    /** 分页模式：角落翻页保持方向（-1=上一页 / 0=无 / +1=下一页） */
    var flipDir by mutableIntStateOf(0)
        private set
    /** 分页模式：角落翻页保持进度（0..1，满 1 才翻页） */
    var flipProgress by mutableFloatStateOf(0f)
        private set

    private enum class DragMode { GROW, START_HANDLE, END_HANDLE }
    private var mode = DragMode.GROW
    private var fixedAnchor = 0
    private var lastEmitAt = 0L
    private var scrollJob: Job? = null
    private var flipJob: Job? = null
    private var lastPos = Offset.Zero

    /** 自动滚动协程作用域（宿主注入 rememberCoroutineScope） */
    var scrollScope: CoroutineScope? = null

    /** 选区几何（渲染层读取）：逐行矩形 */
    val selectionRects: List<Rect>
        get() = if (active && start >= 0 && end > start) target.selectionRects(chapter, start, end) else emptyList()

    /** 起点手柄尖角顶点（第一字符左上角，root px） */
    val startHandleVertex: Offset?
        get() = if (active && start >= 0) target.rectForCharOffset(chapter, start)?.topLeft else null

    /** 终点手柄尖角顶点（最后字符右下角，root px） */
    val endHandleVertex: Offset?
        get() = if (active && end > start) target.rectForCharOffset(chapter, end - 1)?.bottomRight else null

    /** 手柄本体的命中矩形（root px；尖角朝下 = 起点柄，朝上 = 终点柄） */
    private fun handleBodyRect(vertex: Offset, pointDown: Boolean): Rect {
        val m = handleMetrics
        if (m == null) return Rect(vertex.x, vertex.y, vertex.x, vertex.y)
        val w = m.bodyWidth
        val total = m.bodyHeight + m.tipHeight
        return if (pointDown) {
            Rect(vertex.x - w / 2f, vertex.y - total, vertex.x + w / 2f, vertex.y)
        } else {
            Rect(vertex.x - w / 2f, vertex.y, vertex.x + w / 2f, vertex.y + total)
        }
    }

    /** 长按触发：字符级选区（初始 = 长按点单字符） */
    fun begin(hit: Pair<Int, Int>) {
        active = true
        dragging = true
        mode = DragMode.GROW
        chapter = hit.first
        start = hit.second
        end = hit.second + 1
        focusOff = hit.second
        fixedAnchor = hit.second
        onActiveChanged(true)
        onSelectionUpdated(null) // 拖动期间隐藏气泡
    }

    /** 长按确认后的命中测试（根裁判在长按判定通过时调用；返回 null 表示未命中正文） */
    fun hitTest(pos: Offset): Pair<Int, Int>? = target.charOffsetAt(pos)

    /** 拖动中（初始扩选或拖柄） */
    fun drag(pos: Offset) {
        if (!dragging) return
        lastPos = pos
        val hit = target.charOffsetAt(pos)
        if (hit != null && hit.first == chapter) {
            focusOff = hit.second
            applyModeFocus(hit.second)
        }
        emitThrottled()
    }

    /** 按当前拖拽模式应用焦点 */
    private fun applyModeFocus(focus: Int) {
        when (mode) {
            DragMode.GROW -> {
                start = minOf(fixedAnchor, focus)
                end = maxOf(fixedAnchor, focus) + 1
            }
            DragMode.START_HANDLE -> start = minOf(fixedAnchor, focus)
            DragMode.END_HANDLE -> end = maxOf(fixedAnchor, focus) + 1
        }
    }

    /** 松手 → Active（选区保持，双柄可多次调整） */
    fun up() {
        if (!active) return
        dragging = false
        stopAutoScroll()
        if (end > start) emit(focusOff)
    }

    /** Active 态 down 判定：0=选区外 1=选区内 2=起点柄 3=终点柄 */
    fun downInActive(pos: Offset): Int {
        if (!active) return 0
        val startRect = startHandleVertex?.let { handleBodyRect(it, pointDown = true) }
        if (startRect != null && startRect.inflate(handleHitRadiusPx).contains(pos)) return 2
        val endRect = endHandleVertex?.let { handleBodyRect(it, pointDown = false) }
        if (endRect != null && endRect.inflate(handleHitRadiusPx).contains(pos)) return 3
        val inside = target.selectionRects(chapter, start, end)
            .any { it.inflate(handleHitRadiusPx / 2f).contains(pos) }
        return if (inside) 1 else 0
    }

    /** 开始拖柄 */
    fun beginHandleDrag(which: Int) {
        if (!active) return
        dragging = true
        mode = if (which == 2) DragMode.START_HANDLE else DragMode.END_HANDLE
        fixedAnchor = if (which == 2) end - 1 else start
    }

    /** 边缘自动滚动（根裁判按帧调用：deltaPx > 0 = 阅读前进），滚动中持续重命中更新焦点/选区 */
    fun autoScrollStart(deltaPx: Float) {
        val scope = scrollScope ?: return
        if (scrollJob == null) {
            scrollJob = scope.launch {
                while (isActive) {
                    target.scrollByForSelection(deltaPx)
                    delay(16)
                    // 滚动后重命中：选区焦点跟随内容（跨块/跨页连续选区的核心）
                    val hit = target.charOffsetAt(lastPos)
                    if (hit != null && hit.first == chapter) {
                        focusOff = hit.second
                        applyModeFocus(hit.second)
                        emitThrottled()
                    }
                }
            }
        }
    }

    fun autoScrollStop() {
        stopAutoScroll()
    }

    private fun stopAutoScroll() {
        scrollJob?.cancel()
        scrollJob = null
    }

    /** 分页模式：角落翻页保持（进度满才翻页）。direction = -1 上一页 / +1 下一页。 */
    fun flipHoldStart(direction: Int) {
        if (direction == 0) {
            flipHoldStop()
            return
        }
        val scope = scrollScope ?: return
        if (flipJob != null && flipDir == direction) return
        stopFlipHold()
        flipDir = direction
        flipProgress = 0f
        flipJob = scope.launch {
            while (isActive) {
                delay(16)
                flipProgress = (flipProgress + 16f / FLIP_HOLD_MS).coerceAtMost(1f)
                if (flipProgress >= 1f) {
                    flipProgress = 0f
                    // 触发翻页（TxtPagedViewer 按 deltaPx 正负翻上/下一页）
                    target.scrollByForSelection(direction.toFloat())
                }
            }
        }
    }

    fun flipHoldStop() {
        stopFlipHold()
    }

    private fun stopFlipHold() {
        flipJob?.cancel()
        flipJob = null
        flipDir = 0
        flipProgress = 0f
    }

    /** 收起选区 */
    fun dismiss() {
        stopAutoScroll()
        stopFlipHold()
        active = false
        dragging = false
        chapter = -1; start = -1; end = -1; focusOff = -1
        onActiveChanged(false)
        onSelectionUpdated(null)
    }

    /** 外部清除（HUD 打开等）同步回 Idle */
    fun resetIfExternalCleared(selectionActive: Boolean) {
        if (!selectionActive && active) dismiss()
    }

    private companion object {
        /** 分页模式角落翻页保持时长：进度从 0 累到 1 的毫秒数 */
        const val FLIP_HOLD_MS = 500f
    }

    private fun emit(anchorOff: Int) {
        if (!active || chapter < 0 || start < 0 || end <= start) return
        val charRect = target.rectForCharOffset(chapter, anchorOff) ?: Rect.Zero
        val lineRect = target.lineRectAt(chapter, anchorOff) ?: charRect
        // 气泡水平箭头指向锚定字符中心（保证箭头落在选区文字上），垂直基准取锚定字符所在行
        val arrowPoint = if (charRect != Rect.Zero) {
            Offset(charRect.center.x, lineRect.center.y)
        } else {
            Offset(lineRect.right, lineRect.center.y)
        }
        onSelectionUpdated(
            SelectionState(
                chapterIndex = chapter,
                start = start,
                end = end,
                text = target.originalText(chapter, start, end),
                anchorRect = lineRect,
                anchorLineEnd = arrowPoint,
            )
        )
    }

    private fun emitThrottled() {
        val now = SystemClock.uptimeMillis()
        if (now - lastEmitAt >= 100L) {
            lastEmitAt = now
            if (end > start) emit(focusOff)
        }
    }
}
