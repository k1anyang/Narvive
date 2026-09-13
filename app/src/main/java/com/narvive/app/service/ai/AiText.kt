package com.narvive.app.service.ai

import com.narvive.app.core.AppLang

/**
 * AI 相关的**非模板**文案（问候语、建议卡、注入给模型的本地数据说明、意图路由关键词）。
 *
 * 与 [PromptDefaultsI18n] 的分工：
 * - [PromptDefaultsI18n]：用户在 PROMPT 设置里可见/可编辑的 12 套模板
 * - 本对象：App 自己拼装的文案，用户看不到也改不了
 *
 * 为什么不用 `strings.xml`：这些文案在 **ViewModel/Service** 层拼装，不在 Composable 作用域；
 * 且它们多数是「发给模型」的上下文说明而非屏幕文案。用代码内表比资源文件更直接，
 * 也便于把「意图路由关键词」与「问候语池」这类结构化的东西放在一起维护。
 *
 * ⚠️ 改动 [INTENT_STATS] / [INTENT_RECOMMEND] / [INTENT_PROGRESS] 时务必**保留中文分支**：
 * 意图路由是中英并集匹配，删掉中文会让中文用户的功能静默失效；
 * 同时必须保留 [RegexOption.IGNORE_CASE]，否则英文建议卡自己都命中不了。
 */
object AiText {

    // ── L1：系统提示词（决定 AI 用什么语言回答） ──

    /**
     * 全局 AI 主页的系统提示词。
     *
     * 这是「AI 是否跟随界面语言」的关键开关：原文硬编码「请用中文回答」，
     * 导致界面切成英文后 AI 仍用中文回复。
     */
    fun globalSystemPrompt(lang: AppLang): String = when (lang) {
        AppLang.ZH_HANS -> "你是一位乐于助人的 AI 助手，请用中文简洁、准确地回答。"
        AppLang.ZH_HANT -> "你是一位樂於助人的 AI 助手，請用繁體中文簡潔、準確地回答。"
        AppLang.EN -> "You are a helpful AI assistant. Answer concisely and accurately in English."
    }

    // ── L2：问候语与建议卡 ──

    fun greetingPool(lang: AppLang, hour: Int): List<String> {
        val time = when (lang) {
            AppLang.ZH_HANS -> when {
                hour in 5..10 -> listOf("早上好", "早安", "新的一天好")
                hour in 11..13 -> listOf("中午好", "午安")
                hour in 14..17 -> listOf("下午好", "午后好")
                hour in 18..23 -> listOf("晚上好", "夜深了")
                else -> listOf("夜深了", "还没睡呀")
            }
            AppLang.ZH_HANT -> when {
                hour in 5..10 -> listOf("早上好", "早安", "新的一天好")
                hour in 11..13 -> listOf("中午好", "午安")
                hour in 14..17 -> listOf("下午好", "午後好")
                hour in 18..23 -> listOf("晚上好", "夜深了")
                else -> listOf("夜深了", "還沒睡呀")
            }
            AppLang.EN -> when {
                hour in 5..10 -> listOf("Good morning", "Morning!", "A fresh new day")
                hour in 11..13 -> listOf("Good afternoon", "Hello there")
                hour in 14..17 -> listOf("Good afternoon", "Hope your day is going well")
                hour in 18..23 -> listOf("Good evening", "It's getting late")
                else -> listOf("It's late", "Still awake?")
            }
        }
        val extra = when (lang) {
            AppLang.ZH_HANS -> listOf("好久不见，欢迎回来", "嗨，又见面了", "今天想读点什么？")
            AppLang.ZH_HANT -> listOf("好久不見，歡迎回來", "嗨，又見面了", "今天想讀點什麼？")
            AppLang.EN -> listOf("Long time no see, welcome back", "Hey, you again", "What are we reading today?")
        }
        return time + extra
    }

    fun suggestContinue(lang: AppLang, title: String): String = when (lang) {
        AppLang.ZH_HANS -> "接着读《$title》？"
        AppLang.ZH_HANT -> "接著讀《$title》？"
        AppLang.EN -> "Continue reading “$title”?"
    }

