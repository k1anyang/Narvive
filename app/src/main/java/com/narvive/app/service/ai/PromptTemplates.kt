package com.narvive.app.service.ai

import androidx.annotation.StringRes
import com.narvive.app.R
import com.narvive.app.data.datastore.NarviveDataStore
import kotlinx.coroutines.flow.first
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

/** 全部可由用户配置的 AI 提示词模板 */
object PromptTemplates {
    const val CHAT = "chat"
    const val ROLEPLAY = "roleplay"
    const val REWRITE = "rewrite"
    const val CONTINUE = "continue"
    const val TRANSLATE = "translate"
    const val CHARACTER_CARD = "character_card"
    const val QUICK_EXPLAIN = "quick_explain"
    const val QUICK_TRANSLATE = "quick_translate"
    const val QUICK_SUMMARIZE = "quick_summarize"
    const val QUICK_VOCAB = "quick_vocab"
    const val RELATIONSHIP_GRAPH = "relationship_graph"
    const val TIMELINE = "timeline"

    /**
     * 模板元信息。
     *
     * 标题、描述是**给用户在 PROMPT 设置页看的标签**，用字符串资源 id 而非硬编码文案；
     * 模板正文默认值不在这里——正文是「发给模型的内容」，见 [PromptDefaultsI18n]。
     */
    data class Template(
        val id: String,
        @StringRes val titleRes: Int,
        @StringRes val descriptionRes: Int,
        val defaultText: String,
        /**
         * 该模板可用的占位符（语言无关的 `{{...}}` 记号，直接展示）。
         * 空串表示无占位符，由 UI 显示 `prompt_tpl_no_placeholders`。
         */
        val placeholders: String,
    )

