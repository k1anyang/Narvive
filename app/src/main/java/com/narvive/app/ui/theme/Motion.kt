package com.narvive.app.ui.theme

import androidx.compose.animation.core.CubicBezierEasing

/**
 * 动效规范（docs/DESIGN.md §10）。
 *
 * 时长四档 + 三条标准缓动曲线。页面转场、面板进出、按压反馈一律走 Token，
 * 禁止散写 durationMillis 与自定义曲线。
 */
object NarviveMotion {
    /** 80ms — 按压反馈 */
    const val Instant = 80
    /** 150ms — 透明度/选中态切换 */
    const val Fast = 150
    /** 250ms — 组件级过渡（面板展开、Tab 切换） */
    const val Medium = 250
    /** 400ms — 页面转场、共享元素 */
    const val Slow = 400

    /** 标准缓动（默认） M3 Standard */
    val EasingStandard = CubicBezierEasing(0.2f, 0.0f, 0.0f, 1.0f)
    /** 强调减速（入场） M3 Emphasized Decelerate */
    val EasingEmphasizedDecelerate = CubicBezierEasing(0.05f, 0.7f, 0.1f, 1.0f)
    /** 强调加速（退场） M3 Emphasized Accelerate */
    val EasingEmphasizedAccelerate = CubicBezierEasing(0.3f, 0.0f, 0.8f, 0.15f)

    /** 按钮/卡片按压缩放 */
    const val PressScale = 0.97f
}
