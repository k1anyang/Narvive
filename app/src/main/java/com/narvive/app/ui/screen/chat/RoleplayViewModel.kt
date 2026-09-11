package com.narvive.app.ui.screen.chat

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.narvive.app.R
import com.narvive.app.ui.message.UiMessage
import com.narvive.app.ui.message.resolve
import com.narvive.app.domain.model.Annotation
import com.narvive.app.domain.model.AnnotationType
import com.narvive.app.domain.model.Book
import com.narvive.app.domain.model.CharacterCard
import com.narvive.app.domain.model.RoleplayMessage
import com.narvive.app.domain.repository.AiChatRepository
import com.narvive.app.domain.repository.AnnotationRepository
import com.narvive.app.domain.repository.BookshelfRepository
import com.narvive.app.service.ai.AiMessage
import com.narvive.app.service.ai.AiService
import com.narvive.app.service.ai.FallbackChain
import com.narvive.app.service.ai.PromptRenderer
import com.narvive.app.service.ai.PromptService
import com.narvive.app.service.ai.PromptTemplates
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

data class RoleplayUiState(
    val bookTitle: String = "",
    val sessionId: String = "",
    val characterName: String = "",
    val characterCard: CharacterCard? = null,
    /**
     * 实时章节标签（「与xx对话」下方副标题用，随阅读进度变化）。
     *
     * 取到章节标题时是**数据**（[UiMessage.Raw]），否则是本地化兜底（[UiMessage.Res]）。
     * 用 UiMessage 而非 String：本项目切换语言不重建 Activity，存 String 会陈旧。
     */
    val chapterLabel: UiMessage = UiMessage.Raw(""),
    /** 知识边界快照标签（章节名，仅抽取/刷新后随会话快照变化）。同上，用 UiMessage。 */
    val knowledgeLabel: UiMessage = UiMessage.Raw(""),
    val messages: List<AiMessage> = emptyList(),
    val isStreaming: Boolean = false,
    val notFound: Boolean = false,
    val error: String? = null,
    /** 已存为笔记的消息下标 */
    val savedMessageIndices: Set<Int> = emptySet(),
)