    fun suggestReport(lang: AppLang): String = when (lang) {
        AppLang.ZH_HANS -> "帮你总结阅读日报"
        AppLang.ZH_HANT -> "幫你總結閱讀日報"
        AppLang.EN -> "Summarise my reading report"
    }

    fun suggestRecommend(lang: AppLang): String = when (lang) {
        AppLang.ZH_HANS -> "给你推荐几本书"
        AppLang.ZH_HANT -> "給你推薦幾本書"
        AppLang.EN -> "Recommend some books"
    }

    fun suggestSummarize(lang: AppLang, title: String): String = when (lang) {
        AppLang.ZH_HANS -> "总结《$title》"
        AppLang.ZH_HANT -> "總結《$title》"
        AppLang.EN -> "Summarise “$title”"
    }

    // ── L3：意图路由关键词（中英并集，大小写不敏感） ──

    /**
     * 三条意图正则。
     *
     * 两个必须坚持的约束：
     * 1. **中英并集**：中文界面下用户也可能打 `stats`，英文界面下也可能打「推荐」，
     *    所以不再按界面语言分叉，统一用同一份并集。
     * 2. **[RegexOption.IGNORE_CASE]**：旧实现是大小写敏感的，于是 App 自己的英文建议卡
     *    「Recommend what I should read」永远命中不了 `recommend` 规则，
     *    快路径静默失效，还会掉进下面的模糊书名匹配。
     */
    private val INTENT_OPTIONS = setOf(RegexOption.IGNORE_CASE)

    /** 统计 / 日报 / 周报 */
    val INTENT_STATS: Regex = Regex(
        "统计|統計|日报|日報|周报|週報|读了多少|讀了多少|阅读报告|閱讀報告|阅读时长|閱讀時長|读了多久|讀了多久|" +
            "statistics|stats|report|how much (have i|did i) read|reading time|reading report",
        INTENT_OPTIONS,
    )

    /** 推荐 */
    val INTENT_RECOMMEND: Regex = Regex(
        "推荐|推薦|有什么书|有什麼書|读什么书|讀什麼書|挑几本|挑幾本|选几本|選幾本|" +
            "recommend|suggest (me )?(some )?books|what should i read|which book",
        INTENT_OPTIONS,
    )

    /** 进度 / 接着读 */
    val INTENT_PROGRESS: Regex = Regex(
        "读到哪|讀到哪|接着读|接著讀|继续读|繼續讀|上次读到|上次讀到|读到哪里|讀到哪裡|进度|進度|" +
            "where did i (stop|leave off)|continue reading|resume reading|my progress|reading progress",
        INTENT_OPTIONS,
    )

    /**
     * 覆盖型意图：需要「读完整章」而不是「读最相关的几块」。
     *
     * 阅读器面板用它决定走覆盖式压缩还是精读检索——「总结本章」与
     * 「主角最后怎么样了」对上下文的需求是相反的。
     */
    val INTENT_COVERAGE: Regex = Regex(
        "总结|總結|概括|概要|摘要|讲了什么|講了什麼|说了什么|說了什麼|梳理|梗概|大意|全书脉络|" +
            "summar|overview|outline|what happened|recap|synopsis|tl;?dr",
        INTENT_OPTIONS,
    )

    // ── 注入给模型的本地数据说明 ──

    fun statsContext(lang: AppLang, minutes: Int, booksRead: Int, finished: Int, recentTitles: List<String>): String =
        buildString {
            when (lang) {
                AppLang.ZH_HANS -> {
                    append("以下是用户的阅读数据（本地统计，请据此回答）：\n")
                    append("- 本周（近 7 天）阅读总时长：约 $minutes 分钟\n")
                    append("- 本周读过 $booksRead 本书\n")
                    append("- 累计读完 $finished 本\n")
                    if (recentTitles.isNotEmpty()) append("- 最近在读：《${recentTitles.joinToString("》《")}》")
                }
                AppLang.ZH_HANT -> {
                    append("以下是使用者的閱讀資料（本機統計，請據此回答）：\n")
                    append("- 本週（近 7 天）閱讀總時長：約 $minutes 分鐘\n")
                    append("- 本週讀過 $booksRead 本書\n")
                    append("- 累計讀完 $finished 本\n")
                    if (recentTitles.isNotEmpty()) append("- 最近在讀：《${recentTitles.joinToString("》《")}》")
                }
                AppLang.EN -> {
                    append("Here is the user's reading data (local statistics; answer based on this):\n")
                    append("- Total reading time this week (last 7 days): about $minutes minutes\n")
                    append("- Books read this week: $booksRead\n")
                    append("- Books finished in total: $finished\n")
                    if (recentTitles.isNotEmpty()) append("- Currently reading: ${recentTitles.joinToString(", ")}")
                }
            }
        }