    val all = listOf(
        Template(
            id = CHAT,
            titleRes = R.string.prompt_tpl_chat_title,
            descriptionRes = R.string.prompt_tpl_chat_desc,
            placeholders = "{{bookTitle}} {{author}}",
            defaultText = """
                你是一位博学的阅读助手。请用中文简洁回答。
                当前书籍：《{{bookTitle}}》
                作者：{{author}}
            """.trimIndent(),
        ),
        Template(
            id = ROLEPLAY,
            titleRes = R.string.prompt_tpl_roleplay_title,
            descriptionRes = R.string.prompt_tpl_roleplay_desc,
            placeholders = "{{bookTitle}} {{characterName}} {{identity}} {{personality}} {{tone}} {{knowledgeBoundary}}",
            defaultText = """
                你是《{{bookTitle}}》中的{{characterName}}。
                身份：{{identity}}
                性格：{{personality}}
                语气：{{tone}}
                你只知道截至{{knowledgeBoundary}}发生的事，不知道后续剧情。
                以第一人称回答，保持角色一致性。
                决不以"作为AI"开头，不剧透未发生剧情。
                若用户提问超出角色认知，以角色身份自然回应（如"我还不知道这件事"）。
                角色语气需与原文风格对齐。
            """.trimIndent(),
        ),
        Template(
            id = REWRITE,
            titleRes = R.string.prompt_tpl_rewrite_title,
            descriptionRes = R.string.prompt_tpl_rewrite_desc,
            placeholders = "{{originalText}} {{context}} {{instruction}}",
            defaultText = """
                你是一位文学编辑。根据以下指令改写文本段落。

                原文：
                ${"\"\"\""}
                {{originalText}}
                ${"\"\"\""}

                章节上下文（供参考）：
                ${"\"\"\""}
                {{context}}
                ${"\"\"\""}

                改写指令：{{instruction}}

                请只返回改写后的文本，不要加解释。
            """.trimIndent(),
        ),
        Template(
            id = CONTINUE,
            titleRes = R.string.prompt_tpl_continue_title,
            descriptionRes = R.string.prompt_tpl_continue_desc,
            placeholders = "{{originalText}} {{context}} {{instruction}}",
            defaultText = """
                你是一位作家。根据以下指令，为原文续写后续内容。

                原文结尾：
                ${"\"\"\""}
                {{originalText}}
                ${"\"\"\""}

                章节上文：
                ${"\"\"\""}
                {{context}}
                ${"\"\"\""}

                续写指令：{{instruction}}

                请只返回续写文本（从原文结尾处继续），不要加解释。
            """.trimIndent(),
        ),
        Template(
            id = TRANSLATE,
            titleRes = R.string.prompt_tpl_translate_title,
            descriptionRes = R.string.prompt_tpl_translate_desc,
            placeholders = "{{text}} {{contextBlock}}",
            defaultText = """
                将以下文本翻译为中文。如果已有上下文，参考上下文确保术语一致性。
                {{contextBlock}}
                待翻译文本：
                ${"\"\"\""}
                {{text}}
                ${"\"\"\""}

                只返回中文翻译，不加解释。
            """.trimIndent(),
        ),
        Template(
            id = CHARACTER_CARD,
            titleRes = R.string.prompt_tpl_character_card_title,
            descriptionRes = R.string.prompt_tpl_character_card_desc,
            placeholders = "{{characterName}} {{bookTitle}} {{author}} {{chapterLabel}} {{excerptBlock}}",
            defaultText = """
                你是文学分析助手。基于以下信息，为角色「{{characterName}}」生成角色卡，用于角色扮演对话。

                书籍：《{{bookTitle}}》
                作者：{{author}}
                用户已读至：{{chapterLabel}}
                {{excerptBlock}}

                要求：严格输出 JSON（不要 markdown 代码块，不要额外解释）：
                {"identity":"角色身份一句话","personality":"性格倾向，3-5个词逗号分隔","tone":"语气特征一句话","knowledgeBoundary":"该角色截至用户已读进度所知晓剧情的范围描述；不知晓后续剧情，不知晓自己是小说人物"}
                若摘录不足以判断，基于你对该书的了解生成；不了解该书则根据角色名与书名风格合理推断。
            """.trimIndent(),
        ),
        Template(
            id = QUICK_EXPLAIN,
            titleRes = R.string.prompt_tpl_quick_explain_title,
            descriptionRes = R.string.prompt_tpl_quick_explain_desc,
            placeholders = "",
            defaultText = "请解释选中内容的含义、背景与关键点。",
        ),
        Template(
            id = QUICK_TRANSLATE,
            titleRes = R.string.prompt_tpl_quick_translate_title,
            descriptionRes = R.string.prompt_tpl_quick_translate_desc,
            placeholders = "",
            defaultText = "请将选中内容翻译为中文，只返回译文，不加解释。",
        ),
        Template(
            id = QUICK_SUMMARIZE,
            titleRes = R.string.prompt_tpl_quick_summarize_title,
            descriptionRes = R.string.prompt_tpl_quick_summarize_desc,
            placeholders = "",
            defaultText = "请总结当前章节：一句话总结 + 3-5 个关键点 + 术语简释。",
        ),
        Template(
            id = QUICK_VOCAB,
            titleRes = R.string.prompt_tpl_quick_vocab_title,
            descriptionRes = R.string.prompt_tpl_quick_vocab_desc,
            placeholders = "",
            defaultText = "请列出选中内容中的生词与术语，逐一给出简明释义。",
        ),
        Template(
            id = RELATIONSHIP_GRAPH,
            titleRes = R.string.prompt_tpl_relationship_graph_title,
            descriptionRes = R.string.prompt_tpl_relationship_graph_desc,
            placeholders = "{{bookTitle}} {{author}} {{scope}} {{chapterTitle}} {{chapterText}}",
            defaultText = """
                你是一位文学分析助手。根据以下书籍内容，抽取其中的人物关系，用于绘制人物关系图。

                书籍：《{{bookTitle}}》
                作者：{{author}}
                范围：{{scope}}
                章节：{{chapterTitle}}
                参考内容：
                ${"\"\"\""}
                {{chapterText}}
                ${"\"\"\""}

                严格输出 JSON（不要 markdown 代码块，不要额外解释）：
                {"nodes":[{"name":"角色名","category":"主角/配角/反派等","description":"一句话说明"}],"edges":[{"source":"角色A","target":"角色B","relation":"父子/恋人/敌人/师徒等"}]}
                要求：
                - 关系字段 relation 必须填写，尽量具体（如「父子」「恋人」「师徒」「对手」），不要在 relation 里写括号补充。
                - 覆盖范围内明确出现或明确提及的角色与关系。
                - 若内容不足以完整刻画关系，可结合你对本书的了解与公开信息补充，但不要编造不存在的角色或关系；不确定时宁可不列。
            """.trimIndent(),
        ),
        Template(
            id = TIMELINE,
            titleRes = R.string.prompt_tpl_timeline_title,
            descriptionRes = R.string.prompt_tpl_timeline_desc,
            placeholders = "{{bookTitle}} {{author}} {{scope}} {{chapterTitle}} {{chapterText}}",
            defaultText = """
                你是一位文学分析助手。根据以下书籍内容，梳理时间线演进，用于绘制时间轴。

                书籍：《{{bookTitle}}》
                作者：{{author}}
                范围：{{scope}}
                章节：{{chapterTitle}}
                参考内容：
                ${"\"\"\""}
                {{chapterText}}
                ${"\"\"\""}

                严格输出 JSON（不要 markdown 代码块，不要额外解释）：
                {"stages":[{"label":"第X卷 / 第X章 / 某时间阶段","events":[{"time":"时间点或阶段","title":"事件标题","description":"一句话描述"}]}]}
                要求：
                - 按「章节 / 卷 / 时间阶段」把事件分组到 stages，label 写清阶段（如「第一卷·开端」「第3章」「十年前」）。
                - 每个 stage 内 events 按时间先后排序；无明确时间则按剧情推进归纳。
                - 若内容不足以完整梳理，可结合你对本书的了解与公开信息补充，但不要编造；不确定的事件宁可不列。
            """.trimIndent(),
        ),
    )

