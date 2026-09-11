package com.narvive.app.ui.screen.chat

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.narvive.app.R
import com.narvive.app.domain.model.RoleplaySession
import com.narvive.app.domain.repository.AiChatRepository
import com.narvive.app.domain.repository.BookshelfRepository
import com.narvive.app.ui.message.UiMessage
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
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
) : ViewModel() {

    private val _uiState = MutableStateFlow(RoleplaySessionsUiState())
    val uiState: StateFlow<RoleplaySessionsUiState> = _uiState.asStateFlow()
    private var inited = false

    fun init(bookId: String) {
        if (inited) return
        inited = true
        viewModelScope.launch {
            val book = bookshelfRepo.getBook(bookId)
            aiChatRepo.observeRoleplaySessions(bookId).collect { sessions ->
                val items = sessions.map { s ->
                    val last = aiChatRepo.getRoleplayMessages(s.id).lastOrNull()
                    RoleplaySessionItem(
                        s,
                        last?.content?.let { UiMessage.Raw(it) }
                            ?: UiMessage.Res(R.string.ai_internal_empty_conversation),
                    )
                }
                _uiState.value = RoleplaySessionsUiState(
                    bookTitle = book?.title ?: "",
                    items = items,
                    loaded = true,
                )
            }
        }
    }

    /** 删除角色会话（连同其全部消息，由 DB 级联清理） */
    fun deleteSession(id: String) {
        viewModelScope.launch { aiChatRepo.deleteRoleplaySession(id) }
    }
}
