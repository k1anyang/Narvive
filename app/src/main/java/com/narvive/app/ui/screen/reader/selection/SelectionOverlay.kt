package com.narvive.app.ui.screen.reader.selection

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp

/**
 * 选区渲染层（P1）：仅负责绘制选区背景、双柄与分页模式翻页角落提示，不持有任何手势。
 * 手势由 ReaderScreen 根裁判循环驱动 [SelectionController]（本 Compose 版本下，
 * 根 Box 之外的 pointerInput 事件流在 down 后中断，因此手势统一收敛到根裁判）。
 *
 * 手柄样式（书签/水滴倒五角形标签）：
 *  - 上部：圆角矩形（四角圆角），内部 3 条水平灰色短横线（可拖拽指示）；
 *  - 下部：向下收尖，尖角顶点锐利无圆角，精确指向选区边界；
 *  - 起点柄尖角朝下，顶点落在首字符左上角，本体向上伸出；
 *  - 终点柄尖角朝上，顶点落在末字符右下角，本体向下伸出。
 */
@Composable
fun SelectionOverlay(
    controller: SelectionController,
    selectionColor: Color,
    modifier: Modifier = Modifier,
    /** 分页（slide/none）模式下显示左上/右下翻页角落提示与进度 */
    showFlipCorners: Boolean = false,
) {
    val density = LocalDensity.current
    val bodyW = with(density) { 16.5.dp.toPx() }
    val bodyH = with(density) { 19.5.dp.toPx() }
    val tipH = with(density) { 10.dp.toPx() }
    val cornerR = with(density) { 4.dp.toPx() }
    val strokeW = with(density) { 1.dp.toPx() }
    val lineW = with(density) { 1.5.dp.toPx() }
    val linePad = with(density) { 4.dp.toPx() }

    // 命中判定与绘制共用的几何/容差
    controller.handleMetrics = SelectionController.HandleMetrics(bodyW, bodyH, tipH)
    controller.handleHitRadiusPx = with(density) { 12.dp.toPx() }

    // 组合期读取选区几何：本 Compose 版本下 draw 阶段对 snapshot 状态的观测不可靠
    // （与「根 Box 之外 pointerInput 只收 down」同类框架问题），改为组合期读取，
    // start/end/flip 变化触发重组 → Canvas 用捕获的新值重绘。
    val active = controller.active
    val selectionRects = controller.selectionRects
    val startVertex = controller.startHandleVertex
    val endVertex = controller.endHandleVertex
    val flipDir = controller.flipDir
    val flipProgress = controller.flipProgress

    // 返回键：激活时优先收起选区
    BackHandler(enabled = active) { controller.dismiss() }

    Canvas(modifier = modifier) {
        selectionRects.forEach { r ->
            drawRect(color = selectionColor, topLeft = r.topLeft, size = Size(r.width, r.height))
        }
        startVertex?.let { v ->
            drawSelectionHandle(v, pointDown = true, bodyW, bodyH, tipH, cornerR, strokeW, lineW, linePad)
        }
        endVertex?.let { v ->
            drawSelectionHandle(v, pointDown = false, bodyW, bodyH, tipH, cornerR, strokeW, lineW, linePad)
        }
        if (showFlipCorners && active) {
            drawFlipCorners(flipDir, flipProgress)
        }
    }
}

private val HandleFill = Color(0xFFFFFFFF)
private val HandleOutline = Color(0xFFD1D5DB)
private val HandleLine = Color(0xFF9CA3AF)

