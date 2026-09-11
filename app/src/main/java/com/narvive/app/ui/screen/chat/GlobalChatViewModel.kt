package com.narvive.app.ui.screen.chat

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.narvive.app.data.local.dao.GlobalAiDao
import com.narvive.app.data.local.entity.GlobalConversationEntity
import com.narvive.app.data.local.entity.GlobalMessageEntity
import com.narvive.app.domain.model.Book
import com.narvive.app.domain.model.ReadingSession
import com.narvive.app.domain.repository.BookshelfRepository
import com.narvive.app.domain.repository.ReadingRepository
import com.narvive.app.R
import com.narvive.app.core.AppLang
import com.narvive.app.service.ai.AiMessage
import com.narvive.app.service.ai.AiProfile
import com.narvive.app.service.ai.AiProfileStore
import com.narvive.app.service.ai.AiService
import com.narvive.app.service.ai.AiText
import com.narvive.app.service.ai.FallbackChain
import com.narvive.app.service.ai.PromptLocaleProvider
import com.narvive.app.ui.message.UiMessage
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.Calendar
import java.util.UUID
import javax.inject.Inject

data class GlobalConversationInfo(
    val id: String,
    val title: String,
    val updatedAt: Long,
    val pinned: Boolean = false,
)

/**
 * 建议卡片的动作。
 *
 * [AskBook] **只带数据（bookId）**，不再携带已拼好的提示词：提示词会在点击时由界面
 * 按当前语言生成。这样它不会在语言切换后停留旧语言，语义也更正确——用户看到什么语言，
 * 就发什么语言。
 */
sealed interface SuggestionAction {
    data object Report : SuggestionAction
    data object Recommend : SuggestionAction
    data class Continue(val bookId: String) : SuggestionAction
    data class AskBook(val bookId: String) : SuggestionAction
}

/**
 * 建议卡片。
 *
 * [label] 用 [UiMessage] 而非 String：本项目切换语言不重建 Activity，若存已解析的
 * String 会停留在旧语言。详见 docs/i18n.md §2.1。
 */
data class Suggestion(
    val label: UiMessage,
    val action: SuggestionAction,
)

data class GlobalChatUiState(
    val messages: List<AiMessage> = emptyList(),
    val isStreaming: Boolean = false,
    val error: String? = null,
    val isConfigured: Boolean = true,
    val providerName: String = "",
    val conversations: List<GlobalConversationInfo> = emptyList(),
    val activeConversationId: String? = null,
    /** 全部书籍（按 lastReadAt 倒序），最近在读入口与 @选书用 */
    val books: List<Book> = emptyList(),
    /** @引用的书籍 id（最多 5 本）；与 allBooksContext 互斥 */
    val contextBookIds: List<String> = emptyList(),
    /** 全部书库聚合上下文 */
    val allBooksContext: Boolean = false,
    /**
     * 个性化问候（进入/新建会话时随机一次）。
     *
     * 类型是 [UiMessage] 而非 String：切换语言不重建 Activity，存已解析的 String 会陈旧。
     */
    val greeting: UiMessage = UiMessage.Raw(""),
    /** 建议卡片（随机）。label 为 [UiMessage]，见 [Suggestion]。 */
    val suggestions: List<Suggestion> = emptyList(),
    /**
     * AI 偏好是否要求使用表情。
     *
     * 表情后缀（`styled()`）改为**在界面渲染时**追加，因此偏好要随状态传给 UI。
     * 这样既能保留原有 emoji 行为，又不会把文案固化成 String。
     */
    val useEmoji: Boolean = false,
)

