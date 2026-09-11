package com.narvive.app.ui.screen.reader

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.narvive.app.R
import com.narvive.app.ui.components.TabPageScaffold

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MoreSettingsScreen(
    onBackClick: () -> Unit,
    viewModel: MoreSettingsViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsState()
    val loaded by viewModel.loaded.collectAsState()
    var screenOffDialog by remember { mutableStateOf(false) }
    var restReminderDialog by remember { mutableStateOf(false) }

    TabPageScaffold(
        title = stringResource(R.string.reader_settings_title),
        navigationIcon = { IconButton(onClick = onBackClick) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, stringResource(R.string.reader_back)) } },
    ) { padding ->
        if (loaded) LazyColumn(
            modifier = Modifier.padding(padding).fillMaxSize(),
        ) {
            item { GroupHeader(stringResource(R.string.reader_group_page_turn)) }
            item {
                SettingSwitchRow(
                    name = stringResource(R.string.reader_volume_key_page_turn),
                    checked = state.volumeKeyPageTurn,
                    onChange = viewModel::setVolumeKeyPageTurn,
                )
            }
            item { HorizontalDivider() }
            item {
                SettingSwitchRow(
                    name = stringResource(R.string.reader_book_open_animation),
                    checked = state.bookOpenAnimation,
                    enabled = true,
                    hint = stringResource(R.string.reader_book_open_animation_hint),
                    onChange = viewModel::setBookOpenAnimation,
                )
            }
            item { HorizontalDivider() }

            item { GroupHeader(stringResource(R.string.reader_group_screen_reminder)) }
            item {
                SettingValueRow(
                    name = stringResource(R.string.reader_screen_off_time),
                    value = screenOffLabel(state.screenOffMinutes),
                    onClick = { screenOffDialog = true },
                )
            }
            item { HorizontalDivider() }
            item {
                SettingValueRow(
                    name = stringResource(R.string.reader_rest_reminder_display),
                    value = restReminderLabel(state.restReminderEnabled, state.restReminderMinutes),
                    onClick = { restReminderDialog = true },
                )
            }
            item { HorizontalDivider() }

            item { GroupHeader(stringResource(R.string.reader_group_display)) }
            item {
                SettingSwitchRow(
                    name = stringResource(R.string.reader_epub_publisher_styles),
                    checked = state.publisherStyles,
                    onChange = viewModel::setPublisherStyles,
                )
            }
            item { HorizontalDivider() }
            item { ProgressModeRow(selected = state.progressDisplayMode, onSelect = viewModel::setProgressDisplayMode) }
            item { HorizontalDivider() }
            item {
                SettingSwitchRow(
                    name = stringResource(R.string.reader_show_top_info),
                    checked = state.showTopInfo,
                    onChange = viewModel::setShowTopInfo,
                )
            }
            item { HorizontalDivider() }
            item {
                SettingSwitchRow(
                    name = stringResource(R.string.reader_show_bottom_info),
                    checked = state.showBottomInfo,
                    onChange = viewModel::setShowBottomInfo,
                )
            }
            item { HorizontalDivider() }
            item {
                SettingSwitchRow(
                    name = stringResource(R.string.reader_battery_percent),
                    checked = state.batteryPercent,
                    onChange = viewModel::setBatteryPercent,
                )
            }
            item { Spacer(Modifier.height(24.dp)) }
        }
    }

    if (screenOffDialog) {
        ScreenOffDialog(
            current = state.screenOffMinutes,
            onSelect = {
                viewModel.setScreenOffMinutes(it)
                screenOffDialog = false
            },
            onDismiss = { screenOffDialog = false },
        )
    }

    if (restReminderDialog) {
        RestReminderDialog(
            enabled = state.restReminderEnabled,
            currentMinutes = state.restReminderMinutes,
            onSelect = { enabled, minutes ->
                viewModel.setRestReminder(enabled, minutes)
                restReminderDialog = false
            },
            onDismiss = { restReminderDialog = false },
        )
    }
}

