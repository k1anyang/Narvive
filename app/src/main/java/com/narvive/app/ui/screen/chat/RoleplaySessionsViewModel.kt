package com.narvive.app.ui.screen.chat

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.narvive.app.R
import com.narvive.app.domain.model.RoleplaySession
import com.narvive.app.domain.repository.AiChatRepository
import com.narvive.app.domain.repository.BookshelfRepository
import com.narvive.app.service.ai.PromptLocaleProvider
import com.narvive.app.service.ai.observeLanguageChanges
import com.narvive.app.ui.message.UiMessage
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * 角色对话会话列表项。
 *
 * [preview] 用 [UiMessage]：最后一条消息是**数据**（[UiMessage.Raw]），
 * 没有消息时是本地化兜底（[UiMessage.Res]）。本项目切换语言不重建 Activity，
 * 存 String 会让兜底文案停留在旧语言。详见 docs/i18n.md §2.1。
 */
data class RoleplaySessionItem(
    val session: RoleplaySession,
    val preview: UiMessage,
)

data class RoleplaySessionsUiState(
    val bookTitle: String = "",
    val items: List<RoleplaySessionItem> = emptyList(),
    val loaded: Boolean = false,
)

@HiltViewModel
class RoleplaySessionsViewModel @Inject constructor(
    private val bookshelfRepo: BookshelfRepository,
    private val aiChatRepo: AiChatRepository,
    @ApplicationContext private val appContext: Context,
    private val localeProvider: PromptLocaleProvider,
) : ViewModel() {

    private val _uiState = MutableStateFlow(RoleplaySessionsUiState())
    val uiState: StateFlow<RoleplaySessionsUiState> = _uiState.asStateFlow()
    private var inited = false
    /** 供语言变化时重建列表项（切换语言不重建 Activity，ViewModel 会存活） */
    private var initBookId: String? = null

    fun init(bookId: String) {
        if (inited) return
        inited = true
        initBookId = bookId
        // 语言变化后重算「暂无对话」兜底文案：Room 的 Flow 不会因语言变化重新发射，
        // 且兜底文案需要「重新取值」，所以必须订阅语言流。
        observeLanguageChanges(localeProvider) { refreshLocalizedCopy() }
        viewModelScope.launch {
            val book = bookshelfRepo.getBook(bookId)
            aiChatRepo.observeRoleplaySessions(bookId).collect { sessions ->
                _uiState.value = RoleplaySessionsUiState(
                    bookTitle = book?.title ?: "",
                    items = sessions.map { toItem(it) },
                    loaded = true,
                )
            }
        }
    }

    /** 把会话映射为列表项：预览取最后一条消息，没有则用本地化兜底文案。 */
    private suspend fun toItem(s: RoleplaySession): RoleplaySessionItem {
        val last = aiChatRepo.getRoleplayMessages(s.id).lastOrNull()
        return RoleplaySessionItem(
            s,
            last?.content?.let { UiMessage.Raw(it) }
                ?: UiMessage.Res(R.string.ai_internal_empty_conversation),
        )
    }

    /**
     * 界面语言变化后重建列表项（由 [observeLanguageChanges] 驱动）。
     *
     * 只重建展示层映射，不改数据来源；[initBookId] 为空时（尚未 init）直接返回。
     */
    private suspend fun refreshLocalizedCopy() {
        val bookId = initBookId ?: return
        if (!_uiState.value.loaded) return // init 还没跑完，等 Room 首次发射即可
        val sessions = aiChatRepo.observeRoleplaySessions(bookId).first()
        _uiState.value = _uiState.value.copy(items = sessions.map { toItem(it) })
    }

    /** 删除角色会话（连同其全部消息，由 DB 级联清理） */
    fun deleteSession(id: String) {
        viewModelScope.launch { aiChatRepo.deleteRoleplaySession(id) }
    }
}
