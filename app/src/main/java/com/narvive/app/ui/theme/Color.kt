package com.narvive.app.ui.theme

import androidx.compose.ui.graphics.Color

/* ===== Chrome · Light ===== */
val Slate50 = Color(0xFFF8FAFC)
val Slate100 = Color(0xFFF1F5F9)
val Slate200 = Color(0xFFE2E8F0)
val Slate500 = Color(0xFF64748B)
val Slate900 = Color(0xFF0F172A)

/* ===== Chrome · Dark ===== */
val Slate700 = Color(0xFF334155)
val Slate800 = Color(0xFF1E293B)
val Slate900Bg = Color(0xFF0F172A)
val Slate200On = Color(0xFFE2E8F0)
val Slate400On = Color(0xFF94A3B8)

/* ===== Accent · Sky ===== */
val Sky600 = Color(0xFF0284C7)
val Sky400 = Color(0xFF38BDF8)
val Sky100 = Color(0xFFE0F2FE)
val Sky900 = Color(0xFF0C4A6E)

/* ===== Semantic ===== */
val ErrorRed = Color(0xFFDC2626)
val SuccessGreen = Color(0xFF16A34A)

/* ===== Highlight Colors ===== */
val HighlightYellow = Color(0xFFFACC15)
val HighlightBlue = Color(0xFF38BDF8)
val HighlightPink = Color(0xFFF472B6)
val HighlightGreen = Color(0xFF4ADE80)

/* ===== Reading Themes ===== */
object ReadingThemes {
    /** 纸白 Paper */
    val Paper = ReadingTheme(
        page = Color(0xFFFFFFFF),
        ink = Color(0xFF1C1917),
    )

    /** 羊皮纸 Sepia (default) */
    val Sepia = ReadingTheme(
        page = Color(0xFFF7F0E1),
        ink = Color(0xFF5B4636),
    )

    /** 豆沙绿 Green */
    val Green = ReadingTheme(
        page = Color(0xFFDDE8D2),
        ink = Color(0xFF2F4032),
    )

    /** 墨夜 Dark (brand slate night, ink 按 design #CBD5E1) */
    val Dark = ReadingTheme(
        page = Slate800,
        ink = Color(0xFFCBD5E1),
    )

    /** 纯黑 OLED (dimmed ink — reduces halation) */
    val Black = ReadingTheme(
        page = Color(0xFF000000),
        ink = Color(0xFFB0B8C4),
    )
}

data class ReadingTheme(
    val page: Color,
    val ink: Color,
)