/** 绘制书签形手柄；[vertex] 为尖角顶点（root px），[pointDown] = 尖角朝下（起点柄） */
private fun DrawScope.drawSelectionHandle(
    vertex: Offset,
    pointDown: Boolean,
    bodyW: Float,
    bodyH: Float,
    tipH: Float,
    cornerR: Float,
    strokeW: Float,
    lineW: Float,
    linePad: Float,
) {
    val w = bodyW
    val h = bodyH
    val t = tipH
    val r = cornerR
    val left = vertex.x - w / 2f
    val top = if (pointDown) vertex.y - (h + t) else vertex.y

    val path = Path()
    if (pointDown) {
        // 尖角朝下，顶点 = (left + w/2, top + h + t)
        path.moveTo(left + r, top)
        path.lineTo(left + w - r, top)
        path.quadraticTo(left + w, top, left + w, top + r)
        path.lineTo(left + w, top + h)
        path.lineTo(left + w / 2f, top + h + t)
        path.lineTo(left, top + h)
        path.lineTo(left, top + r)
        path.quadraticTo(left, top, left + r, top)
    } else {
        // 尖角朝上，顶点 = (left + w/2, top)
        path.moveTo(left + w / 2f, top)
        path.lineTo(left + w, top + t)
        path.lineTo(left + w, top + t + h - r)
        path.quadraticTo(left + w, top + t + h, left + w - r, top + t + h)
        path.lineTo(left + r, top + t + h)
        path.quadraticTo(left, top + t + h, left, top + t + h - r)
        path.lineTo(left, top + t)
    }
    path.close()

    drawPath(path, HandleFill)
    drawPath(path, HandleOutline, style = Stroke(strokeW))

    val lineYBase = if (pointDown) top else top + t
    for (i in 1..3) {
        val ly = lineYBase + h * i / 4f
        drawLine(
            color = HandleLine,
            start = Offset(left + linePad, ly),
            end = Offset(left + w - linePad, ly),
            strokeWidth = lineW,
            cap = StrokeCap.Round,
        )
    }
}

/** 分页模式：左上/右下翻页角落范围提示 + 保持进度环（进度满才翻页） */
private fun DrawScope.drawFlipCorners(flipDir: Int, flipProgress: Float) {
    val cornerPx = 96.dp.toPx()
    val hint = Color(0x33FFFFFF)
    val active = Color(0x99FFFFFF)
    // 左上角 → 上一页
    drawRect(hint, Offset.Zero, Size(cornerPx, cornerPx))
    drawChevron(cornerPx / 2f, cornerPx / 2f, dir = -1, color = Color(0x66FFFFFF))
    // 右下角 → 下一页
    drawRect(hint, Offset(size.width - cornerPx, size.height - cornerPx), Size(cornerPx, cornerPx))
    drawChevron(size.width - cornerPx / 2f, size.height - cornerPx / 2f, dir = 1, color = Color(0x66FFFFFF))

    if (flipDir != 0) {
        val cx = if (flipDir < 0) cornerPx / 2f else size.width - cornerPx / 2f
        val cy = if (flipDir < 0) cornerPx / 2f else size.height - cornerPx / 2f
        val r = 20.dp.toPx()
        val stroke = 3.dp.toPx()
        drawCircle(Color(0x33FFFFFF), r, Offset(cx, cy), style = Stroke(stroke))
        drawArc(
            color = active,
            startAngle = -90f,
            sweepAngle = (flipProgress * 360f).coerceIn(0f, 360f),
            useCenter = false,
            topLeft = Offset(cx - r, cy - r),
            size = Size(r * 2, r * 2),
            style = Stroke(stroke, cap = StrokeCap.Round),
        )
    }
}

/** 角落方向箭头（-1 左 / +1 右） */
private fun DrawScope.drawChevron(cx: Float, cy: Float, dir: Int, color: Color) {
    val s = 7.dp.toPx()
    val path = Path()
    if (dir < 0) {
        path.moveTo(cx + s, cy - s * 1.4f)
        path.lineTo(cx - s * 0.4f, cy)
        path.lineTo(cx + s, cy + s * 1.4f)
    } else {
        path.moveTo(cx - s, cy - s * 1.4f)
        path.lineTo(cx + s * 0.4f, cy)
        path.lineTo(cx - s, cy + s * 1.4f)
    }
    drawPath(path, color, style = Stroke(2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
}
