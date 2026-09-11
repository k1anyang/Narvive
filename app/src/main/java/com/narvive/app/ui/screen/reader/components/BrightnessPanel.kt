package com.narvive.app.ui.screen.reader.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.WbSunny
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.narvive.app.R
import com.narvive.app.service.reader.ReadSettings
import com.narvive.app.ui.theme.NarviveMotion

/**
 * 亮度控制面板（精简版）：亮度拖动条 + 跟随系统亮度 + 护眼模式。
 */
@Composable
fun BrightnessPanel(
    settings: ReadSettings,
    onApply: (ReadSettings) -> Unit,
) {
    val enabledColor by animateColorAsState(
        if (settings.followSystemBrightness) MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
        else MaterialTheme.colorScheme.onSurface,
        tween(NarviveMotion.Fast), label = "sliderColor",
    )

    Column(
        Modifier
            .fillMaxWidth()
            .fillMaxHeight(0.30f)
            .padding(horizontal = 20.dp, vertical = 12.dp),
    ) {
        Text(
            stringResource(R.string.reader_brightness),
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.padding(bottom = 8.dp),
        )

        // ── 亮度拖动条（小 ☼ — 滑杆 — 大 ☼）──
        Row(
            Modifier.fillMaxWidth().padding(vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                Icons.Rounded.WbSunny,
                contentDescription = null,
                tint = enabledColor.copy(alpha = 0.6f),
                modifier = Modifier.size(18.dp),
            )
            Slider(
                value = settings.brightness,
                onValueChange = { onApply(settings.copy(brightness = it.coerceIn(0.01f, 1f))) },
                valueRange = 0f..1f,
                enabled = !settings.followSystemBrightness,
                modifier = Modifier.weight(1f).padding(horizontal = 12.dp),
            )
            Icon(
                Icons.Rounded.WbSunny,
                contentDescription = null,
                tint = enabledColor,
                modifier = Modifier.size(26.dp),
            )
        }

        HorizontalDivider()

        // ── 跟随系统亮度 ──
        SwitchRow(
            label = stringResource(R.string.reader_follow_system_brightness),
            checked = settings.followSystemBrightness,
            onCheckedChange = { on -> onApply(settings.copy(followSystemBrightness = on)) },
        )
        HorizontalDivider()

        // ── 护眼模式 ──
        SwitchRow(
            label = stringResource(R.string.reader_eye_protection),
            checked = settings.eyeProtection,
            onCheckedChange = { on -> onApply(settings.copy(eyeProtection = on)) },
        )

        Spacer(Modifier.height(12.dp))
    }
}

@Composable
private fun SwitchRow(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            label,
            style = MaterialTheme.typography.titleSmall,
            modifier = Modifier.weight(1f),
        )
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = MaterialTheme.colorScheme.primary,
                checkedTrackColor = MaterialTheme.colorScheme.primaryContainer,
            ),
        )
    }
}
