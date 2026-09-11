package com.narvive.app.ui.screen.chat

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.narvive.app.R
import com.narvive.app.domain.model.Book
import com.narvive.app.domain.model.CharacterCard
import com.narvive.app.domain.model.RoleplaySession
import com.narvive.app.domain.repository.AiChatRepository
import com.narvive.app.domain.repository.BookshelfRepository
import com.narvive.app.service.ai.CharacterCardExtractor
import com.narvive.app.service.reader.TocLoader
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.util.UUID
import javax.inject.Inject

data class CharacterCardUiState(
    val bookTitle: String = "",
    val characterName: String = "",
    val chapterLabel: String = "",
    val card: CharacterCard? = null,
    val isExtracting: Boolean = false,
    /** true=编辑已有会话的角色卡；false=新建（AI 抽取） */
    val editMode: Boolean = false,
    val error: String? = null,
    /** 「开始对话」成功后置位，UI 据此跳转聊天室 */
    val startedSessionId: String? = null,
    /** 初始数据加载完成（避免进入时先渲染空表单再回填的闪动） */
    val loaded: Boolean = false,
)

@HiltViewModel
class CharacterCardViewModel @Inject constructor(
    private val bookshelfRepo: BookshelfRepository,
    private val aiChatRepo: AiChatRepository,
    private val extractor: CharacterCardExtractor,
    private val tocLoader: TocLoader,
    @ApplicationContext private val appContext: Context,
) : ViewModel() {

    private val _uiState = MutableStateFlow(CharacterCardUiState())
    val uiState: StateFlow<CharacterCardUiState> = _uiState.asStateFlow()

    private var book: Book? = null
    private var existingSession: RoleplaySession? = null
    private var chapterIndex = 0
    /** 快照章节名（currentChapter 标题，抽取/刷新/保存时回写会话） */
    private var chapterTitle = ""
    private var inited = false

    fun init(bookId: String, characterName: String, sessionId: String?) {
        if (inited) return
        inited = true
        viewModelScope.launch {
            val b = bookshelfRepo.getBook(bookId) ?: return@launch
            book = b
            chapterIndex = resolveChapterIndex(b)
            val chapterLabel = b.currentChapter?.takeIf { it.isNotBlank() }
                ?: appContext.getString(R.string.ai_internal_chapter_label, chapterIndex + 1)
            chapterTitle = chapterLabel

            val session = sessionId?.takeIf { it.isNotBlank() }?.let { aiChatRepo.getRoleplaySession(it) }
            if (session != null) {
                existingSession = session
                _uiState.update {
                    it.copy(
                        bookTitle = b.title,
                        characterName = session.characterName,
                        chapterLabel = chapterLabel,
                        card = session.characterCard,
                        editMode = true,
                        loaded = true,
                    )
                }
            } else {
                _uiState.update {
                    it.copy(
                        bookTitle = b.title,
                        characterName = characterName,
                        chapterLabel = chapterLabel,
                        editMode = false,
                        loaded = true,
                    )
                }
                extract(characterName)
            }
        }
    }

    /** 解析当前章索引：TXT 用 locator.chapter；EPUB 按目录 href 匹配（修复「知晓至第x章」显示） */
    private suspend fun resolveChapterIndex(b: Book): Int {
        val locator = b.currentLocator ?: return 0
        val obj = runCatching { JSONObject(locator) }.getOrNull() ?: return 0
        if (obj.has("chapter")) return obj.optInt("chapter", 0)
        val href = obj.optString("href").substringBefore('#').takeIf { it.isNotBlank() } ?: return 0
        val toc = tocLoader.load(b.format, b.filePath)
        val idx = toc.indexOfFirst { it.locatorJson.contains("\"href\":\"$href\"") }
        return if (idx >= 0) idx else 0
    }

    /** AI 抽取 / 刷新角色认知（B4.7）：刷新前重读最新进度，成功后回写会话章节 */
    fun extract(name: String = _uiState.value.characterName) {
        val b = book ?: return
        if (name.isBlank()) return
        viewModelScope.launch {
            _uiState.update { it.copy(isExtracting = true, error = null) }
            // 重读最新进度：阅读页可能已推进，刷新不能再用进入卡片时的旧章节
            val latestBook = bookshelfRepo.getBook(b.id) ?: b
            book = latestBook
            chapterIndex = resolveChapterIndex(latestBook)
            val chapterLabel = latestBook.currentChapter?.takeIf { it.isNotBlank() }
                ?: appContext.getString(R.string.ai_internal_chapter_label, chapterIndex + 1)
            chapterTitle = chapterLabel
            val card = extractor.extract(latestBook, name, chapterLabel, chapterIndex)
            _uiState.update {
                it.copy(
                    chapterLabel = chapterLabel,
                    card = card,
                    characterName = name,
                    isExtracting = false,
                    error = if (card == null) appContext.getString(R.string.ai_internal_card_extract_failed) else null,
                )
            }
            // 刷新成功后回写会话最新已读章节，列表「知晓至第N章」及时更新
            if (card != null) {
                existingSession?.let { s ->
                    aiChatRepo.saveRoleplaySession(
                        s.copy(characterCard = card, lastReadChapter = chapterIndex, lastReadChapterTitle = chapterTitle, updatedAt = System.currentTimeMillis())
                    )
                }
            }
        }
    }

    fun updateCard(card: CharacterCard) { _uiState.update { it.copy(card = card) } }

    fun clearError() { _uiState.update { it.copy(error = null) } }

    /** 保存角色卡并进入对话：新建会话或覆盖已有会话 */
    fun startChat() {
        val b = book ?: return
        val card = _uiState.value.card ?: return
        viewModelScope.launch {
            val now = System.currentTimeMillis()
            val existing = existingSession
            val session = if (existing != null) {
                existing.copy(characterName = card.name, characterCard = card, lastReadChapter = chapterIndex, lastReadChapterTitle = chapterTitle, updatedAt = now)
            } else {
                RoleplaySession(
                    id = UUID.randomUUID().toString(),
                    bookId = b.id,
                    characterName = card.name,
                    characterCard = card,
                    lastReadChapter = chapterIndex,
                    lastReadChapterTitle = chapterTitle,
                    createdAt = now,
                    updatedAt = now,
                )
            }
            aiChatRepo.saveRoleplaySession(session)
            _uiState.update { it.copy(startedSessionId = session.id) }
        }
    }
}