    fun recommendContextHeader(lang: AppLang): String = when (lang) {
        AppLang.ZH_HANS -> "用户想获得阅读推荐，以下是其书库（仅元数据）："
        AppLang.ZH_HANT -> "使用者想獲得閱讀推薦，以下是其書庫（僅中繼資料）："
        AppLang.EN -> "The user wants a reading recommendation. Here is their library (metadata only):"
    }

    fun userPreferenceLabel(lang: AppLang): String = when (lang) {
        AppLang.ZH_HANS -> "用户偏好："
        AppLang.ZH_HANT -> "使用者偏好："
        AppLang.EN -> "User preferences: "
    }

    fun allBooksHeader(lang: AppLang): String = when (lang) {
        AppLang.ZH_HANS -> "用户的全部书籍（仅元数据，无正文）："
        AppLang.ZH_HANT -> "使用者的全部書籍（僅中繼資料，無正文）："
        AppLang.EN -> "All of the user's books (metadata only, no body text):"
    }

    fun contextBooksHeader(lang: AppLang): String = when (lang) {
        AppLang.ZH_HANS -> "上下文书籍（仅元数据，无正文）："
        AppLang.ZH_HANT -> "上下文書籍（僅中繼資料，無正文）："
        AppLang.EN -> "Books in context (metadata only, no body text):"
    }

    fun moreBooksLabel(lang: AppLang, count: Int): String = when (lang) {
        AppLang.ZH_HANS -> "另有 $count 本"
        AppLang.ZH_HANT -> "另有 $count 本"
        AppLang.EN -> "Plus $count more"
    }

    /** 上下文书籍的条目（带项目符号，用于 @ 选书场景） */
    fun bookBullet(lang: AppLang, title: String, author: String, progressPercent: Int): String = when (lang) {
        AppLang.ZH_HANS -> "·《$title》 作者：$author，进度 $progressPercent%"
        AppLang.ZH_HANT -> "·《$title》 作者：$author，進度 $progressPercent%"
        AppLang.EN -> "· “$title” by $author, progress $progressPercent%"
    }

    fun bookLine(lang: AppLang, index: Int, title: String, author: String, progressPercent: Int, finished: Boolean): String {
        val done = when (lang) {
            AppLang.ZH_HANS -> if (finished) "（已读完）" else ""
            AppLang.ZH_HANT -> if (finished) "（已讀完）" else ""
            AppLang.EN -> if (finished) " (finished)" else ""
        }
        return when (lang) {
            AppLang.ZH_HANS -> "$index.《$title》 作者：$author，进度 $progressPercent%$done"
            AppLang.ZH_HANT -> "$index.《$title》 作者：$author，進度 $progressPercent%$done"
            AppLang.EN -> "$index. “$title” by $author, progress $progressPercent%$done"
        }
    }

    fun bookContextHeader(lang: AppLang, title: String): String = when (lang) {
        AppLang.ZH_HANS -> "用户正在询问关于书籍《$title》的问题（仅元数据，无正文）："
        AppLang.ZH_HANT -> "使用者正在詢問關於書籍《$title》的問題（僅中繼資料，無正文）："
        AppLang.EN -> "The user is asking about the book “$title” (metadata only, no body text):"
    }

    fun authorLabel(lang: AppLang, author: String): String = when (lang) {
        AppLang.ZH_HANS -> "作者：$author"
        AppLang.ZH_HANT -> "作者：$author"
        AppLang.EN -> "Author: $author"
    }

