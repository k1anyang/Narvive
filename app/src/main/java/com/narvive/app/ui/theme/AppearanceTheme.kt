package com.narvive.app.ui.theme

import androidx.annotation.StringRes
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import com.narvive.app.R

/**
 * 外观主题（Chrome 面色彩气质）。
 *
 * 与「外观模式」（浅色/深色/跟随系统，DataStore `dark_theme`）正交：
 * 每个主题都提供浅色 + 深色两套 [ColorScheme]。
 * 规范见 docs/DESIGN.md §2。
 *
 * 纯视觉层定义，新增主题只需在 [AppearanceThemes.all] 中登记。
 *
 * 名称与描述用字符串资源 id 而非硬编码文案，以支持界面语言切换（见 docs/i18n.md）。
 */
data class AppearanceThemeSpec(
    val id: String,
    @StringRes val displayNameRes: Int,
    @StringRes val descriptionRes: Int,
    /** 设置页预览卡渐变色：[页面底色, 品牌主色] */
    val previewColors: List<Color>,
    val lightScheme: ColorScheme,
    val darkScheme: ColorScheme,
)

object AppearanceThemes {

    const val DEFAULT_ID = "sky"

    /* ===== 暖纸墨 paper_ink —— 羊皮纸底 + 赤陶橙（可选主题，非默认）===== */

    // Light
    private val PiBgL = Color(0xFFF7F4EE)
    private val PiSurfaceL = Color(0xFFFCFAF5)
    private val PiSurfaceVariantL = Color(0xFFF0ECE2)
    private val PiPrimaryL = Color(0xFFC4553B)
    private val PiPrimaryContainerL = Color(0xFFF6DDD2)
    private val PiOnPrimaryContainerL = Color(0xFF5C2417)
    private val PiSecondaryL = Color(0xFF8A7F70)
    private val PiSecondaryContainerL = Color(0xFFEAE2D3)
    private val PiOnSecondaryContainerL = Color(0xFF3E362B)
    private val PiInkL = Color(0xFF2B2620)
    private val PiInkSubL = Color(0xFF6E6455)
    private val PiOutlineL = Color(0xFFE3DCCD)
    private val PiErrorL = Color(0xFFB44336)

    // Dark
    private val PiBgD = Color(0xFF1B1815)
    private val PiSurfaceD = Color(0xFF262119)
    private val PiSurfaceVariantD = Color(0xFF322B22)
    private val PiPrimaryD = Color(0xFFE0815F)
    private val PiPrimaryContainerD = Color(0xFF5A3125)
    private val PiOnPrimaryContainerD = Color(0xFFF9D9C8)
    private val PiSecondaryD = Color(0xFFB9AC99)
    private val PiSecondaryContainerD = Color(0xFF4A4034)
    private val PiOnSecondaryContainerD = Color(0xFFEAE2D3)
    private val PiInkD = Color(0xFFEDE6DA)
    private val PiInkSubD = Color(0xFFB9AC99)
    private val PiOutlineD = Color(0xFF4A4034)
    private val PiErrorD = Color(0xFFE08A7E)

    val PaperInk = AppearanceThemeSpec(
        id = "paper_ink",
        displayNameRes = R.string.appearance_theme_paper_ink,
        descriptionRes = R.string.appearance_theme_paper_ink_desc,
        previewColors = listOf(PiBgL, PiPrimaryL),
        lightScheme = lightColorScheme(
            primary = PiPrimaryL,
            onPrimary = Color(0xFFFFFFFF),
            primaryContainer = PiPrimaryContainerL,
            onPrimaryContainer = PiOnPrimaryContainerL,
            secondary = PiSecondaryL,
            onSecondary = Color(0xFFFFFFFF),
            secondaryContainer = PiSecondaryContainerL,
            onSecondaryContainer = PiOnSecondaryContainerL,
            tertiary = PiSecondaryL,
            onTertiary = Color(0xFFFFFFFF),
            tertiaryContainer = PiSecondaryContainerL,
            onTertiaryContainer = PiOnSecondaryContainerL,
            background = PiBgL,
            onBackground = PiInkL,
            surface = PiSurfaceL,
            onSurface = PiInkL,
            surfaceVariant = PiSurfaceVariantL,
            onSurfaceVariant = PiInkSubL,
            surfaceContainerLowest = Color(0xFFFFFFFF),
            surfaceContainerLow = Color(0xFFF2EEE6),
            surfaceContainer = Color(0xFFECE7DC),
            surfaceContainerHigh = Color(0xFFE6E1D5),
            surfaceContainerHighest = Color(0xFFE0DBCF),
            outline = PiOutlineL,
            outlineVariant = PiOutlineL.copy(alpha = 0.6f),
            error = PiErrorL,
            onError = Color(0xFFFFFFFF),
        ),
        darkScheme = darkColorScheme(
            primary = PiPrimaryD,
            onPrimary = Color(0xFF3B170D),
            primaryContainer = PiPrimaryContainerD,
            onPrimaryContainer = PiOnPrimaryContainerD,
            secondary = PiSecondaryD,
            onSecondary = Color(0xFF2B2620),
            secondaryContainer = PiSecondaryContainerD,
            onSecondaryContainer = PiOnSecondaryContainerD,
            tertiary = PiSecondaryD,
            onTertiary = Color(0xFF2B2620),
            tertiaryContainer = PiSecondaryContainerD,
            onTertiaryContainer = PiOnSecondaryContainerD,
            background = PiBgD,
            onBackground = PiInkD,
            surface = PiSurfaceD,
            onSurface = PiInkD,
            surfaceVariant = PiSurfaceVariantD,
            onSurfaceVariant = PiInkSubD,
            surfaceContainerLowest = Color(0xFF141110),
            surfaceContainerLow = Color(0xFF1F1B17),
            surfaceContainer = Color(0xFF231E18),
            surfaceContainerHigh = Color(0xFF2E2820),
            surfaceContainerHighest = Color(0xFF393227),
            outline = PiOutlineD,
            outlineVariant = PiOutlineD.copy(alpha = 0.6f),
            error = PiErrorD,
            onError = Color(0xFF4A1512),
        ),
    )

