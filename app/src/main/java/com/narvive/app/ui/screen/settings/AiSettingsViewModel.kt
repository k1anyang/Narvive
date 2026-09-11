package com.narvive.app.ui.screen.settings

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.narvive.app.R
import com.narvive.app.data.keystore.ApiKeyStore
import com.narvive.app.service.ai.AiService
import com.narvive.app.service.ai.FallbackChain
import com.narvive.app.service.ai.ProviderConfig
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

/**
 * 连接测试结果。
 *
 * [ok] 显式表示成功/失败：原实现让 UI 用 `message.contains("成功")` 判断颜色，
 * 这在中文以外的语言下必然失效（英文文案里没有「成功」二字）。颜色判定必须依据状态而非文案。
 */
data class TestResult(val ok: Boolean, val message: String)

data class AiSettingsUiState(
    val providers: List<ProviderConfig> = emptyList(),
    val keyProviderIds: Set<String> = emptySet(),
    val testResults: Map<String, TestResult> = emptyMap(),
    val modelLists: Map<String, List<String>> = emptyMap(),
    val modelLoading: Set<String> = emptySet(),
    val modelErrors: Map<String, String> = emptyMap(),
)

@HiltViewModel
class AiSettingsViewModel @Inject constructor(
    private val apiKeyStore: ApiKeyStore,
    private val aiService: AiService,
    private val fallbackChain: FallbackChain,
    @ApplicationContext private val appContext: Context,
) : ViewModel() {

    private val _uiState = MutableStateFlow(AiSettingsUiState())
    val uiState: StateFlow<AiSettingsUiState> = _uiState.asStateFlow()

    init {
        refresh()
        // 降级状态实时刷新
        viewModelScope.launch {
            fallbackChain.failureCounts.collect { refresh() }
        }
    }

    private fun refresh() {
        viewModelScope.launch {
            val providers = fallbackChain.loadProviders()
            val keyIds = withContext(Dispatchers.IO) {
                providers.filter { fallbackChain.hasApiKey(it.id) }.map { it.id }.toSet()
            }
            _uiState.update { it.copy(providers = providers, keyProviderIds = keyIds) }
        }
    }

    private fun persist(providers: List<ProviderConfig>) {
        viewModelScope.launch {
            fallbackChain.persistProviders(providers)
            refresh()
        }
    }

    fun toggleProvider(id: String) {
        persist(_uiState.value.providers.map { if (it.id == id) it.copy(isEnabled = !it.isEnabled) else it })
    }

    /** 优先级调整：上移/下移（原型拖拽手柄的低成本替代，已确认方案） */
    fun moveProvider(id: String, delta: Int) {
        val list = _uiState.value.providers.sortedBy { it.priority }.toMutableList()
        val idx = list.indexOfFirst { it.id == id }
        val target = idx + delta
        if (idx < 0 || target !in list.indices) return
        val item = list.removeAt(idx)
        list.add(target, item)
        persist(list.mapIndexed { i, p -> p.copy(priority = i) })
    }

    fun addCustomProvider(provider: ProviderConfig, apiKey: String) {
        if (apiKey.isNotBlank()) apiKeyStore.setApiKey(provider.id, apiKey)
        val maxPriority = (_uiState.value.providers.maxOfOrNull { it.priority } ?: -1) + 1
        persist(_uiState.value.providers + provider.copy(priority = maxPriority))
    }

    /** 编辑 Provider：预设只允许改模型（UI 层保证），自定义全字段可改；newKey 非空则同时更新 Key */
    fun updateProvider(provider: ProviderConfig, newKey: String? = null) {
        if (!newKey.isNullOrBlank()) apiKeyStore.setApiKey(provider.id, newKey)
        persist(_uiState.value.providers.map { if (it.id == provider.id) provider else it })
    }

    fun removeProvider(id: String) {
        apiKeyStore.removeApiKey(id)
        persist(_uiState.value.providers.filter { p -> p.id != id })
    }

    fun testConnection(id: String) {
        viewModelScope.launch {
            val provider = _uiState.value.providers.find { it.id == id } ?: return@launch
            _uiState.update { it.copy(testResults = it.testResults + (id to TestResult(ok = false, message = appContext.getString(R.string.ai_settings_testing)))) }
            aiService.testConnectivity(provider)
                .onSuccess { ms ->
                    val msg = appContext.getString(R.string.ai_settings_test_ok, ms)
                    _uiState.update { it.copy(testResults = it.testResults + (id to TestResult(ok = true, message = msg))) }
                }
                .onFailure { e ->
                    val msg = appContext.getString(R.string.ai_settings_test_failed, e.message ?: "")
                    _uiState.update { it.copy(testResults = it.testResults + (id to TestResult(ok = false, message = msg))) }
                }
        }
    }

    /** 按协议自动获取模型列表；draftKey 非空时先临时保存（新建 Provider 尚未持久化） */
    fun fetchModels(provider: ProviderConfig, draftKey: String? = null) {
        if (provider.id in _uiState.value.modelLoading) return
        if (!draftKey.isNullOrBlank()) apiKeyStore.setApiKey(provider.id, draftKey)
        viewModelScope.launch {
            _uiState.update { it.copy(modelLoading = it.modelLoading + provider.id, modelErrors = it.modelErrors - provider.id) }
            aiService.fetchModels(provider)
                .onSuccess { list ->
                    _uiState.update {
                        it.copy(
                            modelLoading = it.modelLoading - provider.id,
                            modelLists = it.modelLists + (provider.id to list),
                        )
                    }
                }
                .onFailure { e ->
                    _uiState.update {
                        it.copy(
                            modelLoading = it.modelLoading - provider.id,
                            modelErrors = it.modelErrors + (provider.id to (e.message ?: appContext.getString(R.string.ai_settings_fetch_failed))),
                        )
                    }
                }
        }
    }

    fun resetDegradation(id: String) {
        fallbackChain.resetDegradation(id)
        // failureCounts 流会触发 refresh，UI 自动更新
    }

    fun saveApiKey(providerId: String, key: String) {
        if (key.isBlank()) return
        apiKeyStore.setApiKey(providerId, key)
        refresh()
    }
}