    fun progressLabel(lang: AppLang, percent: Int): String = when (lang) {
        AppLang.ZH_HANS -> "，进度 $percent%"
        AppLang.ZH_HANT -> "，進度 $percent%"
        AppLang.EN -> ", progress $percent%"
    }

    fun currentChapterLabel(lang: AppLang, chapter: String): String = when (lang) {
        AppLang.ZH_HANS -> "，当前读到：$chapter"
        AppLang.ZH_HANT -> "，當前讀到：$chapter"
        AppLang.EN -> ", currently at: $chapter"
    }

    fun descriptionLabel(lang: AppLang, description: String): String = when (lang) {
        AppLang.ZH_HANS -> "，简介：$description"
        AppLang.ZH_HANT -> "，簡介：$description"
        AppLang.EN -> ", summary: $description"
    }

    /** 未提供作者时的占位（会进入发给模型的上下文，也用于界面兜底） */
    fun unknownAuthor(lang: AppLang): String = when (lang) {
        AppLang.ZH_HANS -> "未知"
        AppLang.ZH_HANT -> "未知"
        AppLang.EN -> "Unknown"
    }

    // ── 会话标题自动生成 ──

    /**
     * 自动生成会话标题的提示词。
     *
     * [maxChars] 由调用方决定：中文按 12 字，英文按词数折算（约 6 个词）。
     */
    fun titlePrompt(lang: AppLang, userText: String, assistantText: String, maxChars: Int): String = when (lang) {
        AppLang.ZH_HANS ->
            "把下面的对话概括成 $maxChars 字以内的标题，只返回标题，不要引号、不要解释：\n用户：$userText" +
                (if (assistantText.isNotBlank()) "\n助手：$assistantText" else "")
        AppLang.ZH_HANT ->
            "把下面的對話概括成 $maxChars 字以內的標題，只回傳標題，不要引號、不要解釋：\n使用者：$userText" +
                (if (assistantText.isNotBlank()) "\n助手：$assistantText" else "")
        AppLang.EN ->
            "Summarise the following conversation as a title of at most $maxChars words. Return only the title, without quotes or explanation:\nUser: $userText" +
                (if (assistantText.isNotBlank()) "\nAssistant: $assistantText" else "")
    }

    /** 自动标题的截断长度：中文按字数，英文按词数。 */
    fun titleLimit(lang: AppLang): Int = if (lang == AppLang.EN) 40 else 12

    // ── 书籍内「问 AI」的上下文拼装（ChatViewModel） ──

    fun selectedTextHeader(lang: AppLang): String = when (lang) {
        AppLang.ZH_HANS -> "\n\n── 用户选中的文本 ──\n\"\"\"\n"
        AppLang.ZH_HANT -> "\n\n── 使用者選取的文字 ──\n\"\"\"\n"
        AppLang.EN -> "\n\nThe text the user selected:\n\"\"\"\n"
    }

    fun currentChapterLabelShort(lang: AppLang, chapter: String): String = when (lang) {
        AppLang.ZH_HANS -> "\n当前章节：$chapter"
        AppLang.ZH_HANT -> "\n當前章節：$chapter"
        AppLang.EN -> "\nCurrent chapter: $chapter"
    }

    fun chapterContentHeader(lang: AppLang): String = when (lang) {
        AppLang.ZH_HANS -> "\n\n── 当前章节内容 ──\n\"\"\"\n"
        AppLang.ZH_HANT -> "\n\n── 當前章節內容 ──\n\"\"\"\n"
        AppLang.EN -> "\n\nContent of the current chapter:\n\"\"\"\n"
    }

    fun bookMetaHeader(lang: AppLang): String = when (lang) {
        AppLang.ZH_HANS -> "\n\n本书信息（仅元数据，不发正文）："
        AppLang.ZH_HANT -> "\n\n本書資訊（僅中繼資料，不傳正文）："
        AppLang.EN -> "\n\nBook information (metadata only, no body text):"
    }