/** 底部 AI tab：不绑定书籍的全局通用对话 */
@HiltViewModel
class GlobalChatViewModel @Inject constructor(
    private val aiService: AiService,
    private val fallbackChain: FallbackChain,
    private val globalAiDao: GlobalAiDao,
    private val bookshelfRepo: BookshelfRepository,
    private val readingRepo: ReadingRepository,
    private val profileStore: AiProfileStore,
    private val localeProvider: PromptLocaleProvider,
    @ApplicationContext private val appContext: Context,
) : ViewModel() {

    /** 当前界面语言，决定 AI 输出语言与周边文案（每次取用，切换语言后立即生效） */
    private fun lang(): AppLang = localeProvider.current()

    private val _uiState = MutableStateFlow(GlobalChatUiState())
    val uiState: StateFlow<GlobalChatUiState> = _uiState.asStateFlow()

    private val history = mutableListOf<AiMessage>()
    private var conversationId: String? = null
    private var inited = false
    /** 意图路由注入的一次性上下文，用完即清 */
    private var intentContext: String? = null
    /** 新建会话待生成标题的 id */
    private var pendingTitleConversationId: String? = null

    fun init() {
        if (inited) return
        inited = true
        viewModelScope.launch {
            val providers = fallbackChain.getEnabledProviders()
            _uiState.update {
                it.copy(
                    isConfigured = providers.isNotEmpty(),
                    providerName = providers.firstOrNull()?.name ?: "",
                )
            }
            val conversations = globalAiDao.getConversations()
            _uiState.update { it.copy(conversations = conversations.map(::toInfo)) }
            conversations.firstOrNull()?.let { loadConversationInternal(it.id) }
        }
        // 全部书籍（最近在读 / @选书）
        viewModelScope.launch {
            bookshelfRepo.observeAllBooks().collect { books ->
                _uiState.update { it.copy(books = books) }
            }
        }
        // 问候 + 建议（进入时随机一次；等书籍与偏好就绪后生成）
        viewModelScope.launch {
            val books = bookshelfRepo.observeAllBooks().first()
            val pref = profileStore.state.first()
            refreshGreeting(books, pref.profile)
        }
    }

    fun loadConversation(id: String) {
        if (_uiState.value.isStreaming) return
        viewModelScope.launch { loadConversationInternal(id) }
    }

    private suspend fun loadConversationInternal(id: String) {
        val msgs = globalAiDao.getMessages(id)
        history.clear()
        history.addAll(msgs.map { AiMessage(if (it.role == "USER") "user" else "assistant", it.content) })
        conversationId = id
        _uiState.update {
            it.copy(
                messages = msgs.map { m -> AiMessage(if (m.role == "USER") "user" else "assistant", m.content) },
                activeConversationId = id,
                error = null,
            )
        }
    }

    fun startNewConversation() {
        if (_uiState.value.isStreaming) return
        history.clear()
        conversationId = null
        _uiState.update {
            it.copy(messages = emptyList(), activeConversationId = null, error = null)
        }
        refreshGreeting(_uiState.value.books, profileStore.state.value.profile)
    }

    fun deleteConversation(id: String) {
        if (_uiState.value.isStreaming) return
        viewModelScope.launch {
            globalAiDao.deleteConversation(id)
            val conversations = globalAiDao.getConversations().map(::toInfo)
            if (id == _uiState.value.activeConversationId) {
                history.clear()
                conversationId = null
                _uiState.update {
                    it.copy(messages = emptyList(), activeConversationId = null, conversations = conversations)
                }
            } else {
                _uiState.update { it.copy(conversations = conversations) }
            }
        }
    }

    fun renameConversation(id: String, title: String) {
        val trimmed = title.trim()
        if (trimmed.isEmpty()) return
        viewModelScope.launch {
            globalAiDao.renameConversation(id, trimmed)
            refreshConversations()
        }
    }

    fun togglePin(id: String) {
        viewModelScope.launch {
            val conv = _uiState.value.conversations.find { it.id == id } ?: return@launch
            globalAiDao.setPinned(id, !conv.pinned)
            refreshConversations()
        }
    }

    /** @选书：切换单本书（最多 5 本）；选中后自动退出「全部书库」 */
    fun toggleBookContext(bookId: String) {
        _uiState.update { s ->
            val ids = if (bookId in s.contextBookIds) s.contextBookIds - bookId
            else (s.contextBookIds + bookId).take(5)
            s.copy(contextBookIds = ids, allBooksContext = false)
        }
    }

    /** 全部书库聚合上下文（与多本互斥） */
    fun setAllBooksContext(enabled: Boolean) {
        _uiState.update { s ->
            s.copy(allBooksContext = enabled, contextBookIds = if (enabled) emptyList() else s.contextBookIds)
        }
    }

    fun clearContext() {
        _uiState.update { it.copy(contextBookIds = emptyList(), allBooksContext = false) }
    }

    fun sendMessage(text: String) {
        if (text.isBlank() || _uiState.value.isStreaming) return
        val userMsg = AiMessage("user", text)
        history.add(userMsg)
        _uiState.update { it.copy(messages = it.messages + userMsg) }
        viewModelScope.launch {
            ensureConversation(text)?.let { cid ->
                globalAiDao.insertMessage(GlobalMessageEntity(UUID.randomUUID().toString(), cid, "USER", text))
            }
            resolveIntentContext(text)
            runCompletion()
        }
    }

    private suspend fun ensureConversation(firstUserText: String): String? {
        conversationId?.let { return it }
        val id = UUID.randomUUID().toString()
        val title = firstUserText.take(24)
        globalAiDao.insertConversation(GlobalConversationEntity(id = id, title = title))
        conversationId = id
        pendingTitleConversationId = id
        _uiState.update {
            it.copy(
                activeConversationId = id,
                conversations = listOf(GlobalConversationInfo(id, title, System.currentTimeMillis())) + it.conversations,
            )
        }
        return id
    }

    fun regenerate() {
        if (_uiState.value.isStreaming) return
        if (history.lastOrNull()?.role == "assistant") history.removeAt(history.lastIndex)
        if (history.lastOrNull { it.role == "user" } == null) return
        _uiState.update { state ->
            var msgs = state.messages
            if (msgs.lastOrNull()?.role == "assistant") msgs = msgs.dropLast(1)
            state.copy(messages = msgs)
        }
        runCompletion()
    }

    /** 按当前上下文组装 system prompt（每次发送重建，保证偏好/跨书/书库即时生效） */
    private suspend fun buildSystemPrompt(): String {
        val lang = lang()
        val sb = StringBuilder(AiText.globalSystemPrompt(lang))
        // AI 偏好
        val pref = profileStore.state.value
        if (pref.enabled && pref.profile.summary.isNotBlank()) {
            sb.append("\n").append(AiText.userPreferenceLabel(lang)).append(pref.profile.summary)
        }
        // 上下文（仅元数据，无正文）
        if (_uiState.value.allBooksContext) {
            val books = bookshelfRepo.observeAllBooks().first()
            if (books.isNotEmpty()) {
                sb.append("\n\n").append(AiText.allBooksHeader(lang))
                books.take(20).forEachIndexed { i, b ->
                    sb.append("\n").append(AiText.bookLine(lang, i + 1, b.title, b.author ?: AiText.unknownAuthor(lang), (b.progress * 100).toInt(), b.isFinished || b.progress >= 0.95f))
                    b.currentChapter?.takeIf { it.isNotBlank() }?.let { sb.append(AiText.currentChapterLabel(lang, it.take(24))) }
                }
                if (books.size > 20) sb.append("\n").append(AiText.moreBooksLabel(lang, books.size - 20))
            }
        } else if (_uiState.value.contextBookIds.isNotEmpty()) {
            sb.append("\n\n").append(AiText.contextBooksHeader(lang))
            _uiState.value.contextBookIds.forEach { id ->
                bookshelfRepo.getBook(id)?.let { b ->
                    sb.append("\n").append(AiText.bookBullet(lang, b.title, b.author ?: AiText.unknownAuthor(lang), (b.progress * 100).toInt()))
                    b.currentChapter?.takeIf { it.isNotBlank() }?.let { sb.append(AiText.currentChapterLabel(lang, it.take(24))) }
                    b.description?.takeIf { it.isNotBlank() }?.let { sb.append(AiText.descriptionLabel(lang, it.take(200))) }
                }
            }
        }
        // 意图路由注入（统计/推荐/书名等本地数据）
        intentContext?.let { sb.append("\n\n").append(it) }
        return sb.toString()
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
            intentContext = null

            var success = false
            var lastError: String? = null
            for (provider in providers) {
                if (provider.isDegraded) continue
                _uiState.update { it.copy(messages = it.messages + AiMessage("assistant", "")) }
                try {
                    var finalContent = ""
                    aiService.streamChat(
                        provider,
                        listOf(AiMessage("system", systemPrompt)) + history.toList(),
                    ).collect { response ->
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
                        globalAiDao.insertMessage(GlobalMessageEntity(UUID.randomUUID().toString(), cid, "ASSISTANT", finalContent))
                        globalAiDao.touchConversation(cid)
                    }
                    success = true
                    break
                } catch (e: kotlinx.coroutines.CancellationException) {
                    throw e
                } catch (e: Exception) {
                    fallbackChain.recordFailure(provider.id)
                    lastError = e.message
                    _uiState.update { state ->
                        state.copy(
                            messages = state.messages.dropLast(1),
                            error = appContext.getString(R.string.chat_vm_provider_failed_fallback, provider.name),
                        )
                    }
                }
            }
            if (!success) {
                _uiState.update {
                    it.copy(error = appContext.getString(R.string.chat_vm_all_providers_failed, lastError ?: appContext.getString(R.string.chat_vm_unknown_error)), isStreaming = false)
                }
            } else {
                // 会话标题生成（新会话首轮完成后）
                conversationId?.let { cid ->
                    if (pendingTitleConversationId == cid) {
                        pendingTitleConversationId = null
                        generateTitle(cid)
                    }
                }
                // 每次对话完成后自动归纳偏好（仅主 AI 页；自动模式才生效）
                profileStore.refreshAuto()
            }
        }
    }

    fun clearError() { _uiState.update { it.copy(error = null) } }

    private suspend fun refreshConversations() {
        val conversations = globalAiDao.getConversations().map(::toInfo)
        _uiState.update { it.copy(conversations = conversations) }
    }

    private fun toInfo(c: GlobalConversationEntity) = GlobalConversationInfo(c.id, c.title, c.updatedAt, c.pinned)

    private fun refreshGreeting(books: List<Book>, profile: AiProfile) {
        val recent = books.filter { it.lastReadAt > 0 }.take(4)
        _uiState.update {
            it.copy(
                greeting = buildGreeting(),
                suggestions = buildSuggestions(recent),
                useEmoji = profile.useEmoji,
            )
        }
    }

    /** 问候语：[UiMessage.Raw] 承载，因为它是从语言池里随机取的整句，无需参数化。 */
    private fun buildGreeting(): UiMessage {
        val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        return UiMessage.Raw(AiText.greetingPool(lang(), hour).random())
    }

    /**
     * 建议卡片。
     *
     * 不再在这里拼 emoji 后缀（改由界面按 [GlobalChatUiState.useEmoji] 渲染时追加），
     * 也不再把提示词写进 [SuggestionAction.AskBook]（改由界面点击时生成）。
     */
    private fun buildSuggestions(recent: List<Book>): List<Suggestion> {
        val lang = lang()
        val list = mutableListOf<Suggestion>()
        recent.firstOrNull()?.let { b ->
            list.add(Suggestion(UiMessage.Raw(AiText.suggestContinue(lang, b.title)), SuggestionAction.Continue(b.id)))
        }
        list.add(Suggestion(UiMessage.Raw(AiText.suggestReport(lang)), SuggestionAction.Report))
        list.add(Suggestion(UiMessage.Raw(AiText.suggestRecommend(lang)), SuggestionAction.Recommend))
        recent.firstOrNull()?.let { b ->
            list.add(Suggestion(UiMessage.Raw(AiText.suggestSummarize(lang, b.title)), SuggestionAction.AskBook(b.id)))
        }
        return list.shuffled().take(4)
    }

    // ── 意图路由（本地规则，命中才取本地数据注入） ──

    private suspend fun resolveIntentContext(text: String) {
        intentContext = null
        val t = text.trim()
        val lang = lang()
        val books = bookshelfRepo.observeAllBooks().first()

        // 1 统计/日报/周报
        if (AiText.intentStats(lang).containsMatchIn(t)) {
            intentContext = buildStatsContext(books)
            return
        }
        // 2 推荐
        if (AiText.intentRecommend(lang).containsMatchIn(t)) {
            intentContext = buildRecommendContext(books)
            return
        }
        // 3 具体书名（《》或整名包含）
        val quoted = Regex("《([^》]+)》").find(t)?.groupValues?.get(1)?.trim()
        val exact = books.firstOrNull { b ->
            quoted != null && (b.title == quoted || b.title.contains(quoted) || quoted.contains(b.title))
        } ?: books.firstOrNull { b -> b.title.isNotBlank() && t.contains(b.title) }
        if (exact != null) {
            intentContext = buildBookContext(exact)
            return
        }
        // 4 进度/接着读
        if (AiText.intentProgress(lang).containsMatchIn(t)) {
            books.filter { it.lastReadAt > 0 }.maxByOrNull { it.lastReadAt }?.let {
                intentContext = buildBookContext(it)
            }
            return
        }
        // 5 模糊书名（标题任意 2 字连续命中）
        books.firstOrNull { b ->
            b.title.length >= 2 && b.title.windowed(2).any { seg -> t.contains(seg) }
        }?.let { intentContext = buildBookContext(it) }
    }

    private suspend fun buildStatsContext(books: List<Book>): String {
        val weekStart = weekStartMillis()
        val sessions = readingRepo.observeSessionsSince(weekStart).first()
        fun duration(s: ReadingSession): Long =
            s.durationMs ?: s.endAt?.let { (it - s.startAt).coerceAtLeast(0) } ?: 0L
        val minutes = (sessions.sumOf(::duration) / 60000).toInt()
        val booksRead = sessions.map { it.bookId }.distinct().size
        val finished = books.filter { it.isFinished || it.progress >= 0.95f }.size
        val recentTitles = sessions.map { it.bookId }.distinct()
            .mapNotNull { id -> books.find { it.id == id }?.title }.take(6)
        return AiText.statsContext(lang(), minutes, booksRead, finished, recentTitles)
    }

    private fun buildRecommendContext(books: List<Book>): String {
        val lang = lang()
        return buildString {
            append(AiText.recommendContextHeader(lang))
            books.take(30).forEachIndexed { i, b ->
                val finished = b.isFinished || b.progress >= 0.95f
                append("\n").append(
                    AiText.bookLine(lang, i + 1, b.title, b.author ?: AiText.unknownAuthor(lang), (b.progress * 100).toInt(), finished),
                )
            }
        }
    }

    private fun buildBookContext(b: Book): String {
        val lang = lang()
        return buildString {
            append(AiText.bookContextHeader(lang, b.title))
            append(AiText.authorLabel(lang, b.author ?: AiText.unknownAuthor(lang)))
            append(AiText.progressLabel(lang, (b.progress * 100).toInt()))
            b.currentChapter?.takeIf { it.isNotBlank() }?.let { append(AiText.currentChapterLabel(lang, it.take(24))) }
            b.description?.takeIf { it.isNotBlank() }?.let { append(AiText.descriptionLabel(lang, it.take(200))) }
        }
    }

    /** 会话标题：新会话首轮完成后用 AI 生成 ≤12 字标题 */
    private suspend fun generateTitle(cid: String) {
        val msgs = globalAiDao.getMessages(cid)
        val firstUser = msgs.firstOrNull { it.role == "USER" }?.content?.take(200) ?: return
        val firstAssistant = msgs.firstOrNull { it.role == "ASSISTANT" }?.content?.take(200).orEmpty()
        val providers = fallbackChain.getEnabledProviders()
        if (providers.isEmpty()) return
        val lang = lang()
        val limit = AiText.titleLimit(lang)
        val prompt = AiText.titlePrompt(lang, firstUser, firstAssistant, limit)
        for (provider in providers) {
            if (provider.isDegraded) continue
            aiService.simpleChat(provider, listOf(AiMessage("user", prompt)))
                .onSuccess { raw ->
                    fallbackChain.recordSuccess(provider.id)
                    val t = raw.trim().replace(Regex("[\"“”‘’]"), "").take(limit)
                    if (t.isNotBlank()) globalAiDao.renameConversation(cid, t)
                    refreshConversations()
                    return
                }
                .onFailure { fallbackChain.recordFailure(provider.id) }
        }
    }

    private fun weekStartMillis(): Long {
        val cal = Calendar.getInstance()
        cal.set(Calendar.HOUR_OF_DAY, 0); cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0); cal.set(Calendar.MILLISECOND, 0)
        val dow = cal.get(Calendar.DAY_OF_WEEK)
        val diff = if (dow == Calendar.SUNDAY) -6 else Calendar.MONDAY - dow
        cal.add(Calendar.DAY_OF_YEAR, diff)
        return cal.timeInMillis
    }

    companion object {
        /**
         * 表情池。**由界面在渲染时**用于给问候语与建议卡追加后缀
         * （见 [GlobalChatUiState.useEmoji]）——放在界面层是为了避免把文案
         * 连同 emoji 一起固化成 String，那样切换语言后会陈旧。
         */
        val EMOJI_POOL = listOf("👋", "✨", "📚", "😊", "🌟", "📖")
    }
}
