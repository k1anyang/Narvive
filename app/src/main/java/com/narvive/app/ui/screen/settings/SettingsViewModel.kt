package com.narvive.app.ui.screen.settings

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.narvive.app.R
import com.narvive.app.data.datastore.NarviveDataStore
import com.narvive.app.service.BackupService
import com.narvive.app.ui.message.UiMessage
import com.narvive.app.ui.prefs.AppLanguage
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SettingsUiState(
    val darkTheme: String = "system",
    val appearanceTheme: String = "sky",
    val readingTheme: String = "paper",
    val fontSize: Int = 17,
    val lineHeight: Float = 1.4f,
    val horizontalMargin: Float = 1f,
    val alignment: String = "justify",
    val lastBackupTime: Long = 0L,
    val gridColumns: Int = 2,
    /**
     * 当前界面语言。
     *
     * 来源是 AppCompat 的 per-app locale（见 [AppLanguage.current]），**不经 DataStore**。
     * 每次供 UI 读取时重新求值，不缓存，避免与真实 locale 漂移。
     */
    val language: AppLanguage = AppLanguage.DEFAULT,
)

private data class ReadingPrefs(
    val theme: String, val fontSize: Int, val lineHeight: Float, val horizontalMargin: Float, val alignment: String,
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val dataStore: NarviveDataStore,
    private val backupService: BackupService,
) : ViewModel() {

    private val _backupMessage = MutableStateFlow<UiMessage?>(null)
    val backupMessage: StateFlow<UiMessage?> = _backupMessage.asStateFlow()

    private val readingPrefs: StateFlow<ReadingPrefs> = combine(
        dataStore.readingTheme,
        dataStore.defaultFontSize,
        dataStore.defaultLineHeight,
        dataStore.defaultHorizontalMargin,
        dataStore.defaultAlignment,
    ) { theme, size, lh, horizontalMargin, align -> ReadingPrefs(theme, size, lh, horizontalMargin, align) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, ReadingPrefs("paper", 17, 1.4f, 1f, "justify"))

    /**
     * 用 MutableStateFlow 承载，而不是把 DataStore 的 flow 直接 combine 成 uiState，
     * 原因是界面语言（language）来自 AppCompat 的 per-app locale、不经 DataStore；
     * 这样 DataStore 每次发射做一次字段级更新，不会覆盖语言状态。
     */
    private val _uiState = MutableStateFlow(SettingsUiState(language = AppLanguage.current()))
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            combine(
                dataStore.darkTheme,
                dataStore.appearanceTheme,
                readingPrefs,
                dataStore.lastBackupTime,
                dataStore.gridColumns,
            ) { dark, appearance, prefs, backup, columns ->
                SettingsUiState(
                    darkTheme = dark,
                    appearanceTheme = appearance,
                    readingTheme = prefs.theme,
                    fontSize = prefs.fontSize,
                    lineHeight = prefs.lineHeight,
                    horizontalMargin = prefs.horizontalMargin,
                    alignment = prefs.alignment,
                    lastBackupTime = backup,
                    gridColumns = columns,
                )
            }.collect { fromStore ->
                _uiState.update { it.copy(
                    darkTheme = fromStore.darkTheme,
                    appearanceTheme = fromStore.appearanceTheme,
                    readingTheme = fromStore.readingTheme,
                    fontSize = fromStore.fontSize,
                    lineHeight = fromStore.lineHeight,
                    horizontalMargin = fromStore.horizontalMargin,
                    alignment = fromStore.alignment,
                    lastBackupTime = fromStore.lastBackupTime,
                    gridColumns = fromStore.gridColumns,
                ) }
            }
        }
    }

    fun setDarkTheme(mode: String) {
        viewModelScope.launch { dataStore.setDarkTheme(mode) }
    }

    fun setAppearanceTheme(themeId: String) {
        viewModelScope.launch { dataStore.setAppearanceTheme(themeId) }
    }

    fun setReadingTheme(theme: String) {
        viewModelScope.launch { dataStore.setReadingTheme(theme) }
    }

    fun setFontSize(size: Int) {
        viewModelScope.launch { dataStore.setDefaultFontSize(size) }
    }

    fun setLineHeight(lh: Float) {
        viewModelScope.launch { dataStore.setDefaultLineHeight(lh) }
    }

    fun setHorizontalMargin(margin: Float) {
        viewModelScope.launch { dataStore.setDefaultHorizontalMargin(margin) }
    }

    fun setAlignment(alignment: String) {
        viewModelScope.launch { dataStore.setDefaultAlignment(alignment) }
    }

    fun setGridColumns(columns: Int) {
        viewModelScope.launch { dataStore.setGridColumns(columns) }
    }

    /**
     * 切换界面语言。
     *
     * [AppLanguage.apply] 会持久化偏好并触发 Activity 重创建；重创建后 ViewModel 重建，
     * 初始化即从 AppCompat 读回真实 locale，因此不需要（也不应该）依赖内存缓存。
     *
     * 这里同步刷新一次内存态：一是让当前页面立刻反映选择，二是万一重创建被系统推迟，
     * 界面也不会停留在旧值。同时在**下一次组合时**再从 AppCompat 复核一次，
     * 保证显示的永远是实际生效的语言（防止「记忆值」与真实值漂移）。
     */
    fun setLanguage(language: AppLanguage) {
        if (language == AppLanguage.current()) return
        AppLanguage.apply(language)
        syncLanguageFromSystem()
    }

    /** 以 AppCompat 实际 locale 为准刷新语言状态。 */
    private fun syncLanguageFromSystem() {
        val actual = AppLanguage.current()
        _uiState.update { it.copy(language = actual) }
    }

    /**
     * 供界面在每次进入时调用：重新从 AppCompat 读一次语言。
     *
     * 覆盖这些场景：用户从**系统设置**里改了应用语言（不经本应用代码）、
     * Activity 被系统回收后恢复、或重创建被推迟。读的是同步的系统值，无 IO。
     */
    fun refreshLanguage() = syncLanguageFromSystem()

    fun exportBackup(uri: Uri) {
        viewModelScope.launch {
            _backupMessage.value = UiMessage.Res(R.string.backup_msg_exporting)
            backupService.exportBackupTo(uri)
                .onSuccess { fileName ->
                    _backupMessage.value = UiMessage.Res(R.string.backup_msg_export_ok, fileName)
                }
                .onFailure { e ->
                    _backupMessage.value = UiMessage.Res(R.string.backup_msg_export_failed, e.message ?: "")
                }
        }
    }

    fun importBackup(uri: Uri) {
        viewModelScope.launch {
            _backupMessage.value = UiMessage.Res(R.string.backup_msg_importing)
            backupService.importBackup(uri)
                .onSuccess { count -> _backupMessage.value = UiMessage.Res(R.string.backup_msg_import_ok, count) }
                .onFailure { e ->
                    _backupMessage.value = UiMessage.Res(R.string.backup_msg_import_failed, e.message ?: "")
                }
        }
    }

    fun clearMessage() { _backupMessage.value = null }
}