@HiltViewModel
class RoleplayViewModel @Inject constructor(
    private val aiService: AiService,
    private val fallbackChain: FallbackChain,
    private val promptService: PromptService,
    private val bookshelfRepo: BookshelfRepository,
    private val aiChatRepo: AiChatRepository,
    private val annotationRepo: AnnotationRepository,
    @ApplicationContext private val appContext: Context,
) : ViewModel() {

    private val _uiState = MutableStateFlow(RoleplayUiState())
    val uiState: StateFlow<RoleplayUiState> = _uiState.asStateFlow()

    /** 对话历史：system + 成功交换（错误不入历史） */
    private val history = mutableListOf<AiMessage>()
    /** 与 uiState.messages 平行的持久化消息（保存片段回写 savedAsAnnotationId 用，保留原 createdAt） */
    private val persistedMessages = mutableListOf<RoleplayMessage>()
    private var book: Book? = null
    private var inited = false

    fun init(bookId: String, sessionId: String?) {
        if (inited) return
        inited = true
        viewModelScope.launch {
            val b = bookshelfRepo.getBook(bookId) ?: run {
                _uiState.update { it.copy(notFound = true) }
                return@launch
            }
            book = b
            // 取到章节标题即数据（Raw），否则用资源兜底（Res）——后者会随语言变化
            val chapterLabel = b.currentChapter?.takeIf { it.isNotBlank() }
                ?.let { UiMessage.Raw(it) }
                ?: UiMessage.Res(R.string.roleplay_vm_not_started)

            val session = sessionId?.let { aiChatRepo.getRoleplaySession(it) }
            if (session == null) {
                _uiState.update { it.copy(notFound = true) }
                return@launch
            }

            val card = session.characterCard
            val knowledgeLabel = session.lastReadChapterTitle.ifBlank { "" }
                .takeIf { it.isNotBlank() }
                ?.let { UiMessage.Raw(it) }
                ?: UiMessage.Res(R.string.roleplay_vm_chapter_label, session.lastReadChapter + 1)
            _uiState.update {
                it.copy(
                    bookTitle = b.title,
                    sessionId = session.id,
                    characterName = card.name,
                    characterCard = card,
                    chapterLabel = chapterLabel,
                    knowledgeLabel = knowledgeLabel,
                )
            }

            // 发给模型的 knowledgeBoundary 需要**纯文本**，这里按当前语言解析一次
            // （提示词本身即按界面语言生成，故不存在陈旧问题）
            val knowledgeBoundaryText = knowledgeLabel.resolve(appContext)

            history.add(
                AiMessage(
                    "system",
                    PromptRenderer.render(
                        promptService.get(PromptTemplates.ROLEPLAY),
                        mapOf(
                            "bookTitle" to b.title,
                            "characterName" to card.name,
                            "identity" to card.identity,
                            "personality" to card.personality,
                            "tone" to card.tone,
                            "knowledgeBoundary" to card.knowledgeBoundary.ifBlank { knowledgeBoundaryText },
                        ),
                    ),
                )
            )

            // 载入历史消息
            val stored = aiChatRepo.getRoleplayMessages(session.id)
            stored.forEach { m ->
                val role = if (m.role == "USER") "user" else "assistant"
                history.add(AiMessage(role, m.content))
                persistedMessages.add(m)
            }
            _uiState.update { state ->
                state.copy(
                    messages = stored.map { AiMessage(if (it.role == "USER") "user" else "assistant", it.content) },
                    savedMessageIndices = stored.mapIndexedNotNull { i, m -> if (m.savedAsAnnotationId != null) i else null }.toSet(),
                )
            }
        }
    }

    fun sendMessage(text: String) {
        if (text.isBlank() || _uiState.value.isStreaming) return
        val sid = _uiState.value.sessionId.takeIf { it.isNotBlank() } ?: return
        history.add(AiMessage("user", text))
        val userMsg = RoleplayMessage(UUID.randomUUID().toString(), sid, "USER", text, null, System.currentTimeMillis())
        persistedMessages.add(userMsg)
        _uiState.update { it.copy(messages = it.messages + AiMessage("user", text), isStreaming = true, error = null) }

        viewModelScope.launch {
            aiChatRepo.addRoleplayMessage(userMsg)

            val providers = fallbackChain.getEnabledProviders()
            if (providers.isEmpty()) {
                _uiState.update { it.copy(isStreaming = false, error = appContext.getString(R.string.reader_vm_no_ai_provider)) }
                return@launch
            }
            for (provider in providers) {
                if (provider.isDegraded) continue
                _uiState.update { it.copy(messages = it.messages + AiMessage("assistant", "")) }
                try {
                    var finalContent = ""
                    aiService.streamChat(provider, history.toList())
                        .collect { resp ->
                            finalContent = resp.content
                            _uiState.update {
                                it.copy(
                                    messages = it.messages.dropLast(1) + AiMessage("assistant", resp.content),
                                    isStreaming = !resp.isComplete,
                                )
                            }
                        }
                    fallbackChain.recordSuccess(provider.id)
                    history.add(AiMessage("assistant", finalContent))
                    val charMsg = RoleplayMessage(UUID.randomUUID().toString(), sid, "CHARACTER", finalContent, null, System.currentTimeMillis())
                    persistedMessages.add(charMsg)
                    aiChatRepo.addRoleplayMessage(charMsg)
                    aiChatRepo.touchRoleplaySession(sid)
                    return@launch
                } catch (e: kotlinx.coroutines.CancellationException) {
                    throw e
                } catch (e: Exception) {
                    fallbackChain.recordFailure(provider.id)
                    _uiState.update { it.copy(messages = it.messages.dropLast(1)) }
                }
            }
            _uiState.update { it.copy(isStreaming = false, error = appContext.getString(R.string.roleplay_vm_all_providers_unavailable)) }
        }
    }

    /** 对话片段保存为笔记（ROLEPLAY 类型，紫色，带角色标签） */
    fun saveSnippetAsNote(messageIndex: Int) {
        val msg = _uiState.value.messages.getOrNull(messageIndex) ?: return
        if (msg.role != "assistant" || msg.content.isBlank()) return
        val b = book ?: return
        val character = _uiState.value.characterName
        val question = _uiState.value.messages.take(messageIndex).lastOrNull { it.role == "user" }?.content.orEmpty()
        val persisted = persistedMessages.getOrNull(messageIndex)
        viewModelScope.launch {
            val annotationId = UUID.randomUUID().toString()
            annotationRepo.insertOrUpdate(
                Annotation(
                    id = annotationId,
                    bookId = b.id,
                    locatorJson = b.currentLocator ?: "{}",
                    selectedText = question.take(200),
                    type = AnnotationType.ROLEPLAY,
                    color = null,
                    note = appContext.getString(R.string.ai_internal_roleplay_note, character, msg.content),
                    translation = null,
                    rewrittenText = null,
                    rewriteInstruction = null,
                    providerId = null,
                    createdAt = System.currentTimeMillis(),
                    updatedAt = System.currentTimeMillis(),
                )
            )
            if (persisted != null) {
                aiChatRepo.addRoleplayMessage(persisted.copy(savedAsAnnotationId = annotationId))
                persistedMessages[messageIndex] = persisted.copy(savedAsAnnotationId = annotationId)
            }
            _uiState.update { it.copy(savedMessageIndices = it.savedMessageIndices + messageIndex) }
        }
    }

    fun clearError() { _uiState.update { it.copy(error = null) } }
}
