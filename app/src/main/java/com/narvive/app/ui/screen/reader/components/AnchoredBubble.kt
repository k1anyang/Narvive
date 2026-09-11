package com.narvive.app.ui.screen.reader.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.SubcomposeLayout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

/**
 * 选区气泡 / 高亮菜单的动态定位容器（无箭头，v1.6）：
 *  - 基准定位：以 [anchorLineEnd]（最近交互所在行的末端/锚定字符中心）为水平居中点；
 *  - 自动翻转：先置于基准行上方，上方空间不足则翻到下方；
 *  - 水平防切边：气泡以锚点水平居中后整体夹取到 [hMargin, viewport.width - hMargin]。
 * 所有几何量均为阅读根容器坐标 px；[viewport] 为根容器尺寸。
 * 锚点无效（Rect.Zero）时回退到视口中心偏上。
 */
@Composable
fun AnchoredBubble(
    anchorRect: Rect,
    anchorLineEnd: Offset,
    viewport: androidx.compose.ui.geometry.Size,
    modifier: Modifier = Modifier,
    safeTopPx: Float? = null,
    safeBottomPx: Float? = null,
    onBoundsChanged: ((Rect) -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    if (viewport.width <= 0f || viewport.height <= 0f) return
    val density = LocalDensity.current
    val gapPx = with(density) { 6.dp.toPx() }
    val hMarginPx = with(density) { 12.dp.toPx() }
    val topLimit = safeTopPx ?: with(density) { 56.dp.toPx() }
    val bottomLimitInset = safeBottomPx ?: with(density) { 32.dp.toPx() }

    // 锚点无效（EPUB 老路径等）→ 回退视口中心偏上
    val effRect = if (anchorRect == Rect.Zero) {
        val cx = viewport.width / 2f
        val cy = viewport.height * 0.35f
        Rect(cx, cy, cx, cy)
    } else anchorRect
    val effEnd = if (anchorLineEnd == Offset.Zero) {
        Offset(effRect.right, effRect.top)
    } else anchorLineEnd

    SubcomposeLayout(modifier = modifier) { constraints ->
        val vw = viewport.width.roundToInt()
        val vh = viewport.height.roundToInt()
        if (vw <= 0 || vh <= 0) return@SubcomposeLayout layout(constraints.maxWidth, constraints.maxHeight) {}

        // 清零最小约束：fillMaxSize 传入的精确约束 minWidth=视口宽，不清会让气泡被强制撑满全屏，
        // 破坏 wrap-content 测量与水平防切边定位
        val contentConstraints = constraints.copy(
            minWidth = 0,
            minHeight = 0,
            maxWidth = (vw - 2 * hMarginPx).roundToInt().coerceAtLeast(1),
        )
        val bubble = subcompose("bubble") { content() }.first().measure(contentConstraints)
        val bw = bubble.width
        val bh = bubble.height
        if (bw <= 0 || bh <= 0) return@SubcomposeLayout layout(vw, vh) {}

        val gap = gapPx.roundToInt()

        // ── 水平：以锚点 x 居中，夹取防切边 ──
        val minX = hMarginPx.roundToInt()
        val maxX = (vw - hMarginPx - bw).roundToInt().coerceAtLeast(minX)
        val bubbleX = (effEnd.x - bw / 2f).roundToInt().coerceIn(minX, maxX)

        // ── 垂直：先上置，放不下翻到下置 ──
        val aboveY = (effRect.top - gap - bh).roundToInt()
        val belowY = (effRect.bottom + gap).roundToInt()
        val bottomLimit = (vh - bottomLimitInset - bh).roundToInt()
        val placeAbove = aboveY >= topLimit.roundToInt() || belowY > bottomLimit
        val bubbleY = if (placeAbove) {
            aboveY.coerceAtLeast(topLimit.roundToInt())
        } else {
            belowY.coerceAtMost(bottomLimit.coerceAtLeast(topLimit.roundToInt()))
        }

        layout(vw, vh) {
            bubble.place(bubbleX, bubbleY)
            // 上报气泡实际矩形（根裁判据此放行气泡上的点按，避免误判为「点选区外」）
            onBoundsChanged?.invoke(
                Rect(
                    bubbleX.toFloat(),
                    bubbleY.toFloat(),
                    (bubbleX + bw).toFloat(),
                    (bubbleY + bh).toFloat(),
                )
            )
        }
    }
}