@Composable
private fun GroupHeader(title: String) {
    Text(
        text = title,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        style = MaterialTheme.typography.labelLarge,
        modifier = Modifier.padding(horizontal = 20.dp, vertical = 14.dp),
    )
}

@Composable
private fun SettingSwitchRow(
    name: String,
    checked: Boolean,
    onChange: (Boolean) -> Unit,
    enabled: Boolean = true,
    hint: String? = null,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(name, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface)
            if (hint != null) {
                Text(
                    hint,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Switch(
            checked = checked,
            onCheckedChange = onChange,
            enabled = enabled,
            colors = SwitchDefaults.colors(uncheckedTrackColor = MaterialTheme.colorScheme.surfaceVariant),
        )
    }
}

@Composable
private fun SettingValueRow(
    name: String,
    value: String,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(name, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.weight(1f))
        Text(value, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.width(4.dp))
        Icon(Icons.Rounded.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
    }
}

@Composable
private fun ProgressModeRow(
    selected: String,
    onSelect: (String) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(stringResource(R.string.reader_progress_display_mode), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.weight(1f))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ProgressModeChip(stringResource(R.string.reader_progress_percentage), selected == "percentage") { onSelect("percentage") }
            ProgressModeChip(stringResource(R.string.reader_progress_page), selected == "page") { onSelect("page") }
        }
    }
}

@Composable
private fun ProgressModeChip(label: String, selected: Boolean, onClick: () -> Unit) {
    val bg = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant
    val fg = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
    Box(
        modifier = Modifier
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 6.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, color = fg, style = MaterialTheme.typography.labelLarge, fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal)
    }
}

@Composable
private fun ScreenOffDialog(
    current: Int,
    onSelect: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    OptionDialog(
        title = stringResource(R.string.reader_screen_off_time),
        options = listOf(
            stringResource(R.string.reader_screen_off_always) to 0,
            stringResource(R.string.reader_minutes_format, 2) to 2,
            stringResource(R.string.reader_minutes_format, 5) to 5,
            stringResource(R.string.reader_minutes_format, 10) to 10,
            stringResource(R.string.reader_minutes_format, 15) to 15,
        ),
        current = current,
        onSelect = onSelect,
        onDismiss = onDismiss,
    )
}

@Composable
private fun RestReminderDialog(
    enabled: Boolean,
    currentMinutes: Int,
    onSelect: (Boolean, Int) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.reader_rest_reminder_display)) },
        text = {
            Column {
                DialogOptionRow(stringResource(R.string.reader_off), !enabled) {
                    onSelect(false, currentMinutes)
                }
                listOf(15, 30, 45, 60).forEach { minutes ->
                    DialogOptionRow(stringResource(R.string.reader_minutes_format, minutes), enabled && currentMinutes == minutes) {
                        onSelect(true, minutes)
                    }
                }
            }
        },
        confirmButton = {},
    )
}

@Composable
private fun OptionDialog(
    title: String,
    options: List<Pair<String, Int>>,
    current: Int,
    onSelect: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                options.forEach { (label, value) ->
                    DialogOptionRow(label, current == value) { onSelect(value) }
                }
            }
        },
        confirmButton = {},
    )
}

@Composable
private fun DialogOptionRow(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val fg = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            label,
            style = MaterialTheme.typography.bodyLarge,
            color = fg,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            modifier = Modifier.weight(1f),
        )
        if (selected) {
            Text("✓", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodyLarge)
        }
    }
}

@Composable
private fun screenOffLabel(minutes: Int): String =
    if (minutes <= 0) stringResource(R.string.reader_screen_off_always)
    else stringResource(R.string.reader_minutes_format, minutes)

@Composable
private fun restReminderLabel(enabled: Boolean, minutes: Int): String =
    if (enabled) stringResource(R.string.reader_minutes_format, minutes) else stringResource(R.string.reader_off)
