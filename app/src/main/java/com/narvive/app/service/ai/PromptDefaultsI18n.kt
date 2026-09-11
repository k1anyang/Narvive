package com.narvive.app.service.ai

import android.app.Application
import android.content.ComponentCallbacks
import android.content.Context
import android.content.res.Configuration
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.narvive.app.core.AppLang
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

/**
 * 判断当前应使用的提示词语言。
 *
 * 依据是**界面语言**（AppCompat per-app locale 会写入资源配置），
 * 因此用户切换界面语言后，新发起的 AI 请求自然改用对应语言的提示词。
 *
 * 注意：这只影响「默认模板」；用户在 PROMPT 设置里改过的模板永远优先，
 * 不因语言切换而被覆盖（见 [PromptService.get]）。
 */
@Singleton
class PromptLocaleProvider @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    fun current(): AppLang {
        val locale: Locale = context.resources.configuration.locales[0] ?: return AppLang.ZH_HANS
        return when (locale.language) {
            "zh" -> if (locale.script == "Hant") AppLang.ZH_HANT else AppLang.ZH_HANS
            "en" -> AppLang.EN
            else -> AppLang.ZH_HANS
        }
    }

    /**
     * 语言变化流：[current] 的可观察版本，语言变化时发射新值。
     *
     * **为什么需要它**：`UiMessage` 只保证「渲染时按当前语言解析」，但**不会让值重算**。
     * 凡是「需要根据语言重新计算内容」的状态（问候语要从语言池重新随机、建议卡要重建、
     * 章节标签要重取兜底文案），都必须订阅本流自行重算，否则会停留在旧语言。
     * 本项目不重建 Activity，ViewModel 存活，所以这是必需的一环。
     *
     * 实现：监听 `Application` 的配置变化（AppCompat / 系统改 per-app locale 都会回调它），
     * 并做 `distinctUntilChanged`，避免无意义的重复发射。
     */
    fun currentFlow(): Flow<AppLang> = callbackFlow {
        trySend(current())
        val app = context.applicationContext as? Application
        if (app == null) {
            // 极端情况下拿不到 Application（如测试环境）：退化为只发射当前值
            awaitClose { }
            return@callbackFlow
        }
        val callbacks = object : ComponentCallbacks {
            override fun onConfigurationChanged(newConfig: Configuration) {
                trySend(current())
            }

            override fun onLowMemory() = Unit
        }
        app.registerComponentCallbacks(callbacks)
        awaitClose { app.unregisterComponentCallbacks(callbacks) }
    }.distinctUntilChanged()
}

/**
 * 让 ViewModel 对**界面语言变化**做出反应，且**每个实例只注册一次**。
 *
 * 这是本项目「不重建 Activity」方案下必须配套的一环：
 * `UiMessage` 解决「渲染时解析」，本函数解决「值要重算」。
 *
 * 用法：放在 ViewModel 的 `init { }` 或 `fun init(...)` 里，
 * [onLanguageChanged] 里只做「重算 + 写回 `_uiState`」，不要做与语言无关的重活。
 *
 * 注意：首次订阅会立即收到当前语言一次，因此 [onLanguageChanged] 也会先被调用一次；
 * 对幂等的重算（重取 greeting / 标签 / 预览）来说这是无害的。
 */
fun ViewModel.observeLanguageChanges(
    localeProvider: PromptLocaleProvider,
    onLanguageChanged: suspend () -> Unit,
): Job = viewModelScope.launch {
    localeProvider.currentFlow().collect { onLanguageChanged() }
}

/**
 * 各语言下的默认提示词正文。
 *
 * 与 [PromptTemplates] 的分工：
 * - [PromptTemplates] 保留简体默认值（历史行为、向后兼容）
 * - 本表提供繁体与英文默认值；缺失的 id 回退到 [PromptTemplates.defaultOf]
 *
 * **JSON 输出的模板（角色卡/关系图/时间轴）必须保留键名与结构不变**，
 * 因为解析器按固定的英文键读取；只翻译说明性文字与取值示例。
 */
