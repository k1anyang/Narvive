package com.narvive.app.ui.screen.chat

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.narvive.app.R
import com.narvive.app.domain.model.RoleplaySession
import com.narvive.app.domain.repository.AiChatRepository
import com.narvive.app.domain.repository.BookshelfRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class RoleplaySessionItem(
    val session: RoleplaySession,
    val preview: String,
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
                _uiState.value = RoleplaySessionsUiState(
                    bookTitle = book?.title ?: "",
                    items = sessions.map { s ->
                        val last = aiChatRepo.getRoleplayMessages(s.id).lastOrNull()
                        RoleplaySessionItem(s, last?.content ?: appContext.getString(R.string.ai_internal_empty_conversation))
                    },
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
