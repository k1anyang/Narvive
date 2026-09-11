package com.narvive.app.ui.theme

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * 海拔/阴影规范（docs/DESIGN.md §8）。
 *
 * 浅色主题"以边代影"（L1 细边为主）；深色主题禁用投影，用表面明度差分层。
 * 通过 [LocalElevation] 获取。禁止散写 shadow(N.dp) / tonalElevation。
 */
class NarviveElevationTokens(
    /** L0 平面 — 页面大底 */
    val level0: Dp = 0.dp,
    /** L1 细边 — 卡片/输入框（配合 outline 50% 透明 1dp 边） */
    val level1: Dp = 1.dp,
    /** L2 浮起 — 选中卡片、悬浮按钮 */
    val level2: Dp = 2.dp,
    /** L3 弹层 — BottomSheet、菜单、HUD */
    val level3: Dp = 6.dp,
    /** L4 模态 — 对话框、全屏弹层 */
    val level4: Dp = 12.dp,
) {
    /** 细边界宽度（配合 outline.copy(alpha = 0.5f)） */
    val hairline: Dp get() = level1
}

val NarviveElevation = NarviveElevationTokens()

val LocalElevation = staticCompositionLocalOf { NarviveElevation }
