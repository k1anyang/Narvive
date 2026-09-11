package com.narvive.app.service.ai

import android.content.Context
import com.narvive.app.R
import com.narvive.app.core.AppLang
import com.narvive.app.data.datastore.NarviveDataStore
import com.narvive.app.data.local.dao.GlobalAiDao
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

data class AiPrefState(
    val enabled: Boolean = false,
    val mode: String = "auto", // auto | manual
    val profile: AiProfile = AiProfile(),
)

/**
 * AI 偏好（主 AI 页客制化）持久化与自动归纳。
 * 自动模式：归纳主 AI 页最近对话生成偏好档案；手动模式：用户自行填写。
 */
@Singleton
class AiProfileStore @Inject constructor(
    private val dataStore: NarviveDataStore,
    private val globalAiDao: GlobalAiDao,
    private val aiService: AiService,
    private val fallbackChain: FallbackChain,
    private val localeProvider: PromptLocaleProvider,
    @ApplicationContext private val appContext: Context,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    val state: StateFlow<AiPrefState> = combine(
        dataStore.aiPrefEnabled,
        dataStore.aiPrefMode,
        dataStore.aiPrefProfileJson,
    ) { enabled, mode, json ->
        AiPrefState(enabled, mode, AiProfile.fromJson(json))
    }.stateIn(scope, SharingStarted.Eagerly, AiPrefState())

    /**
     * 当前界面语言（每次取用，切换语言后下一次归纳即生效）。
     *
     * 用于决定归纳提示词的**说明文字与示例值**用哪种语言：
     * 指令文字取自 [R.string.ai_internal_profile_instruction]，JSON 骨架留在代码里
     * （骨架含大量双引号，放资源文件需逐字转义，反而更易出错）。
     *
     * 恒定不变的部分：[AiProfile.fromJson] 解析用的 JSON 键名
     * `summary` / `style` / `useEmoji` / `terms`，以及 `style` 的候选值
     * `casual|concise|formal|friendly` 与 true/false —— 这些不属于可翻译文案。
     */
    private fun lang(): AppLang = localeProvider.current()

    suspend fun setEnabled(enabled: Boolean) = dataStore.setAiPrefEnabled(enabled)

    suspend fun setMode(mode: String) = dataStore.setAiPrefMode(mode)

    suspend fun setManualProfile(text: String) {
        dataStore.setAiPrefProfileJson(AiProfile(summary = text.trim()).toJson())
    }

    suspend fun clearProfile() = dataStore.setAiPrefProfileJson(null)

    /** 自动归纳：读取主 AI 页最近对话 → 归纳偏好 → 落库。仅在 开关开启 且 自动 模式下执行。 */
    suspend fun refreshAuto() {
        val s = state.value
        if (!s.enabled || s.mode != "auto") return
        val recent = recentMessages() ?: return
        val providers = fallbackChain.getEnabledProviders()
        if (providers.isEmpty()) return

        val prompt = buildString {
            append(appContext.getString(R.string.ai_internal_profile_instruction)).append("\n")
            // JSON 骨架：键名与 style 候选值 / true|false 恒定不变（解析器按固定英文键读取），
            // 只随语言更换字段说明与示例值。骨架留在代码里避免资源文件的引号转义风险。
            append(
                when (lang()) {
                    AppLang.ZH_HANS ->
                        "{\"summary\":\"一句话概括用户偏好（如：喜欢简洁、中文、不爱用表情）\"," +
                            "\"style\":\"casual|concise|formal|friendly\"," +
                            "\"useEmoji\":true或false," +
                            "\"terms\":[\"用户常用术语\"]}\n\n"
                    AppLang.ZH_HANT ->
                        "{\"summary\":\"一句話概括使用者偏好（如：喜歡簡潔、繁體中文、不愛用表情）\"," +
                            "\"style\":\"casual|concise|formal|friendly\"," +
                            "\"useEmoji\":true或false," +
                            "\"terms\":[\"使用者常用術語\"]}\n\n"
                    AppLang.EN ->
                        "{\"summary\":\"one sentence summarising the user's preferences (e.g. prefers concise replies, English, no emoji)\"," +
                            "\"style\":\"casual|concise|formal|friendly\"," +
                            "\"useEmoji\":true或false," +
                            "\"terms\":[\"terms the user often uses\"]}\n\n"
                }
            )
            recent.forEach { m ->
                val label = appContext.getString(
                    if (m.role == "user") R.string.ai_internal_profile_role_user
                    else R.string.ai_internal_profile_role_assistant
                )
                append(label).append(m.content.take(200)).append("\n")
            }
        }

        for (provider in providers) {
            if (provider.isDegraded) continue
            aiService.simpleChat(provider, listOf(AiMessage("user", prompt)))
                .onSuccess { text ->
                    fallbackChain.recordSuccess(provider.id)
                    val p = AiProfile.fromJson(text)
                    if (!p.isEmpty) dataStore.setAiPrefProfileJson(p.toJson())
                    return
                }
                .onFailure { fallbackChain.recordFailure(provider.id) }
        }
    }

    private suspend fun recentMessages(): List<AiMessage>? = withContext(Dispatchers.IO) {
        val convs = globalAiDao.getConversations().take(3)
        if (convs.isEmpty()) return@withContext null
        convs.flatMap { c ->
            globalAiDao.getMessages(c.id).map {
                AiMessage(if (it.role == "USER") "user" else "assistant", it.content)
            }
        }.takeLast(20).ifEmpty { null }
    }
}
