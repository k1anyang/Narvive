package com.narvive.app.ui.screen.settings

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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.narvive.app.R
import com.narvive.app.service.ai.AiProtocol
import com.narvive.app.service.ai.ProviderConfig
import com.narvive.app.ui.components.TabPageScaffold
import com.narvive.app.ui.theme.NarviveShape
import com.narvive.app.ui.theme.SemanticColors
import java.util.UUID

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AiSettingsScreen(
    onBackClick: () -> Unit,
    viewModel: AiSettingsViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    var showAddSheet by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<ProviderConfig?>(null) }
    var showKeyDialogFor by remember { mutableStateOf<String?>(null) }
    var keyInput by remember { mutableStateOf("") }

    // Key input dialog
    showKeyDialogFor?.let { providerId ->
        AlertDialog(
            onDismissRequest = { keyInput = ""; showKeyDialogFor = null },
            title = { Text(stringResource(R.string.ai_settings_key_dialog_title)) },
            text = { OutlinedTextField(keyInput, { keyInput = it }, label = { Text("API Key") }, singleLine = true, modifier = Modifier.fillMaxWidth()) },
            confirmButton = {
                TextButton(
                    onClick = { viewModel.saveApiKey(providerId, keyInput); keyInput = ""; showKeyDialogFor = null },
                    enabled = keyInput.isNotBlank(),
                ) { Text(stringResource(R.string.common_save)) }
            },
            dismissButton = { TextButton(onClick = { keyInput = ""; showKeyDialogFor = null }) { Text(stringResource(R.string.common_cancel)) } },
        )
    }

    // Add custom provider sheet
    if (showAddSheet) {
        val draft = remember {
            ProviderConfig(
                id = "custom_${UUID.randomUUID().toString().take(8)}",
                name = "",
                baseUrl = "",
                modelName = "",
                isCustom = true,
            )
        }
        ProviderFormSheet(
            initial = draft,
            isNew = true,
            hasKey = false,
            modelList = uiState.modelLists[draft.id].orEmpty(),
            modelLoading = draft.id in uiState.modelLoading,
            modelError = uiState.modelErrors[draft.id],
            onFetchModels = { config, key -> viewModel.fetchModels(config, key) },
            onSave = { config, key ->
                viewModel.addCustomProvider(config, key ?: "")
                showAddSheet = false
            },
            onDismiss = { showAddSheet = false },
        )
    }

    // Edit provider sheet（预设仅模型可改，自定义全字段可改）
    editing?.let { target ->
        ProviderFormSheet(
            initial = target,
            isNew = false,
            hasKey = target.id in uiState.keyProviderIds,
            modelList = uiState.modelLists[target.id].orEmpty(),
            modelLoading = target.id in uiState.modelLoading,
            modelError = uiState.modelErrors[target.id],
            onFetchModels = { config, key -> viewModel.fetchModels(config, key) },
            onSave = { config, key ->
                viewModel.updateProvider(config, key)
                editing = null
            },
            onDismiss = { editing = null },
        )
    }

    TabPageScaffold(
        title = stringResource(R.string.ai_settings_title),
        navigationIcon = { IconButton(onClick = onBackClick) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, stringResource(R.string.common_back)) } },
        floatingActionButton = { FloatingActionButton(onClick = { showAddSheet = true }) { Icon(Icons.Rounded.Add, stringResource(R.string.ai_settings_add)) } },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp)) {
            Text(stringResource(R.string.ai_settings_providers_header), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(4.dp))
            Text(stringResource(R.string.ai_settings_providers_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(8.dp))
            val sorted = uiState.providers.sortedBy { it.priority }
            // 降级醒目黄条（每个降级 Provider 一条，附「恢复」操作）
            sorted.filter { it.isDegraded }.forEach { degraded ->
                DegradedWarnBanner(
                    provider = degraded,
                    onReset = { viewModel.resetDegradation(degraded.id) },
                )
                Spacer(Modifier.height(8.dp))
            }
            sorted.forEachIndexed { index, provider ->
                ProviderCard(
                    provider = provider,
                    hasKey = provider.id in uiState.keyProviderIds,
                    canMoveUp = index > 0,
                    canMoveDown = index < sorted.lastIndex,
                    onClick = { editing = provider },
                    onMoveUp = { viewModel.moveProvider(provider.id, -1) },
                    onMoveDown = { viewModel.moveProvider(provider.id, 1) },
                    onToggle = { viewModel.toggleProvider(provider.id) },
                    onSetKey = { keyInput = ""; showKeyDialogFor = provider.id },
                    onTest = { viewModel.testConnection(provider.id) },
                    onReset = { viewModel.resetDegradation(provider.id) },
                    testResult = uiState.testResults[provider.id],
                    onRemove = if (provider.isCustom) ({ viewModel.removeProvider(provider.id) }) else null,
                )
                Spacer(Modifier.height(8.dp))
            }
        }
    }
}

