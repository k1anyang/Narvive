package com.narvive.app.ui.navigation

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.narvive.app.ui.components.BookCoverFallback
import com.narvive.app.ui.theme.NarviveMotion

/**
 * 阅读页顶部的封面展开遮罩。
 *
 * 数据由 [ReaderOpenAnimState] 在详情页点击按钮时写入，本组件进入组合时消费一次。
 * 动画期间覆盖在 ReaderScreen 之上，播放完毕后自动移除。
 */
@Composable
fun BookOpenOverlay() {
    val animation = ReaderOpenAnimState.pending ?: return
    var visible by remember(animation) { mutableStateOf(true) }
    if (!visible) return

    val progress = remember { Animatable(0f) }
    val easing = NarviveMotion.EasingEmphasizedDecelerate
    val density = LocalDensity.current

    LaunchedEffect(animation) {
        progress.snapTo(0f)
        progress.animateTo(1f, tween(durationMillis = NarviveMotion.Slow, easing = easing))
        ReaderOpenAnimState.clear()
        visible = false
    }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val fullWidth = constraints.maxWidth.toFloat()
        val fullHeight = constraints.maxHeight.toFloat()
        val rect = animation.rect
        val p = progress.value.coerceIn(0f, 1f)

        val startWidth = rect.width.coerceAtLeast(1).toFloat()
        val startHeight = rect.height.coerceAtLeast(1).toFloat()
        val scaleX = lerp(1f, fullWidth / startWidth, p)
        val scaleY = lerp(1f, fullHeight / startHeight, p)
        val translationX = -rect.left * p
        val translationY = -rect.top * p
        val alpha = if (p >= 0.78f) ((1f - p) / 0.22f).coerceIn(0f, 1f) else 1f

        Box(
            modifier = Modifier
                .offset { IntOffset(rect.left, rect.top) }
                .size(
                    width = (rect.width.coerceAtLeast(1).toFloat() / density.density).dp,
                    height = (rect.height.coerceAtLeast(1).toFloat() / density.density).dp,
                )
                .graphicsLayer {
                    transformOrigin = TransformOrigin(0f, 0f)
                    this.scaleX = scaleX
                    this.scaleY = scaleY
                    this.translationX = translationX
                    this.translationY = translationY
                    this.alpha = alpha
                },
            contentAlignment = Alignment.Center,
        ) {
            if (animation.coverPath != null) {
                AsyncImage(
                    model = animation.coverPath,
                    contentDescription = animation.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                BookCoverFallback(
                    title = animation.title,
                    modifier = Modifier.fillMaxSize(),
                    fontSize = 22.sp,
                )
            }
        }
    }
}

private fun lerp(start: Float, stop: Float, fraction: Float): Float =
    start + (stop - start) * fraction
