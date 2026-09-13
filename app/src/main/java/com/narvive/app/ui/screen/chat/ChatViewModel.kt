package com.narvive.app.ui.screen.chat

import android.content.Context
import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.narvive.app.R
import com.narvive.app.core.AppLang
import com.narvive.app.domain.model.Annotation
import com.narvive.app.domain.model.AnnotationType
import com.narvive.app.domain.model.Book
import com.narvive.app.domain.repository.AiChatRepository
import com.narvive.app.domain.repository.AiConversationInfo
import com.narvive.app.domain.repository.AnnotationRepository
import com.narvive.app.domain.repository.BookshelfRepository
import com.narvive.app.domain.repository.CachedChapterSummary
import com.narvive.app.domain.repository.ReadingRepository
import com.narvive.app.service.ai.AiMessage
import com.narvive.app.service.ai.AiService
import com.narvive.app.service.ai.AiText
import com.narvive.app.service.ai.FallbackChain
import com.narvive.app.service.ai.PromptLocaleProvider
import com.narvive.app.service.ai.PromptRenderer
import com.narvive.app.service.ai.PromptService
import com.narvive.app.service.ai.PromptTemplates
import com.narvive.app.service.ai.ProviderConfig
import com.narvive.app.service.ai.RelationshipGraph
import com.narvive.app.service.ai.TimelineStage
import com.narvive.app.service.ai.parseRelationshipGraph
import com.narvive.app.service.ai.parseTimeline
import com.narvive.app.service.ai.retrieval.ChapterIndex
import com.narvive.app.service.ai.retrieval.ChapterRetriever
import com.narvive.app.service.ai.retrieval.BookRetrieval
import com.narvive.app.service.ai.retrieval.BookSummary
import com.narvive.app.service.ai.retrieval.ChunkPick
import com.narvive.app.service.ai.retrieval.ChunkSelectionParser
import com.narvive.app.service.ai.retrieval.CoveragePurpose
import com.narvive.app.service.ai.retrieval.CueLabels
import com.narvive.app.service.ai.retrieval.QaRetrieval
import com.narvive.app.service.ai.retrieval.RetrievalBudget
import com.narvive.app.service.ai.retrieval.RetrievalBudgetConfig
import com.narvive.app.service.ai.retrieval.RetrievalCache
import com.narvive.app.service.ai.retrieval.RetrievalMode
import com.narvive.app.service.ai.retrieval.TokenEstimator
import com.narvive.app.service.reader.TocLoader
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID
import javax.inject.Inject

/** AI 面板上下文范围（原型：选区 / 本章 / 全书 三 chip 可切换） */
enum class ChatContextScope { SELECTION, CHAPTER, BOOK }

/**
 * 长章节检索阶段（用于进度提示）。
 *
 * 检索发生在「用户点发送」到「模型开始吐字」之间，占 1~3 秒且此前没有任何反馈；
 * 这里把阶段暴露给界面，让等待可感知。
 */
enum class RetrievalStage {
    NONE,

    /** 从概览中筛选相关段落（含一次模型调用） */
    SELECTING,

    /** 覆盖式压缩整章（总结 / 关系图 / 时间线） */
    CONDENSING,
}

/** 快捷指令（原型屏15 chips；rewrite/continue/roleplay 由 UI 层接管跳转） */
enum class QuickCommand(@StringRes val labelRes: Int) {
    EXPLAIN(R.string.chat_vm_cmd_explain),
    TRANSLATE(R.string.chat_vm_cmd_translate),
    SUMMARIZE(R.string.chat_vm_cmd_summarize),
    ROLEPLAY(R.string.chat_vm_cmd_roleplay),
    REWRITE(R.string.chat_vm_cmd_rewrite),
    CONTINUE(R.string.chat_vm_cmd_continue),
    VOCAB(R.string.chat_vm_cmd_vocab),
    RELATIONSHIP_GRAPH(R.string.chat_vm_cmd_relationship_graph),
    TIMELINE(R.string.chat_vm_cmd_timeline),
}

/** 图表生成结果（人物关系图 / 时间轴演进图） */
sealed interface GraphSheet {
    data class Relationship(val graph: RelationshipGraph) : GraphSheet
    data class TimelineGraph(val stages: List<TimelineStage>) : GraphSheet
    /** [message] 已是本地化文案（ViewModel 层用 context.getString 解析），保持 String 便于 UI 直接渲染 */
    data class Error(val message: String) : GraphSheet
}