    /* ===== 天际蓝 sky（经典 1.0 外观，原 Slate/Sky 方案收录） ===== */

    val Sky = AppearanceThemeSpec(
        id = "sky",
        displayNameRes = R.string.appearance_theme_sky,
        descriptionRes = R.string.appearance_theme_sky_desc,
        previewColors = listOf(Slate50, Sky600),
        lightScheme = lightColorScheme(
            primary = Sky600,
            onPrimary = Color(0xFFFFFFFF),
            primaryContainer = Sky100,
            onPrimaryContainer = Sky900,
            secondary = Slate500,
            background = Slate50,
            onBackground = Slate900,
            surface = Color(0xFFFFFFFF),
            onSurface = Slate900,
            surfaceVariant = Slate100,
            onSurfaceVariant = Slate500,
            outline = Slate200,
            error = ErrorRed,
        ),
        darkScheme = darkColorScheme(
            primary = Sky400,
            onPrimary = Slate900,
            primaryContainer = Sky900,
            onPrimaryContainer = Sky100,
            secondary = Slate400On,
            background = Slate900Bg,
            onBackground = Slate200On,
            surface = Slate800,
            onSurface = Slate200On,
            surfaceVariant = Slate700,
            onSurfaceVariant = Slate400On,
            outline = Color(0xFF475569),
            error = Color(0xFFF87171),
        ),
    )

    /* ===== 松烟绿 pine ===== */

    val Pine = AppearanceThemeSpec(
        id = "pine",
        displayNameRes = R.string.appearance_theme_pine,
        descriptionRes = R.string.appearance_theme_pine_desc,
        previewColors = listOf(Color(0xFFF4F6F1), Color(0xFF3F6B4F)),
        lightScheme = lightColorScheme(
            primary = Color(0xFF3F6B4F),
            onPrimary = Color(0xFFFFFFFF),
            primaryContainer = Color(0xFFDCE9DE),
            onPrimaryContainer = Color(0xFF1C3A28),
            secondary = Color(0xFF5A675A),
            onSecondary = Color(0xFFFFFFFF),
            secondaryContainer = Color(0xFFE2E8DE),
            onSecondaryContainer = Color(0xFF2C362C),
            tertiary = Color(0xFF5A675A),
            onTertiary = Color(0xFFFFFFFF),
            tertiaryContainer = Color(0xFFE2E8DE),
            onTertiaryContainer = Color(0xFF2C362C),
            background = Color(0xFFF4F6F1),
            onBackground = Color(0xFF21271F),
            surface = Color(0xFFFBFCF9),
            onSurface = Color(0xFF21271F),
            surfaceVariant = Color(0xFFE8EDE3),
            onSurfaceVariant = Color(0xFF5A675A),
            surfaceContainerLowest = Color(0xFFFFFFFF),
            surfaceContainerLow = Color(0xFFEFF2EA),
            surfaceContainer = Color(0xFFE9EDE2),
            surfaceContainerHigh = Color(0xFFE3E7DB),
            surfaceContainerHighest = Color(0xFFDDE2D4),
            outline = Color(0xFFDDE4D6),
            outlineVariant = Color(0xFFDDE4D6).copy(alpha = 0.6f),
            error = PiErrorL,
            onError = Color(0xFFFFFFFF),
        ),
        darkScheme = darkColorScheme(
            primary = Color(0xFF8FBE9C),
            onPrimary = Color(0xFF12301D),
            primaryContainer = Color(0xFF2C4A38),
            onPrimaryContainer = Color(0xFFDCE9DE),
            secondary = Color(0xFFA8B5A4),
            onSecondary = Color(0xFF21271F),
            secondaryContainer = Color(0xFF3E4A3E),
            onSecondaryContainer = Color(0xFFE2E8DE),
            tertiary = Color(0xFFA8B5A4),
            onTertiary = Color(0xFF21271F),
            tertiaryContainer = Color(0xFF3E4A3E),
            onTertiaryContainer = Color(0xFFE2E8DE),
            background = Color(0xFF151A16),
            onBackground = Color(0xFFE4EAE0),
            surface = Color(0xFF1E2620),
            onSurface = Color(0xFFE4EAE0),
            surfaceVariant = Color(0xFF293229),
            onSurfaceVariant = Color(0xFFA8B5A4),
            surfaceContainerLowest = Color(0xFF101411),
            surfaceContainerLow = Color(0xFF1A201B),
            surfaceContainer = Color(0xFF1E2420),
            surfaceContainerHigh = Color(0xFF292F29),
            surfaceContainerHighest = Color(0xFF343A33),
            outline = Color(0xFF3E4A3E),
            outlineVariant = Color(0xFF3E4A3E).copy(alpha = 0.6f),
            error = PiErrorD,
            onError = Color(0xFF4A1512),
        ),
    )

