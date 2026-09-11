package com.narvive.app.ui.screen.reader.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.narvive.app.R
import com.narvive.app.service.reader.ReadSettings
import com.narvive.app.ui.theme.NarviveShape
import java.util.Locale
import kotlin.math.roundToInt

/** 恢复默认设置：与 ReadSettings 默认值一致 */
private val DEFAULT_LINE_HEIGHT = 1.4f
private val DEFAULT_PARAGRAPH_SPACING = 0.0f
private val DEFAULT_FIRST_LINE_INDENT = 2f
private val DEFAULT_VERTICAL_MARGIN = 0.8f
private val DEFAULT_HORIZONTAL_MARGIN = 1f

/** 小数步进：按 0.1 为单位增减并夹取范围，避免浮点累积误差 */
private fun stepFloat(v: Float, delta: Int, min: Float, max: Float): Float {
    val tenths = ((v * 10f).roundToInt() + delta)
        .coerceIn((min * 10f).roundToInt(), (max * 10f).roundToInt())
    return tenths / 10f
}

private fun formatFloat(v: Float): String = String.format(Locale.US, "%.1f", v)

/**
 * 自定义间距设置窗口（底部弹出）：
 * 标题区：左「自定义间距」/ 右「恢复默认设置」
 * 内容区 5 行：行间距 / 段间距 / 首行缩进 / 上下边距 / 左右边距，每行 − 数值 +。
 * 所有调整实时生效（onApply）；「恢复默认设置」同样实时生效且窗口保持打开。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CustomSpacingBottomSheet(
    settings: ReadSettings,
    onApply: (ReadSettings) -> Unit,
    onDismiss: () -> Unit,
    /** EPUB 开启出版方样式时，行距/段距/首行缩进由出版方控制，相关行置灰 */
    publisherStylesActive: Boolean = false,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.50f)
                .padding(horizontal = 20.dp)
                .padding(bottom = 16.dp),
        ) {
            // ── 标题区 ──
            Row(
                Modifier.fillMaxWidth().padding(bottom = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(stringResource(R.string.reader_custom_spacing), style = MaterialTheme.typography.titleLarge)
                Spacer(Modifier.weight(1f))
                TextButton(onClick = {
                    onApply(
                        settings.copy(
                            lineHeight = DEFAULT_LINE_HEIGHT,
                            paragraphSpacing = DEFAULT_PARAGRAPH_SPACING,
                            firstLineIndent = DEFAULT_FIRST_LINE_INDENT,
                            verticalMargin = DEFAULT_VERTICAL_MARGIN,
                            horizontalMargin = DEFAULT_HORIZONTAL_MARGIN,
                        )
                    )
                }) {
                    Text(stringResource(R.string.reader_restore_defaults), style = MaterialTheme.typography.bodySmall)
                }
            }

            HorizontalDivider(Modifier.padding(bottom = 6.dp))

            // ── 内容区：5 行 ──
            // 第1行：行间距 1.0–3.0 小数
            StepperRow(
                label = stringResource(R.string.reader_line_spacing),
                valueText = formatFloat(settings.lineHeight),
                enabled = !publisherStylesActive,
                onDecrease = { onApply(settings.copy(lineHeight = stepFloat(settings.lineHeight, -1, 1.0f, 3.0f))) },
                onIncrease = { onApply(settings.copy(lineHeight = stepFloat(settings.lineHeight, 1, 1.0f, 3.0f))) },
            )
            // 第2行：段间距（em）0–3.0 小数，1.0 = 一个字高
            StepperRow(
                label = stringResource(R.string.reader_paragraph_spacing),
                valueText = formatFloat(settings.paragraphSpacing),
                enabled = !publisherStylesActive,
                onDecrease = { onApply(settings.copy(paragraphSpacing = stepFloat(settings.paragraphSpacing, -1, 0f, 3.0f))) },
                onIncrease = { onApply(settings.copy(paragraphSpacing = stepFloat(settings.paragraphSpacing, 1, 0f, 3.0f))) },
            )
            // 第3行：首行缩进 0–4 整数
            StepperRow(
                label = stringResource(R.string.reader_first_line_indent),
                valueText = settings.firstLineIndent.roundToInt().toString(),
                enabled = !publisherStylesActive,
                onDecrease = { onApply(settings.copy(firstLineIndent = (settings.firstLineIndent.roundToInt() - 1).coerceIn(0, 4).toFloat())) },
                onIncrease = { onApply(settings.copy(firstLineIndent = (settings.firstLineIndent.roundToInt() + 1).coerceIn(0, 4).toFloat())) },
            )
            // 第4行：上下边距 0–2.0 小数
            StepperRow(
                label = stringResource(R.string.reader_vertical_margin),
                valueText = formatFloat(settings.verticalMargin),
                onDecrease = { onApply(settings.copy(verticalMargin = stepFloat(settings.verticalMargin, -1, 0f, 2.0f))) },
                onIncrease = { onApply(settings.copy(verticalMargin = stepFloat(settings.verticalMargin, 1, 0f, 2.0f))) },
            )
            // 第5行：左右边距 0–2.0 小数
            StepperRow(
                label = stringResource(R.string.reader_horizontal_margin),
                valueText = formatFloat(settings.horizontalMargin),
                onDecrease = { onApply(settings.copy(horizontalMargin = stepFloat(settings.horizontalMargin, -1, 0f, 2.0f))) },
                onIncrease = { onApply(settings.copy(horizontalMargin = stepFloat(settings.horizontalMargin, 1, 0f, 2.0f))) },
            )
        }
    }
}

@Composable
private fun StepperRow(
    label: String,
    valueText: String,
    enabled: Boolean = true,
    onDecrease: () -> Unit,
    onIncrease: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
            .alpha(if (enabled) 1f else 0.38f),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        StepButton("-", enabled, onDecrease)
        Spacer(Modifier.width(12.dp))
        Text(
            valueText,
            style = MaterialTheme.typography.titleMedium,
            textAlign = TextAlign.Center,
            modifier = Modifier.width(52.dp),
        )
        Spacer(Modifier.width(12.dp))
        StepButton("+", enabled, onIncrease)
    }
}

@Composable
private fun StepButton(symbol: String, enabled: Boolean, onClick: () -> Unit) {
    Box(
        Modifier
            .size(32.dp)
            .clip(NarviveShape.Sm)
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(symbol, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
    }
}
