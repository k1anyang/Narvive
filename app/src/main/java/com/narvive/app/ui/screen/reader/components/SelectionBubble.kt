package com.narvive.app.ui.screen.reader.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.NoteAdd
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Draw
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.EditNote
import androidx.compose.material.icons.rounded.Translate
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.narvive.app.R
import com.narvive.app.ui.theme.AnnotationPalette
import com.narvive.app.ui.theme.LocalElevation
import com.narvive.app.ui.theme.NarviveMotion
import com.narvive.app.ui.theme.NarviveShape
import kotlinx.coroutines.launch

/**
 * 高亮 4 色（原型固定色板）；第二个元素为遗留色名占位，界面从不展示，故不参与本地化。
 */
val HighlightColors = listOf(
    Color(0xFFFACC15) to "黄",
    Color(0xFF38BDF8) to "蓝",
    Color(0xFFF472B6) to "粉",
    Color(0xFF4ADE80) to "绿",
)

/** 三种气泡菜单统一宽度：刚好容纳初始（未展开）形态，多余内容横向滚动 */
internal val BubbleMenuWidth = 240.dp

/** 气泡出现动画：缩放 + 淡入（graphicsLayer 不触发重测量，配合 SubcomposeLayout 使用安全） */
@Composable
internal fun Modifier.bubblePopIn(): Modifier {
    val scale = remember { Animatable(0.82f) }
    val alpha = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        launch { scale.animateTo(1f, tween(NarviveMotion.Fast, easing = NarviveMotion.EasingStandard)) }
        launch { alpha.animateTo(1f, tween(NarviveMotion.Fast)) }
    }
    return this.graphicsLayer {
        scaleX = scale.value
        scaleY = scale.value
        this.alpha = alpha.value
    }
}

/**
 * 选区气泡（原型屏 07）：
 *  - 默认态：高亮 / 笔记 / 复制 / 翻译 / 问 AI / 改写 / 续写；
 *  - 点「高亮」→ 色标自左侧滑入替换「高亮」文字，其余动作位置/顺序不变，无额外说明文字；
 *  - 菜单可横向滚动，整体高度与默认态一致。
 */
@Composable
fun SelectionBubble(
    selectedText: String,
    onDismiss: () -> Unit,
    onHighlight: (Color) -> Unit = {},
    onNote: () -> Unit = {},
    onCopy: () -> Unit = {},
    onTranslate: () -> Unit = {},
    onAskAi: () -> Unit = {},
    onRewrite: () -> Unit = {},
    onContinue: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    var colorMode by remember { mutableStateOf(false) }
    var pickedColor by remember { mutableStateOf(HighlightColors[0].first) }

    Column(
        modifier
            .bubblePopIn()
            .shadow(LocalElevation.current.level3, NarviveShape.Md)
            .clip(NarviveShape.Md)
            .background(MaterialTheme.colorScheme.surface)
            .padding(horizontal = 6.dp, vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(Modifier.width(BubbleMenuWidth)) {
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                verticalAlignment = Alignment.CenterVertically,
            ) {
            AnimatedContent(
                targetState = colorMode,
                transitionSpec = {
                    if (targetState) {
                        (slideInHorizontally(tween(NarviveMotion.Medium)) { -it } + fadeIn(tween(NarviveMotion.Fast))) togetherWith
                            (slideOutHorizontally(tween(NarviveMotion.Fast)) { -it } + fadeOut(tween(NarviveMotion.Fast)))
                    } else {
                        (slideInHorizontally(tween(NarviveMotion.Medium)) { it } + fadeIn(tween(NarviveMotion.Fast))) togetherWith
                            (slideOutHorizontally(tween(NarviveMotion.Fast)) { it } + fadeOut(tween(NarviveMotion.Fast)))
                    }
                },
                label = "highlightColorMode",
            ) { mode ->
                if (mode) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        HighlightColors.forEach { (color, _) ->
                            val selected = pickedColor == color
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier
                                    .clip(NarviveShape.Sm)
                                    .clickable {
                                        pickedColor = color
                                        onHighlight(color)
                                    }
                                    .padding(horizontal = 6.dp, vertical = 2.dp),
                            ) {
                                Box(
                                    Modifier
                                        .size(26.dp)
                                        .clip(CircleShape)
                                        .background(color)
                                        .border(
                                            width = if (selected) 2.dp else 1.dp,
                                            color = if (selected) {
                                                MaterialTheme.colorScheme.onSurface
                                            } else {
                                                MaterialTheme.colorScheme.outline
                                            },
                                            shape = CircleShape,
                                        ),
                                )
                            }
                        }
                    }
                } else {
                    BubbleAction(icon = Icons.Rounded.Draw, label = stringResource(R.string.reader_highlight), labelColor = AnnotationPalette.Highlight) { colorMode = true }
                }
            }
            BubbleAction(icon = Icons.Rounded.EditNote, label = stringResource(R.string.reader_note)) { onNote() }
            BubbleAction(icon = Icons.Rounded.ContentCopy, label = stringResource(R.string.reader_copy)) { onCopy() }
            BubbleAction(icon = Icons.Rounded.Translate, label = stringResource(R.string.reader_translate), labelColor = MaterialTheme.colorScheme.primary) { onTranslate() }
            BubbleAction(icon = Icons.Rounded.AutoAwesome, label = stringResource(R.string.reader_ask_ai), labelColor = MaterialTheme.colorScheme.primary) { onAskAi() }
            BubbleAction(icon = Icons.Rounded.Edit, label = stringResource(R.string.reader_rewrite), labelColor = MaterialTheme.colorScheme.tertiary) { onRewrite() }
            BubbleAction(icon = Icons.AutoMirrored.Rounded.NoteAdd, label = stringResource(R.string.reader_continue), labelColor = MaterialTheme.colorScheme.tertiary) { onContinue() }
            }
        }
    }
}

@Composable
internal fun BubbleAction(
    icon: ImageVector,
    label: String,
    labelColor: Color = MaterialTheme.colorScheme.onSurface,
    onClick: () -> Unit,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clip(NarviveShape.Sm)
            .clickable(onClick = onClick)
            .padding(horizontal = 7.dp, vertical = 4.dp),
    ) {
        Icon(icon, contentDescription = label, tint = labelColor, modifier = Modifier.size(18.dp))
        Text(label, color = labelColor, style = MaterialTheme.typography.labelSmall)
    }
}
