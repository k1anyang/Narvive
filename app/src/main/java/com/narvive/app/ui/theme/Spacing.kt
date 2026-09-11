package com.narvive.app.ui.theme

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * 间距 Token —— 8dp 网格（docs/DESIGN.md §5）。
 *
 * 通过 [LocalSpacing] 获取：`val spacing = LocalSpacing.current`。
 * 新代码禁止散写 .dp 间距，统一走 Token；页面左右边距固定 [NarviveSpacingTokens.page]。
 */
class NarviveSpacingTokens(
    /** 2dp 图标与文字微间隙 */
    val xxxs: Dp = 2.dp,
    /** 4dp */
    val xxs: Dp = 4.dp,
    /** 6dp 内边距微调 */
    val xs: Dp = 6.dp,
    /** 8dp 组件内部间距 */
    val sm: Dp = 8.dp,
    /** 10dp Chip 间距 */
    val smd: Dp = 10.dp,
    /** 12dp 组件内部间距 */
    val md: Dp = 12.dp,
    /** 16dp 卡片内边距 */
    val lg: Dp = 16.dp,
    /** 20dp 页面左右边距 */
    val xl: Dp = 20.dp,
    /** 24dp 分组间距 */
    val xxl: Dp = 24.dp,
    /** 32dp 区块间距 */
    val xxxl: Dp = 32.dp,
    /** 40dp 屏级呼吸位 */
    val huge: Dp = 40.dp,
) {
    /** 页面左右边距（20dp） */
    val page: Dp get() = xl
}

val NarviveSpacing = NarviveSpacingTokens()

val LocalSpacing = staticCompositionLocalOf { NarviveSpacing }
