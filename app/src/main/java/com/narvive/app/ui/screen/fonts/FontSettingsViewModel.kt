package com.narvive.app.ui.screen.fonts

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.narvive.app.R
import com.narvive.app.data.datastore.NarviveDataStore
import com.narvive.app.domain.model.FontInfo
import com.narvive.app.service.font.FontCatalogRepository
import com.narvive.app.service.font.FontDownloadManager
import com.narvive.app.service.font.FontDownloadState
import com.narvive.app.service.font.FontResolver
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * 字体设置页 ViewModel：目录加载 + 下载状态 + 中英字体选择（写入全局 DataStore）。
 */
@HiltViewModel
class FontSettingsViewModel @Inject constructor(
    private val catalogRepo: FontCatalogRepository,
    private val downloadManager: FontDownloadManager,
    private val dataStore: NarviveDataStore,
    @ApplicationContext private val appContext: Context,
) : ViewModel() {

    data class UiState(
        val loading: Boolean = true,
        val error: String? = null,
        val zh: List<FontInfo> = emptyList(),
        val en: List<FontInfo> = emptyList(),
        val currentCjk: String = FontResolver.SYSTEM_FONT_ID,
        val currentLatin: String = FontResolver.SYSTEM_FONT_ID,
        val states: Map<String, FontDownloadState> = emptyMap(),
    )

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    init {
        loadCatalog()
        viewModelScope.launch {
            combine(
                dataStore.fontFamilyCjk,
                dataStore.fontFamilyLatin,
                downloadManager.allStates,
            ) { cjk, latin, states -> Triple(cjk, latin, states) }
                .collect { (cjk, latin, states) ->
                    _uiState.update { it.copy(currentCjk = cjk, currentLatin = latin, states = states) }
                }
        }
    }

    fun retry() = loadCatalog()

    private fun loadCatalog() {
        viewModelScope.launch {
            _uiState.update { it.copy(loading = true, error = null) }
            val catalog = catalogRepo.loadCatalog()
            downloadManager.refreshFromDisk(catalog)
            _uiState.update {
                it.copy(
                    loading = false,
                    zh = catalog.filter { info -> info.isChinese },
                    en = catalog.filter { info -> info.isEnglish },
                    error = if (catalog.isEmpty()) appContext.getString(R.string.font_settings_catalog_failed) else null,
                )
            }
        }
    }

    fun download(font: FontInfo) = downloadManager.download(font)

    fun delete(font: FontInfo) {
        downloadManager.delete(font)
        // 若删除的字体正处于使用中，自动切回系统默认，避免阅读器引用不存在的字体文件
        viewModelScope.launch {
            val st = _uiState.value
            if (st.currentCjk == font.id) dataStore.setFontFamilyCjk(FontResolver.SYSTEM_FONT_ID)
            if (st.currentLatin == font.id) dataStore.setFontFamilyLatin(FontResolver.SYSTEM_FONT_ID)
        }
    }

    /** 选中某字体（中/英独立），写全局默认；「系统默认」传 [FontResolver.SYSTEM_FONT_ID] */
    fun use(fontId: String, lang: String) {
        viewModelScope.launch {
            if (lang == "zh") dataStore.setFontFamilyCjk(fontId)
            else dataStore.setFontFamilyLatin(fontId)
        }
    }
}