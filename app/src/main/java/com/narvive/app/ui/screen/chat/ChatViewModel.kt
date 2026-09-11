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
import com.narvive.app.service.ai.AiMessage
import com.narvive.app.service.ai.AiService
import com.narvive.app.service.ai.AiText
import com.narvive.app.service.ai.FallbackChain
import com.narvive.app.service.ai.PromptLocaleProvider
import com.narvive.app.service.ai.PromptRenderer
import com.narvive.app.service.ai.PromptService
import com.narvive.app.service.ai.PromptTemplates
import com.narvive.app.service.ai.RelationshipGraph
import com.narvive.app.service.ai.TimelineStage
import com.narvive.app.service.ai.parseRelationshipGraph
import com.narvive.app.service.ai.parseTimeline
import com.narvive.app.service.reader.TocLoader
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

/** AI 面板上下文范围（原型：选区 / 本章 / 全书 三 chip 可切换） */
enum class ChatContextScope { SELECTION, CHAPTER, BOOK }

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
)

@HiltViewModel
class ChatViewModel @Inject constructor(
    private val aiService: AiService,
    private val fallbackChain: FallbackChain,
    private val promptService: PromptService,
    private val bookshelfRepo: BookshelfRepository,
    private val annotationRepo: AnnotationRepository,
    private val aiChatRepo: AiChatRepository,
    private val tocLoader: TocLoader,
    private val localeProvider: PromptLocaleProvider,
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
                }
            }
        }
        return sb.toString()
    }

    /** 本章正文：≤5万字完整；>5万字分块（AI 先看概览选 2 块，泛问题仅凭概览） */
    private suspend fun buildChapterContext(text: String): String {
        if (text.length <= CHAPTER_FULL_LIMIT) return text
        val lang = lang()
        val chunks = chunkText(text, CHUNK_SIZE)
        val overview = chunks.mapIndexed { i, c -> "[${i + 1}] ${c.take(80).replace('\n', ' ')}" }.joinToString("\n")
        val question = _uiState.value.messages.lastOrNull { it.role == "user" }?.content ?: ""
        val selected = aiSelectChunks(overview, question)

        val sb = StringBuilder()
        sb.append(AiText.chunkOverviewHeader(lang, text.length, chunks.size)).append(overview)
        if (selected.isEmpty()) {
            sb.append(AiText.vagueQuestionNote(lang))
        } else {
            sb.append(AiText.relevantChunksHeader(lang))
            selected.forEach { i ->
                chunks.getOrNull(i)?.let { sb.append(AiText.chunkLabel(lang, i + 1)).append(it) }
            }
        }
        return sb.toString()
    }

    private fun chunkText(text: String, target: Int): List<String> {
        val chunks = mutableListOf<String>()
        val cur = StringBuilder()
        text.split("\n").forEach { p ->
            if (cur.isNotEmpty() && cur.length + p.length + 1 > target) {
                chunks.add(cur.toString()); cur.clear()
            }
            cur.append(p).append("\n")
        }
        if (cur.isNotBlank()) chunks.add(cur.toString())
        return chunks.ifEmpty { listOf(text) }
    }

    private suspend fun aiSelectChunks(overview: String, question: String, maxBlocks: Int = 2): List<Int> {
        val providers = fallbackChain.getEnabledProviders()
        if (providers.isEmpty()) return emptyList()
        val prompt = AiText.chunkSelectPrompt(lang(), question, overview)
        for (provider in providers) {
            if (provider.isDegraded) continue
            aiService.simpleChat(provider, listOf(AiMessage("user", prompt)))
                .onSuccess { raw ->
                    fallbackChain.recordSuccess(provider.id)
                    return Regex("\\d+").findAll(raw).mapNotNull { it.value.toIntOrNull() }
                        .filter { it >= 1 }.distinct().take(maxBlocks).map { it - 1 }.toList()
                }
                .onFailure { fallbackChain.recordFailure(provider.id) }
        }
        return emptyList()
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
                _uiState.update { it.copy(contextScope = ChatContextScope.CHAPTER) }
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
        _uiState.update { it.copy(isStreaming = true, error = null) }
        viewModelScope.launch {
            val providers = fallbackChain.getEnabledProviders()
            if (providers.isEmpty()) {
                _uiState.update { it.copy(isConfigured = false, isStreaming = false) }
                return@launch
            }
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

    fun clearError() { _uiState.update { it.copy(error = null) } }

    // ---------- 人物关系图 / 时间轴演进图（阅读器内 AI 面板） ----------

    fun generateRelationshipGraph() {
        if (_uiState.value.graphLoading || _uiState.value.isStreaming) return
        _uiState.update { it.copy(graphLoading = true, graphSheet = null) }
        viewModelScope.launch {
            val raw = graphCompletion(PromptTemplates.RELATIONSHIP_GRAPH, AiText.graphPurposeRelationship(lang()))
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
            val raw = graphCompletion(PromptTemplates.TIMELINE, AiText.graphPurposeTimeline(lang()))
            val sheet = when {
                raw == null -> GraphSheet.Error(tr(R.string.chat_vm_graph_failed))
                else -> parseTimeline(raw)?.let { GraphSheet.TimelineGraph(it) }
                    ?: GraphSheet.Error(tr(R.string.chat_vm_graph_parse_failed))
            }
            _uiState.update { it.copy(graphLoading = false, graphSheet = sheet) }
        }
    }

    fun dismissGraphSheet() { _uiState.update { it.copy(graphSheet = null) } }

    /** 图表用：按范围组装 prompt（本章分块提取；全书让 AI 结合自身知识/公开信息补足）→ 非流式请求 */
    private suspend fun graphCompletion(templateId: String, purpose: String): String? {
        val providers = fallbackChain.getEnabledProviders()
        if (providers.isEmpty()) return null
        val lang = lang()
        val b = book
        val isBookScope = _uiState.value.contextScope == ChatContextScope.BOOK
        val chapterText = if (isBookScope) "" else loadChapterText()?.let { buildGraphContext(it, purpose) }.orEmpty()
        val chapterBlock = if (isBookScope) {
            AiText.graphBookScopeFallback(lang, b?.title ?: "")
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

    /** 生图正文：≤5万字全量；>5万字分块（AI 看概览选最多 3 块） */
    private suspend fun buildGraphContext(text: String, purpose: String): String {
        if (text.length <= CHAPTER_FULL_LIMIT) return text
        val lang = lang()
        val chunks = chunkText(text, CHUNK_SIZE)
        val overview = chunks.mapIndexed { i, c -> "[${i + 1}] ${c.take(80).replace('\n', ' ')}" }.joinToString("\n")
        val selected = aiSelectChunks(overview, purpose, maxBlocks = 3)
        val sb = StringBuilder()
        sb.append(AiText.chunkOverviewHeader(lang, text.length, chunks.size)).append(overview)
        if (selected.isEmpty()) {
            sb.append(AiText.graphVagueNote(lang))
        } else {
            sb.append(AiText.graphRelevantChunksHeader(lang, purpose))
            selected.forEach { i ->
                chunks.getOrNull(i)?.let { sb.append(AiText.chunkLabel(lang, i + 1)).append(it) }
            }
        }
        return sb.toString()
    }

    private fun truncate(text: String, maxChars: Int): String =
        if (text.length <= maxChars) text else text.take(maxChars) + AiText.truncatedSuffix(lang())

    companion object {
        const val CHAPTER_FULL_LIMIT = 50_000
        const val CHUNK_SIZE = 8_000
    }
}