/** Provider 表单：新建（全字段）与编辑（预设仅模型、自定义全字段）共用 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ProviderFormSheet(
    initial: ProviderConfig,
    isNew: Boolean,
    hasKey: Boolean,
    modelList: List<String>,
    modelLoading: Boolean,
    modelError: String?,
    onFetchModels: (ProviderConfig, String?) -> Unit,
    onSave: (ProviderConfig, String?) -> Unit,
    onDismiss: () -> Unit,
) {
    var name by remember(initial.id) { mutableStateOf(initial.name) }
    var url by remember(initial.id) { mutableStateOf(initial.baseUrl) }
    var model by remember(initial.id) { mutableStateOf(initial.modelName) }
    var key by remember(initial.id) { mutableStateOf("") }
    var protocol by remember(initial.id) { mutableStateOf(initial.protocol) }
    var protocolMenu by remember(initial.id) { mutableStateOf(false) }

    val urlValid = runCatching {
        val u = java.net.URL(url)
        (u.protocol == "http" || u.protocol == "https") && u.host.isNotBlank()
    }.getOrDefault(false)
    val canFetch = urlValid && (key.isNotBlank() || hasKey)
    val canSave = if (initial.isCustom) name.isNotBlank() && urlValid && model.isNotBlank() else model.isNotBlank()

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 32.dp),
        ) {
            Text(if (isNew) stringResource(R.string.ai_settings_add_custom) else stringResource(R.string.ai_settings_edit_provider), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(6.dp))
            Text(
                if (initial.isCustom) stringResource(R.string.ai_settings_protocols_supported)
                else stringResource(R.string.ai_settings_preset_model_only),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(20.dp))

            if (initial.isCustom) {
                OutlinedTextField(name, { name = it }, label = { Text(stringResource(R.string.ai_settings_name)) }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    url, { url = it }, label = { Text(stringResource(R.string.ai_settings_base_url)) }, singleLine = true, modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("https://api.xxx.com") }, isError = url.isNotBlank() && !urlValid,
                    supportingText = { if (url.isNotBlank() && !urlValid) Text(stringResource(R.string.ai_settings_url_invalid)) },
                )
                Spacer(Modifier.height(12.dp))

                // 协议选择
                Box {
                    TextButton(onClick = { protocolMenu = true }, modifier = Modifier.fillMaxWidth()) {
                        Text(stringResource(R.string.ai_settings_protocol_prefix, protocol.label), modifier = Modifier.weight(1f), textAlign = androidx.compose.ui.text.style.TextAlign.Start)
                        Icon(Icons.Rounded.KeyboardArrowDown, null)
                    }
                    DropdownMenu(expanded = protocolMenu, onDismissRequest = { protocolMenu = false }) {
                        AiProtocol.entries.forEach { p ->
                            DropdownMenuItem(
                                text = { Text(p.label) },
                                onClick = { protocol = p; protocolMenu = false },
                            )
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
            } else {
                Text(
                    stringResource(R.string.ai_settings_protocol_preset_fixed, initial.protocol.label),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(12.dp))
            }

            OutlinedTextField(
                model, { model = it }, label = { Text(stringResource(R.string.ai_settings_model_name)) }, singleLine = true, modifier = Modifier.fillMaxWidth(),
                placeholder = { Text(stringResource(R.string.ai_settings_model_placeholder)) },
            )
            Spacer(Modifier.height(8.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                Button(
                    onClick = {
                        val config = initial.copy(
                            name = name.trim(),
                            baseUrl = url.trim(),
                            modelName = model.trim(),
                            protocol = protocol,
                        )
                        onFetchModels(config, key.ifBlank { null })
                    },
                    enabled = canFetch && !modelLoading,
                    modifier = Modifier.height(40.dp),
                ) {
                    if (modelLoading) {
                        CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
                        Spacer(Modifier.width(6.dp))
                    }
                    Text(stringResource(R.string.ai_settings_fetch_models), style = MaterialTheme.typography.labelSmall)
                }
                if (key.isBlank() && !hasKey) {
                    Spacer(Modifier.width(10.dp))
                    Text(stringResource(R.string.ai_settings_key_first), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            modelError?.let {
                Spacer(Modifier.height(6.dp))
                Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error)
            }

            if (modelList.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                Text(stringResource(R.string.ai_settings_pick_model), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(4.dp))
                Column(
                    Modifier
                        .fillMaxWidth()
                        .heightIn(max = 160.dp)
                        .verticalScroll(rememberScrollState())
                        .border(1.dp, MaterialTheme.colorScheme.outlineVariant, NarviveShape.Sm),
                ) {
                    modelList.forEach { id ->
                        Text(
                            id,
                            style = MaterialTheme.typography.bodySmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            color = if (id == model) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { model = id }
                                .padding(horizontal = 10.dp, vertical = 8.dp),
                        )
                    }
                }
            }

            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                key, { key = it },
                label = { Text(stringResource(if (hasKey) R.string.ai_settings_key_keep else R.string.ai_settings_password_label)) },
                singleLine = true, modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(24.dp))
            Button(
                onClick = {
                    val config = initial.copy(
                        name = name.trim(),
                        baseUrl = url.trim(),
                        modelName = model.trim(),
                        protocol = protocol,
                    )
                    onSave(config, key.trim().ifBlank { null })
                },
                enabled = canSave,
                modifier = Modifier.fillMaxWidth().height(48.dp),
                shape = NarviveShape.Md,
            ) { Text(stringResource(R.string.common_save), style = MaterialTheme.typography.titleSmall) }
        }
    }
}

/** 降级警告黄条（原型 AI 配置页 warn-banner：黄底#FEF9C3 / 边框#FDE047 / 文字#854D0E / 图标#CA8A04） */
@Composable
private fun DegradedWarnBanner(provider: ProviderConfig, onReset: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .background(SemanticColors.WarningBg, NarviveShape.Md)
            .border(1.dp, SemanticColors.WarningBorder, NarviveShape.Md)
            .padding(horizontal = 14.dp, vertical = 12.dp),
    ) {
        Icon(
            Icons.Rounded.Warning, contentDescription = null,
            tint = SemanticColors.WarningIcon,
            modifier = Modifier.height(18.dp).width(18.dp),
        )
        Spacer(Modifier.width(10.dp))
        Text(
            stringResource(R.string.ai_settings_degraded_banner, provider.name, provider.consecutiveFailures),
            style = MaterialTheme.typography.bodySmall,
            color = SemanticColors.WarningText,
            modifier = Modifier.weight(1f),
        )
        Spacer(Modifier.width(10.dp))
        Surface(
            shape = NarviveShape.Sm,
            color = Color.White,
            border = androidx.compose.foundation.BorderStroke(1.dp, SemanticColors.WarningBorder),
            modifier = Modifier.clickable(onClick = onReset),
        ) {
            Text(
                stringResource(R.string.ai_settings_restore),
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.SemiBold,
                color = SemanticColors.WarningText,
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
            )
        }
    }
}

