package com.narvive.app.ui.screen.chat

import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.narvive.app.R
import com.narvive.app.domain.repository.AiConversationInfo
import com.narvive.app.ui.theme.NarviveShape
import com.narvive.app.ui.theme.SemanticColors

/**
 * AI 对话全屏路由页（书籍详情「✦ 问 AI」入口）。
 * 阅读器内为半屏 BottomSheet（ReaderScreen 内嵌），本页复用同一 ChatContent。
 * H8：顶栏 More → 历史会话列表（切换/新对话）。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    bookId: String,
    onBackClick: () -> Unit,
    onOpenRewrite: (text: String, mode: String) -> Unit = { _, _ -> },
    onOpenRoleplay: () -> Unit = {},
    onOpenAiSettings: () -> Unit = {},
    viewModel: ChatViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    var moreOpen by remember { mutableStateOf(false) }
    var showHistorySheet by remember { mutableStateOf(false) }
    var deleteTarget by remember { mutableStateOf<AiConversationInfo?>(null) }
    val untitledLabel = stringResource(R.string.chat_untitled_conversation)

    LaunchedEffect(bookId) { viewModel.init(bookId) }

    deleteTarget?.let { conv ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text(stringResource(R.string.chat_delete_conversation_title)) },
            text = { Text(stringResource(R.string.chat_delete_conversation_body, conv.title.ifBlank { untitledLabel })) },
            confirmButton = {
                TextButton(onClick = { viewModel.deleteConversation(conv.id); deleteTarget = null }) {
                    Text(stringResource(R.string.chat_action_delete), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { deleteTarget = null }) { Text(stringResource(R.string.chat_action_cancel)) } },
        )
    }

    // 历史会话 BottomSheet（H8）
    if (showHistorySheet) {
        ModalBottomSheet(
            onDismissRequest = { showHistorySheet = false },
            sheetState = rememberModalBottomSheetState(),
        ) {
            Column(Modifier.padding(bottom = 24.dp)) {
                Text(
                    stringResource(R.string.chat_history_title),
                    Modifier.padding(horizontal = 20.dp),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(Modifier.height(8.dp))
                // 新对话入口置顶
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clickable { viewModel.startNewConversation(); showHistorySheet = false }
                        .padding(horizontal = 20.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Rounded.Add, null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(12.dp))
                    Text(stringResource(R.string.chat_new_conversation), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Medium)
                }
                HorizontalDivider(Modifier.padding(horizontal = 20.dp))
                if (uiState.conversations.isEmpty()) {
                    Text(
                        stringResource(R.string.chat_history_empty),
                        Modifier.padding(horizontal = 20.dp, vertical = 20.dp),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    LazyColumn(Modifier.heightIn(max = 420.dp)) {
                        items(uiState.conversations, key = { it.id }) { conv ->
                            val active = conv.id == uiState.activeConversationId
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .clickable { viewModel.loadConversation(conv.id); showHistorySheet = false }
                                    .padding(horizontal = 20.dp, vertical = 14.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Column(Modifier.weight(1f)) {
                                    Text(
                                        conv.title.ifBlank { untitledLabel },
                                        style = MaterialTheme.typography.bodyLarge,
                                        fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal,
                                        color = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                    Text(
                                        relativeTime(conv.updatedAt),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                                if (active) {
                                    Text(stringResource(R.string.chat_current), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                                }
                                Spacer(Modifier.width(8.dp))
                                Box(
                                    Modifier.size(32.dp).clip(CircleShape).background(SemanticColors.Danger)
                                        .clickable { deleteTarget = conv },
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Icon(Icons.Rounded.Delete, stringResource(R.string.chat_action_delete), tint = Color.White, modifier = Modifier.size(18.dp))
                                }
                            }
                            HorizontalDivider(Modifier.padding(horizontal = 20.dp))
                        }
                    }
                }
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.AutoAwesome, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.height(20.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(stringResource(uiState.titleRes), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        if (uiState.providerName.isNotBlank()) {
                            Spacer(Modifier.width(8.dp))
                            Surface(
                                shape = NarviveShape.Md,
                                color = MaterialTheme.colorScheme.primaryContainer,
                            ) {
                                Text(
                                    uiState.providerName,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                                )
                            }
                        }
                    }
                },
                navigationIcon = { IconButton(onClick = onBackClick) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, stringResource(R.string.chat_action_back)) } },
                actions = {
                    androidx.compose.foundation.layout.Box {
                        IconButton(onClick = { moreOpen = true }) { Icon(Icons.Rounded.MoreVert, stringResource(R.string.chat_action_more)) }
                        DropdownMenu(expanded = moreOpen, onDismissRequest = { moreOpen = false }) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.chat_history_title)) },
                                leadingIcon = { Icon(Icons.Rounded.History, null) },
                                onClick = { moreOpen = false; showHistorySheet = true },
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.chat_new_conversation)) },
                                leadingIcon = { Icon(Icons.Rounded.Add, null) },
                                onClick = { moreOpen = false; viewModel.startNewConversation() },
                            )
                        }
                    }
                },
            )
        },
    ) { padding ->
        ChatContent(
            uiState = uiState,
            onScopeChange = viewModel::setScope,
            onSend = viewModel::sendMessage,
            onQuickCommand = { cmd ->
                when (cmd) {
                    QuickCommand.REWRITE -> onOpenRewrite(uiState.selectionText ?: "", "rewrite")
                    QuickCommand.CONTINUE -> onOpenRewrite(uiState.selectionText ?: "", "continue")
                    QuickCommand.ROLEPLAY -> onOpenRoleplay()
                    else -> viewModel.sendQuickCommand(cmd)
                }
            },
            onSaveAsNote = viewModel::saveAsNote,
            onRegenerate = viewModel::regenerate,
            onDismissError = viewModel::clearError,
            onOpenAiSettings = onOpenAiSettings,
            onStartBookIndex = viewModel::startBookIndexing,
            onCancelBookIndex = viewModel::cancelBookIndexing,
            modifier = Modifier.fillMaxSize().padding(padding).imePadding(),
            simplified = true,
        )
    }
}

@Composable
private fun relativeTime(millis: Long): String {
    val diff = System.currentTimeMillis() - millis
    val minutes = diff / 60_000
    val hours = diff / 3_600_000
    val days = diff / 86_400_000
    return when {
        minutes < 1 -> stringResource(R.string.chat_time_just_now)
        minutes < 60 -> stringResource(R.string.chat_time_minutes_ago, minutes)
        hours < 24 -> stringResource(R.string.chat_time_hours_ago, hours)
        days < 30 -> stringResource(R.string.chat_time_days_ago, days)
        else -> java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault()).format(java.util.Date(millis))
    }
}
