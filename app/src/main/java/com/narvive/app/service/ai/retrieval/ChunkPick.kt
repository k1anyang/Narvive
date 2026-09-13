package com.narvive.app.service.ai.retrieval

/**
 * 模型选块的结果：**必须区分三态**。
 *
 * 旧实现把「模型说概览就够了」「模型返回了垃圾」「全部 Provider 调用失败」统一收敛成空列表，
 * 于是网络故障会被伪装成「用户问题较泛」，让模型在只有 80 字概览的情况下编答案。
 * 这三种情况的正确处置完全不同：
 * - [OverviewEnough]：真的可以只凭概览回答；
 * - [Picked]：注入选中的块；
 * - [Failed]：用本地词法兜底，并在提示里如实标明检索降级。
 */
sealed interface ChunkPick {
    data object OverviewEnough : ChunkPick
    data class Picked(val indices: List<Int>) : ChunkPick
    data class Failed(val reason: String) : ChunkPick
}

/**
 * 选块结果解析：**严格校验，绝不全文扫数字**。
 *
 * 旧实现 `Regex("\\d+").findAll(raw)` 会把模型复述概览时的 `[1] [2] …` 全部当成选择结果，
 * 配合 `.take(maxBlocks)` 永远取到前两块——看起来「正常」，实则完全没在做检索。
 * 这里只接受「纯编号列表」这一种形态，其余一律判失败走本地兜底。
 */
object ChunkSelectionParser {

    /** 只允许：可选方括号 + 数字 + 分隔符（逗号/顿号/分号/斜杠/空白），末尾可有句号 */
    private val STRICT_LIST = Regex("^\\[?\\s*\\d+(?:\\s*[,，、;；/]\\s*\\d+|\\s+\\d+)*\\s*]?[\\s.。]*$")

    private const val MAX_LINE_CHARS = 160

    /**
     * @param itemCount 候选条数（1 基编号的上界）
     * @param maxPick 最多采纳几个
     */
    fun parse(raw: String, itemCount: Int, maxPick: Int): ChunkPick {
        if (itemCount <= 0) return ChunkPick.Failed("no-items")
        val text = raw.trim()
        if (text.isEmpty()) return ChunkPick.Failed("empty-response")

        // 取第一行有内容的；模型偶尔会在前面加一句「好的」
        var line: String? = null
        for (l in text.lineSequence()) {
            if (l.isNotBlank()) {
                line = l.trim()
                break
            }
        }
        if (line == null) return ChunkPick.Failed("empty-response")

        val candidate = normalize(line)
            ?: return ChunkPick.Failed("unparsable:${line.take(48)}")

        if (!STRICT_LIST.matches(candidate)) {
            return ChunkPick.Failed("unparsable:${candidate.take(48)}")
        }
        val numbers = Regex("\\d+").findAll(candidate).mapNotNull { it.value.toIntOrNull() }.toList()
        if (numbers.isEmpty()) return ChunkPick.Failed("no-number")
        // 明确表示「概览就够了」
        if (numbers.size == 1 && numbers[0] == 0) return ChunkPick.OverviewEnough

        val picked = numbers.asSequence()
            .filter { it in 1..itemCount }
            .distinct()
            .take(maxPick)
            .map { it - 1 }
            .toList()
        if (picked.isEmpty()) return ChunkPick.Failed("out-of-range")
        return ChunkPick.Picked(picked)
    }

    /**
     * 容忍「块编号：3, 7」这类带标签的写法：从第一个数字开始截取，并限制长度。
     * 不做更宽松的模糊匹配——宁可判失败走本地兜底，也不要选错块。
     */
    private fun normalize(line: String): String? {
        val trimmed = line.trim().trim('`', '*', '#', '"', '“', '”')
        val firstDigit = trimmed.indexOfFirst { it.isDigit() }
        if (firstDigit < 0) return null
        val from = trimmed.substring(firstDigit)
        if (from.length > MAX_LINE_CHARS) return null
        return from
    }
}
