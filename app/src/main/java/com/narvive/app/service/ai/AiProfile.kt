package com.narvive.app.service.ai

import org.json.JSONArray
import org.json.JSONObject

/** 用户 AI 偏好档案（主 AI 页客制化；自动归纳或手动填写） */
data class AiProfile(
    val summary: String = "",
    val style: String = "casual", // casual | concise | formal | friendly
    val useEmoji: Boolean = false,
    val terms: List<String> = emptyList(),
) {
    val isEmpty: Boolean get() = summary.isBlank() && terms.isEmpty()

    fun toJson(): String = JSONObject().apply {
        put("summary", summary)
        put("style", style)
        put("useEmoji", useEmoji)
        put("terms", JSONArray(terms))
    }.toString()

    companion object {
        fun fromJson(json: String?): AiProfile = runCatching {
            if (json.isNullOrBlank()) return@runCatching AiProfile()
            val start = json.indexOf('{')
            val end = json.lastIndexOf('}')
            val raw = if (start >= 0 && end > start) json.substring(start, end + 1) else json
            val o = JSONObject(raw)
            AiProfile(
                summary = o.optString("summary"),
                style = o.optString("style", "casual"),
                useEmoji = o.optBoolean("useEmoji"),
                terms = o.optJSONArray("terms")?.let { arr ->
                    (0 until arr.length()).mapNotNull { i -> arr.optString(i).takeIf { it.isNotBlank() } }
                } ?: emptyList(),
            )
        }.getOrDefault(AiProfile())
    }
}
