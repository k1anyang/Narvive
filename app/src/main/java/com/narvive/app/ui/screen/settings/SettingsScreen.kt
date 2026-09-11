package com.narvive.app.ui.screen.settings

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.rounded.MenuBook
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Brightness6
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Cloud
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.DriveFolderUpload
import androidx.compose.material.icons.rounded.FontDownload
import androidx.compose.material.icons.rounded.Feedback
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.PrivacyTip
import androidx.compose.material.icons.rounded.Storage
import androidx.compose.material.icons.rounded.Upload
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.narvive.app.R
import com.narvive.app.ui.components.TabPageScaffold
import com.narvive.app.ui.message.text
import com.narvive.app.ui.prefs.AppLanguage
import com.narvive.app.ui.theme.AppearanceThemes
import com.narvive.app.ui.theme.NarviveShape
import com.narvive.app.ui.theme.SemanticColors
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun SettingsScreen(
    onAiClick: () -> Unit = {},
    onPromptClick: () -> Unit = {},
    onAiPreferencesClick: () -> Unit = {},
    onAppearanceClick: () -> Unit = {},
    onFontClick: () -> Unit = {},
    onLanguageClick: () -> Unit = {},
    onReadingSettingsClick: () -> Unit = {},
    onBackupClick: () -> Unit = {},
    onServicesClick: () -> Unit = {},
    onStorageClick: () -> Unit = {},
    onAboutClick: () -> Unit = {},
    onPrivacyClick: () -> Unit = {},
    onUserAgreementClick: () -> Unit = {},
    onLicensesClick: () -> Unit = {},
    onFeedbackClick: () -> Unit = {},
    modifier: Modifier = Modifier,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    // 每次进入设置页都从 AppCompat 复核一次语言，避免显示值与真实值漂移
    LaunchedEffect(Unit) { viewModel.refreshLanguage() }
    val themeLabel = when (uiState.darkTheme) {
        "light" -> stringResource(R.string.settings_appearance_mode_light)
        "dark" -> stringResource(R.string.settings_appearance_mode_dark)
        else -> stringResource(R.string.settings_appearance_mode_system)
    }
    val backupDesc = if (uiState.lastBackupTime > 0)
        stringResource(R.string.settings_backup_last, SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date(uiState.lastBackupTime)))
    else stringResource(R.string.settings_backup_never)

    TabPageScaffold(title = stringResource(R.string.settings_title), modifier = modifier) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 20.dp)) {

        // 品牌卡
        Card(Modifier.fillMaxWidth(), shape = NarviveShape.Md) {
            Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(48.dp).clip(NarviveShape.Md).background(Brush.linearGradient(listOf(MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.primaryContainer))), contentAlignment = Alignment.Center) {
                    Image(painter = painterResource(R.drawable.ic_launcher_foreground), contentDescription = "Narvive", modifier = Modifier.size(32.dp))
                }
                Spacer(Modifier.width(14.dp))
                Column {
                    Text("Narvive", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text("v1.0.0", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }

        SettingsGroup(stringResource(R.string.settings_group_appearance)) {
            SettingRow(Icons.Rounded.Brightness6, stringResource(R.string.settings_row_appearance), "$themeLabel · ${stringResource(AppearanceThemes.displayNameResOf(uiState.appearanceTheme))}", onAppearanceClick)
            SettingRow(Icons.Rounded.FontDownload, stringResource(R.string.settings_row_font), "", onFontClick)
            SettingRow(Icons.Rounded.Language, stringResource(R.string.settings_row_language), stringResource(uiState.language.labelRes), onLanguageClick)
            SettingRow(Icons.AutoMirrored.Rounded.MenuBook, stringResource(R.string.settings_row_reading), "", onReadingSettingsClick, showDivider = false)
        }
        SettingsGroup(stringResource(R.string.settings_group_ai)) {
            SettingRow(Icons.Rounded.AutoAwesome, stringResource(R.string.settings_row_ai), stringResource(R.string.settings_row_ai_desc), onAiClick)
            SettingRow(Icons.Rounded.AutoAwesome, stringResource(R.string.settings_row_prompt), stringResource(R.string.settings_row_prompt_desc), onPromptClick)
            SettingRow(Icons.Rounded.AutoAwesome, stringResource(R.string.settings_row_ai_pref), stringResource(R.string.settings_row_ai_pref_desc), onAiPreferencesClick, showDivider = false)
        }
        SettingsGroup(stringResource(R.string.settings_group_data)) {
            SettingRow(Icons.Rounded.DriveFolderUpload, stringResource(R.string.settings_row_backup), backupDesc, onBackupClick)
            SettingRow(Icons.Rounded.Cloud, stringResource(R.string.settings_row_services), stringResource(R.string.settings_row_services_desc), onServicesClick)
            SettingRow(Icons.Rounded.Storage, stringResource(R.string.settings_row_storage), "", onStorageClick, showDivider = false)
        }
        SettingsGroup(stringResource(R.string.settings_group_about)) {
            SettingRow(Icons.Rounded.Info, stringResource(R.string.settings_row_about), "", onAboutClick)
            SettingRow(Icons.Rounded.PrivacyTip, stringResource(R.string.settings_row_privacy), "", onPrivacyClick)
            SettingRow(Icons.Rounded.Description, stringResource(R.string.settings_row_user_agreement), "", onUserAgreementClick)
            SettingRow(Icons.Rounded.Code, stringResource(R.string.settings_row_licenses), "", onLicensesClick)
            SettingRow(Icons.Rounded.Feedback, stringResource(R.string.settings_row_feedback), "", onFeedbackClick, showDivider = false)
        }
        }
    }
}

