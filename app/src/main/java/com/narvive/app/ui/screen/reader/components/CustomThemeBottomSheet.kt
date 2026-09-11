package com.narvive.app.ui.screen.reader.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.text.BasicTextField
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
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.narvive.app.R
import com.narvive.app.service.reader.ReadSettings
import com.narvive.app.ui.screen.reader.NIGHT_READER_BG
import com.narvive.app.ui.screen.reader.NIGHT_READER_INK
import com.narvive.app.ui.screen.reader.readerInkColor
import com.narvive.app.ui.screen.reader.readerPageBackground
import com.narvive.app.ui.theme.NarviveShape

private val darkFontColors = listOf(
    0xFF1C1917L, 0xFF292524L, 0xFF3F3A36L,
    0xFF0F172AL, 0xFF1E293BL, 0xFF5B4636L,
)

private val lightFontColors = listOf(
    0xFF78716CL, 0xFF9CA3AFL, 0xFFB0B8C4L,
    0xFFD6D3D1L, 0xFFE2E8F0L, 0xFFF8FAFCL,
)

private val darkBgColors = listOf(
    0xFF0B1622L, 0xFF1E293BL, 0xFF000000L,
    0xFF27272AL, 0xFF334155L, 0xFF3F3F46L,
)

private val lightBgColors = listOf(
    0xFFFFFFFFL, 0xFFF7F0E1L, 0xFFDDE8D2L,
    0xFFF8FAFCL, 0xFFFFF7EDL, 0xFFE7F0FAL,
)

private fun colorLong(color: Color): Long = color.toArgb().toLong() and 0xFFFFFFFFL

private fun rgbLong(r: Int, g: Int, b: Int): Long =
    (0xFF000000L or (r.toLong() shl 16) or (g.toLong() shl 8) or b.toLong())

private fun channel(s: String): Int? = s.toIntOrNull()?.coerceIn(0, 255)

private fun rgbText(input: String): String {
    val digits = input.filter { it.isDigit() }.take(3)
    if (digits.isEmpty()) return ""
    return if (digits.toInt() > 255) "255" else digits
}

