package com.narvive.app.ui.screen.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Cached
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.narvive.app.R
import com.narvive.app.service.StorageInfo
import com.narvive.app.service.StorageService
import com.narvive.app.ui.components.TabPageScaffold
import com.narvive.app.ui.message.UiMessage
import com.narvive.app.ui.message.text
import com.narvive.app.ui.theme.NarviveShape
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

@HiltViewModel
class StorageViewModel @Inject constructor(
    private val storageService: StorageService,
) : ViewModel() {

    private val _uiState = MutableStateFlow(StorageInfo())
    val uiState: StateFlow<StorageInfo> = _uiState.asStateFlow()
    private val _message = MutableStateFlow<UiMessage?>(null)
    val message: StateFlow<UiMessage?> = _message.asStateFlow()

    init { refresh() }

    fun refresh() {
        viewModelScope.launch {
            _uiState.value = withContext(Dispatchers.IO) { storageService.compute() }
        }
    }

    fun clearCache() {
        viewModelScope.launch {
            val freed = withContext(Dispatchers.IO) { storageService.clearCache() }
            refresh()
            _message.value = UiMessage.Res(R.string.storage_cleared, formatBytes(freed))
        }
    }

    fun clearMessage() { _message.value = null }
}

@Composable
fun StorageScreen(
    onBackClick: () -> Unit,
    viewModel: StorageViewModel = hiltViewModel(),
) {
    val info by viewModel.uiState.collectAsState()
    val message by viewModel.message.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    var showClearConfirm by remember { mutableStateOf(false) }

    // 消息文本必须在 Composable 作用域内解析（UiMessage.text() 是 @Composable），
    // 再交给 LaunchedEffect 使用；不能把它写进 effect 的 lambda 里。
    val messageText = message?.text()
    LaunchedEffect(messageText) {
        if (messageText != null) {
            snackbarHostState.showSnackbar(messageText)
            viewModel.clearMessage()
        }
    }

    if (showClearConfirm) {
        AlertDialog(
            onDismissRequest = { showClearConfirm = false },
            title = { Text(stringResource(R.string.storage_clear_confirm_title)) },
            text = { Text(stringResource(R.string.storage_clear_confirm_body, formatBytes(info.cacheBytes))) },
            confirmButton = {
                TextButton(onClick = { viewModel.clearCache(); showClearConfirm = false }) { Text(stringResource(R.string.storage_clear_cache)) }
            },
            dismissButton = { TextButton(onClick = { showClearConfirm = false }) { Text(stringResource(R.string.common_cancel)) } },
        )
    }

    TabPageScaffold(
        title = stringResource(R.string.storage_title),
        navigationIcon = { IconButton(onClick = onBackClick) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, stringResource(R.string.common_back)) } },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
        ) {
            Card(Modifier.fillMaxWidth(), shape = NarviveShape.Md) {
                Column(Modifier.padding(16.dp)) {
                    Text(stringResource(R.string.storage_total), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(formatBytes(info.totalBytes), style = MaterialTheme.typography.headlineMedium)
                }
            }
            Spacer(Modifier.height(16.dp))

            StorageRow(stringResource(R.string.storage_books), stringResource(R.string.storage_books_desc), formatBytes(info.booksBytes), stringResource(R.string.storage_count_items, info.booksCount))
            HorizontalDivider(Modifier.padding(vertical = 4.dp))
            StorageRow(stringResource(R.string.storage_covers), stringResource(R.string.storage_covers_desc), formatBytes(info.coversBytes), stringResource(R.string.storage_count_items, info.coversCount))
            HorizontalDivider(Modifier.padding(vertical = 4.dp))
            StorageRow(stringResource(R.string.storage_fonts), stringResource(R.string.storage_fonts_desc), formatBytes(info.fontsBytes), stringResource(R.string.storage_count_items, info.fontsCount))
            HorizontalDivider(Modifier.padding(vertical = 4.dp))
            StorageRow(stringResource(R.string.storage_cache), stringResource(R.string.storage_cache_desc), formatBytes(info.cacheBytes), "")

            Spacer(Modifier.height(16.dp))
            OutlinedButton(
                onClick = { showClearConfirm = true },
                enabled = info.cacheBytes > 0,
                modifier = Modifier.fillMaxWidth().height(46.dp),
            ) {
                Icon(Icons.Rounded.Cached, null)
                Spacer(Modifier.padding(horizontal = 4.dp))
                Text(stringResource(R.string.storage_clear_cache))
            }
            Spacer(Modifier.height(12.dp))
            Text(
                stringResource(R.string.storage_delete_note),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                lineHeight = 18.sp,
            )
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun StorageRow(name: String, desc: String, size: String, extra: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(name, style = MaterialTheme.typography.titleSmall)
            Text(desc, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(size, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
            if (extra.isNotBlank()) {
                Text(extra, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

private fun formatBytes(bytes: Long): String = when {
    bytes <= 0 -> "0 B"
    bytes < 1024 -> "$bytes B"
    bytes < 1024 * 1024 -> "%.1f KB".format(bytes / 1024.0)
    else -> "%.1f MB".format(bytes / 1024.0 / 1024.0)
}