@Composable
private fun SettingsGroup(label: String, content: @Composable () -> Unit) {
    Spacer(Modifier.height(20.dp))
    Text(label, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant, letterSpacing = 0.8.sp, modifier = Modifier.padding(bottom = 12.dp))
    Card(Modifier.fillMaxWidth(), shape = NarviveShape.Md) { Column { content() } }
}

@Composable
private fun SettingRow(icon: ImageVector, name: String, desc: String, onClick: () -> Unit, showDivider: Boolean = true) {
    Column {
        Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 14.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(32.dp).clip(NarviveShape.Sm).background(MaterialTheme.colorScheme.primaryContainer), contentAlignment = Alignment.Center) {
                Icon(icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(name, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                if (desc.isNotEmpty()) Text(desc, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f))
        }
        if (showDivider) Box(Modifier.padding(start = 60.dp).fillMaxWidth().height(1.dp).background(MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)))
    }
}

// ── 外观 ──
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppearanceScreen(onBackClick: () -> Unit, viewModel: SettingsViewModel = hiltViewModel()) {
    val uiState by viewModel.uiState.collectAsState()
    TabPageScaffold(
        title = stringResource(R.string.settings_appearance_title),
        navigationIcon = { IconButton(onClick = onBackClick) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, stringResource(R.string.common_back)) } },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(20.dp)) {
            Text(stringResource(R.string.settings_appearance_section_theme), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(10.dp))
            AppearanceThemes.all.chunked(2).forEach { rowThemes ->
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    rowThemes.forEach { spec ->
                        AppearanceOption(
                            stringResource(spec.displayNameRes),
                            stringResource(spec.descriptionRes),
                            uiState.appearanceTheme == spec.id,
                            spec.previewColors,
                            Modifier.weight(1f),
                        ) { viewModel.setAppearanceTheme(spec.id) }
                    }
                    if (rowThemes.size == 1) Spacer(Modifier.weight(1f))
                }
                Spacer(Modifier.height(10.dp))
            }
            Spacer(Modifier.height(10.dp))
            Text(stringResource(R.string.settings_appearance_section_mode), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                AppearanceOption(stringResource(R.string.settings_appearance_mode_light), stringResource(R.string.settings_appearance_mode_light_desc), uiState.darkTheme == "light", listOf(Color(0xFFF8FAFC), Color(0xFFF8FAFC)), Modifier.weight(1f)) { viewModel.setDarkTheme("light") }
                AppearanceOption(stringResource(R.string.settings_appearance_mode_dark), stringResource(R.string.settings_appearance_mode_dark_desc), uiState.darkTheme == "dark", listOf(Color(0xFF0F172A), Color(0xFF0F172A)), Modifier.weight(1f)) { viewModel.setDarkTheme("dark") }
                AppearanceOption(stringResource(R.string.settings_appearance_mode_system), stringResource(R.string.settings_appearance_mode_system_desc), uiState.darkTheme == "system", listOf(Color(0xFFF8FAFC), Color(0xFF0F172A)), Modifier.weight(1f)) { viewModel.setDarkTheme("system") }
            }
            Spacer(Modifier.height(20.dp))
            Text(stringResource(R.string.settings_appearance_section_grid), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                (1..4).forEach { count ->
                    GridColumnsOption(
                        count = count,
                        selected = uiState.gridColumns == count,
                        modifier = Modifier.weight(1f),
                        onClick = { viewModel.setGridColumns(count) },
                    )
                }
            }
            Spacer(Modifier.height(20.dp))
            InfoCard(stringResource(R.string.settings_appearance_info))
        }
    }
}

