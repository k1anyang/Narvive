package com.narvive.app.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.narvive.app.R

/**
 * 全站无衬线字体：HarmonyOS Sans（见 res/raw/harmonyos_sans_license.txt）。
 *
 * 打包两套字族，按界面语言选用（见 [narviveTypography] 与 docs/i18n.md）：
 * - [NarviveSansSC]：简体中文字形，亦用于英文界面
 * - [NarviveSansTC]：繁体中文字形
 *
 * 为什么不共用 SC：SC 子集只覆盖简体字形，用 SC 渲染繁体用词会出现字形风格混杂
 * （如「骨」「迴」「體」的写法差异）。
 *
 * 打包字重：Regular(400) / Medium(500) / Bold(700)。
 * 代码中的 SemiBold(600) 请求由 Compose 就近匹配到 Medium 或 Bold（HarmonyOS Sans 无 600 档）。
 *
 * 注：阅读正文（EPUB/TXT/PDF）不使用此字族，走 service/font 的独立字体系统，二者互不影响。
 */
val NarviveSansSC = FontFamily(
    Font(R.font.harmonyos_sans_sc_regular, FontWeight.Normal),
    Font(R.font.harmonyos_sans_sc_medium, FontWeight.Medium),
    Font(R.font.harmonyos_sans_sc_bold, FontWeight.Bold),
)

val NarviveSansTC = FontFamily(
    Font(R.font.harmonyos_sans_tc_regular, FontWeight.Normal),
    Font(R.font.harmonyos_sans_tc_medium, FontWeight.Medium),
    Font(R.font.harmonyos_sans_tc_bold, FontWeight.Bold),
)

/** 兼容别名：指向简体字族。新代码请按语言选择，或直接用 `MaterialTheme.typography`。 */
val NarviveSans = NarviveSansSC

/**
 * 排版体系（docs/DESIGN.md §4）—— 10+1 档梯度，全站无衬线。
 *
 * 规则：字重只用 400/500/700；行比 ∈ [1.2, 1.6]；正文行比 ≥ 1.4；
 * 最小字号 11sp（labelSmall），不再使用 10sp；Chip 文字不换行。
 * 新代码禁止散写 .sp，统一走 MaterialTheme.typography。
 */
val NarviveTypography = Typography(
    /** 28 Bold — 页面主标题 */
    headlineLarge = TextStyle(
        fontFamily = NarviveSansSC,
        fontWeight = FontWeight.Bold,
        fontSize = 28.sp,
        lineHeight = 36.sp,
        letterSpacing = (-0.4).sp,
    ),
    /** 24 Bold — 区块标题 */
    headlineMedium = TextStyle(
        fontFamily = NarviveSansSC,
        fontWeight = FontWeight.Bold,
        fontSize = 24.sp,
        lineHeight = 32.sp,
        letterSpacing = (-0.2).sp,
    ),
    /** 20 Bold — 卡片大标题 */
    headlineSmall = TextStyle(
        fontFamily = NarviveSansSC,
        fontWeight = FontWeight.Bold,
        fontSize = 20.sp,
        lineHeight = 28.sp,
    ),
    /** 18 SemiBold — 列表主标题/书名 */
    titleLarge = TextStyle(
        fontFamily = NarviveSansSC,
        fontWeight = FontWeight.SemiBold,
        fontSize = 18.sp,
        lineHeight = 26.sp,
    ),
    /** 16 Medium — 强调正文/按钮 */
    titleMedium = TextStyle(
        fontFamily = NarviveSansSC,
        fontWeight = FontWeight.Medium,
        fontSize = 16.sp,
        lineHeight = 24.sp,
    ),
    /** 15 Medium — 紧凑列表标题 */
    titleSmall = TextStyle(
        fontFamily = NarviveSansSC,
        fontWeight = FontWeight.Medium,
        fontSize = 15.sp,
        lineHeight = 22.sp,
    ),
    /** 15 Normal — 主要正文（行比 1.6） */
    bodyLarge = TextStyle(
        fontFamily = NarviveSansSC,
        fontWeight = FontWeight.Normal,
        fontSize = 15.sp,
        lineHeight = 24.sp,
    ),
    /** 14 Normal — 次级正文（行比 ~1.43） */
    bodyMedium = TextStyle(
        fontFamily = NarviveSansSC,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 20.sp,
    ),
    /** 13 Normal — 辅助说明（行比 ~1.38） */
    bodySmall = TextStyle(
        fontFamily = NarviveSansSC,
        fontWeight = FontWeight.Normal,
        fontSize = 13.sp,
        lineHeight = 18.sp,
    ),
    /** 13 Medium — 强调标签 */
    labelLarge = TextStyle(
        fontFamily = NarviveSansSC,
        fontWeight = FontWeight.Medium,
        fontSize = 13.sp,
        lineHeight = 18.sp,
    ),
    /** 12 Medium — 标签/元数据 */
    labelMedium = TextStyle(
        fontFamily = NarviveSansSC,
        fontWeight = FontWeight.Medium,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.2.sp,
    ),
    /** 11 Medium — 胶囊/角标（全站最小字号） */
    labelSmall = TextStyle(
        fontFamily = NarviveSansSC,
        fontWeight = FontWeight.Medium,
        fontSize = 11.sp,
        lineHeight = 15.sp,
        letterSpacing = 0.4.sp,
    ),
)

/**
 * 按界面语言构建排版体系（繁体 → TC 字族，其余 → SC 字族）。
 *
 * 用法见 `NarviveTheme`：`typography = narviveTypography(traditionalChinese)`。
 * 做成工厂函数而非固定常量，是为了让字族随语言变化，同时保持
 * `MaterialTheme.typography` 作为全站唯一排版入口——消费方零改动。
 */
fun narviveTypography(traditionalChinese: Boolean): Typography {
    val family = if (traditionalChinese) NarviveSansTC else NarviveSansSC
    return NarviveTypography.copy(
        headlineLarge = NarviveTypography.headlineLarge.copy(fontFamily = family),
        headlineMedium = NarviveTypography.headlineMedium.copy(fontFamily = family),
        headlineSmall = NarviveTypography.headlineSmall.copy(fontFamily = family),
        titleLarge = NarviveTypography.titleLarge.copy(fontFamily = family),
        titleMedium = NarviveTypography.titleMedium.copy(fontFamily = family),
        titleSmall = NarviveTypography.titleSmall.copy(fontFamily = family),
        bodyLarge = NarviveTypography.bodyLarge.copy(fontFamily = family),
        bodyMedium = NarviveTypography.bodyMedium.copy(fontFamily = family),
        bodySmall = NarviveTypography.bodySmall.copy(fontFamily = family),
        labelLarge = NarviveTypography.labelLarge.copy(fontFamily = family),
        labelMedium = NarviveTypography.labelMedium.copy(fontFamily = family),
        labelSmall = NarviveTypography.labelSmall.copy(fontFamily = family),
    )
}
