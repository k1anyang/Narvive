package com.narvive.app.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

/**
 * 全局圆角规范（docs/DESIGN.md §9）。
 *
 * 规则：同组元素圆角一致；嵌套时外角 ≥ 内角；书封全站固定 8dp。
 * 新代码禁止使用散写的 RoundedCornerShape(N.dp)，统一走 Token。
 */
object NarviveShape {
    /** 4dp — 小标签、进度条 */
    val Xs = RoundedCornerShape(4.dp)
    /** 8dp — 书封、小按钮、图标容器 */
    val Sm = RoundedCornerShape(8.dp)
    /** 12dp — 输入框、小卡片、列表项 */
    val Md = RoundedCornerShape(12.dp)
    /** 16dp — 标准卡片、对话框内容区 */
    val Lg = RoundedCornerShape(16.dp)
    /** 24dp — 特色卡片 */
    val Xl = RoundedCornerShape(24.dp)
    /** 24dp 顶角 — BottomSheet */
    val Sheet = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
    /** 50% — Chip、胶囊按钮 */
    val Pill = RoundedCornerShape(percent = 50)
    /** 书封（全站固定 8dp） */
    val BookCover = Sm
}

/** Material3 Shapes 映射（供 MaterialTheme 使用） */
val NarviveShapes = Shapes(
    extraSmall = NarviveShape.Xs,
    small = NarviveShape.Sm,
    medium = NarviveShape.Md,
    large = NarviveShape.Lg,
    extraLarge = NarviveShape.Xl,
)