object PromptDefaultsI18n {

    /** 英文默认模板 */
    private val en: Map<String, String> = mapOf(
        PromptTemplates.CHAT to """
            You are a knowledgeable reading assistant. Answer concisely in English.
            Current book: 《{{bookTitle}}》
            Author: {{author}}
        """.trimIndent(),

        PromptTemplates.ROLEPLAY to """
            You are {{characterName}} from 《{{bookTitle}}》.
            Identity: {{identity}}
            Personality: {{personality}}
            Tone: {{tone}}
            You only know what has happened up to {{knowledgeBoundary}} and nothing beyond it.
            Reply in the first person and stay in character.
            Never begin with "As an AI", and never reveal plot developments that have not happened yet.
            If the user asks about something beyond your knowledge, respond naturally in character (e.g. "I don't know about that yet").
            Match the character's voice to the style of the original text.
        """.trimIndent(),

        PromptTemplates.REWRITE to """
            You are a literary editor. Rewrite the following passage according to the instruction.

            Original:
            ${"\"\"\""}
            {{originalText}}
            ${"\"\"\""}

            Chapter context (for reference):
            ${"\"\"\""}
            {{context}}
            ${"\"\"\""}

            Rewrite instruction: {{instruction}}

            Return only the rewritten text, with no explanation.
        """.trimIndent(),

        PromptTemplates.CONTINUE to """
            You are a writer. Continue the original passage according to the instruction.

            End of the original:
            ${"\"\"\""}
            {{originalText}}
            ${"\"\"\""}

            Earlier chapter text:
            ${"\"\"\""}
            {{context}}
            ${"\"\"\""}

            Continuation instruction: {{instruction}}

            Return only the continuation (continuing from where the original ends), with no explanation.
        """.trimIndent(),

        PromptTemplates.TRANSLATE to """
            Translate the following text into English. If context is provided, use it to keep terminology consistent.
            {{contextBlock}}
            Text to translate:
            ${"\"\"\""}
            {{text}}
            ${"\"\"\""}

            Return only the English translation, with no explanation.
        """.trimIndent(),

        PromptTemplates.CHARACTER_CARD to """
            You are a literary analysis assistant. Build a character card for "{{characterName}}" from the information below, to be used in role-play conversation.

            Book: 《{{bookTitle}}》
            Author: {{author}}
            Reader has reached: {{chapterLabel}}
            {{excerptBlock}}

            Output strict JSON (no markdown code fence, no extra explanation):
            {"identity":"one sentence of identity","personality":"3-5 comma-separated traits","tone":"one sentence describing the speaking tone","knowledgeBoundary":"description of what this character knows as of the reader's progress; unaware of later plot, unaware of being a fictional character"}
            If the excerpt is insufficient, rely on your knowledge of the book; if you do not know the book, infer sensibly from the character and book names.
        """.trimIndent(),

        PromptTemplates.QUICK_EXPLAIN to "Explain the meaning, background and key points of the selected text.",
        PromptTemplates.QUICK_TRANSLATE to "Translate the selected text into English. Return only the translation, with no explanation.",
        PromptTemplates.QUICK_SUMMARIZE to "Summarise the current chapter: a one-sentence summary + 3-5 key points + brief glossary notes.",
        PromptTemplates.QUICK_VOCAB to "List the unfamiliar words and terms in the selected text, with a concise definition for each.",

        PromptTemplates.RELATIONSHIP_GRAPH to """
            You are a literary analysis assistant. Extract the relationships between characters from the book content below, to draw a relationship graph.

            Book: 《{{bookTitle}}》
            Author: {{author}}
            Scope: {{scope}}
            Chapter: {{chapterTitle}}
            Reference content:
            ${"\"\"\""}
            {{chapterText}}
            ${"\"\"\""}

            Output strict JSON (no markdown code fence, no extra explanation):
            {"nodes":[{"name":"character name","category":"protagonist/supporting/antagonist etc.","description":"one sentence"}],"edges":[{"source":"character A","target":"character B","relation":"parent-child/lovers/enemies/mentor-student etc."}]}
            Requirements:
            - The relation field is mandatory and should be specific (e.g. "parent-child", "lovers", "mentor-student", "rival"); do not put parenthetical notes inside relation.
            - Cover characters and relationships that clearly appear or are clearly mentioned within scope.
            - If the content is insufficient, you may supplement from your knowledge of the book and public information, but never invent characters or relationships; omit anything you are unsure about.
        """.trimIndent(),

        PromptTemplates.TIMELINE to """
            You are a literary analysis assistant. Organise the timeline from the book content below, to draw a timeline chart.

            Book: 《{{bookTitle}}》
            Author: {{author}}
            Scope: {{scope}}
            Chapter: {{chapterTitle}}
            Reference content:
            ${"\"\"\""}
            {{chapterText}}
            ${"\"\"\""}

            Output strict JSON (no markdown code fence, no extra explanation):
            {"stages":[{"label":"Volume X / Chapter X / some period","events":[{"time":"point or period","title":"event title","description":"one sentence"}]}]}
            Requirements:
            - Group events into stages by "chapter / volume / period" and make the label explicit (e.g. "Volume 1 · Beginning", "Chapter 3", "Ten years ago").
            - Within each stage, order events chronologically; if no explicit time is given, follow the plot order.
            - If the content is insufficient, you may supplement from your knowledge of the book and public information, but never invent events; omit anything you are unsure about.
        """.trimIndent(),
    )