    fun defaultOf(id: String): String = all.firstOrNull { it.id == id }?.defaultText ?: ""
}

/** 模板占位符渲染 */
object PromptRenderer {
    fun render(template: String, vars: Map<String, String>): String {
        var out = template
        vars.forEach { (key, value) ->
            out = out.replace("{{$key}}", value)
        }
        return out
    }
}

/** 提示词的读写：用户自定义值存 DataStore（JSON map），缺省时回退当前语言的默认模板 */
@Singleton
class PromptService @Inject constructor(
    private val dataStore: NarviveDataStore,
    private val localeProvider: PromptLocaleProvider,
) {
    private val json = Json { ignoreUnknownKeys = true }

    /**
     * 取提示词正文。
     *
     * 优先级：**用户自定义值** > 当前语言的默认模板。
     * 语言切换不会覆盖用户已保存的自定义模板（这是本地化的硬约束：
     * 用户改过的提示词属于用户数据，不随界面语言改写）。
     */
    suspend fun get(id: String): String {
        val map = load()
        return map[id]?.takeIf { it.isNotBlank() }
            ?: PromptDefaultsI18n.defaultOf(id, localeProvider.current())
    }

    suspend fun getAll(): Map<String, String> {
        val stored = load()
        val lang = localeProvider.current()
        return PromptTemplates.all.associate { template ->
            template.id to (stored[template.id]?.takeIf { t -> t.isNotBlank() } ?: PromptDefaultsI18n.defaultOf(template.id, lang))
        }
    }

    suspend fun save(id: String, text: String) {
        val map = load().toMutableMap()
        map[id] = text
        dataStore.setPromptsJson(json.encodeToString(map))
    }

    suspend fun reset(id: String) {
        val map = load().toMutableMap()
        map.remove(id)
        dataStore.setPromptsJson(json.encodeToString(map))
    }

    private suspend fun load(): Map<String, String> {
        val raw = dataStore.promptsJson.first() ?: return emptyMap()
        return runCatching { json.decodeFromString<Map<String, String>>(raw) }.getOrDefault(emptyMap())
    }
}