data class ChatUiState(
    /** 标题资源 id（由 UI 用 stringResource 解析，支持界面语言切换） */
    @StringRes val titleRes: Int = R.string.chat_vm_ai_assistant,
    val messages: List<AiMessage> = emptyList(),
    val isStreaming: Boolean = false,
    val error: String? = null,
    val contextScope: ChatContextScope = ChatContextScope.BOOK,
    val selectionText: String? = null,
    val chapterTitle: String? = null,
    /** 本章字符数（范围说明条用；0=未加载） */
    val chapterChars: Int = 0,
    val providerName: String = "",
    val isConfigured: Boolean = true,
    /** 已保存为笔记的消息下标（按钮态「✓ 已保存」） */
    val savedMessageIndices: Set<Int> = emptySet(),
    /** 本书历史会话（按最近更新倒序），历史会话列表入口用 */
    val conversations: List<AiConversationInfo> = emptyList(),
    /** 当前激活会话 id（null=未创建/新对话） */
    val activeConversationId: String? = null,
    /** 图表弹窗状态（null=未打开） */
    val graphSheet: GraphSheet? = null,
    val graphLoading: Boolean = false,
    /** 长章节检索阶段（NONE=不在检索） */
    val retrievalStage: RetrievalStage = RetrievalStage.NONE,
    /** 本轮检索已降级（智能选段失败，改用本地关键词匹配） */
    val retrievalDegraded: Boolean = false,
)

