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
)

object ProviderPresets {
    val deepseek = ProviderConfig(
        id = "deepseek", name = "DeepSeek",
        baseUrl = "https://api.deepseek.com", modelName = "deepseek-chat",
    )
    val openai = ProviderConfig(
        id = "openai", name = "OpenAI",
        baseUrl = "https://api.openai.com", modelName = "gpt-4o-mini",
    )
    val gemini = ProviderConfig(
        id = "gemini", name = "Gemini",
        baseUrl = "https://generativelanguage.googleapis.com/v1beta/openai", modelName = "gemini-2.0-flash",
    )
    val all = listOf(deepseek, openai, gemini)

    fun getDefault(): ProviderConfig = deepseek
}
