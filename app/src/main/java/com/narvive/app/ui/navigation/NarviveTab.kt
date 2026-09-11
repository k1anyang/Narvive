package com.narvive.app.ui.navigation

import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.AutoStories
import androidx.compose.material.icons.rounded.BarChart
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.automirrored.rounded.StickyNote2
import androidx.compose.ui.graphics.vector.ImageVector
import com.narvive.app.R

/**
 * 底部导航项。
 *
 * [label] 用字符串资源 id 而非硬编码文案，以支持界面语言切换（见 docs/i18n.md）；
 * 消费方用 `stringResource(tab.label)` 解析。
 */
enum class NarviveTab(
    val route: String,
    @StringRes val label: Int,
    val icon: ImageVector,
) {
    Library("library", R.string.tab_library, Icons.Rounded.AutoStories),
    Notes("notes", R.string.tab_notes, Icons.AutoMirrored.Rounded.StickyNote2),
    Ai("ai", R.string.tab_ai, Icons.Rounded.AutoAwesome),
    Stats("stats", R.string.tab_stats, Icons.Rounded.BarChart),
    Settings("settings", R.string.tab_settings, Icons.Rounded.Settings),
}
