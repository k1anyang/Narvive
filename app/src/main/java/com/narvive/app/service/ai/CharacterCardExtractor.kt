package com.narvive.app.service.ai

import android.content.Context
import com.narvive.app.R
import com.narvive.app.domain.model.Book
import com.narvive.app.domain.model.CharacterCard
import com.narvive.app.service.reader.TxtReaderController
import dagger.hilt.android.qualifiers.ApplicationContext
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 角色卡 AI 抽取（B4.7）：基于书籍信息与已读内容生成 身份/性格/语气/知识边界 四字段。
 * TXT 直接读原文喂给模型；EPUB 依赖模型自身对书籍的了解（阅读器外无法取章文本）。
 */
@Singleton
class CharacterCardExtractor @Inject constructor(
    private val aiService: AiService,
    private val fallbackChain: FallbackChain,
    private val promptService: PromptService,
    private val localeProvider: PromptLocaleProvider,
    @ApplicationContext private val appContext: Context,
) {

    /**
     * 已读内容摘录的段落头，随界面语言取用。
     *
     * 通过 [PromptLocaleProvider] 取当前语言（与 [PromptService] 的默认模板同一依据）；
     * 三套资源文件的键相同，占位符位置也一致。
     */
    private fun excerptHeader(): String = appContext.getString(R.string.ai_internal_excerpt_header)

    /**
     * @param chapterLabel 用户已读进度标签（如「第 23 章」）
     * @return 抽取成功返回 CharacterCard；未配置/全部失败返回 null
     */
    suspend fun extract(book: Book, characterName: String, chapterLabel: String, chapterIndex: Int): CharacterCard? {
        val providers = fallbackChain.getEnabledProviders()
        if (providers.isEmpty()) return null

        val excerpt = loadSourceExcerpt(book, chapterIndex)
        // 摘录头随界面语言；正文模板本身由 PromptService 按语言提供，此处只补自己拼装的部分
        val excerptBlock = if (!excerpt.isNullOrBlank()) {
            excerptHeader() + "\"\"\"\n$excerpt\n\"\"\"\n"
        } else ""
        val prompt = PromptRenderer.render(
            promptService.get(PromptTemplates.CHARACTER_CARD),
            mapOf(
                "characterName" to characterName,
                "bookTitle" to book.title,
                "author" to (book.author ?: AiText.unknownAuthor(localeProvider.current())),
                "chapterLabel" to chapterLabel,
                "excerptBlock" to excerptBlock,
            ),
        )

        for (provider in providers) {
            if (provider.isDegraded) continue
            val result = aiService.simpleChat(provider, listOf(AiMessage("user", prompt)))
            result.onSuccess { text ->
                fallbackChain.recordSuccess(provider.id)
                parse(text, characterName)?.let { return it }
            }.onFailure {
                fallbackChain.recordFailure(provider.id)
            }
        }
        return null
    }

    /** 解析模型输出：截取首个 { 到末个 }，容忍 markdown 包裹与前后废话 */
    private fun parse(text: String, characterName: String): CharacterCard? = runCatching {
        val start = text.indexOf('{')
        val end = text.lastIndexOf('}')
        if (start < 0 || end <= start) return null
        val o = JSONObject(text.substring(start, end + 1))
        CharacterCard(
            name = characterName,
            identity = o.optString("identity"),
            personality = o.optString("personality"),
            tone = o.optString("tone"),
            knowledgeBoundary = o.optString("knowledgeBoundary"),
        )
    }.getOrNull()

    /** TXT：读取已读部分末尾 6000 字作为抽取素材；其余格式返回 null */
    private suspend fun loadSourceExcerpt(book: Book, chapterIndex: Int): String? {
        if (book.format != "TXT") return null
        return runCatching {
            val controller = TxtReaderController()
            controller.open(book.filePath)
            val chapters = controller.chapters
            if (chapters.isEmpty() || controller.fullText.isEmpty()) return null
            val safeIdx = chapterIndex.coerceIn(0, chapters.lastIndex)
            val readUpTo = chapters[safeIdx].endOffset.coerceIn(0, controller.fullText.length)
            controller.fullText.take(readUpTo).takeLast(6000)
        }.getOrNull()
    }
}