    fun bookTitleAuthorLine(lang: AppLang, title: String, author: String): String = when (lang) {
        AppLang.ZH_HANS -> "\n书名：《$title》，作者：$author"
        AppLang.ZH_HANT -> "\n書名：《$title》，作者：$author"
        AppLang.EN -> "\nTitle: “$title”, author: $author"
    }

    fun tocHeader(lang: AppLang): String = when (lang) {
        AppLang.ZH_HANS -> "\n目录："
        AppLang.ZH_HANT -> "\n目錄："
        AppLang.EN -> "\nTable of contents:"
    }

    fun summaryLabel(lang: AppLang, text: String): String = when (lang) {
        AppLang.ZH_HANS -> "\n简介：$text"
        AppLang.ZH_HANT -> "\n簡介：$text"
        AppLang.EN -> "\nSummary: $text"
    }

    fun chunkOverviewHeader(lang: AppLang, charCount: Int, itemCount: Int, grouped: Boolean): String = when (lang) {
        AppLang.ZH_HANS ->
            if (grouped) "本章共 $charCount 字，已分 $itemCount 组（每组含若干小节）。各组概览：\n"
            else "本章共 $charCount 字，已分 $itemCount 块。各块概览：\n"
        AppLang.ZH_HANT ->
            if (grouped) "本章共 $charCount 字，已分 $itemCount 組（每組含若干小節）。各組概覽：\n"
            else "本章共 $charCount 字，已分 $itemCount 塊。各塊概覽：\n"
        AppLang.EN ->
            if (grouped) "This chapter has $charCount characters, split into $itemCount groups (each group holds several sections). Overview:\n"
            else "This chapter has $charCount characters, split into $itemCount chunks. Overview:\n"
    }

    /** 模型判定「仅凭概览即可回答」时的说明。**只在真的如此时使用**，不能用来表示检索失败。 */
    fun vagueQuestionNote(lang: AppLang): String = when (lang) {
        AppLang.ZH_HANS -> "\n\n（用户问题较泛，请仅基于以上概览回答，不要编造细节。）"
        AppLang.ZH_HANT -> "\n\n（使用者問題較廣泛，請僅依以上概覽回答，不要編造細節。）"
        AppLang.EN -> "\n\n(The user's question is broad; answer only from the overview above and do not invent details.)"
    }

    /**
     * 检索降级说明：模型选块失败后改用本地词法兜底。
     *
     * 与 [vagueQuestionNote] 严格区分——把网络故障说成「问题较泛」，
     * 等于把系统故障转写成模型幻觉的许可证。
     */
    fun retrievalDegradedNote(lang: AppLang): String = when (lang) {
        AppLang.ZH_HANS ->
            "\n\n（说明：本轮未能完成智能选段，以下片段由本地关键词匹配得到，可能不完整；若信息不足请如实告知用户。）"
        AppLang.ZH_HANT ->
            "\n\n（說明：本輪未能完成智慧選段，以下片段由本機關鍵字比對取得，可能不完整；若資訊不足請如實告知使用者。）"
        AppLang.EN ->
            "\n\n(Note: chunk selection could not be completed this round; the passages below come from local keyword matching and may be incomplete. If the information is insufficient, tell the user honestly.)"
    }

    /** 覆盖式压缩正文的说明头（总结 / 关系图 / 时间线）；正文本身由 chapterContentHeader 的引号包裹 */
    fun coverageHeader(lang: AppLang): String = when (lang) {
        AppLang.ZH_HANS ->
            "\n\n── 本章精简版全文（保留全部情节主干，句子有删减，省略处以「……」标记）──\n"
        AppLang.ZH_HANT ->
            "\n\n── 本章精簡版全文（保留全部情節主幹，句子有刪減，省略處以「……」標記）──\n"
        AppLang.EN ->
            "\n\n── Condensed full text of this chapter (all plot threads kept, some sentences omitted, gaps marked with \"...\") ──\n"
    }

    /** 概览线索标签：出场人物 / 时间 */
    fun cueNamesLabel(lang: AppLang): String = when (lang) {
        AppLang.ZH_HANS -> "人物"
        AppLang.ZH_HANT -> "人物"
        AppLang.EN -> "characters"
    }