/**
 * 自定义主题设置窗口（底部弹出）：
 * 顶部“主题设置 / 还原上次设置”，下方分别配置字体颜色与背景颜色。
 * 每个区块 6 深色 + 6 浅色色标，以及 R/G/B 输入 + 圆形预览色标；
 * RGB 预览色标点击后才应用，避免输入过程中反复重排。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CustomThemeBottomSheet(
    settings: ReadSettings,
    onApply: (ReadSettings) -> Unit,
    onDismiss: () -> Unit,
) {
    val snapshot = remember { settings }
    val initialInk = remember {
        if (settings.nightMode) NIGHT_READER_INK else colorLong(readerInkColor(settings))
    }
    val initialBg = remember {
        if (settings.nightMode) NIGHT_READER_BG else colorLong(readerPageBackground(settings))
    }

    var currentInk by remember { mutableStateOf(initialInk) }
    var currentBg by remember { mutableStateOf(initialBg) }
    var inkR by remember { mutableStateOf(((initialInk shr 16) and 0xFF).toString()) }
    var inkG by remember { mutableStateOf(((initialInk shr 8) and 0xFF).toString()) }
    var inkB by remember { mutableStateOf((initialInk and 0xFF).toString()) }
    var bgR by remember { mutableStateOf(((initialBg shr 16) and 0xFF).toString()) }
    var bgG by remember { mutableStateOf(((initialBg shr 8) and 0xFF).toString()) }
    var bgB by remember { mutableStateOf((initialBg and 0xFF).toString()) }

    val pendingInk = listOf(channel(inkR), channel(inkG), channel(inkB)).let { vals ->
        if (vals.any { it == null }) currentInk else rgbLong(vals[0]!!, vals[1]!!, vals[2]!!)
    }
    val pendingBg = listOf(channel(bgR), channel(bgG), channel(bgB)).let { vals ->
        if (vals.any { it == null }) currentBg else rgbLong(vals[0]!!, vals[1]!!, vals[2]!!)
    }

    fun updateInkState(color: Long) {
        currentInk = color
        inkR = ((color shr 16) and 0xFF).toString()
        inkG = ((color shr 8) and 0xFF).toString()
        inkB = (color and 0xFF).toString()
    }

    fun updateBgState(color: Long) {
        currentBg = color
        bgR = ((color shr 16) and 0xFF).toString()
        bgG = ((color shr 8) and 0xFF).toString()
        bgB = (color and 0xFF).toString()
    }

    fun applyFont(color: Long) {
        val next = if (settings.nightMode) {
            settings.copy(
                nightMode = false,
                theme = "custom",
                customInkColor = color,
                customBgColor = NIGHT_READER_BG,
            )
        } else {
            settings.copy(theme = "custom", customInkColor = color, customBgColor = currentBg)
        }
        updateInkState(color)
        if (settings.nightMode) updateBgState(NIGHT_READER_BG)
        onApply(next)
    }

    fun applyBackground(color: Long) {
        val next = if (settings.nightMode) {
            settings.copy(
                nightMode = false,
                theme = "custom",
                customBgColor = color,
                customInkColor = NIGHT_READER_INK,
            )
        } else {
            settings.copy(theme = "custom", customBgColor = color, customInkColor = currentInk)
        }
        updateBgState(color)
        if (settings.nightMode) updateInkState(NIGHT_READER_INK)
        onApply(next)
    }

    fun restoreSnapshot() {
        val s = snapshot
        val ink = if (s.nightMode) NIGHT_READER_INK else colorLong(readerInkColor(s))
        val bg = if (s.nightMode) NIGHT_READER_BG else colorLong(readerPageBackground(s))
        updateInkState(ink)
        updateBgState(bg)
        onApply(s)
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.50f)
                .padding(horizontal = 20.dp)
                .padding(bottom = 12.dp),
        ) {
            Row(
                Modifier.fillMaxWidth().padding(bottom = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(stringResource(R.string.reader_theme_settings), style = MaterialTheme.typography.titleLarge)
                Spacer(Modifier.weight(1f))
                TextButton(onClick = ::restoreSnapshot) {
                    Text(stringResource(R.string.reader_restore_last_settings), style = MaterialTheme.typography.bodySmall)
                }
            }

            HorizontalDivider(Modifier.padding(bottom = 6.dp))

            ColorSection(
                title = stringResource(R.string.reader_font_color),
                darkColors = darkFontColors,
                lightColors = lightFontColors,
                pendingColor = pendingInk,
                currentColor = currentInk,
                nightMode = settings.nightMode,
                rText = inkR,
                gText = inkG,
                bText = inkB,
                onRChange = { inkR = rgbText(it) },
                onGChange = { inkG = rgbText(it) },
                onBChange = { inkB = rgbText(it) },
                onApplyPending = { applyFont(pendingInk) },
                onSelect = ::applyFont,
            )

            HorizontalDivider(Modifier.padding(vertical = 6.dp))

            ColorSection(
                title = stringResource(R.string.reader_background_color),
                darkColors = darkBgColors,
                lightColors = lightBgColors,
                pendingColor = pendingBg,
                currentColor = currentBg,
                nightMode = settings.nightMode,
                rText = bgR,
                gText = bgG,
                bText = bgB,
                onRChange = { bgR = rgbText(it) },
                onGChange = { bgG = rgbText(it) },
                onBChange = { bgB = rgbText(it) },
                onApplyPending = { applyBackground(pendingBg) },
                onSelect = ::applyBackground,
            )
        }
    }
}

@Composable
private fun ColorSection(
    title: String,
    darkColors: List<Long>,
    lightColors: List<Long>,
    pendingColor: Long,
    currentColor: Long,
    nightMode: Boolean,
    rText: String,
    gText: String,
    bText: String,
    onRChange: (String) -> Unit,
    onGChange: (String) -> Unit,
    onBChange: (String) -> Unit,
    onApplyPending: () -> Unit,
    onSelect: (Long) -> Unit,
) {
    Column(Modifier.fillMaxWidth().padding(top = 4.dp)) {
        Text(title, style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(bottom = 6.dp))

        SwatchRow(darkColors, currentColor, nightMode, onSelect)
        Spacer(Modifier.height(6.dp))
        SwatchRow(lightColors, currentColor, nightMode, onSelect)
        Spacer(Modifier.height(6.dp))

        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(Color(pendingColor))
                    .border(2.dp, MaterialTheme.colorScheme.outline, CircleShape)
                    .clickable(onClick = onApplyPending),
            )
            Spacer(Modifier.width(10.dp))
            RgbField("R", rText, onRChange)
            Spacer(Modifier.width(6.dp))
            RgbField("G", gText, onGChange)
            Spacer(Modifier.width(6.dp))
            RgbField("B", bText, onBChange)
        }
    }
}

@Composable
private fun SwatchRow(
    colors: List<Long>,
    currentColor: Long,
    nightMode: Boolean,
    onSelect: (Long) -> Unit,
) {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        colors.forEach { color ->
            val selected = !nightMode && currentColor == color
            Box(
                Modifier
                    .size(26.dp)
                    .clip(CircleShape)
                    .background(Color(color))
                    .then(
                        if (selected) Modifier.border(2.dp, MaterialTheme.colorScheme.primary, CircleShape)
                        else Modifier.border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f), CircleShape)
                    )
                    .clickable { onSelect(color) },
            )
        }
    }
}

@Composable
private fun RgbField(
    label: String,
    value: String,
    onChange: (String) -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.width(4.dp))
        BasicTextField(
            value = value,
            onValueChange = onChange,
            singleLine = true,
            textStyle = MaterialTheme.typography.bodyMedium.copy(
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center,
            ),
            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
            modifier = Modifier
                .width(60.dp)
                .height(40.dp)
                .border(1.dp, MaterialTheme.colorScheme.outline, NarviveShape.Sm)
                .padding(horizontal = 8.dp),
            decorationBox = { innerTextField ->
                Box(contentAlignment = Alignment.Center) { innerTextField() }
            },
        )
    }
}
