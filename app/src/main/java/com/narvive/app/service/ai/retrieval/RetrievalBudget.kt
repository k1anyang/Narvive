package com.narvive.app.service.ai.retrieval

/**
 * 检索预算：**用 token 预算代替写死的字符阈值与块数**。
 *
 * 旧实现是 `CHAPTER_FULL_LIMIT = 50_000`（字符）与写死的「问答取 2 块 / 图表取 3 块」。
 * 字符阈值对中英不公平（8000 中文字≈5500 token，8000 英文字符≈2000 token），
 * 固定块数则让 20 万字与 6 万字的章节拿到同样多的上下文。
 *
 * 这里改为按模型上下文比例分配，默认值刻意保守（宁可少注入，也不要撑爆上下文）。
 */
data class RetrievalBudget(
    /** 章节正文可直接全量注入的上限（token） */
    val fullTextTokens: Int,
    /** 问答可注入的「选中块」总量（token） */
    val qaChunkTokens: Int,
    /** 覆盖式压缩（总结/关系图/时间线）的预算（token） */
    val coverageTokens: Int,
    val contextTokens: Int,
)

object RetrievalBudgetConfig {

    /**
     * 默认上下文规模。
     *
     * 取 128k：主流云 API（DeepSeek / GPT-4o / Gemini）都在这一档，
     * 且按 45% 折算出的全文上限（约 5.7 万 token）仍大于旧的 5 万字符阈值，
     * 因此**默认配置下短章节的行为与旧实现完全一致**；
     * 若将来把每个 Provider 的上下文规模接进来，小上下文模型会自动转为检索模式。
     */
    const val DEFAULT_CONTEXT_TOKENS = 128_000

    private const val FULL_TEXT_RATIO = 0.45
    private const val QA_CHUNK_TOKENS = 8_000
    private const val COVERAGE_RATIO = 0.32

    fun of(contextTokens: Int = DEFAULT_CONTEXT_TOKENS): RetrievalBudget {
        val ctx = contextTokens.coerceIn(8_000, 2_000_000)
        return RetrievalBudget(
            fullTextTokens = (ctx * FULL_TEXT_RATIO).toInt(),
            qaChunkTokens = minOf(QA_CHUNK_TOKENS, (ctx * 0.25).toInt()),
            coverageTokens = (ctx * COVERAGE_RATIO).toInt(),
            contextTokens = ctx,
        )
    }
}
