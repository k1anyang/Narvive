package com.narvive.app.service.ai

import kotlinx.serialization.Serializable

/** AI 请求协议（自定义 Provider 可选；预设默认 OpenAI Chat Completions） */
@Serializable
enum class AiProtocol(val label: String) {
    OPENAI_CHAT("OpenAI Chat Completions"),
    OPENAI_RESPONSES("OpenAI Responses API"),
    ANTHROPIC("Anthropic Messages"),
}

/**
 * AI Provider 配置 — 预设 + 自定义。
 * 序列化后存 DataStore（不含 API Key，Key 单独存 EncryptedSharedPreferences）。
 */
@Serializable
data class ProviderConfig(
    val id: String,
    val name: String,
    val baseUrl: String,
    val modelName: String,
    val isCustom: Boolean = false,
    val isEnabled: Boolean = true,
    val priority: Int = 0,
    val consecutiveFailures: Int = 0,
    val isDegraded: Boolean = false,
    val protocol: AiProtocol = AiProtocol.OPENAI_CHAT,
    /**
     * 模型上下文规模（token），决定长章节是整章发送还是先走检索。
     *
     * **0 表示「未设置」**：旧版本持久化的 JSON 里没有这个字段，反序列化会得到 0，
     * 从而回落到预设值或 [DEFAULT_CONTEXT_WINDOW]——因此不需要数据迁移，
     * 也不会把用户从没设过的值误当成 128K 覆盖掉预设。
     */
    val contextWindow: Int = 0,
) {
    /** 实际生效的上下文规模 */
    val effectiveContextWindow: Int
        get() = contextWindow.coerceIn(
            if (contextWindow == 0) DEFAULT_CONTEXT_WINDOW else MIN_CONTEXT_WINDOW,
            MAX_CONTEXT_WINDOW,
        )

    companion object {
        /**
         * 默认上下文规模。
         *
         * 三个预设统一按 128K 设定：这是主流云 API 的常见档位，
         * 折算出「正文直发上限」约 5.7 万 token，既不会一上来就撑爆小模型，
         * 也不至于让普通章节频繁走检索。用户可在设置里按模型实际能力调整。
         */
        const val DEFAULT_CONTEXT_WINDOW = 128_000

        /** 允许的上下文范围：过小会让检索本身失去意义，过大则失去保护作用 */
        const val MIN_CONTEXT_WINDOW = 8_000
        const val MAX_CONTEXT_WINDOW = 2_000_000
    }
}

object ProviderPresets {
    val deepseek = ProviderConfig(
        id = "deepseek", name = "DeepSeek",
        baseUrl = "https://api.deepseek.com", modelName = "deepseek-flash",
        contextWindow = 128_000,
    )
    val openai = ProviderConfig(
        id = "openai", name = "OpenAI",
        baseUrl = "https://api.openai.com", modelName = "gpt-4o-mini",
        contextWindow = 128_000,
    )
    val gemini = ProviderConfig(
        id = "gemini", name = "Gemini",
        baseUrl = "https://generativelanguage.googleapis.com/v1beta/openai", modelName = "gemini-3.5-flash",
        contextWindow = 128_000,
    )
    val all = listOf(deepseek, openai, gemini)

    fun getDefault(): ProviderConfig = deepseek
}
