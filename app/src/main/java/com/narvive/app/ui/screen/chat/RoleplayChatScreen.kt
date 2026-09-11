package com.narvive.app.ui.screen.chat

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.PushPin
import androidx.compose.material.icons.rounded.SwapHoriz
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.narvive.app.R
import com.narvive.app.service.ai.AiMessage
import com.narvive.app.ui.components.StreamingCursor
import com.narvive.app.ui.theme.NarviveShape

/**
 * 角色聊天室（原型屏 roleplay-chat）：IM 风格，角色衬线气泡 + 头像，
 * 顶部知识边界 chip，快捷指令 chips，长按角色气泡保存片段为笔记。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RoleplayChatScreen(
    bookId: String,
    sessionId: String?,
    onBackClick: () -> Unit,
    onEditCard: (characterName: String, sessionId: String) -> Unit = { _, _ -> },
    viewModel: RoleplayViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    var inputText by remember { mutableStateOf("") }
    val listState = rememberLazyListState()
    val characterFallback = stringResource(R.string.chat_role_fallback)
    val quickPrompts = roleplayQuickPrompts()

    LaunchedEffect(bookId, sessionId) { viewModel.init(bookId, sessionId) }
    var lastMessageCount by remember { mutableStateOf(0) }
    LaunchedEffect(uiState.messages.size) {
        val lastIdx = uiState.messages.lastIndex
        if (lastIdx >= 0) {
            // 首次进入：瞬时定位到底部，避免“从顶部动画拖到底”的闪动；后续新增消息才平滑跟随
            if (lastMessageCount == 0) listState.scrollToItem(lastIdx)
            else listState.animateScrollToItem(lastIdx)
        }
        lastMessageCount = uiState.messages.size
    }
    // 会话丢失兜底：直接返回
    LaunchedEffect(uiState.notFound) { if (uiState.notFound) onBackClick() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            stringResource(R.string.chat_roleplay_title_with_name, uiState.characterName.ifEmpty { characterFallback }),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                        )
                        Text(
                            "${uiState.bookTitle} · ${uiState.chapterLabel}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
                navigationIcon = { IconButton(onClick = onBackClick) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, stringResource(R.string.chat_action_back)) } },
                actions = {
                    // 切换角色 = 返回会话列表
                    IconButton(onClick = onBackClick) { Icon(Icons.Rounded.SwapHoriz, stringResource(R.string.chat_action_switch_character)) }
                    IconButton(onClick = { onEditCard(uiState.characterName, uiState.sessionId) }) {
                        Icon(Icons.Rounded.Edit, stringResource(R.string.chat_action_edit_card))
                    }
                },
            )
        },
        bottomBar = {
            Column(Modifier.imePadding()) {
                // 快捷指令（原型：换话题/让我想想/推进剧情/聊聊这本书）
                LazyRow(
                    Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    items(quickPrompts.size) { i ->
                        Surface(
                            shape = NarviveShape.Md,
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                            modifier = Modifier.height(26.dp).clickable { inputText = quickPrompts[i] },
                        ) {
                            Box(Modifier.padding(horizontal = 10.dp), contentAlignment = Alignment.Center) {
                                Text(quickPrompts[i], style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
                Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = inputText,
                        onValueChange = { inputText = it },
                        modifier = Modifier.weight(1f),
                        placeholder = { Text(stringResource(R.string.chat_roleplay_input_placeholder, uiState.characterName.ifEmpty { characterFallback })) },
                        singleLine = false,
                        maxLines = 4,
                    )
                    Spacer(Modifier.width(8.dp))
                    Box(
                        Modifier.size(42.dp).clip(CircleShape)
                            .background(if (inputText.isNotBlank() && !uiState.isStreaming) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant)
                            .clickable(enabled = inputText.isNotBlank() && !uiState.isStreaming) {
                                viewModel.sendMessage(inputText.trim()); inputText = ""
                            },
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            Icons.AutoMirrored.Rounded.Send, stringResource(R.string.chat_action_send),
                            tint = if (inputText.isNotBlank() && !uiState.isStreaming) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                }
            }
        },
    ) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp),
            state = listState,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // 顶部知识边界 chip（原型：🎭 角色扮演 · X知晓截至第N回的剧情）
            item {
                Spacer(Modifier.height(2.dp))
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Surface(shape = NarviveShape.Md, color = MaterialTheme.colorScheme.primaryContainer) {
                        Text(
                            stringResource(R.string.chat_roleplay_knowledge_chip, uiState.characterName, uiState.knowledgeLabel),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp),
                        )
                    }
                }
            }
            items(uiState.messages.size) { index ->
                val msg = uiState.messages[index]
                RoleplayBubble(
                    message = msg,
                    characterName = uiState.characterName,
                    saved = index in uiState.savedMessageIndices,
                    isStreamingTail = uiState.isStreaming && index == uiState.messages.lastIndex && msg.role == "assistant",
                    onSaveSnippet = { viewModel.saveSnippetAsNote(index) },
                )
            }
            if (uiState.isStreaming && uiState.messages.lastOrNull()?.role != "assistant") {
                item {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(start = 8.dp)) {
                        CircularProgressIndicator(Modifier.size(14.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.width(6.dp))
                        Text(stringResource(R.string.chat_role_thinking), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            uiState.error?.let { err ->
                item {
                    Text(
                        err,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.clickable(onClick = viewModel::clearError).padding(4.dp),
                    )
                }
            }
            item { Spacer(Modifier.height(8.dp)) }
        }
    }
}

@Composable
private fun roleplayQuickPrompts(): List<String> = listOf(
    stringResource(R.string.chat_roleplay_prompt_change_topic),
    stringResource(R.string.chat_roleplay_prompt_let_me_think),
    stringResource(R.string.chat_roleplay_prompt_advance_plot),
    stringResource(R.string.chat_roleplay_prompt_about_book),
)

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun RoleplayBubble(
    message: AiMessage,
    characterName: String,
    saved: Boolean,
    isStreamingTail: Boolean,
    onSaveSnippet: () -> Unit,
) {
    val isUser = message.role == "user"
    Column(Modifier.fillMaxWidth(), horizontalAlignment = if (isUser) Alignment.End else Alignment.Start) {
        if (!isUser) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = 4.dp, start = 4.dp)) {
                RoleplayAvatar(characterName, size = 28.dp)
                Spacer(Modifier.width(8.dp))
                Text(characterName, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Card(
            shape = NarviveShape.Md,
            colors = CardDefaults.cardColors(
                containerColor = if (isUser) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface,
            ),
            modifier = Modifier
                .widthIn(max = 320.dp)
                .combinedClickable(
                    onClick = {},
                    onLongClick = { if (!isUser && message.content.isNotBlank()) onSaveSnippet() },
                ),
        ) {
            if (!isUser && isStreamingTail) {
                Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(message.content, color = MaterialTheme.colorScheme.onSurface, style = MaterialTheme.typography.bodyMedium, lineHeight = 22.sp)
                    StreamingCursor(MaterialTheme.colorScheme.onSurface)
                }
            } else {
                Text(
                    message.content,
                    modifier = Modifier.padding(12.dp),
                    color = if (isUser) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                    style = MaterialTheme.typography.bodyMedium,
                    lineHeight = if (isUser) 20.sp else 22.sp,
                )
            }
        }
        if (!isUser && saved) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(start = 8.dp, top = 2.dp)) {
                Icon(Icons.Rounded.PushPin, null, Modifier.size(11.dp), tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(3.dp))
                Text(stringResource(R.string.chat_saved_as_note), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
            }
        }
    }
}
