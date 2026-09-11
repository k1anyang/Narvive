package com.narvive.app.ui.screen.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.sp
import androidx.annotation.StringRes
import com.narvive.app.R
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.narvive.app.service.ai.PromptService
import com.narvive.app.service.ai.PromptTemplates
import com.narvive.app.ui.components.TabPageScaffold
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class PromptSettingsItem(
    val id: String,
    /** 标题/描述为资源 id：界面语言切换后随之更新 */
    @StringRes val titleRes: Int,
    @StringRes val descriptionRes: Int,
    /** 占位符记号（`{{...}}`，语言无关）；空串表示无占位符 */
    val placeholders: String,
    val text: String,
    val modified: Boolean,
)

@HiltViewModel
class PromptSettingsViewModel @Inject constructor(
    private val promptService: PromptService,
) : ViewModel() {

    private val _uiState = MutableStateFlow<List<PromptSettingsItem>>(emptyList())
    val uiState: StateFlow<List<PromptSettingsItem>> = _uiState.asStateFlow()

    init { refresh() }

    private fun refresh() {
        viewModelScope.launch {
            val map = promptService.getAll()
            _uiState.value = PromptTemplates.all.map { t ->
                PromptSettingsItem(
                    id = t.id,
                    titleRes = t.titleRes,
                    descriptionRes = t.descriptionRes,
                    placeholders = t.placeholders,
                    text = map[t.id] ?: t.defaultText,
                    modified = map[t.id] != null && map[t.id]?.isNotBlank() == true,
                )
            }
        }
    }

    fun save(id: String, text: String) {
        viewModelScope.launch { promptService.save(id, text); refresh() }
    }

    fun reset(id: String) {
        viewModelScope.launch { promptService.reset(id); refresh() }
    }
}

@Composable
fun PromptSettingsScreen(
    onBackClick: () -> Unit,
    viewModel: PromptSettingsViewModel = hiltViewModel(),
) {
    val items by viewModel.uiState.collectAsState()
    var editing by remember { mutableStateOf<PromptSettingsItem?>(null) }

    editing?.let { item ->
        PromptEditDialog(
            item = item,
            onSave = { text -> viewModel.save(item.id, text); editing = null },
            onReset = { viewModel.reset(item.id); editing = null },
            onDismiss = { editing = null },
        )
    }

    TabPageScaffold(
        title = stringResource(R.string.prompt_settings_title),
        navigationIcon = { IconButton(onClick = onBackClick) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, stringResource(R.string.common_back)) } },
    ) { padding ->
        LazyColumn(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 20.dp),
        ) {
            item {
                Spacer(Modifier.height(8.dp))
                Text(
                    stringResource(R.string.prompt_settings_intro),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    lineHeight = 18.sp,
                )
                Spacer(Modifier.height(12.dp))
            }
            items(items, key = { it.id }) { item ->
                Card(Modifier.fillMaxWidth()) {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable { editing = item }
                            .padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(stringResource(item.titleRes), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                                if (item.modified) {
                                    Spacer(Modifier.padding(horizontal = 4.dp))
                                    Text(stringResource(R.string.prompt_settings_modified), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                                }
                            }
                            Spacer(Modifier.height(2.dp))
                            Text(stringResource(item.descriptionRes), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Icon(Icons.Rounded.ChevronRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                Spacer(Modifier.height(8.dp))
            }
            item { Spacer(Modifier.height(24.dp)) }
        }
    }
}

@Composable
private fun PromptEditDialog(
    item: PromptSettingsItem,
    onSave: (String) -> Unit,
    onReset: () -> Unit,
    onDismiss: () -> Unit,
) {
    var text by remember(item.id) { mutableStateOf(item.text) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(item.titleRes)) },
        text = {
            Column {
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 6,
                    maxLines = 12,
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    stringResource(
                        R.string.prompt_settings_placeholders,
                        item.placeholders.ifBlank { stringResource(R.string.prompt_tpl_no_placeholders) },
                    ),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    lineHeight = 16.sp,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(text) }, enabled = text.isNotBlank()) { Text(stringResource(R.string.common_save)) }
        },
        dismissButton = {
            Row {
                if (item.modified) {
                    TextButton(onClick = onReset) { Text(stringResource(R.string.prompt_settings_reset_default), color = MaterialTheme.colorScheme.error) }
                }
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_cancel)) }
            }
        },
    )
}