@Composable
private fun ProviderCard(
    provider: ProviderConfig, hasKey: Boolean,
    canMoveUp: Boolean, canMoveDown: Boolean,
    onClick: () -> Unit,
    onMoveUp: () -> Unit, onMoveDown: () -> Unit,
    onToggle: () -> Unit, onSetKey: () -> Unit, onTest: () -> Unit, onReset: () -> Unit,
    testResult: TestResult?, onRemove: (() -> Unit)?,
) {
    Card(
        Modifier.fillMaxWidth().clickable(onClick = onClick),
        shape = NarviveShape.Md,
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column {
                    IconButton(onClick = onMoveUp, enabled = canMoveUp) { Icon(Icons.Rounded.KeyboardArrowUp, stringResource(R.string.ai_settings_move_up)) }
                    IconButton(onClick = onMoveDown, enabled = canMoveDown) { Icon(Icons.Rounded.KeyboardArrowDown, stringResource(R.string.ai_settings_move_down)) }
                }
                Spacer(Modifier.padding(4.dp))
                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(provider.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        if (provider.isDegraded) Text(stringResource(R.string.ai_settings_degrades), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error)
                    }
                    Text(
                        "${provider.modelName}  ·  ${provider.protocol.label}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Switch(checked = provider.isEnabled, onCheckedChange = { onToggle() })
            }
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onSetKey, modifier = Modifier.height(36.dp)) {
                    Text(stringResource(if (hasKey) R.string.ai_settings_change_key else R.string.ai_settings_set_key), style = MaterialTheme.typography.labelSmall)
                }
                Button(onClick = onTest, modifier = Modifier.height(36.dp), enabled = hasKey) { Text(stringResource(R.string.ai_settings_test_connection), style = MaterialTheme.typography.labelSmall) }
                if (provider.isDegraded) TextButton(onClick = onReset, modifier = Modifier.height(36.dp)) { Text(stringResource(R.string.ai_settings_restore)) }
                onRemove?.let { TextButton(onClick = it, modifier = Modifier.height(36.dp)) { Text(stringResource(R.string.common_delete)) } }
            }
            testResult?.let {
                Spacer(Modifier.height(4.dp))
                // 颜色依结构化状态判定，而非匹配文案（原实现 contains("成功") 在非中文语言下失效）
                Text(it.message, style = MaterialTheme.typography.bodySmall, color = if (it.ok) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error)
            }
        }
    }
}
