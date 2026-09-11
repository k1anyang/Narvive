package com.narvive.app.ui.screen.reader.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.FontDownload
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.narvive.app.R
import com.narvive.app.service.reader.ReadSettings
import com.narvive.app.ui.theme.NarviveShape

data class ThemeOption(val key: String, val label: String, val pageColor: Color, val inkColor: Color)

// label 为遗留占位字段：界面从不展示主题名（主题名统一取 R.string.reading_theme_*），故不参与本地化。
val themeOptions = listOf(
    ThemeOption("paper", "米白", Color(0xFFF5F1E6), Color(0xFF333333)),
    ThemeOption("sepia", "牛皮", Color(0xFFE6D2B5), Color(0xFF4A3B2A)),
    ThemeOption("green", "豆沙", Color(0xFFC7EDCC), Color(0xFF2F4F2F)),
    ThemeOption("dark", "天空", Color(0xFFAEC6CF), Color(0xFF2C3E50)),
    ThemeOption("black", "深灰", Color(0xFF2C2C2C), Color(0xFFCCCCCC)),
)

@Composable
private fun CustomTextButton(onClick: () -> Unit) {
    TextButton(onClick = onClick) {
        Text(
            stringResource(R.string.reading_theme_custom),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
        )
    }
}

/** 行距快捷预设：value=行距倍数，gapRatio=图标线条间距比例（越大越稀疏）；label 为遗留占位字段、界面从不展示，故不参与本地化 */
private data class LineHeightPreset(val label: String, val value: Float, val gapRatio: Float)

private val lineHeightPresets = listOf(
    LineHeightPreset("紧凑", 1.2f, 0.16f),
    LineHeightPreset("标准", 1.6f, 0.26f),
    LineHeightPreset("宽松", 2.0f, 0.36f),
    LineHeightPreset("舒展", 2.5f, 0.45f),
)

/** 翻页动画 id 列表（展示文案取自 reader_flip_* 资源，id 本身为持久化值不可改） */
private val flipAnimOptions = listOf("slide", "updown", "none")

/**
 * Aa 阅读设置面板：
 * 一行 — 字体大小 | 字体按钮
 * 二行 — 行间距 紧凑/标准/宽松/舒展 + 自定义
 * 三行 — 5 预设主题（背景色 + 文字色） + 自定义
 * 四行 — 翻页动画 3 选 1
 * 五行 — 自动翻页开关 + 更多设置（等分空间）
 */
