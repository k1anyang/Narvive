package com.narvive.app.service.ai

import android.content.Context
import com.narvive.app.R
import com.narvive.app.data.keystore.ApiKeyStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.BufferedReader
import java.io.InputStreamReader
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

data class AiMessage(
    val role: String,
    val content: String,
)

data class AiResponse(
    val content: String,
    val isComplete: Boolean,
)

/** AI 请求失败（网络/协议/鉴权），供 Fallback 链记录并切换 */
class AiException(message: String, cause: Throwable? = null) : Exception(message, cause)

/**
 * 统一 AI 服务：按 Provider 协议分发到
 * OpenAI Chat Completions / OpenAI Responses API / Anthropic Messages。
 */
@Singleton
class AiService @Inject constructor(
    @ApplicationContext private val appContext: Context,
    private val apiKeyStore: ApiKeyStore,
) {
    private val jsonParser = Json { ignoreUnknownKeys = true }

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    /** SSE 流式专用：读超时放宽（长流可能长时间无新 token），连接超时仍 15s */
    private val streamClient = client.newBuilder()
        .readTimeout(0, TimeUnit.SECONDS)
        .build()

    /** 流式对话。失败抛出 [AiException]（取消异常正常上抛），调用方据此驱动 Fallback 链。 */
    fun streamChat(
        provider: ProviderConfig,
        messages: List<AiMessage>,
    ): Flow<AiResponse> = when (provider.protocol) {
        AiProtocol.OPENAI_CHAT -> streamOpenAiChat(provider, messages)
        AiProtocol.OPENAI_RESPONSES -> streamOpenAiResponses(provider, messages)
        AiProtocol.ANTHROPIC -> streamAnthropic(provider, messages)
    }

    suspend fun simpleChat(provider: ProviderConfig, messages: List<AiMessage>): Result<String> =
        withContext(Dispatchers.IO) {
            try {
                val apiKey = apiKeyStore.getApiKey(provider.id)
                    ?: return@withContext Result.failure(AiException(appContext.getString(R.string.ai_service_no_api_key)))
                val (url, body) = when (provider.protocol) {
                    AiProtocol.OPENAI_CHAT -> chatUrl(provider) to buildChatBody(provider.modelName, messages, stream = false)
                    AiProtocol.OPENAI_RESPONSES -> responsesUrl(provider) to buildResponsesBody(provider.modelName, messages)
                    AiProtocol.ANTHROPIC -> messagesUrl(provider) to buildAnthropicBody(provider.modelName, messages)
                }
                val req = authedRequest(provider, apiKey, url, body)
                client.newCall(req).execute().use { r ->
                    val text = r.body?.string() ?: ""
                    if (!r.isSuccessful) throw AiException("HTTP ${r.code}")
                    val obj = jsonParser.parseToJsonElement(text) as? JsonObject
                        ?: throw AiException(appContext.getString(R.string.ai_service_response_parse_failed))
                    val content = when (provider.protocol) {
                        AiProtocol.OPENAI_CHAT -> parseChatCompletionContent(obj)
                        AiProtocol.OPENAI_RESPONSES -> parseResponsesContent(obj)
                        AiProtocol.ANTHROPIC -> parseAnthropicContent(obj)
                    }
                    Result.success(content)
                }
            } catch (e: AiException) {
                Result.failure(e)
            } catch (e: Exception) {
                Result.failure(AiException(e.message ?: appContext.getString(R.string.ai_service_network_error), e))
            }
        }

    suspend fun testConnectivity(provider: ProviderConfig): Result<Long> = withContext(Dispatchers.IO) {
        val apiKey = apiKeyStore.getApiKey(provider.id)
            ?: return@withContext Result.failure(AiException(appContext.getString(R.string.ai_service_no_api_key)))
        val start = System.currentTimeMillis()
        try {
            val req = authedRequest(provider, apiKey, modelsUrl(provider), null, method = "GET")
            client.newCall(req).execute().use { r ->
                if (r.isSuccessful) Result.success(System.currentTimeMillis() - start)
                else Result.failure(httpError(appContext.getString(R.string.ai_service_op_test_connection), r.code, r.body?.string()))
            }
        } catch (e: AiException) {
            Result.failure(e)
        } catch (e: IllegalArgumentException) {
            Result.failure(AiException(appContext.getString(R.string.ai_service_invalid_base_url)))
        } catch (e: Exception) {
            Result.failure(AiException(appContext.getString(R.string.ai_service_network_error_detail, e.message ?: e.javaClass.simpleName), e))
        }
    }

    /** 获取模型列表（OpenAI 两协议与 Anthropic 均返回 {"data":[{"id":...}]}） */
    suspend fun fetchModels(provider: ProviderConfig): Result<List<String>> = withContext(Dispatchers.IO) {
        val apiKey = apiKeyStore.getApiKey(provider.id) ?: return@withContext Result.failure(AiException(appContext.getString(R.string.ai_service_api_key_required)))
        try {
            val req = authedRequest(provider, apiKey, modelsUrl(provider), null, method = "GET")
            client.newCall(req).execute().use { r ->
                val text = r.body?.string() ?: ""
                if (!r.isSuccessful) return@withContext Result.failure(httpError(appContext.getString(R.string.ai_service_op_fetch_models), r.code, text))
                val obj = jsonParser.parseToJsonElement(text) as? JsonObject
                    ?: return@withContext Result.failure(AiException(appContext.getString(R.string.ai_service_model_list_parse_failed)))
                val ids = (obj["data"] as? JsonArray).orEmpty().mapNotNull { item ->
                    (item as? JsonObject)?.get("id")?.let { jsonPrimitive -> (jsonPrimitive as? JsonPrimitive)?.content }
                }.filter { it.isNotBlank() }
                Result.success(ids)
            }
        } catch (e: AiException) {
            Result.failure(e)
        } catch (e: IllegalArgumentException) {
            Result.failure(AiException(appContext.getString(R.string.ai_service_invalid_base_url)))
        } catch (e: Exception) {
            Result.failure(AiException(appContext.getString(R.string.ai_service_network_error_detail, e.message ?: e.javaClass.simpleName), e))
        }
    }

    /** 把非 2xx 响应翻译成可读错误：区分 Key 错误 / URL 错误 / 参数错误，并透出服务端 error.message */
    private fun httpError(operation: String, code: Int, bodyText: String?): AiException {
        val providerMsg = parseErrorMessage(bodyText)
        val base = when (code) {
            401, 403 -> appContext.getString(R.string.ai_service_http_401_403, code)
            404 -> appContext.getString(R.string.ai_service_http_404, code)
            400, 422 -> appContext.getString(R.string.ai_service_http_400_422, code)
            else -> appContext.getString(R.string.ai_service_http_other, code)
        }
        val detail = providerMsg?.let { appContext.getString(R.string.ai_service_http_error_provider_detail, it) } ?: ""
        return AiException(appContext.getString(R.string.ai_service_http_error, operation, base, detail))
    }

    private fun parseErrorMessage(bodyText: String?): String? {
        if (bodyText.isNullOrBlank()) return null
        return runCatching {
            val obj = jsonParser.parseToJsonElement(bodyText) as? JsonObject ?: return null
            val errObj = obj["error"] as? JsonObject
            val msg = errObj?.get("message")?.let { (it as? JsonPrimitive)?.content }
                ?: (obj["error"] as? JsonPrimitive)?.content
            msg?.take(160)
        }.getOrNull()
    }

    // ── 流式：OpenAI Chat Completions ──

    private fun streamOpenAiChat(provider: ProviderConfig, messages: List<AiMessage>): Flow<AiResponse> = flow {
        val apiKey = apiKeyStore.getApiKey(provider.id)
            ?: throw AiException(appContext.getString(R.string.ai_service_api_key_required_for_provider, provider.name))
        val body = buildChatBody(provider.modelName, messages, stream = true)
        val request = authedRequest(provider, apiKey, chatUrl(provider), body)

        try {
            streamClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) throw AiException(appContext.getString(R.string.ai_service_request_failed_http, response.code))
                val reader = readerOf(response)
                var full = ""
                while (true) {
                    val line = reader.readLine() ?: break
                    if (!line.startsWith("data: ")) continue
                    val data = line.removePrefix("data: ").trim()
                    if (data == "[DONE]") break
                    try {
                        val obj = jsonParser.parseToJsonElement(data) as? JsonObject ?: continue
                        val choices = obj["choices"] as? JsonArray ?: continue
                        if (choices.isEmpty()) continue
                        val delta = (choices[0] as? JsonObject)?.get("delta") as? JsonObject ?: continue
                        val token = (delta["content"] as? JsonPrimitive)?.content ?: ""
                        if (token.isNotEmpty()) {
                            full += token
                            emit(AiResponse(full, false))
                        }
                    } catch (e: CancellationException) {
                        throw e
                    } catch (_: Exception) {
                        // 单行解析失败跳过
                    }
                }
                emit(AiResponse(full, true))
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: AiException) {
            throw e
        } catch (e: Exception) {
            throw AiException(appContext.getString(R.string.ai_service_network_error_detail, e.message ?: e.javaClass.simpleName), e)
        }
    }.flowOn(Dispatchers.IO)

    // ── 流式：OpenAI Responses API ──

    private fun streamOpenAiResponses(provider: ProviderConfig, messages: List<AiMessage>): Flow<AiResponse> = flow {
        val apiKey = apiKeyStore.getApiKey(provider.id)
            ?: throw AiException(appContext.getString(R.string.ai_service_api_key_required_for_provider, provider.name))
        val body = buildResponsesBody(provider.modelName, messages, stream = true)
        val request = authedRequest(provider, apiKey, responsesUrl(provider), body)

        try {
            streamClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) throw AiException(appContext.getString(R.string.ai_service_request_failed_http, response.code))
                val reader = readerOf(response)
                var full = ""
                var event = ""
                while (true) {
                    val line = reader.readLine() ?: break
                    when {
                        line.startsWith("event: ") -> event = line.removePrefix("event: ").trim()
                        line.startsWith("data: ") -> {
                            val data = line.removePrefix("data: ").trim()
                            if (data == "[DONE]") break
                            try {
                                val obj = jsonParser.parseToJsonElement(data) as? JsonObject ?: continue
                                val type = obj["type"]?.let { (it as? JsonPrimitive)?.content }.orEmpty()
                                if (type == "error") throw AiException(appContext.getString(R.string.ai_service_request_failed_detail, obj.toString().take(300)))
                                val delta = (obj["delta"] as? JsonPrimitive)?.content ?: ""
                                if (delta.isNotEmpty()) {
                                    full += delta
                                    emit(AiResponse(full, false))
                                }
                            } catch (e: CancellationException) {
                                throw e
                            } catch (_: Exception) {
                                // 单帧解析失败跳过
                            }
                        }
                        else -> Unit
                    }
                    if (event == "response.completed") break
                }
                emit(AiResponse(full, true))
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: AiException) {
            throw e
        } catch (e: Exception) {
            throw AiException(appContext.getString(R.string.ai_service_network_error_detail, e.message ?: e.javaClass.simpleName), e)
        }
    }.flowOn(Dispatchers.IO)

    // ── 流式：Anthropic Messages ──

    private fun streamAnthropic(provider: ProviderConfig, messages: List<AiMessage>): Flow<AiResponse> = flow {
        val apiKey = apiKeyStore.getApiKey(provider.id)
            ?: throw AiException(appContext.getString(R.string.ai_service_api_key_required_for_provider, provider.name))
        val body = buildAnthropicBody(provider.modelName, messages, stream = true)
        val request = authedRequest(provider, apiKey, messagesUrl(provider), body)

        try {
            streamClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    val err = response.body?.string().orEmpty()
                    throw AiException(appContext.getString(R.string.ai_service_request_failed_http_detail, response.code, err.take(200)))
                }
                val reader = readerOf(response)
                var full = ""
                var finished = false
                while (true) {
                    val line = reader.readLine() ?: break
                    if (!line.startsWith("data: ")) continue
                    val data = line.removePrefix("data: ").trim()
                    if (data == "[DONE]") break
                    try {
                        val obj = jsonParser.parseToJsonElement(data) as? JsonObject ?: continue
                        val type = obj["type"]?.let { (it as? JsonPrimitive)?.content }.orEmpty()
                        if (type == "error") throw AiException(appContext.getString(R.string.ai_service_request_failed_detail, obj.toString().take(300)))
                        if (type == "message_stop") finished = true
                        val delta = obj["delta"] as? JsonObject
                        val text = delta?.get("text")?.let { (it as? JsonPrimitive)?.content } ?: ""
                        if (text.isNotEmpty()) {
                            full += text
                            emit(AiResponse(full, false))
                        }
                    } catch (e: CancellationException) {
                        throw e
                    } catch (_: Exception) {
                        // 单帧解析失败跳过
                    }
                    if (finished) break
                }
                emit(AiResponse(full, true))
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: AiException) {
            throw e
        } catch (e: Exception) {
            throw AiException(appContext.getString(R.string.ai_service_network_error_detail, e.message ?: e.javaClass.simpleName), e)
        }
    }.flowOn(Dispatchers.IO)

    // ── URL / Header / Body ──

    private fun apiBase(provider: ProviderConfig): String {
        val trimmed = provider.baseUrl.trimEnd('/')
        return if (trimmed.endsWith("/v1")) trimmed else "$trimmed/v1"
    }
    private fun chatUrl(provider: ProviderConfig): String = "${apiBase(provider)}/chat/completions"
    private fun responsesUrl(provider: ProviderConfig): String = "${apiBase(provider)}/responses"
    private fun messagesUrl(provider: ProviderConfig): String = "${apiBase(provider)}/messages"
    private fun modelsUrl(provider: ProviderConfig): String = "${apiBase(provider)}/models"

    private fun authedRequest(
        provider: ProviderConfig,
        apiKey: String,
        url: String,
        body: String?,
        method: String = "POST",
    ): Request {
        val builder = Request.Builder().url(url)
        if (provider.protocol == AiProtocol.ANTHROPIC) {
            builder.header("x-api-key", apiKey)
                .header("anthropic-version", ANTHROPIC_VERSION)
        } else {
            builder.header("Authorization", "Bearer $apiKey")
        }
        if (body != null) builder.method(method, body.toRequestBody("application/json".toMediaType()))
        else builder.method(method, null)
        return builder.build()
    }

    private fun readerOf(response: okhttp3.Response): BufferedReader {
        val input = response.body?.byteStream() ?: throw AiException(appContext.getString(R.string.ai_service_empty_response))
        return BufferedReader(InputStreamReader(input, Charsets.UTF_8))
    }

    private fun buildChatBody(model: String, msgs: List<AiMessage>, stream: Boolean): String =
        buildJsonObject {
            put("model", model)
            put("messages", buildJsonArray {
                msgs.forEach { m ->
                    add(buildJsonObject {
                        put("role", m.role)
                        put("content", m.content)
                    })
                }
            })
            put("stream", stream)
        }.toString()

    private fun buildResponsesBody(model: String, msgs: List<AiMessage>, stream: Boolean = false): String =
        buildJsonObject {
            put("model", model)
            put("input", buildJsonArray {
                msgs.forEach { m ->
                    add(buildJsonObject {
                        put("role", m.role)
                        put("content", m.content)
                    })
                }
            })
            put("stream", stream)
        }.toString()

    private fun buildAnthropicBody(model: String, msgs: List<AiMessage>, stream: Boolean = false): String =
        buildJsonObject {
            put("model", model)
            put("max_tokens", 4096)
            val system = msgs.filter { it.role == "system" }.joinToString("\n") { it.content }
            if (system.isNotBlank()) put("system", system)
            put("messages", buildJsonArray {
                msgs.filter { it.role != "system" }.forEach { m ->
                    add(buildJsonObject {
                        put("role", m.role)
                        put("content", m.content)
                    })
                }
            })
            put("stream", stream)
        }.toString()

    private fun parseChatCompletionContent(obj: JsonObject): String {
        val choices = obj["choices"] as? JsonArray ?: return ""
        val msg = (choices.firstOrNull() as? JsonObject)?.get("message") as? JsonObject ?: return ""
        return (msg["content"] as? JsonPrimitive)?.content ?: ""
    }

    private fun parseResponsesContent(obj: JsonObject): String {
        val output = obj["output"] as? JsonArray ?: return ""
        val sb = StringBuilder()
        output.forEach { item ->
            val o = item as? JsonObject ?: return@forEach
            val type = o["type"]?.let { (it as? JsonPrimitive)?.content }
            if (type == "message") {
                val contentArr = o["content"] as? JsonArray ?: return@forEach
                contentArr.forEach { c ->
                    val co = c as? JsonObject ?: return@forEach
                    if (co["type"]?.let { (it as? JsonPrimitive)?.content } == "output_text") {
                        sb.append((co["text"] as? JsonPrimitive)?.content ?: "")
                    }
                }
            }
        }
        return sb.toString()
    }

    private fun parseAnthropicContent(obj: JsonObject): String {
        val content = obj["content"] as? JsonArray ?: return ""
        val sb = StringBuilder()
        content.forEach { item ->
            val o = item as? JsonObject ?: return@forEach
            if (o["type"]?.let { (it as? JsonPrimitive)?.content } == "text") {
                sb.append((o["text"] as? JsonPrimitive)?.content ?: "")
            }
        }
        return sb.toString()
    }

    companion object {
        const val ANTHROPIC_VERSION = "2023-06-01"
    }
}