@HiltViewModel
class ChatViewModel @Inject constructor(
    private val aiService: AiService,
    private val fallbackChain: FallbackChain,
    private val promptService: PromptService,
    private val bookshelfRepo: BookshelfRepository,
    private val readingRepo: ReadingRepository,
    private val annotationRepo: AnnotationRepository,
    private val aiChatRepo: AiChatRepository,
    private val tocLoader: TocLoader,
    private val localeProvider: PromptLocaleProvider,
    private val retrievalCache: RetrievalCache,
    @ApplicationContext private val appContext: Context,
) : ViewModel() {

    /** 当前界面语言，决定 AI 输出语言与注入模型的说明文案 */
    private fun lang(): AppLang = localeProvider.current()

    /** 非 Composable 场景取文案（图表错误提示等） */
    private fun tr(@StringRes id: Int, vararg args: Any): String = appContext.getString(id, *args)

    private val _uiState = MutableStateFlow(ChatUiState())
    val uiState: StateFlow<ChatUiState> = _uiState.asStateFlow()

    /** 对话历史：仅含成功的 user/assistant 交换，错误文案永不入历史（B4.11） */
    private val history = mutableListOf<AiMessage>()
    private var book: Book? = null
    private var chapterTextProvider: (suspend () -> String?)? = null
    private var chapterTextCache: String? = null
    private var currentLocatorJson: String = "{}"
    private var conversationId: String? = null
    private var inited = false

    /** 章节检索编排（纯逻辑，无 Android 依赖） */
    private val retriever = ChapterRetriever()

    /** 「总结本章」等快捷指令显式要求覆盖全章，由下一次组装上下文时消费一次 */
    private var coverageRequested = false

    /**
     * 当前生效的模型上下文规模（token），由 Provider 配置带入。
     *
     * 在加载 Provider 后立即写入，避免每个提问都重复读一次 KeyStore；
     * 用户把模型换成小上下文档时，长章节会自动转为检索模式而不是硬塞整章。
     */
    private var activeContextTokens: Int = ProviderConfig.DEFAULT_CONTEXT_WINDOW

    private fun applyProviderBudget(providers: List<ProviderConfig>) {
        activeContextTokens = providers.firstOrNull()?.effectiveContextWindow
            ?: ProviderConfig.DEFAULT_CONTEXT_WINDOW
    }

    /**
     * @param selectedText 阅读器选区文本（问 AI 入口）
     * @param chapterTextProvider 懒加载当前章纯文本（CHAPTER 范围用）
     */
    fun init(
        bookId: String,
        selectedText: String? = null,
        chapterTitle: String? = null,
        locatorJson: String? = null,
        chapterTextProvider: (suspend () -> String?)? = null,
    ) {
        // 选区随每次打开更新；书籍/历史同一 VM 只初始化一次
        if (!selectedText.isNullOrBlank()) {
            _uiState.update { it.copy(selectionText = selectedText, contextScope = ChatContextScope.SELECTION) }
        }
        locatorJson?.let { currentLocatorJson = it }
        chapterTitle?.let { t -> _uiState.update { it.copy(chapterTitle = t) } }
        if (chapterTextProvider != null) this.chapterTextProvider = chapterTextProvider
        if (inited) return
        inited = true

        viewModelScope.launch {
            book = bookshelfRepo.getBook(bookId)
            val providers = fallbackChain.getEnabledProviders()
            _uiState.update {
                it.copy(
                    titleRes = R.string.chat_vm_ai_assistant,
                    providerName = providers.firstOrNull()?.name ?: "",
                    isConfigured = providers.isNotEmpty(),
                    contextScope = when {
                        it.selectionText != null -> ChatContextScope.SELECTION
                        this@ChatViewModel.chapterTextProvider != null -> ChatContextScope.CHAPTER
                        else -> ChatContextScope.BOOK
                    },
                )
            }
            // 预取本章字符数（范围说明条「本章约 N 字」）
            if (this@ChatViewModel.chapterTextProvider != null) {
                val text = loadChapterText()
                _uiState.update { it.copy(chapterChars = text?.length ?: 0) }
            }
            // F3：恢复历史——加载本书会话列表，并自动恢复最近一个会话的消息
            val conversations = aiChatRepo.getConversations(bookId)
            _uiState.update { it.copy(conversations = conversations) }
            conversations.firstOrNull()?.let { loadConversationInternal(it.id) }
        }
    }

    /** 切换到指定历史会话（流式进行中禁止切换，避免串台） */
    fun loadConversation(conversationId: String) {
        if (_uiState.value.isStreaming) return
        viewModelScope.launch { loadConversationInternal(conversationId) }
    }

    private suspend fun loadConversationInternal(conversationId: String) {
        val msgs = aiChatRepo.getAiMessages(conversationId)
        history.clear()
        history.addAll(msgs.map { AiMessage(it.role, it.content) })
        this.conversationId = conversationId
        _uiState.update {
            it.copy(
                messages = msgs.map { m -> AiMessage(m.role, m.content) },
                activeConversationId = conversationId,
                savedMessageIndices = emptySet(),
                error = null,
            )
        }
    }

    /** 新对话：清空当前上下文，首条消息发送时创建新会话 */
    fun startNewConversation() {
        if (_uiState.value.isStreaming) return
        history.clear()
        conversationId = null
        _uiState.update {
            it.copy(messages = emptyList(), activeConversationId = null, savedMessageIndices = emptySet(), error = null)
        }
    }

    /** 删除历史会话；若删的是当前会话则同时清空当前消息与历史 */
    fun deleteConversation(conversationId: String) {
        if (_uiState.value.isStreaming) return
        viewModelScope.launch {
            val bookId = book?.id ?: return@launch
            aiChatRepo.deleteConversation(conversationId)
            val conversations = aiChatRepo.getConversations(bookId)
            val wasActive = conversationId == this@ChatViewModel.conversationId
            if (wasActive) {
                history.clear()
                this@ChatViewModel.conversationId = null
            }
            _uiState.update { state ->
                state.copy(
                    conversations = conversations,
                    messages = if (wasActive) emptyList() else state.messages,
                    activeConversationId = if (wasActive) null else state.activeConversationId,
                    savedMessageIndices = if (wasActive) emptySet() else state.savedMessageIndices,
                    error = null,
                )
            }
        }
    }

    fun setScope(scope: ChatContextScope) {
        if (scope == ChatContextScope.SELECTION && _uiState.value.selectionText == null) return
        _uiState.update { it.copy(contextScope = scope) }
    }

    private suspend fun loadChapterText(): String? {
        chapterTextCache?.let { return it }
        val text = chapterTextProvider?.invoke()
        chapterTextCache = text
        return text
    }

    /** 按当前范围组装 system prompt（每次发送重建，保证范围切换即时生效） */
    private suspend fun buildSystemPrompt(): String {
        val b = book
        val lang = lang()
        val sb = StringBuilder(
            PromptRenderer.render(
                promptService.get(PromptTemplates.CHAT),
                mapOf(
                    "bookTitle" to (b?.title ?: ""),
                    "author" to (b?.author ?: AiText.unknownAuthor(lang)),
                ),
            )
        )
        if (b != null) {
            when (_uiState.value.contextScope) {
                ChatContextScope.SELECTION -> _uiState.value.selectionText?.let {
                    sb.append(AiText.selectedTextHeader(lang)).append(it).append("\n\"\"\"")
                }
                ChatContextScope.CHAPTER -> {
                    _uiState.value.chapterTitle?.let { sb.append(AiText.currentChapterLabelShort(lang, it)) }
                    loadChapterText()?.let { sb.append(AiText.chapterContentHeader(lang)).append(buildChapterContext(it)).append("\n\"\"\"") }
                }
                ChatContextScope.BOOK -> {
                    sb.append(AiText.bookMetaHeader(lang))
                    sb.append(AiText.bookTitleAuthorLine(lang, b.title, b.author ?: AiText.unknownAuthor(lang)))
                    b.description?.takeIf { it.isNotBlank() }?.let { sb.append(AiText.summaryLabel(lang, it)) }
                    val toc = tocLoader.load(b.format, b.filePath)
                    if (toc.isNotEmpty()) {
                        sb.append(AiText.tocHeader(lang))
                        toc.take(100).forEach { item ->
                            sb.append("\n").append("  ".repeat(item.depth.coerceIn(0, 3))).append(item.title)
                        }
                    }
                    sb.append(buildBookSummaryContext(b.id))
                }
            }
        }
        return sb.toString()
    }

    /**
     * 本章正文上下文。
     *
     * 三条分流：
     * 1. 未超预算（默认约 5 万字符，与旧实现一致）→ 原样注入全文，**行为与旧版相同**；
     * 2. 覆盖型提问（总结 / 梳理 / 人物关系）→ 本地覆盖式压缩，一次调用看到全章主干；
     * 3. 其余提问 → 概览 + 精读选中块（模型选块 → 失败则本地词法兜底）。
     */
    private suspend fun buildChapterContext(text: String): String {
        val lang = lang()
        val budget = retrievalBudget()
        // 快速跳过：即使整章全是汉字，估算 token 也不会超预算——此时连索引都不必建，
        // 与旧实现逐字节一致，短章节在低配机上零额外开销。
        if (text.length <= CHAPTER_FULL_LIMIT &&
            text.length * TokenEstimator.CJK_WEIGHT <= budget.fullTextTokens
        ) {
            return text
        }
        val index = indexOfChapter(text)
        if (text.length <= CHAPTER_FULL_LIMIT && index.estTokens <= budget.fullTextTokens) return text

        val question = _uiState.value.messages.lastOrNull { it.role == "user" }?.content.orEmpty()
        val wantCoverage = coverageRequested || AiText.INTENT_COVERAGE.containsMatchIn(question)
        coverageRequested = false
        if (wantCoverage) {
            return withStage(RetrievalStage.CONDENSING) {
                buildCoverageContext(text, lang, index, budget, CoveragePurpose.SUMMARY)
            }
        }

        val ctx = currentCoroutineContext()
        val result = withStage(RetrievalStage.SELECTING) {
            retriever.retrieveQa(
                text = text,
                index = index,
                question = question,
                budget = budget,
                labels = cueLabels(lang),
                isActive = { ctx.isActive },
            ) { overview, q, itemCount, maxPick -> aiSelectChunks(overview, q, itemCount, maxPick) }
        }
        if (result.mode == RetrievalMode.LEXICAL_FALLBACK) {
            _uiState.update { it.copy(retrievalDegraded = true) }
        }
        return assembleQaContext(lang, text, index, result)
    }

    /** 在本地检索/压缩期间暴露进度阶段，结束（含异常）后必定复位 */
    private suspend fun <T> withStage(stage: RetrievalStage, block: suspend () -> T): T {
        _uiState.update { it.copy(retrievalStage = stage) }
        try {
            return block()
        } finally {
            _uiState.update { it.copy(retrievalStage = RetrievalStage.NONE) }
        }
    }

    /** 组装问答上下文：概览 + 选中块全文，并按检索模式给出**如实**的说明 */
    private fun assembleQaContext(lang: AppLang, text: String, index: ChapterIndex, result: QaRetrieval): String {
        val sb = StringBuilder()
        sb.append(AiText.chunkOverviewHeader(lang, text.length, result.overviewLines.size, !index.singleLevel))
        sb.append(result.overviewLines.joinToString("\n"))
        if (result.pickedTexts.isEmpty()) {
            // 只有「模型明确说概览够」才是问题较泛；检索降级必须如实说明
            sb.append(
                if (result.mode == RetrievalMode.LEXICAL_FALLBACK) AiText.retrievalDegradedNote(lang)
                else AiText.vagueQuestionNote(lang),
            )
            return sb.toString()
        }
        sb.append(AiText.relevantChunksHeader(lang))
        result.pickedLabels.forEachIndexed { i, label ->
            sb.append(AiText.chunkLabel(lang, label)).append(result.pickedTexts[i])
        }
        if (result.mode == RetrievalMode.LEXICAL_FALLBACK) sb.append(AiText.retrievalDegradedNote(lang))
        return sb.toString()
    }

    /** 覆盖式压缩上下文：把整章压进预算，保证每块都有内容进上下文 */
    private suspend fun buildCoverageContext(
        text: String,
        lang: AppLang,
        index: ChapterIndex,
        budget: RetrievalBudget,
        purpose: CoveragePurpose,
    ): String {
        val ctx = currentCoroutineContext()
        val result = withContext(Dispatchers.Default) {
            retriever.coverage(text, index, budget, purpose) { ctx.isActive }
        }
        // 几乎没有压缩空间时不加说明头，直接用全文（短章节的既有表现）
        if (result.keptRatio >= 0.995) return text
        return AiText.coverageHeader(lang) + result.text
    }

    /** 取章节索引（CPU 密集，放后台线程；按正文哈希缓存） */
    private suspend fun indexOfChapter(text: String): ChapterIndex =
        withContext(Dispatchers.Default) { retrievalCache.indexOf(text) }

    // ---------- 全书范围：章节摘要（增量缓存 + 两阶段检索） ----------

    /**
     * 在「已读章节摘要」上做两阶段检索。
     *
     * 摘要不是预生成的：只有用户真正在「本章」范围提问过的章节才会留下摘要
     * （见 [cacheChapterSummaryIfNeeded]），因此这里通常只有零星几章，
     * 但**永远不会为了一本书一次性打出几百次调用**。
     *
     * @return null 表示本地还没有任何摘要，调用方应保持原有的「只有书目信息」行为
     */
    private suspend fun retrieveBookSummaries(bookId: String, question: String): BookRetrieval? {
        val cached = readingRepo.getCachedSummaries(bookId)
        if (cached.isEmpty()) return null
        val summaries = cached.mapIndexed { i, s -> s.toBookSummary(i + 1) }
        val ctx = currentCoroutineContext()
        return withStage(RetrievalStage.SELECTING) {
            retriever.retrieveBook(summaries, question, isActive = { ctx.isActive }) { overview, q, itemCount, maxPick ->
                aiSelectChunks(overview, q, itemCount, maxPick)
            }
        }
    }

    /** 摘要文本的约定：首行是章节标题（写入时前置），其余是摘要正文 */
    private fun CachedChapterSummary.toBookSummary(ordinal: Int): BookSummary {
        val lines = summary.lineSequence().filter { it.isNotBlank() }.toList()
        if (lines.size <= 1) return BookSummary("第 $ordinal 章", lines.firstOrNull() ?: "")
        return BookSummary(lines.first().take(48), lines.drop(1).joinToString("\n"))
    }

    private suspend fun buildBookSummaryContext(bookId: String): String {
        val lang = lang()
        val question = _uiState.value.messages.lastOrNull { it.role == "user" }?.content.orEmpty()
        val result = retrieveBookSummaries(bookId, question)
            ?: return AiText.bookSummaryNoneNote(lang)
        val sb = StringBuilder()
        sb.append(AiText.bookSummaryHeader(lang, result.pickedSummaries.size, result.overviewLines.size))
        result.pickedSummaries.forEach { s ->
            sb.append("\n【").append(s.label).append("】").append(s.text)
        }
        if (result.mode == RetrievalMode.LEXICAL_FALLBACK) sb.append(AiText.retrievalDegradedNote(lang))
        return sb.toString()
    }

    /**
     * 为刚问过的长章节回填一份摘要，供以后的全书提问检索。
     *
     * 三条纪律：
     * 1. **只在长到已经走检索的章节上做**——短章节本来就整章发送，摘要没有增量价值；
     * 2. **已缓存就跳过**，同一章无论问多少次都只花一次调用；
     * 3. 在回答完成之后另行发起，不阻塞、不影响本轮回答。
     */
    private suspend fun cacheChapterSummaryIfNeeded() {
        val b = book ?: return
        val text = chapterTextCache ?: return
        val key = currentLocatorJson
        if (key.isBlank() || key == "{}") return
        if (readingRepo.getCachedSummary(b.id, key) != null) return

        val index = indexOfChapter(text)
        val budget = retrievalBudget()
        if (text.length <= CHAPTER_FULL_LIMIT && index.estTokens <= budget.fullTextTokens) return

        val lang = lang()
        val compressed = withContext(Dispatchers.Default) {
            retriever.coverage(text, index, budget, CoveragePurpose.SUMMARY)
        }
        val title = _uiState.value.chapterTitle.orEmpty()
        val prompt = AiText.chapterSummaryPrompt(lang, title, compressed.text)
        for (provider in fallbackChain.getEnabledProviders()) {
            if (provider.isDegraded) continue
            aiService.simpleChat(provider, listOf(AiMessage("user", prompt)))
                .onSuccess { raw ->
                    fallbackChain.recordUtilitySuccess(provider.id)
                    val body = raw.trim().take(SUMMARY_MAX_CHARS)
                    if (body.isBlank()) return@onSuccess
                    val stored = if (title.isBlank()) body else "$title\n$body"
                    readingRepo.cacheSummary(b.id, key, stored, provider.id)
                }
                .onFailure { fallbackChain.recordUtilityFailure(provider.id) }
            // 只尝试当前可用的第一个 Provider，失败也不重试——这是后台回填，不值得占用链路
            return
        }
    }

    private fun retrievalBudget(): RetrievalBudget = RetrievalBudgetConfig.of(activeContextTokens)

    private fun cueLabels(lang: AppLang): CueLabels =
        CueLabels(AiText.cueNamesLabel(lang), AiText.cueTimeLabel(lang))

    /**
     * 让模型从概览里选块。
     *
     * 三条硬约束：
     * 1. 返回 [ChunkPick] **三态**而不是空列表——「模型说够了」与「调用失败」必须能区分；
     * 2. 计入**辅助调用**失败计数，不参与主对话的 Provider 降级判定；
     * 3. 解析只接受纯编号列表，避免把模型复述的概览当成选择结果。
     */
    private suspend fun aiSelectChunks(overview: String, question: String, itemCount: Int, maxPick: Int): ChunkPick {
        val providers = fallbackChain.getEnabledProviders()
        if (providers.isEmpty()) return ChunkPick.Failed("no-provider")
        val prompt = AiText.chunkSelectPrompt(lang(), question, overview)
        for (provider in providers) {
            if (provider.isDegraded) continue
            aiService.simpleChat(provider, listOf(AiMessage("user", prompt)))
                .onSuccess { raw ->
                    fallbackChain.recordUtilitySuccess(provider.id)
                    return ChunkSelectionParser.parse(raw, itemCount, maxPick)
                }
                .onFailure { fallbackChain.recordUtilityFailure(provider.id) }
        }
        return ChunkPick.Failed("all-providers-failed")
    }

    fun sendMessage(text: String) {
        if (text.isBlank() || _uiState.value.isStreaming) return
        val userMsg = AiMessage("user", text)
        history.add(userMsg)
        _uiState.update { it.copy(messages = it.messages + userMsg) }
        // D9：写穿到 ai_conversations/ai_messages（首条消息创建会话）
        viewModelScope.launch {
            ensureConversation(text)?.let { cid ->
                aiChatRepo.addAiMessage(cid, "USER", text, _uiState.value.contextScope.name, _uiState.value.selectionText)
            }
        }
        runCompletion()
    }

    private suspend fun ensureConversation(firstUserText: String): String? {
        conversationId?.let { return it }
        val b = book ?: return null
        val id = aiChatRepo.createConversation(b.id, firstUserText.take(20))
        conversationId = id
        // 新会话同步进历史列表（置顶）
        _uiState.update {
            it.copy(
                activeConversationId = id,
                conversations = listOf(AiConversationInfo(id, firstUserText.take(20), System.currentTimeMillis())) + it.conversations,
            )
        }
        return id
    }

    /** 快捷指令：display 为用户气泡展示文案，prompt 为实际发送内容 */
    fun sendQuickCommand(command: QuickCommand) {
        viewModelScope.launch {
            val templateId = when (command) {
                QuickCommand.EXPLAIN -> PromptTemplates.QUICK_EXPLAIN
                QuickCommand.TRANSLATE -> PromptTemplates.QUICK_TRANSLATE
                QuickCommand.SUMMARIZE -> PromptTemplates.QUICK_SUMMARIZE
                QuickCommand.VOCAB -> PromptTemplates.QUICK_VOCAB
                // REWRITE / CONTINUE / ROLEPLAY 由 UI 层跳转对应页面
                else -> return@launch
            }
            if (command == QuickCommand.SUMMARIZE) {
                // 总结要的是「读完整章」而不是「读最相关的几块」——显式标记走覆盖式压缩
                _uiState.update { it.copy(contextScope = ChatContextScope.CHAPTER) }
                coverageRequested = true
            }
            val text = PromptRenderer.render(promptService.get(templateId), emptyMap())
            sendMessage(text)
        }
    }

    /** 重新生成：撤回最后一条 assistant 回答后重跑 */
    fun regenerate() {
        if (_uiState.value.isStreaming) return
        if (history.lastOrNull()?.role == "assistant") history.removeAt(history.lastIndex)
        val lastUser = history.lastOrNull { it.role == "user" } ?: return
        // UI 同步撤到最后一条 user 气泡
        _uiState.update { state ->
            var msgs = state.messages
            if (msgs.lastOrNull()?.role == "assistant") msgs = msgs.dropLast(1)
            state.copy(messages = msgs)
        }
        runCompletion()
    }

    private fun runCompletion() {
        _uiState.update { it.copy(isStreaming = true, error = null, retrievalDegraded = false) }
        viewModelScope.launch {
            val providers = fallbackChain.getEnabledProviders()
            if (providers.isEmpty()) {
                _uiState.update { it.copy(isConfigured = false, isStreaming = false) }
                return@launch
            }
            applyProviderBudget(providers)
            val systemPrompt = buildSystemPrompt()

            var success = false
            var lastError: String? = null
            for (provider in providers) {
                if (provider.isDegraded) continue
                val assistantMsg = AiMessage("assistant", "")
                _uiState.update { it.copy(messages = it.messages + assistantMsg) }

                try {
                    var finalContent = ""
                    aiService.streamChat(provider, listOf(AiMessage("system", systemPrompt)) + history.toList())
                        .collect { response ->
                            finalContent = response.content
                            _uiState.update { state ->
                                state.copy(
                                    messages = state.messages.dropLast(1) + AiMessage("assistant", response.content),
                                    isStreaming = !response.isComplete,
                                )
                            }
                        }
                    fallbackChain.recordSuccess(provider.id)
                    history.add(AiMessage("assistant", finalContent))
                    // 长章节答完后顺带回填一份章节摘要，供以后的全书提问检索；
                    // 单独起协程，既不阻塞本轮回答，也不影响流式结束状态。
                    if (_uiState.value.contextScope == ChatContextScope.CHAPTER) {
                        launch { runCatching { cacheChapterSummaryIfNeeded() } }
                    }
                    conversationId?.let { cid ->
                        aiChatRepo.addAiMessage(cid, "ASSISTANT", finalContent, null, null)
                        aiChatRepo.touchConversation(cid)
                    }
                    success = true
                    break
                } catch (e: kotlinx.coroutines.CancellationException) {
                    throw e
                } catch (e: Exception) {
                    // 失败：记录并切换下一个 Provider；错误文本仅进 error 栏，不进对话历史（B4.11）
                    fallbackChain.recordFailure(provider.id)
                    lastError = e.message
                    _uiState.update { state ->
                        state.copy(
                            messages = state.messages.dropLast(1),
                            error = tr(R.string.chat_vm_provider_failed_fallback, provider.name),
                        )
                    }
                }
            }
            if (!success) {
                _uiState.update { it.copy(error = tr(R.string.chat_vm_all_providers_failed, lastError ?: tr(R.string.chat_vm_unknown_error)), isStreaming = false) }
            }
        }
    }

    /** AI 回答保存为笔记（AI_ANSWER 类型，蓝色，笔记中心聚合） */
    fun saveAsNote(messageIndex: Int) {
        val msg = _uiState.value.messages.getOrNull(messageIndex) ?: return
        if (msg.role != "assistant" || msg.content.isBlank()) return
        val bookId = book?.id ?: return
        // 引文：优先选区，其次该回答对应的用户提问
        val question = _uiState.value.messages.take(messageIndex).lastOrNull { it.role == "user" }?.content
        val quote = (_uiState.value.selectionText ?: question ?: "").take(200)
        viewModelScope.launch {
            annotationRepo.insertOrUpdate(
                Annotation(
                    id = UUID.randomUUID().toString(),
                    bookId = bookId,
                    locatorJson = currentLocatorJson,
                    selectedText = quote,
                    type = AnnotationType.AI_ANSWER,
                    color = null,
                    note = msg.content,
                    translation = null,
                    rewrittenText = null,
                    rewriteInstruction = null,
                    providerId = null,
                    createdAt = System.currentTimeMillis(),
                    updatedAt = System.currentTimeMillis(),
                )
            )
            _uiState.update { it.copy(savedMessageIndices = it.savedMessageIndices + messageIndex) }
        }
    }

    fun clearError() {
        // 同时收起「检索降级」提示：两者都是横幅，用户点一下就都该消失
        _uiState.update { it.copy(error = null, retrievalDegraded = false) }
    }

    // ---------- 人物关系图 / 时间轴演进图（阅读器内 AI 面板） ----------

    fun generateRelationshipGraph() {
        if (_uiState.value.graphLoading || _uiState.value.isStreaming) return
        _uiState.update { it.copy(graphLoading = true, graphSheet = null) }
        viewModelScope.launch {
            val raw = graphCompletion(PromptTemplates.RELATIONSHIP_GRAPH, CoveragePurpose.GRAPH)
            val sheet = when {
                raw == null -> GraphSheet.Error(tr(R.string.chat_vm_graph_failed))
                else -> parseRelationshipGraph(raw)?.let { GraphSheet.Relationship(it) }
                    ?: GraphSheet.Error(tr(R.string.chat_vm_graph_parse_failed))
            }
            _uiState.update { it.copy(graphLoading = false, graphSheet = sheet) }
        }
    }

    fun generateTimeline() {
        if (_uiState.value.graphLoading || _uiState.value.isStreaming) return
        _uiState.update { it.copy(graphLoading = true, graphSheet = null) }
        viewModelScope.launch {
            val raw = graphCompletion(PromptTemplates.TIMELINE, CoveragePurpose.TIMELINE)
            val sheet = when {
                raw == null -> GraphSheet.Error(tr(R.string.chat_vm_graph_failed))
                else -> parseTimeline(raw)?.let { GraphSheet.TimelineGraph(it) }
                    ?: GraphSheet.Error(tr(R.string.chat_vm_graph_parse_failed))
            }
            _uiState.update { it.copy(graphLoading = false, graphSheet = sheet) }
        }
    }

    fun dismissGraphSheet() { _uiState.update { it.copy(graphSheet = null) } }

    /** 图表用：按范围组装 prompt（本章压缩/全文；全书让 AI 结合自身知识/公开信息补足）→ 非流式请求 */
    private suspend fun graphCompletion(templateId: String, purpose: CoveragePurpose): String? {
        val providers = fallbackChain.getEnabledProviders()
        if (providers.isEmpty()) return null
        applyProviderBudget(providers)
        val lang = lang()
        val b = book
        val isBookScope = _uiState.value.contextScope == ChatContextScope.BOOK
        val chapterText = if (isBookScope) "" else loadChapterText()?.let { buildGraphContext(it, purpose) }.orEmpty()
        val chapterBlock = if (isBookScope) {
            buildBookGraphScopeContext(b) ?: AiText.graphBookScopeFallback(lang, b?.title ?: "")
        } else if (chapterText.isBlank()) {
            AiText.graphNoChapterFallback(lang)
        } else {
            chapterText
        }
        val prompt = PromptRenderer.render(
            promptService.get(templateId),
            mapOf(
                "bookTitle" to (b?.title ?: ""),
                "author" to (b?.author ?: AiText.unknownAuthor(lang)),
                // scope 会直接进入提示词正文，因此必须跟随界面语言，否则英文界面下会插入中文
                "scope" to (if (isBookScope) AiText.scopeWholeBook(lang) else AiText.scopeThisChapter(lang)),
                "chapterTitle" to (_uiState.value.chapterTitle ?: ""),
                "chapterText" to chapterBlock,
            ),
        )
        for (provider in providers) {
            if (provider.isDegraded) continue
            aiService.simpleChat(provider, listOf(AiMessage("user", prompt)))
                .onSuccess { fallbackChain.recordSuccess(provider.id); return it }
                .onFailure { fallbackChain.recordFailure(provider.id) }
        }
        return null
    }

    /**
     * 全书图表的输入：已缓存的章节摘要。
     *
     * 图表要的是**覆盖面**而不是相似度，所以这里不做 top-k 选择，而是按预算尽量多地带；
     * 章数超过预算时**等距抽样**，保证覆盖全书各阶段而不是只留开头。
     *
     * @return null 表示本地还没有摘要，调用方回落到「结合你自己的了解」的既有文案
     */
    private suspend fun buildBookGraphScopeContext(b: Book?): String? {
        val id = b?.id ?: return null
        val cached = readingRepo.getCachedSummaries(id)
        if (cached.isEmpty()) return null
        val summaries = cached.mapIndexed { i, s -> s.toBookSummary(i + 1) }

        val perChapter = summaries.sumOf { TokenEstimator.estimate(it.text) } / summaries.size.coerceAtLeast(1)
        val affordable = (BOOK_GRAPH_SUMMARY_TOKENS / perChapter.coerceAtLeast(1)).coerceAtLeast(1)
        val stride = if (summaries.size <= affordable) 1 else (summaries.size + affordable - 1) / affordable
        val picked = summaries.filterIndexed { i, _ -> i % stride == 0 }

        val lang = lang()
        val sb = StringBuilder()
        sb.append(AiText.bookSummaryHeader(lang, picked.size, summaries.size))
        picked.forEach { s -> sb.append("\n【").append(s.label).append("】").append(s.text) }
        return sb.toString()
    }

    /**
     * 生图正文。
     *
     * 关系图与时间线要的是**覆盖率**而不是相似度：人物的出场、事件的先后分布在全章，
     * 只挑「最相关的 3 块」必然漏人漏事。因此这里改为覆盖式压缩——
     * 全章压进预算、每块都有内容，且仍只花一次调用（对比按块摘要有 N+1 次调用）。
     */
    private suspend fun buildGraphContext(text: String, purpose: CoveragePurpose): String {
        val budget = retrievalBudget()
        if (text.length <= CHAPTER_FULL_LIMIT &&
            text.length * TokenEstimator.CJK_WEIGHT <= budget.fullTextTokens
        ) {
            return text
        }
        val index = indexOfChapter(text)
        if (text.length <= CHAPTER_FULL_LIMIT && index.estTokens <= budget.fullTextTokens) return text
        return buildCoverageContext(text, lang(), index, budget, purpose)
    }

    companion object {
        /** 正文直接全量注入的字符上限；与 token 预算共同决定是否进入检索模式 */
        const val CHAPTER_FULL_LIMIT = 50_000

        /** 单章摘要的字符上限（回填时截断，避免模型写成长文） */
        const val SUMMARY_MAX_CHARS = 800

        /** 全书图表可注入的摘要总量上限（token） */
        const val BOOK_GRAPH_SUMMARY_TOKENS = 20_000
    }
}
