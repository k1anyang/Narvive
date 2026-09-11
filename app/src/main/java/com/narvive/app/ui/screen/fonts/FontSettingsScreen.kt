package com.narvive.app.ui.screen.fonts

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.narvive.app.R
import com.narvive.app.domain.model.FontInfo
import com.narvive.app.service.font.FontDownloadState
import com.narvive.app.service.font.FontResolver
import com.narvive.app.ui.components.TabPageScaffold
import com.narvive.app.ui.theme.NarviveShape

/**
 * 字体设置页：
 * 顶部栏（返回 + "字体"）→ 双 Tab（中文字体/英文字体）→ 滚动列表。
 * 列表行：左 = 字体名称 + 官方网站链接；右 = 状态（下载/下载进度/立即使用/正在使用）+ 删除。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FontSettingsScreen(
    onBackClick: () -> Unit,
    viewModel: FontSettingsViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    var tab by rememberSaveable { mutableIntStateOf(0) }

    TabPageScaffold(
        title = stringResource(R.string.font_settings_title),
        navigationIcon = { IconButton(onClick = onBackClick) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, stringResource(R.string.common_back)) } },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            PrimaryTabRow(selectedTabIndex = tab) {
                Tab(selected = tab == 0, onClick = { tab = 0 }, text = { Text(stringResource(R.string.font_settings_tab_cjk)) })
                Tab(selected = tab == 1, onClick = { tab = 1 }, text = { Text(stringResource(R.string.font_settings_tab_latin)) })
            }

            Surface(
                shape = NarviveShape.Md,
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 10.dp),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                ) {
                    Icon(
                        Icons.Rounded.Info,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(Modifier.width(10.dp))
                    Text(
                        stringResource(R.string.font_settings_note),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.7f))

            when {
                uiState.loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
                uiState.zh.isEmpty() && uiState.en.isEmpty() -> Box(
                    Modifier.fillMaxSize().padding(32.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(uiState.error ?: stringResource(R.string.font_settings_empty), color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.height(12.dp))
                        OutlinedButton(onClick = viewModel::retry) { Text(stringResource(R.string.common_retry)) }
                    }
                }
                else -> {
                    val isZh = tab == 0
                    val fonts = if (isZh) uiState.zh else uiState.en
                    val current = if (isZh) uiState.currentCjk else uiState.currentLatin
                    LazyColumn(Modifier.fillMaxSize()) {
                        // 内置「系统默认」行（不使用下载字体）
                        item(key = "system") {
                            FontRow(
                                name = stringResource(R.string.font_settings_system_default),
                                subtitle = stringResource(R.string.font_settings_system_default_subtitle),
                                website = null,
                                state = FontDownloadState.Ready,
                                isInUse = current == FontResolver.SYSTEM_FONT_ID,
                                onDownload = null,
                                onUse = { viewModel.use(FontResolver.SYSTEM_FONT_ID, if (isZh) "zh" else "en") },
                                onDelete = null,
                            )
                            HorizontalDivider()
                        }
                        items(fonts, key = { it.id }) { font ->
                            val state = uiState.states[font.id] ?: FontDownloadState.Idle
                            FontRow(
                                name = font.name,
                                subtitle = font.category,
                                website = font.website.takeIf { it.isNotBlank() },
                                state = state,
                                isInUse = current == font.id,
                                onDownload = { viewModel.download(font) },
                                onUse = { viewModel.use(font.id, font.lang) },
                                onDelete = { viewModel.delete(font) },
                            )
                            HorizontalDivider()
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun FontRow(
    name: String,
    subtitle: String,
    website: String?,
    state: FontDownloadState,
    isInUse: Boolean,
    onDownload: (() -> Unit)?,
    onUse: (() -> Unit)?,
    onDelete: (() -> Unit)?,
) {
    val context = LocalContext.current
    Row(
        Modifier.fillMaxWidth().padding(start = 20.dp, end = 12.dp, top = 10.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // ── 左侧：名称 + 官方网站链接 ──
        Column(Modifier.weight(1f)) {
            Text(
                name,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (subtitle.isNotBlank()) {
                Spacer(Modifier.height(2.dp))
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            website?.let { url ->
                Spacer(Modifier.height(4.dp))
                TextButton(
                    onClick = {
                        runCatching {
                            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                        }
                    },
                    contentPadding = PaddingValues(0.dp),
                    modifier = Modifier.height(28.dp),
                ) {
                    Text(stringResource(R.string.font_settings_official_site), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(2.dp))
                    Icon(
                        Icons.AutoMirrored.Rounded.OpenInNew,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(14.dp),
                    )
                }
            }
        }
        Spacer(Modifier.width(12.dp))

        // ── 右侧：状态标签 ──
        StatusArea(
            state = state,
            isInUse = isInUse,
            onDownload = onDownload,
            onUse = onUse,
            onDelete = onDelete,
        )
    }
}

@Composable
private fun StatusArea(
    state: FontDownloadState,
    isInUse: Boolean,
    onDownload: (() -> Unit)?,
    onUse: (() -> Unit)?,
    onDelete: (() -> Unit)?,
) {
    if (isInUse) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .height(32.dp)
                    .padding(horizontal = 10.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    stringResource(R.string.font_settings_in_use),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            if (state == FontDownloadState.Ready && onDelete != null) {
                IconButton(onClick = onDelete, modifier = Modifier.size(36.dp)) {
                    Icon(Icons.Rounded.Delete, stringResource(R.string.font_settings_delete_font), tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
                }
            }
        }
        return
    }

    when (state) {
        FontDownloadState.Idle -> {
            OutlinedButton(onClick = onDownload ?: onUse ?: {}, contentPadding = PaddingValues(horizontal = 14.dp), modifier = Modifier.height(32.dp)) {
                Text(stringResource(R.string.font_settings_download), style = MaterialTheme.typography.labelLarge)
            }
        }
        is FontDownloadState.Downloading -> {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(stringResource(R.string.font_settings_downloading, (state.progress * 100).toInt()), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(6.dp))
                LinearProgressIndicator(
                    progress = { state.progress },
                    modifier = Modifier.width(76.dp).height(4.dp),
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.surfaceVariant,
                )
            }
        }
        is FontDownloadState.Error -> {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(state.message, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error, maxLines = 2)
                Spacer(Modifier.height(4.dp))
                TextButton(onClick = onDownload ?: {}, contentPadding = PaddingValues(0.dp), modifier = Modifier.height(28.dp)) {
                    Text(stringResource(R.string.common_retry), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                }
            }
        }
        FontDownloadState.Ready -> {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Button(
                    onClick = onUse ?: {},
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    shape = NarviveShape.Lg,
                    modifier = Modifier.height(32.dp),
                ) {
                    Text(stringResource(R.string.font_settings_use_now), style = MaterialTheme.typography.labelLarge)
                }
                if (onDelete != null) {
                    IconButton(onClick = onDelete, modifier = Modifier.size(36.dp)) {
                        Icon(Icons.Rounded.Delete, stringResource(R.string.font_settings_delete_font), tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
                    }
                }
            }
        }
    }
}
