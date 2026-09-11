package com.narvive.app.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.IosShare
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.narvive.app.R
import com.narvive.app.ui.theme.NarviveShape
import com.narvive.app.ui.theme.SemanticColors
import kotlin.math.roundToInt

/**
 * 左滑露出「删除 / 导出」操作的容器（需求5/6）。
 * 前景内容可横向滑动露出右侧两个操作按钮；点按前景本身仍可触发原有点击。
 */
@Composable
fun SwipeRevealRow(
    onDelete: () -> Unit,
    onExport: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val actionWidthPx = with(LocalDensity.current) { 136.dp.toPx() }
    var offsetX by remember { mutableFloatStateOf(0f) }
    var revealed by remember { mutableStateOf(false) }

    Box(modifier = modifier.clip(NarviveShape.Md)) {
        // 右侧操作按钮（背景层）
        Box(
            Modifier
                .align(Alignment.CenterEnd)
                .fillMaxHeight()
                .width(136.dp),
        ) {
            androidx.compose.foundation.layout.Row(Modifier.fillMaxSize()) {
                RevealAction(
                    label = stringResource(R.string.common_delete),
                    icon = Icons.Rounded.Delete,
                    color = SemanticColors.Danger,
                    modifier = Modifier.weight(1f),
                ) { offsetX = 0f; revealed = false; onDelete() }
                RevealAction(
                    label = stringResource(R.string.common_export),
                    icon = Icons.Rounded.IosShare,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.weight(1f),
                ) { offsetX = 0f; revealed = false; onExport() }
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

/**
 * 单个滑动操作按钮。
 *
 * [icon] 由调用方显式指定，**不能**再由 [label] 文案推断：
 * 文案会随界面语言变化，一旦用 `label == "删除"` 这类比较，切换语言后图标就会错。
 */
@Composable
private fun RevealAction(
    label: String,
    icon: ImageVector,
    color: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Box(
        modifier.clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            icon,
            contentDescription = label,
            tint = color,
            modifier = Modifier.size(20.dp),
        )
    }
}
