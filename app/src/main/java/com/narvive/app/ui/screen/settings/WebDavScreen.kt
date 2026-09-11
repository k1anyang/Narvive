package com.narvive.app.ui.screen.settings

import android.content.Context
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.CloudUpload
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.narvive.app.R
import com.narvive.app.data.datastore.NarviveDataStore
import com.narvive.app.data.keystore.WebDavStore
import com.narvive.app.service.RemoteBackup
import com.narvive.app.ui.components.EightSegmentLoading
import com.narvive.app.ui.message.UiMessage
import com.narvive.app.ui.message.text
import dagger.hilt.android.qualifiers.ApplicationContext
import com.narvive.app.service.WebDavService
import com.narvive.app.ui.components.TabPageScaffold
import com.narvive.app.ui.theme.NarviveShape
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.math.cos
import kotlin.math.sin
import javax.inject.Inject

data class WebDavUiState(
    val enabled: Boolean = false,
    val url: String = "",
    val username: String = "",
    val remotePath: String = "Narvive",
    val autoSync: Boolean = false,
    val hasPassword: Boolean = false,
    val loaded: Boolean = false,
    val busy: Boolean = false,
    val message: UiMessage? = null,
    val backups: List<RemoteBackup> = emptyList(),
)

@HiltViewModel
class WebDavViewModel @Inject constructor(
    private val dataStore: NarviveDataStore,
    private val webDavStore: WebDavStore,
    private val webDavService: WebDavService,
    @ApplicationContext private val appContext: Context,
) : ViewModel() {

    private val _uiState = MutableStateFlow(WebDavUiState())
    val uiState: StateFlow<WebDavUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    enabled = dataStore.webdavEnabled.first(),
                    url = dataStore.webdavUrl.first(),
                    username = dataStore.webdavUsername.first(),
                    remotePath = dataStore.webdavRemotePath.first(),
                    autoSync = dataStore.webdavAutoSync.first(),
                    hasPassword = webDavStore.getPassword() != null,
                    loaded = true,
                )
            }
            refreshBackups()
        }
    }

    fun setEnabled(enabled: Boolean) {
        _uiState.update { it.copy(enabled = enabled) }
        viewModelScope.launch { dataStore.setWebdavEnabled(enabled) }
    }

    fun setAutoSync(enabled: Boolean) {
        _uiState.update { it.copy(autoSync = enabled) }
        viewModelScope.launch { dataStore.setWebdavAutoSync(enabled) }
    }

    fun saveConfig(url: String, username: String, password: String?, remotePath: String) {
        viewModelScope.launch {
            dataStore.setWebdavUrl(url.trim())
            dataStore.setWebdavUsername(username.trim())
            dataStore.setWebdavRemotePath(remotePath.trim())
            if (!password.isNullOrBlank()) webDavStore.setPassword(password)
            _uiState.update {
                it.copy(
                    url = url.trim(),
                    username = username.trim(),
                    remotePath = remotePath.trim(),
                    hasPassword = it.hasPassword || !password.isNullOrBlank(),
                    message = UiMessage.Res(R.string.webdav_msg_config_saved),
                )
            }
        }
    }

    fun testConnection() {
        if (_uiState.value.busy) return
        viewModelScope.launch {
            _uiState.update { it.copy(busy = true, message = null) }
            webDavService.testConnection()
                .onSuccess {
                    _uiState.update { s -> s.copy(busy = false, message = UiMessage.Res(R.string.webdav_msg_connect_ok)) }
                }
                .onFailure { e ->
                    _uiState.update { s -> s.copy(busy = false, message = UiMessage.Res(R.string.webdav_msg_connect_failed, e.message ?: "")) }
                }
        }
    }

    fun syncNow() {
        if (_uiState.value.busy) return
        viewModelScope.launch {
            _uiState.update { it.copy(busy = true, message = null) }
            webDavService.syncNow()
                .onSuccess { msg -> _uiState.update { it.copy(busy = false, message = UiMessage.Raw(msg)) }; refreshBackups() }
                .onFailure { e -> _uiState.update { it.copy(busy = false, message = UiMessage.Res(R.string.webdav_msg_sync_failed, e.message ?: "")) } }
        }
    }

    fun refreshBackups() {
        viewModelScope.launch {
            webDavService.listBackups()
                .onSuccess { list -> _uiState.update { it.copy(backups = list) } }
                .onFailure { _uiState.update { it.copy(backups = emptyList()) } }
        }
    }

    fun restore(name: String) {
        if (_uiState.value.busy) return
        viewModelScope.launch {
            _uiState.update { it.copy(busy = true, message = null) }
            webDavService.restore(name)
                .onSuccess { count ->
                    _uiState.update { it.copy(busy = false, message = UiMessage.Res(R.string.webdav_msg_restore_ok, count)) }
                    refreshBackups()
                }
                .onFailure { e -> _uiState.update { it.copy(busy = false, message = UiMessage.Res(R.string.webdav_msg_restore_failed, e.message ?: "")) } }
        }
    }

    fun clearMessage() { _uiState.update { it.copy(message = null) } }
}

