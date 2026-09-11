package com.narvive.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.narvive.app.R
import com.narvive.app.ui.theme.NarviveShape
import com.narvive.app.ui.theme.SemanticColors
import kotlin.math.roundToInt

/**
 * 左滑露出「删除」单按钮的容器。
 * 删除按钮：扁平化设计、圆形图标、无文字（红色圆底 + 白色删除图标）。
 * 前景内容可横向左滑露出右侧删除区；点按前景本身仍触发原有点击。
 */
@Composable
fun SwipeToDeleteRow(
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val actionWidthPx = with(LocalDensity.current) { 64.dp.toPx() }
    var offsetX by remember { mutableFloatStateOf(0f) }
    var revealed by remember { mutableStateOf(false) }

    Box(modifier = modifier.clip(NarviveShape.Md)) {
        // 右侧删除区（背景层，扁平无底色，仅红色图标）
        Box(
            Modifier
                .align(Alignment.CenterEnd)
                .fillMaxHeight()
                .width(64.dp),
            contentAlignment = Alignment.Center,
        ) {
            Box(
                Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .clickable {
                        offsetX = 0f; revealed = false; onDelete()
                    },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Rounded.Delete,
                    contentDescription = stringResource(R.string.common_delete),
                    tint = SemanticColors.Danger,
                    modifier = Modifier.size(22.dp),
                )
            }
        }
        // 前景内容（可左滑）
        Box(
            Modifier
                .offset { IntOffset(offsetX.roundToInt(), 0) }
                .pointerInput(Unit) {
                    detectHorizontalDragGestures(
                        onDragEnd = {
                            revealed = offsetX < -actionWidthPx / 2f
                            offsetX = if (revealed) -actionWidthPx else 0f
                        },
                        onDragCancel = { offsetX = if (revealed) -actionWidthPx else 0f },
                    ) { change, dragAmount ->
                        change.consume()
                        offsetX = (offsetX + dragAmount).coerceIn(-actionWidthPx, 0f)
                    }
                },
        ) {
            content()
        }
    }
}