    /* ===== 绛紫 plum ===== */

    val Plum = AppearanceThemeSpec(
        id = "plum",
        displayNameRes = R.string.appearance_theme_plum,
        descriptionRes = R.string.appearance_theme_plum_desc,
        previewColors = listOf(Color(0xFFF8F5F7), Color(0xFF7A4A6B)),
        lightScheme = lightColorScheme(
            primary = Color(0xFF7A4A6B),
            onPrimary = Color(0xFFFFFFFF),
            primaryContainer = Color(0xFFF2DFE9),
            onPrimaryContainer = Color(0xFF47203C),
            secondary = Color(0xFF6B5A64),
            onSecondary = Color(0xFFFFFFFF),
            secondaryContainer = Color(0xFFEDE3EA),
            onSecondaryContainer = Color(0xFF382E35),
            tertiary = Color(0xFF6B5A64),
            onTertiary = Color(0xFFFFFFFF),
            tertiaryContainer = Color(0xFFEDE3EA),
            onTertiaryContainer = Color(0xFF382E35),
            background = Color(0xFFF8F5F7),
            onBackground = Color(0xFF282126),
            surface = Color(0xFFFDFBFC),
            onSurface = Color(0xFF282126),
            surfaceVariant = Color(0xFFF0E9EE),
            onSurfaceVariant = Color(0xFF6B5A64),
            surfaceContainerLowest = Color(0xFFFFFFFF),
            surfaceContainerLow = Color(0xFFF2EDF1),
            surfaceContainer = Color(0xFFECE6EB),
            surfaceContainerHigh = Color(0xFFE6E0E5),
            surfaceContainerHighest = Color(0xFFE1DBE0),
            outline = Color(0xFFE5DAE1),
            outlineVariant = Color(0xFFE5DAE1).copy(alpha = 0.6f),
            error = PiErrorL,
            onError = Color(0xFFFFFFFF),
        ),
        darkScheme = darkColorScheme(
            primary = Color(0xFFD8A7C4),
            onPrimary = Color(0xFF3C1B31),
            primaryContainer = Color(0xFF553249),
            onPrimaryContainer = Color(0xFFF2DFE9),
            secondary = Color(0xFFC2AFBC),
            onSecondary = Color(0xFF282126),
            secondaryContainer = Color(0xFF4A3D47),
            onSecondaryContainer = Color(0xFFEDE3EA),
            tertiary = Color(0xFFC2AFBC),
            onTertiary = Color(0xFF282126),
            tertiaryContainer = Color(0xFF4A3D47),
            onTertiaryContainer = Color(0xFFEDE3EA),
            background = Color(0xFF1B151A),
            onBackground = Color(0xFFEEE5EC),
            surface = Color(0xFF251D24),
            onSurface = Color(0xFFEEE5EC),
            surfaceVariant = Color(0xFF322832),
            onSurfaceVariant = Color(0xFFC2AFBC),
            surfaceContainerLowest = Color(0xFF151115),
            surfaceContainerLow = Color(0xFF1E1A1E),
            surfaceContainer = Color(0xFF221D22),
            surfaceContainerHigh = Color(0xFF2D272D),
            surfaceContainerHighest = Color(0xFF383138),
            outline = Color(0xFF4A3D47),
            outlineVariant = Color(0xFF4A3D47).copy(alpha = 0.6f),
            error = PiErrorD,
            onError = Color(0xFF4A1512),
        ),
    )

    /** 全部可选外观主题（顺序即设置页展示顺序） */
    val all: List<AppearanceThemeSpec> = listOf(PaperInk, Sky, Pine, Plum)

    fun byId(id: String): AppearanceThemeSpec = all.firstOrNull { it.id == id } ?: PaperInk

    /** 主题名资源 id（供 stringResource 解析，支持界面语言切换） */
    @StringRes
    fun displayNameResOf(id: String): Int = byId(id).displayNameRes
}