@Composable
fun WebDavScreen(
    onBackClick: () -> Unit,
    viewModel: WebDavViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    // 以 state.loaded 为 key 同步初始化可编辑字段，避免「空→有值」触发 Switch/浮动标签的进入动画
    var url by remember(state.loaded) { mutableStateOf(if (state.loaded) state.url else "") }
    var username by remember(state.loaded) { mutableStateOf(if (state.loaded) state.username else "") }
    var password by remember { mutableStateOf("") }
    var remotePath by remember(state.loaded) { mutableStateOf(if (state.loaded) state.remotePath else "Narvive") }
    var restoreTarget by remember { mutableStateOf<String?>(null) }

    val messageText = state.message?.text()
    LaunchedEffect(messageText) {
        if (messageText != null) {
            snackbarHostState.showSnackbar(messageText)
            viewModel.clearMessage()
        }
    }

    restoreTarget?.let { name ->
        AlertDialog(
            onDismissRequest = { restoreTarget = null },
            title = { Text(stringResource(R.string.webdav_restore_title)) },
            text = { Text(stringResource(R.string.webdav_restore_body, name)) },
            confirmButton = {
                TextButton(onClick = { viewModel.restore(name); restoreTarget = null }) { Text(stringResource(R.string.webdav_restore)) }
            },
            dismissButton = { TextButton(onClick = { restoreTarget = null }) { Text(stringResource(R.string.common_cancel)) } },
        )
    }

    TabPageScaffold(
        title = stringResource(R.string.webdav_title),
        navigationIcon = { IconButton(onClick = onBackClick) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, stringResource(R.string.common_back)) } },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        if (state.loaded) {
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.webdav_enable), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                    Text(stringResource(R.string.webdav_enable_desc), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Switch(checked = state.enabled, onCheckedChange = viewModel::setEnabled)
            }

            Spacer(Modifier.height(16.dp))
            OutlinedTextField(url, { url = it }, label = { Text(stringResource(R.string.webdav_server)) }, singleLine = true, modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("https://dav.example.com/dav") })
            Spacer(Modifier.height(10.dp))
            OutlinedTextField(username, { username = it }, label = { Text(stringResource(R.string.webdav_username)) }, singleLine = true, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(10.dp))
            OutlinedTextField(password, { password = it }, label = { Text(stringResource(if (state.hasPassword) R.string.webdav_password_keep else R.string.webdav_password)) }, singleLine = true, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(10.dp))
            OutlinedTextField(remotePath, { remotePath = it }, label = { Text(stringResource(R.string.webdav_remote_dir)) }, singleLine = true, modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("Narvive") })
            Spacer(Modifier.height(12.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton(
                    onClick = { viewModel.saveConfig(url, username, password.ifBlank { null }, remotePath) },
                    modifier = Modifier.weight(1f).height(44.dp),
                ) { Text(stringResource(R.string.webdav_save_config)) }
                OutlinedButton(
                    onClick = viewModel::testConnection,
                    enabled = !state.busy,
                    modifier = Modifier.weight(1f).height(44.dp),
                ) { Text(stringResource(R.string.webdav_test)) }
            }

            Spacer(Modifier.height(20.dp))
            HorizontalDivider()
            Spacer(Modifier.height(12.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.webdav_auto_sync), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                    Text(stringResource(R.string.webdav_auto_sync_desc), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Switch(checked = state.autoSync, onCheckedChange = viewModel::setAutoSync)
            }
            Spacer(Modifier.height(12.dp))
            Button(
                onClick = viewModel::syncNow,
                enabled = !state.busy,
                modifier = Modifier.fillMaxWidth().height(46.dp),
            ) {
                Box(Modifier.size(20.dp), contentAlignment = Alignment.Center) {
                    if (state.busy) {
                        EightSegmentLoading(
                            modifier = Modifier.size(18.dp),
                            color = MaterialTheme.colorScheme.onPrimary,
                        )
                    } else {
                        Icon(Icons.Rounded.CloudUpload, null, Modifier.size(20.dp))
                    }
                }
                Spacer(Modifier.padding(horizontal = 5.dp))
                Text(stringResource(R.string.webdav_sync_now))
            }

            Spacer(Modifier.height(20.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.webdav_remote_backups), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                IconButton(onClick = viewModel::refreshBackups, enabled = !state.busy) {
                    Icon(Icons.Rounded.Refresh, stringResource(R.string.webdav_refresh), tint = MaterialTheme.colorScheme.primary)
                }
            }
            Spacer(Modifier.height(8.dp))
            if (state.backups.isEmpty()) {
                Text(stringResource(R.string.webdav_no_backups), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(vertical = 8.dp))
            } else {
                state.backups.forEach { backup ->
                    Card(Modifier.fillMaxWidth(), shape = NarviveShape.Md) {
                        Row(
                            Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(backup.name, style = MaterialTheme.typography.labelLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(
                                    "${formatBytes(backup.size)} · ${formatDate(backup.modified)}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            TextButton(onClick = { restoreTarget = backup.name }) {
                                Icon(Icons.Rounded.Download, null, Modifier.height(16.dp))
                                Spacer(Modifier.padding(horizontal = 3.dp))
                                Text(stringResource(R.string.webdav_restore))
                            }
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                }
            }
            Spacer(Modifier.height(24.dp))
        }
        } else {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        }
    }
}

@Composable
private fun formatBytes(bytes: Long): String = when {
    bytes <= 0 -> stringResource(R.string.webdav_unknown_size)
    bytes < 1024 -> "$bytes B"
    bytes < 1024 * 1024 -> "%.1f KB".format(bytes / 1024.0)
    else -> "%.1f MB".format(bytes / 1024.0 / 1024.0)
}

@Composable
private fun formatDate(millis: Long): String =
    if (millis <= 0) stringResource(R.string.webdav_unknown_time)
    else java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", java.util.Locale.getDefault()).format(java.util.Date(millis))