@Composable
fun AaPanel(
    settings: ReadSettings,
    onApply: (ReadSettings) -> Unit,
    /** 点击「字体」→ 打开字体设置页 */
    onFontClick: () -> Unit = {},
    /** 点击「自定义」→ 打开自定义间距窗口（AaPanel 第二行右侧按钮） */
    onCustomSpacingClick: () -> Unit = {},
    /** 点击「自定义」→ 打开自定义主题窗口（AaPanel 第三行右侧按钮） */
    onCustomThemeClick: () -> Unit = {},
    /** 点击「更多设置」→ 打开更多设置页 */
    onMoreSettingsClick: () -> Unit = {},
    /** 自动翻页是否开启（受控状态） */
    isAutoFlipActive: Boolean = false,
    /** 切换自动翻页开关 */
    onAutoFlipToggle: (Boolean) -> Unit = {},
    /** 是否 EPUB 格式（用于判断出版方样式是否生效，非 EPUB 恒为 false） */
    isEpub: Boolean = false,
) {
    Column(
        Modifier
            .fillMaxWidth()
            .fillMaxHeight(0.50f)
            .padding(horizontal = 20.dp, vertical = 12.dp),
    ) {
        // EPUB 且开启出版方样式时，行距/段距/缩进由出版方控制，相关控件置灰
        val publisherStylesActive = isEpub && settings.publisherStyles
        Text(
            stringResource(R.string.reader_settings_title),
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.padding(bottom = 6.dp),
        )

        // ============ row 1: font size ============
        Row(
            Modifier.fillMaxWidth().padding(vertical = 3.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier.size(32.dp).clip(NarviveShape.Sm)
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .clickable { onApply(settings.copy(fontSize = (settings.fontSize - 1).coerceAtLeast(12))) },
                contentAlignment = Alignment.Center,
            ) {
                Text("A−", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
            }
            Spacer(Modifier.width(12.dp))
            Text(
                "${settings.fontSize}",
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.width(12.dp))
            Box(
                Modifier.size(32.dp).clip(NarviveShape.Sm)
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .clickable { onApply(settings.copy(fontSize = (settings.fontSize + 1).coerceAtMost(30))) },
                contentAlignment = Alignment.Center,
            ) {
                Text("A+", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
            }

            Spacer(Modifier.weight(1f))

            TextButton(onClick = onFontClick) {
                Icon(Icons.Rounded.FontDownload, null, Modifier.size(20.dp))
                Spacer(Modifier.width(4.dp))
                Text(stringResource(R.string.reader_font), style = MaterialTheme.typography.bodyMedium)
            }
        }

        HorizontalDivider(Modifier.padding(vertical = 2.dp))

        // ============ row 2: line height ============
        Row(
            Modifier.fillMaxWidth().padding(vertical = 3.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            lineHeightPresets.forEach { preset ->
                val selected = (settings.lineHeight - preset.value).let { kotlin.math.abs(it) } < 0.05f
                val lineColor = if (selected) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.onSurfaceVariant
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .alpha(if (publisherStylesActive) 0.38f else 1f)
                        .clip(NarviveShape.Sm)
                        .clickable(enabled = !publisherStylesActive) { onApply(settings.copy(lineHeight = preset.value)) }
                        .padding(horizontal = 6.dp, vertical = 3.dp),
                ) {
                    // 三根横线，间距随预设增大而变疏，直观表达行距
                    Canvas(Modifier.size(24.dp, 20.dp)) {
                        val strokeW = 1.8.dp.toPx()
                        val midY = size.height / 2f
                        val gap = size.height * preset.gapRatio
                        for (i in -1..1) {
                            val y = midY + i * gap
                            drawLine(lineColor, Offset(4.dp.toPx(), y), Offset(size.width - 4.dp.toPx(), y), strokeW)
                        }
                    }
                }
            }
            CustomTextButton(onClick = onCustomSpacingClick)

        }
        HorizontalDivider(Modifier.padding(vertical = 2.dp))

        // ============ row 3: theme presets (background + ink) ============
        Row(
            Modifier.fillMaxWidth().padding(vertical = 3.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                themeOptions.forEach { opt ->
                    val selected = !settings.nightMode && settings.theme == opt.key
                    Box(
                        Modifier
                            .size(28.dp)
                            .clip(CircleShape)
                            .background(opt.pageColor)
                            .then(
                                if (selected) Modifier.border(3.dp, MaterialTheme.colorScheme.primary, CircleShape)
                                else Modifier.border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f), CircleShape)
                            )
                            .clickable { onApply(settings.copy(nightMode = false, theme = opt.key)) },
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            "A",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                            color = opt.inkColor,
                        )
                    }
                }
            }
            Spacer(Modifier.weight(1f))
            CustomTextButton(onClick = onCustomThemeClick)
        }

        HorizontalDivider(Modifier.padding(vertical = 2.dp))

        // ============ row 4: flip animation ============
        Column(Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
            Text(stringResource(R.string.reader_page_flip_animation), style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(bottom = 4.dp))
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                flipAnimOptions.forEach { key ->
                    val selected = settings.pageFlipAnimation == key
                    val chipColor = if (selected) MaterialTheme.colorScheme.primaryContainer
                    else MaterialTheme.colorScheme.surfaceVariant
                    val textColor = if (selected) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurfaceVariant
                    Box(
                        Modifier
                            .clip(NarviveShape.Md)
                            .background(chipColor)
                            .clickable { onApply(settings.copy(pageFlipAnimation = key)) }
                            .padding(horizontal = 8.dp, vertical = 5.dp),
                    ) {
                        Text(
                            when (key) {
                                "slide" -> stringResource(R.string.reader_flip_slide)
                                "updown" -> stringResource(R.string.reader_flip_updown)
                                else -> stringResource(R.string.reader_flip_none)
                            },
                            style = MaterialTheme.typography.bodySmall, color = textColor,
                            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                        )
                    }
                }
            }
        }

        HorizontalDivider(Modifier.padding(vertical = 2.dp))

        // ============ row 5: auto-flip + more settings ============
        Row(
            Modifier.fillMaxWidth().padding(vertical = 3.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.reader_auto_flip), style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.width(4.dp))
                Switch(
                    checked = isAutoFlipActive,
                    onCheckedChange = onAutoFlipToggle,
                    colors = SwitchDefaults.colors(uncheckedTrackColor = MaterialTheme.colorScheme.surfaceVariant),
                )
            }
            Box(Modifier.weight(1f), contentAlignment = Alignment.CenterEnd) {
                TextButton(onClick = onMoreSettingsClick) {
                    Text(stringResource(R.string.reader_more_settings), style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Icon(Icons.Rounded.ChevronRight, null, Modifier.size(18.dp))
                }
            }
        }

        Spacer(Modifier.height(4.dp))
    }
}