    fun cueTimeLabel(lang: AppLang): String = when (lang) {
        AppLang.ZH_HANS -> "含时间线索"
        AppLang.ZH_HANT -> "含時間線索"
        AppLang.EN -> "time cues"
    }

    fun relevantChunksHeader(lang: AppLang): String = when (lang) {
        AppLang.ZH_HANS -> "\n\n以下为与问题最相关的块全文："
        AppLang.ZH_HANT -> "\n\n以下為與問題最相關的區塊全文："
        AppLang.EN -> "\n\nFull text of the chunks most relevant to the question:"
    }

    fun chunkLabel(lang: AppLang, index: Int): String = when (lang) {
        AppLang.ZH_HANS -> "\n\n[块 $index]\n"
        AppLang.ZH_HANT -> "\n\n[區塊 $index]\n"
        AppLang.EN -> "\n\n[Chunk $index]\n"
    }

    // ── 全书范围的章节摘要（增量缓存 + 两阶段检索） ──

    /** 生成／回填单章摘要的提示词：要求保覆盖而非提炼观点 */
    fun chapterSummaryPrompt(lang: AppLang, chapterTitle: String, text: String): String = when (lang) {
        AppLang.ZH_HANS ->
            "请用 200 字以内总结下面这一章的主要情节、关键事件与人物动向。\n" +
                "要求：按事件发生的先后顺序写，不要评论、不要剧透后文；只输出摘要正文。\n" +
                (if (chapterTitle.isNotBlank()) "章节：$chapterTitle\n" else "") +
                "\"\"\"\n$text\n\"\"\""
        AppLang.ZH_HANT ->
            "請用 200 字以內總結下面這一章的主要情節、關鍵事件與人物動向。\n" +
                "要求：按事件發生的先後順序寫，不要評論、不要劇透後文；只輸出摘要正文。\n" +
                (if (chapterTitle.isNotBlank()) "章節：$chapterTitle\n" else "") +
                "\"\"\"\n$text\n\"\"\""
        AppLang.EN ->
            "Summarise this chapter in at most 120 words: the main plot, key events and what the characters do.\n" +
                "Write in chronological order, do not comment, do not spoil later chapters; output the summary only.\n" +
                (if (chapterTitle.isNotBlank()) "Chapter: $chapterTitle\n" else "") +
                "\"\"\"\n$text\n\"\"\""
    }

    /**
     * 全书范围注入的章节摘要块头。
     *
     * [total] 是已缓存摘要的章数，[shown] 是本轮选中的章数——必须如实写明，
     * 否则模型会误以为这就是全书内容。
     */
    fun bookSummaryHeader(lang: AppLang, shown: Int, total: Int): String = when (lang) {
        AppLang.ZH_HANS -> "\n\n── 已读章节摘要（本地缓存 $total 章，以下为与问题最相关的 $shown 章）──\n"
        AppLang.ZH_HANT -> "\n\n── 已讀章節摘要（本機快取 $total 章，以下為與問題最相關的 $shown 章）──\n"
        AppLang.EN -> "\n\n── Summaries of chapters read so far ($total cached locally; the $shown most relevant below) ──\n"
    }

    fun bookSummaryOverviewHeader(lang: AppLang, total: Int): String = when (lang) {
        AppLang.ZH_HANS -> "\n\n已读章节摘要一览（共 $total 章）：\n"
        AppLang.ZH_HANT -> "\n\n已讀章節摘要一覽（共 $total 章）：\n"
        AppLang.EN -> "\n\nOverview of cached chapter summaries ($total chapters):\n"
    }

