package com.narvive.app.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.material3.MaterialTheme
import kotlin.math.cos
import kotlin.math.sin

/** 短圆角条旋转 loading（八段圆角线段组），与 WebDAV「立即同步」按钮一致 */
@Composable
fun EightSegmentLoading(
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.primary,
    barCount: Int = 8,
) {
    val transition = rememberInfiniteTransition(label = "eight_segment_loading")
    val rotation by transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1000, easing = LinearEasing),
        ),
        label = "rotation",
    )
    Canvas(modifier) {
        drawEightSegmentLoading(rotation, color, barCount)
    }
}

private fun DrawScope.drawEightSegmentLoading(rotation: Float, color: Color, barCount: Int) {
    val strokeWidth = size.minDimension * 0.14f
    val radius = (size.minDimension - strokeWidth) / 2f
    val center = Offset(size.width / 2f, size.height / 2f)
    for (i in 0 until barCount) {
        val angle = Math.toRadians((rotation + i * (360f / barCount)).toDouble())
        val dx = cos(angle).toFloat()
        val dy = sin(angle).toFloat()
        val inner = radius * 0.58f
        val start = Offset(center.x + dx * inner, center.y + dy * inner)
        val end = Offset(center.x + dx * radius, center.y + dy * radius)
        drawLine(
            color = color.copy(alpha = 0.30f + 0.70f * (i.toFloat() / barCount)),
            start = start,
            end = end,
            strokeWidth = strokeWidth,
            cap = StrokeCap.Round,
        )
    }
}