// ── 语言 ──

/**
 * 界面语言选择（独立路由页）。
 *
 * 语言纵向排列、每行一项：语言名称较长（如「繁體中文」）时横向排列容易挤压变形，
 * 纵向列表每行有完整宽度，也为后续新增语言留出空间。
 *
 * 切换后由 AppCompat 重创建 Activity，文案随之更新（机制见 docs/i18n.md）。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LanguageScreen(onBackClick: () -> Unit, viewModel: SettingsViewModel = hiltViewModel()) {
    val uiState by viewModel.uiState.collectAsState()
    // 语言页同样复核一次：用户可能刚从系统「应用语言」里改过
    LaunchedEffect(Unit) { viewModel.refreshLanguage() }
    TabPageScaffold(
        title = stringResource(R.string.settings_language_section),
        navigationIcon = { IconButton(onClick = onBackClick) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, stringResource(R.string.common_back)) } },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(20.dp)) {
            AppLanguage.entries.forEach { language ->
                LanguageRow(
                    label = stringResource(language.labelRes),
                    selected = uiState.language == language,
                    onClick = { viewModel.setLanguage(language) },
                )
                Spacer(Modifier.height(10.dp))
            }
            Spacer(Modifier.height(10.dp))
            InfoCard(stringResource(R.string.settings_language_info))
        }
    }
}

/** 单个语言行（整行可点，右侧选中打勾） */
@Composable
private fun LanguageRow(label: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(NarviveShape.Md)
            .clickable(onClick = onClick)
            .border(1.5.dp, if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline, NarviveShape.Md)
            .background(if (selected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            label,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
            color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
        )
        if (selected) {
            Icon(Icons.Rounded.Check, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
        }
    }
}

@Composable
private fun AppearanceOption(label: String, desc: String, active: Boolean, previewColors: List<Color>, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Column(
        modifier.clip(NarviveShape.Md).clickable(onClick = onClick)
            .border(1.5.dp, if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline, NarviveShape.Md)
            .padding(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier.fillMaxWidth().height(64.dp).clip(NarviveShape.Sm).border(1.dp, MaterialTheme.colorScheme.outline, NarviveShape.Sm)
                .background(Brush.horizontalGradient(previewColors)),
        )
        Spacer(Modifier.height(8.dp))
        Text(label, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
        Text(desc, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun GridColumnsOption(count: Int, selected: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Column(
        modifier
            .clip(NarviveShape.Md)
            .clickable(onClick = onClick)
            .border(1.5.dp, if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline, NarviveShape.Md)
            .background(if (selected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent)
            .padding(vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            stringResource(R.string.settings_appearance_grid_books, count),
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
            color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
        )
        Text(stringResource(R.string.settings_appearance_grid_per_row), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

// ── 阅读默认值 ──

// ── 备份与恢复 ──

// ── 服务 ──

@Composable
fun ServicesScreen(
    onBackClick: () -> Unit,
    onWebdavClick: () -> Unit,
) {
    TabPageScaffold(
        title = stringResource(R.string.services_title),
        navigationIcon = { IconButton(onClick = onBackClick) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, stringResource(R.string.common_back)) } },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(20.dp)) {
            SettingsGroup(stringResource(R.string.services_group_sync)) {
                SettingRow(Icons.Rounded.Cloud, "WebDAV", stringResource(R.string.services_webdav_desc), onWebdavClick, showDivider = false)
            }
            Spacer(Modifier.height(12.dp))
            Text(
                stringResource(R.string.services_webdav_note),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                lineHeight = 18.sp,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BackupScreen(onBackClick: () -> Unit, viewModel: SettingsViewModel = hiltViewModel()) {
    val uiState by viewModel.uiState.collectAsState()
    val backupMessage by viewModel.backupMessage.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri: Uri? ->
        uri?.let { viewModel.exportBackup(it) }
    }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        uri?.let { viewModel.importBackup(it) }
    }
    // Snackbar 文案在 Composable 作用域解析（UiMessage.text() 是 @Composable），
    // 否则语言切换后已排队的消息会停留在旧语言。
    val backupMessageText = backupMessage?.text()
    LaunchedEffect(backupMessageText) {
        if (backupMessageText != null) {
            snackbarHostState.showSnackbar(backupMessageText)
            viewModel.clearMessage()
        }
    }

    TabPageScaffold(
        title = stringResource(R.string.backup_title),
        navigationIcon = { IconButton(onClick = onBackClick) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, stringResource(R.string.common_back)) } },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(20.dp)) {
            Text(stringResource(R.string.backup_export_section), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant, letterSpacing = 0.8.sp)
            Spacer(Modifier.height(12.dp))
            Card(Modifier.fillMaxWidth(), shape = NarviveShape.Md) {
                Column(Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(32.dp).clip(NarviveShape.Sm).background(MaterialTheme.colorScheme.primaryContainer), contentAlignment = Alignment.Center) {
                            Icon(Icons.Rounded.Upload, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                        }
                        Spacer(Modifier.width(12.dp))
                        Column {
                            Text(stringResource(R.string.backup_export_card_title), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                            Text("NarviveBackup-${SimpleDateFormat("yyyyMMdd", Locale.getDefault()).format(Date())}.zip", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                    Column(Modifier.fillMaxWidth().clip(NarviveShape.Sm).background(MaterialTheme.colorScheme.surfaceVariant).padding(10.dp)) {
                        Text(stringResource(R.string.backup_export_contents_books), style = MaterialTheme.typography.labelMedium, lineHeight = 18.sp)
                        Text(stringResource(R.string.backup_export_contents_data), style = MaterialTheme.typography.labelMedium, lineHeight = 18.sp)
                        Text(stringResource(R.string.backup_export_contents_manifest), style = MaterialTheme.typography.labelMedium, lineHeight = 18.sp)
                    }
                    Spacer(Modifier.height(12.dp))
                    Button(
                        onClick = { exportLauncher.launch("NarviveBackup-${SimpleDateFormat("yyyyMMdd", Locale.getDefault()).format(Date())}.zip") },
                        modifier = Modifier.fillMaxWidth().height(44.dp),
                    ) { Text(stringResource(R.string.backup_export_action)) }
                    Text(stringResource(R.string.backup_export_hint), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.fillMaxWidth().padding(top = 8.dp))
                    if (uiState.lastBackupTime > 0) {
                        Spacer(Modifier.height(4.dp))
                        Text(stringResource(R.string.backup_last_time, SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date(uiState.lastBackupTime))), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.fillMaxWidth())
                    }
                }
            }

            Spacer(Modifier.height(20.dp))
            Text(stringResource(R.string.backup_restore_section), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant, letterSpacing = 0.8.sp)
            Spacer(Modifier.height(12.dp))
            Card(Modifier.fillMaxWidth(), shape = NarviveShape.Md) {
                Column(Modifier.padding(16.dp)) {
                    Text(stringResource(R.string.backup_restore_card_title), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(8.dp))
                    Text(stringResource(R.string.backup_restore_desc), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, lineHeight = 18.sp)
                    Spacer(Modifier.height(12.dp))
                    OutlinedButton(onClick = { importLauncher.launch(arrayOf("application/zip", "application/octet-stream")) }, modifier = Modifier.fillMaxWidth().height(44.dp)) { Text(stringResource(R.string.backup_restore_pick)) }
                }
            }

            Spacer(Modifier.height(20.dp))
            WarnBanner(stringResource(R.string.backup_key_warn_title), stringResource(R.string.backup_key_warn_body))
        }
    }
}

@Composable
private fun WarnBanner(title: String, body: String) {
    Row(
        Modifier.fillMaxWidth().clip(NarviveShape.Md).background(SemanticColors.WarningBg).border(1.dp, SemanticColors.WarningBorder, NarviveShape.Md).padding(12.dp),
    ) {
        Icon(Icons.Rounded.WarningAmber, null, tint = SemanticColors.WarningIcon, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(10.dp))
        Column {
            Text(title, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = SemanticColors.WarningText)
            Text(body, style = MaterialTheme.typography.labelMedium, color = SemanticColors.WarningText, lineHeight = 16.sp)
        }
    }
}

@Composable
private fun InfoCard(text: String) {
    Row(
        Modifier.fillMaxWidth().clip(NarviveShape.Md).background(MaterialTheme.colorScheme.primaryContainer).border(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.3f), NarviveShape.Md).padding(14.dp),
    ) {
        Text("ⓘ", color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(top = 1.dp))
        Spacer(Modifier.width(10.dp))
        Text(text, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurface, lineHeight = 18.sp, modifier = Modifier.weight(1f))
    }
}

// ── 关于与隐私 ──

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AboutScreen(onBackClick: () -> Unit) {
    val uriHandler = androidx.compose.ui.platform.LocalUriHandler.current
    TabPageScaffold(
        title = stringResource(R.string.about_title),
        navigationIcon = { IconButton(onClick = onBackClick) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, stringResource(R.string.common_back)) } },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Spacer(Modifier.height(24.dp))
            Box(Modifier.size(64.dp).clip(NarviveShape.Lg).background(Brush.linearGradient(listOf(MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.primaryContainer))), contentAlignment = Alignment.Center) {
                Image(painter = painterResource(R.drawable.ic_launcher_foreground), contentDescription = "Narvive", modifier = Modifier.size(44.dp))
            }
            Spacer(Modifier.height(12.dp))
            Text("Narvive", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
            Text(stringResource(R.string.about_version, "1.0.0"), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(8.dp))
            Text(stringResource(R.string.about_tagline), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.height(24.dp))
            Card(Modifier.fillMaxWidth(), shape = NarviveShape.Md) {
                Column(Modifier.padding(16.dp)) {
                    Text(stringResource(R.string.about_card_title), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(8.dp))
                    Text(stringResource(R.string.about_card_body), style = MaterialTheme.typography.bodySmall, lineHeight = 20.sp)
                }
            }
            Spacer(Modifier.height(16.dp))
            Card(Modifier.fillMaxWidth(), shape = NarviveShape.Md) {
                Column(Modifier.padding(16.dp)) {
                    Text(stringResource(R.string.about_developer), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "k1anyang@163.com",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.clickable { uriHandler.openUri("mailto:k1anyang@163.com") },
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "github.com/k1anyang",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.clickable { uriHandler.openUri("https://github.com/k1anyang") },
                    )
                }
            }
            Spacer(Modifier.height(16.dp))
            Card(Modifier.fillMaxWidth(), shape = NarviveShape.Md) {
                Column(Modifier.padding(16.dp)) {
                    Text(stringResource(R.string.about_font_title), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(8.dp))
                    Text(
                        stringResource(R.string.about_font_body),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        lineHeight = 20.sp,
                    )
                }
            }
        }
    }
}
