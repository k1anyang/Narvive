package com.narvive.app.service.ai

import com.narvive.app.data.datastore.NarviveDataStore
import com.narvive.app.data.keystore.ApiKeyStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Fallback 链：Provider 列表持久化于 DataStore（不含 Key）；
 * 已启用且有 Key 的 Provider 按优先级排序 → 逐个尝试 → 失败自动切下一个。
 * 某 Provider 连续失败 3 次标记「降级」，可在设置页手动恢复。
 */
@Singleton
class FallbackChain @Inject constructor(
    private val apiKeyStore: ApiKeyStore,
    private val dataStore: NarviveDataStore,
) {
    private val json = Json { ignoreUnknownKeys = true }

    /** 失败计数（响应式，供设置页实时刷新降级状态） */
    private val _failureCounts = MutableStateFlow<Map<String, Int>>(emptyMap())
    val failureCounts: StateFlow<Map<String, Int>> = _failureCounts.asStateFlow()

    /**
     * 辅助调用（选块、生成标题等内部请求）的失败计数。
     *
     * 与 [failureCounts] **分开记账**：这些调用不是用户直接发起的主对话，
     * 一次选块超时不应该把主对话的 Provider 标记为降级——否则用户会遇到
     * 「明明聊天正常，Provider 却被禁用」的怪现象。这里只做可观测性统计。
     */
    private val _utilityFailureCounts = MutableStateFlow<Map<String, Int>>(emptyMap())
    val utilityFailureCounts: StateFlow<Map<String, Int>> = _utilityFailureCounts.asStateFlow()

    val degradationThreshold = 3

    /**
     * 加载全部 Provider：预设以代码内最新值为准（enabled/priority 取持久化值），
     * 自定义 Provider 全量来自持久化。
     */
    suspend fun loadProviders(): List<ProviderConfig> {
        val stored = dataStore.providersJson.first()
            ?.let { raw -> runCatching { json.decodeFromString<List<ProviderConfig>>(raw) }.getOrNull() }
            ?: emptyList()

        val presets = ProviderPresets.all.map { preset ->
            val s = stored.firstOrNull { it.id == preset.id }
            // 预设：名称/URL/协议锁定，模型与开关/优先级可改
            if (s != null) preset.copy(
                isEnabled = s.isEnabled,
                priority = s.priority,
                modelName = s.modelName.ifBlank { preset.modelName },
                // 0 = 旧数据里没有这个字段，保留预设自身的上下文规模
                contextWindow = if (s.contextWindow > 0) s.contextWindow else preset.contextWindow,
            )
            else preset
        }
        val customs = stored.filter { it.isCustom && it.id.isNotBlank() }
        return (presets + customs).map { withRuntimeState(it) }
    }

    /** 持久化 Provider 列表（剥离运行时状态字段） */
    suspend fun persistProviders(providers: List<ProviderConfig>) {
        val stripped = providers.map { it.copy(consecutiveFailures = 0, isDegraded = false) }
        dataStore.setProvidersJson(json.encodeToString(stripped))
    }

    /**
     * 已启用且已配置 Key 的 Provider，按优先级排序。
     * Key 读取在 IO 线程执行（EncryptedSharedPreferences 为磁盘 IO）。
     */
    suspend fun getEnabledProviders(): List<ProviderConfig> = withContext(Dispatchers.IO) {
        loadProviders()
            .filter { it.isEnabled && apiKeyStore.getApiKey(it.id) != null }
            .sortedBy { it.priority }
    }

    fun hasApiKey(providerId: String): Boolean =
        apiKeyStore.getApiKey(providerId) != null

    fun recordFailure(providerId: String) {
        _failureCounts.value = _failureCounts.value + (providerId to ((_failureCounts.value[providerId] ?: 0) + 1))
    }

    fun recordSuccess(providerId: String) {
        _failureCounts.value = _failureCounts.value + (providerId to 0)
    }

    /** 辅助调用成功：只清辅助计数，不影响主链路的降级判定 */
    fun recordUtilitySuccess(providerId: String) {
        _utilityFailureCounts.value = _utilityFailureCounts.value + (providerId to 0)
    }

    /** 辅助调用失败：只累计辅助计数，**不**触发降级 */
    fun recordUtilityFailure(providerId: String) {
        _utilityFailureCounts.value =
            _utilityFailureCounts.value + (providerId to ((_utilityFailureCounts.value[providerId] ?: 0) + 1))
    }

    fun resetDegradation(providerId: String) {
        _failureCounts.value = _failureCounts.value + (providerId to 0)
    }

    private fun withRuntimeState(provider: ProviderConfig): ProviderConfig {
        val fails = _failureCounts.value[provider.id] ?: 0
        return provider.copy(
            consecutiveFailures = fails,
            isDegraded = fails >= degradationThreshold,
        )
    }
}