    /**
     * 全书范围且本地还没有任何章节摘要时的说明。
     *
     * 此时模型只有书名/作者/目录，明确说明可以避免它假装读过正文。
     */
    fun bookSummaryNoneNote(lang: AppLang): String = when (lang) {
        AppLang.ZH_HANS ->
            "\n\n（本地尚无章节摘要：用户还没有在本章范围内向 AI 提问过。请仅依据以上书目信息与目录回答，" +
                "结合你对本书的了解时须说明这是背景知识，不要编造具体情节。）"
        AppLang.ZH_HANT ->
            "\n\n（本機尚無章節摘要：使用者還沒有在本章範圍內向 AI 提問過。請僅依據以上書目資訊與目錄回答，" +
                "結合你對本書的了解時須說明這是背景知識，不要編造具體情節。）"
        AppLang.EN ->
            "\n\n(No chapter summaries cached yet: the user has not asked the AI anything in chapter scope. " +
                "Answer from the bibliographic metadata and table of contents only; if you draw on your own knowledge of the book, " +
                "say so, and never invent plot details.)"
    }

    /**
     * 选块提示词。
     *
     * 输出格式是**硬契约**：调用方只接受「纯编号列表」，任何解释性文字都会被判为不可解析
     * 并转入本地兜底。因此这里必须明确禁止解释，否则等于每次都在浪费一次调用。
     */
    fun chunkSelectPrompt(lang: AppLang, question: String, overview: String): String = when (lang) {
        AppLang.ZH_HANS ->
            "下面是一本书某一章的概览。请判断回答下面的问题时，需要阅读哪些条目的全文。\n" +
                "问题：$question\n\n$overview\n\n" +
                "只输出编号，用英文逗号分隔（例如：2,5）；若仅凭概览即可回答，只输出 0。\n" +
                "不要输出解释、标题或标点以外的任何文字。最多选择 3 个。"
        AppLang.ZH_HANT ->
            "下面是一本書某一章的概覽。請判斷回答下面的問題時，需要閱讀哪些條目的全文。\n" +
                "問題：$question\n\n$overview\n\n" +
                "只輸出編號，用英文逗號分隔（例如：2,5）；若僅憑概覽即可回答，只輸出 0。\n" +
                "不要輸出解釋、標題或標點以外的任何文字。最多選擇 3 個。"
        AppLang.EN ->
            "Below is an overview of one chapter of a book. Decide which items must be read in full to answer the question.\n" +
                "Question: $question\n\n$overview\n\n" +
                "Return only the numbers, comma-separated (e.g. 2,5); if the overview alone is enough, return only 0.\n" +
                "Do not output any explanation, heading or other text. Pick at most 3."
    }

    /** 图表请求：全书范围无正文时的兜底说明 */
    fun graphBookScopeFallback(lang: AppLang, title: String): String = when (lang) {
        AppLang.ZH_HANS ->
            "（全书范围：本地无正文）请结合你对《$title》的了解与公开信息生成，信息不足可合理补充，但不要编造；不确定的宁可不列。"
        AppLang.ZH_HANT ->
            "（全書範圍：本機無正文）請結合你對《$title》的了解與公開資訊生成，資訊不足可合理補充，但不要編造；不確定的寧可不列。"
        AppLang.EN ->
            "(Whole-book scope: no body text available locally) Generate from your knowledge of “$title” and public information. You may fill gaps reasonably, but never fabricate; omit anything you are unsure about."
    }

    /** 图表请求：章节正文取不到时的兜底说明 */
    fun graphNoChapterFallback(lang: AppLang): String = when (lang) {
        AppLang.ZH_HANS ->
            "（本地未取到章节正文）请结合你对本书的了解与公开信息生成，信息不足可合理补充，但不要编造。"
        AppLang.ZH_HANT ->
            "（本機未取得章節正文）請結合你對本書的了解與公開資訊生成，資訊不足可合理補充，但不要編造。"
        AppLang.EN ->
            "(No chapter body text available locally) Generate from your knowledge of the book and public information. You may fill gaps reasonably, but never fabricate."
    }

    /** 图表 scope 变量值（会进入提示词，需与界面语言一致） */
    fun scopeWholeBook(lang: AppLang): String = when (lang) {
        AppLang.ZH_HANS -> "全书"
        AppLang.ZH_HANT -> "全書"
        AppLang.EN -> "whole book"
    }

    fun scopeThisChapter(lang: AppLang): String = when (lang) {
        AppLang.ZH_HANS -> "本章"
        AppLang.ZH_HANT -> "本章"
        AppLang.EN -> "this chapter"
    }
}
