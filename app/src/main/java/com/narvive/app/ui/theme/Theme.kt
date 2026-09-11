package com.narvive.app.ui.theme

import android.app.Activity
import android.graphics.drawable.ColorDrawable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import com.narvive.app.ui.prefs.AppLanguage

/** 阅读主题上下文 — 独立于系统深浅色与外观主题 */
val LocalReadingTheme = compositionLocalOf { ReadingThemes.Sepia }

/** 当前外观主题 id 上下文（供需要感知主题身份的组件使用，如头像渐变） */
val LocalAppearanceThemeId = compositionLocalOf { AppearanceThemes.DEFAULT_ID }

/**
 * Chrome 主题（App 外壳）。
 *
 * 两个正交维度：
 * - [darkTheme]：外观模式（浅色/深色，由 MainActivity 按 `dark_theme` 偏好解析）
 * - [appearanceTheme]：外观主题 id（docs/DESIGN.md §2，默认 [AppearanceThemes.DEFAULT_ID]）
 *
 * 阅读面（正文）应单独使用 [LocalReadingTheme] 渲染，不走 Material 配色。
 *
 * **关于切换界面语言时的闪烁**（重要，勿误改）：
 *
 * 闪烁的根因是 **Activity 重建**：销毁与重建之间系统会绘制一帧空窗口，底色与当前主题相反
 * （浅色闪黑、深色闪白）。窗口背景、淡入过渡都只能影响重建**之后**的帧，改不掉那一帧。
 *
 * 因此现在**不重建 Activity**：见 AndroidManifest 的 `android:configChanges="locale|layoutDirection"`
 * 与 `MainActivity.onConfigurationChanged`。这里保留的窗口背景同步只用于覆盖**冷启动**首帧，
 * 并保证系统栏颜色与界面一致。切勿改回「靠淡入遮盖重建」的老做法。
 */
@Composable
fun NarviveTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    appearanceTheme: String = AppearanceThemes.DEFAULT_ID,
    content: @Composable () -> Unit,
) {
    val spec = AppearanceThemes.byId(appearanceTheme)
    val colorScheme = if (darkTheme) spec.darkScheme else spec.lightScheme
    // 繁体界面使用 HarmonyOS Sans TC 字族，其余（简中/英文）使用 SC。
    val typography = narviveTypography(traditionalChinese = AppLanguage.current() == AppLanguage.ZH_HANT)

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            val bg = colorScheme.background.toArgb()
            window.statusBarColor = bg
            window.navigationBarColor = bg
            // 让重创建瞬间绘制的窗口背景与应用底色一致（深色主题尤其重要）
            window.setBackgroundDrawable(ColorDrawable(bg))
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme
        }
    }

    CompositionLocalProvider(
        LocalSpacing provides NarviveSpacing,
        LocalElevation provides NarviveElevation,
        LocalAppearanceThemeId provides spec.id,
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = typography,
            shapes = NarviveShapes,
            content = content,
        )
    }
}