    /** 繁体默认模板 */
    private val zhHant: Map<String, String> = mapOf(
        PromptTemplates.CHAT to """
            你是一位博學的閱讀助手。請用繁體中文簡潔回答。
            當前書籍：《{{bookTitle}}》
            作者：{{author}}
        """.trimIndent(),

        PromptTemplates.ROLEPLAY to """
            你是《{{bookTitle}}》中的{{characterName}}。
            身分：{{identity}}
            性格：{{personality}}
            語氣：{{tone}}
            你只知道截至{{knowledgeBoundary}}發生的事，不知道後續劇情。
            以第一人稱回答，保持角色一致性。
            絕不以「作為 AI」開頭，不劇透未發生的劇情。
            若使用者提問超出角色認知，以角色身分自然回應（如「我還不知道這件事」）。
            角色語氣需與原文風格對齊。
        """.trimIndent(),

        PromptTemplates.REWRITE to """
            你是一位文學編輯。根據以下指令改寫文字段落。

            原文：
            ${"\"\"\""}
            {{originalText}}
            ${"\"\"\""}

            章節上下文（供參考）：
            ${"\"\"\""}
            {{context}}
            ${"\"\"\""}

            改寫指令：{{instruction}}

            請只回傳改寫後的文字，不要加解釋。
        """.trimIndent(),

        PromptTemplates.CONTINUE to """
            你是一位作家。根據以下指令，為原文續寫後續內容。

            原文結尾：
            ${"\"\"\""}
            {{originalText}}
            ${"\"\"\""}

            章節上文：
            ${"\"\"\""}
            {{context}}
            ${"\"\"\""}

            續寫指令：{{instruction}}

            請只回傳續寫文字（從原文結尾處繼續），不要加解釋。
        """.trimIndent(),

        PromptTemplates.TRANSLATE to """
            將以下文字翻譯為繁體中文。如果有上下文，參考上下文確保術語一致。
            {{contextBlock}}
            待翻譯文字：
            ${"\"\"\""}
            {{text}}
            ${"\"\"\""}

            只回傳繁體中文翻譯，不加解釋。
        """.trimIndent(),

        PromptTemplates.CHARACTER_CARD to """
            你是文學分析助手。基於以下資訊，為角色「{{characterName}}」產生角色卡，用於角色扮演對話。

            書籍：《{{bookTitle}}》
            作者：{{author}}
            使用者已讀至：{{chapterLabel}}
            {{excerptBlock}}

            要求：嚴格輸出 JSON（不要 markdown 程式碼區塊，不要額外解釋）：
            {"identity":"角色身分一句話","personality":"性格傾向，3-5 個詞逗號分隔","tone":"語氣特徵一句話","knowledgeBoundary":"該角色截至使用者已讀進度所知曉劇情的範圍描述；不知曉後續劇情，不知曉自己是小說人物"}
            若摘錄不足以判斷，基於你對該書的了解產生；不了解該書則根據角色名與書名風格合理推斷。
        """.trimIndent(),

        PromptTemplates.QUICK_EXPLAIN to "請解釋選取內容的含義、背景與關鍵點。",
        PromptTemplates.QUICK_TRANSLATE to "請將選取內容翻譯為繁體中文，只回傳譯文，不加解釋。",
        PromptTemplates.QUICK_SUMMARIZE to "請總結當前章節：一句話總結 + 3-5 個關鍵點 + 術語簡釋。",
        PromptTemplates.QUICK_VOCAB to "請列出選取內容中的生詞與術語，逐一給出簡明釋義。",

        PromptTemplates.RELATIONSHIP_GRAPH to """
            你是一位文學分析助手。根據以下書籍內容，抽取其中的人物關係，用於繪製人物關係圖。

            書籍：《{{bookTitle}}》
            作者：{{author}}
            範圍：{{scope}}
            章節：{{chapterTitle}}
            參考內容：
            ${"\"\"\""}
            {{chapterText}}
            ${"\"\"\""}

            嚴格輸出 JSON（不要 markdown 程式碼區塊，不要額外解釋）：
            {"nodes":[{"name":"角色名","category":"主角/配角/反派等","description":"一句話說明"}],"edges":[{"source":"角色A","target":"角色B","relation":"父子/戀人/敵人/師徒等"}]}
            要求：
            - 關係欄位 relation 必須填寫，盡量具體（如「父子」「戀人」「師徒」「對手」），不要在 relation 裡寫括號補充。
            - 覆蓋範圍內明確出現或明確提及的角色與關係。
            - 若內容不足以完整刻畫關係，可結合你對本書的了解與公開資訊補充，但不要編造不存在的角色或關係；不確定時寧可不列。
        """.trimIndent(),

        PromptTemplates.TIMELINE to """
            你是一位文學分析助手。根據以下書籍內容，梳理時間線演進，用於繪製時間軸。

            書籍：《{{bookTitle}}》
            作者：{{author}}
            範圍：{{scope}}
            章節：{{chapterTitle}}
            參考內容：
            ${"\"\"\""}
            {{chapterText}}
            ${"\"\"\""}

            嚴格輸出 JSON（不要 markdown 程式碼區塊，不要額外解釋）：
            {"stages":[{"label":"第X卷 / 第X章 / 某時間階段","events":[{"time":"時間點或階段","title":"事件標題","description":"一句話描述"}]}]}
            要求：
            - 按「章節 / 卷 / 時間階段」將事件分組到 stages，label 寫清階段（如「第一卷·開端」「第3章」「十年前」）。
            - 每個 stage 內 events 按時間先後排序；無明確時間則按劇情推進歸納。
            - 若內容不足以完整梳理，可結合你對本書的了解與公開資訊補充，但不要編造；不確定的事件寧可不列。
        """.trimIndent(),
    )

    /** 取指定語言的默认模板；缺失時回退到 [PromptTemplates] 的簡體默认值。 */
    fun defaultOf(id: String, lang: AppLang): String = when (lang) {
        AppLang.EN -> en[id] ?: PromptTemplates.defaultOf(id)
        AppLang.ZH_HANT -> zhHant[id] ?: PromptTemplates.defaultOf(id)
        AppLang.ZH_HANS -> PromptTemplates.defaultOf(id)
    }
}
