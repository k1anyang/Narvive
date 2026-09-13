package com.narvive.app.ui.screen.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.PushPin
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.narvive.app.R
import com.narvive.app.service.ai.AiMessage
import com.narvive.app.ui.components.StreamingCursor
import com.narvive.app.ui.theme.NarviveShape

/**
 * AI 对话共享内容：上下文 chips + 发送范围说明条 + 快捷指令 + 消息流 + 输入行。
 * 供阅读器半屏/全屏 BottomSheet 与书籍详情 CHAT 路由页复用（B4.1/4.2/4.3/4.5）。
 */
@Composable
fun ChatContent(
    uiState: ChatUiState,
    onScopeChange: (ChatContextScope) -> Unit,
    onSend: (String) -> Unit,
    onQuickCommand: (QuickCommand) -> Unit,
    onSaveAsNote: (Int) -> Unit,
    onRegenerate: () -> Unit,
    onDismissError: () -> Unit,
    onOpenAiSettings: () -> Unit,
    modifier: Modifier = Modifier,
    /** 简化模式（书籍详情 AI 面板）：上下文固定全书、隐藏快捷指令栏 */
    simplified: Boolean = false,
) {
    var inputText by remember { mutableStateOf("") }
    val listState = rememberLazyListState()
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

    Column(modifier = modifier) {
        if (!uiState.isConfigured) {
            AiOnboardingCard(onOpenAiSettings, Modifier.fillMaxWidth().weight(1f))
            return@Column
        }

        // ---------- 上下文范围 chips（原型屏10/15） ----------
        if (simplified) {
            LaunchedEffect(Unit) { onScopeChange(ChatContextScope.BOOK) }
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(stringResource(R.string.chat_context_whole_book), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(stringResource(R.string.chat_context_label), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                uiState.selectionText?.let { sel ->
                    MiniChip(
                        text = stringResource(R.string.chat_context_selection_chars, sel.length),
                        selected = uiState.contextScope == ChatContextScope.SELECTION,
                        onClick = { onScopeChange(ChatContextScope.SELECTION) },
                    )
                }
                MiniChip(
                    text = stringResource(R.string.chat_context_chapter),
                    selected = uiState.contextScope == ChatContextScope.CHAPTER,
                    onClick = { onScopeChange(ChatContextScope.CHAPTER) },
                )
                MiniChip(
                    text = stringResource(R.string.chat_context_book),
                    selected = uiState.contextScope == ChatContextScope.BOOK,
                    onClick = { onScopeChange(ChatContextScope.BOOK) },
                )
            }
        }

        // ---------- 发送范围说明条（B4.2） ----------
        Surface(
            shape = NarviveShape.Sm,
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
        ) {
            Row(Modifier.padding(horizontal = 8.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.Info, null, Modifier.size(14.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.width(6.dp))
                Text(
                    sendScopeText(uiState),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        // ---------- 快捷指令 chips（B4.5，原型屏15；简化模式隐藏） ----------
        if (!simplified) {
            LazyRow(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 2.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                items(QuickCommand.entries.size) { i ->
                    val cmd = QuickCommand.entries[i]
                    MiniChip(
                        text = stringResource(cmd.labelRes),
                        selected = false,
                        enabled = quickCommandEnabled(cmd, uiState),
                        onClick = { onQuickCommand(cmd) },
                    )
                }
            }
        }

        // ---------- 错误条（B4.11：错误不入对话历史，仅横幅提示） ----------
        uiState.error?.let { err ->
            Surface(
                shape = NarviveShape.Sm,
                color = MaterialTheme.colorScheme.errorContainer,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp).clickable(onClick = onDismissError),
            ) {
                Text(
                    err,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                )
            }
        }

        // ---------- 检索降级提示（横幅，与错误条同区，点击可收起） ----------
        if (uiState.retrievalDegraded) {
            Surface(
                shape = NarviveShape.Sm,
                color = MaterialTheme.colorScheme.tertiaryContainer,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp).clickable(onClick = onDismissError),
            ) {
                Text(
                    stringResource(R.string.chat_vm_retrieval_degraded),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onTertiaryContainer,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                )
            }
        }

        // ---------- 消息流 ----------
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxWidth().weight(1f).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item { Spacer(Modifier.height(2.dp)) }
            itemsIndexed(uiState.messages.filter { it.role != "system" }) { _, msg ->
                // 下标映射回原始 messages（saveAsNote 用原始下标）
                val rawIndex = uiState.messages.indexOfFirst { it === msg }
                ChatBubbleRow(
                    message = msg,
                    isStreamingTail = uiState.isStreaming && rawIndex == uiState.messages.lastIndex,
                    saved = rawIndex in uiState.savedMessageIndices,
                    isLastAssistant = msg.role == "assistant" && rawIndex == uiState.messages.indexOfLast { it.role == "assistant" },
                    onSaveAsNote = { onSaveAsNote(rawIndex) },
                    onRegenerate = onRegenerate,
                )
            }
            if (uiState.isStreaming && uiState.messages.lastOrNull()?.role != "assistant") {
                item {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(start = 8.dp)) {
                        CircularProgressIndicator(Modifier.size(14.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.width(6.dp))
                        // 长章节会先做本地检索/压缩再发请求，此前只有「正在思考」会让用户以为卡住
                        Text(
                            stringResource(
                                when (uiState.retrievalStage) {
                                    RetrievalStage.SELECTING -> R.string.chat_vm_retrieving
                                    RetrievalStage.CONDENSING -> R.string.chat_vm_condensing
                                    RetrievalStage.NONE -> R.string.chat_thinking
                                },
                            ),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            item { Spacer(Modifier.height(4.dp)) }
        }

        // ---------- 输入行 ----------
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OutlinedTextField(
                value = inputText,
                onValueChange = { inputText = it },
                modifier = Modifier.weight(1f),
                placeholder = { Text(stringResource(R.string.chat_input_placeholder)) },
                singleLine = false,
                maxLines = 4,
            )
            Spacer(Modifier.width(8.dp))
            Box(
                Modifier.size(42.dp).clip(CircleShape)
                    .background(if (inputText.isNotBlank() && !uiState.isStreaming) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant)
                    .clickable(enabled = inputText.isNotBlank() && !uiState.isStreaming) {
                        onSend(inputText.trim()); inputText = ""
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
}

@Composable
private fun sendScopeText(uiState: ChatUiState): String = when (uiState.contextScope) {
    ChatContextScope.SELECTION ->
        stringResource(R.string.chat_send_scope_selection, uiState.selectionText?.length ?: 0)
    ChatContextScope.CHAPTER -> {
        val title = uiState.chapterTitle
        val titlePart = if (title != null) stringResource(R.string.chat_send_scope_chapter_title, title) else ""
        val charsPart = if (uiState.chapterChars > 0) stringResource(R.string.chat_send_scope_chapter_chars, uiState.chapterChars) else ""
        stringResource(R.string.chat_send_scope_chapter, titlePart, charsPart)
    }
    ChatContextScope.BOOK ->
        stringResource(R.string.chat_send_scope_book)
}

/** 快捷指令可用性：选区类指令需有选区，总结本章需有章上下文，角色对话始终可用 */
private fun quickCommandEnabled(cmd: QuickCommand, uiState: ChatUiState): Boolean = when (cmd) {
    QuickCommand.EXPLAIN, QuickCommand.TRANSLATE, QuickCommand.VOCAB,
    QuickCommand.REWRITE, QuickCommand.CONTINUE -> uiState.selectionText != null
    QuickCommand.SUMMARIZE -> uiState.chapterChars > 0
    QuickCommand.ROLEPLAY -> true
    QuickCommand.RELATIONSHIP_GRAPH, QuickCommand.TIMELINE -> uiState.contextScope != ChatContextScope.SELECTION
}

@Composable
private fun MiniChip(text: String, selected: Boolean, enabled: Boolean = true, onClick: () -> Unit) {
    val contentAlpha = if (enabled) 1f else 0.38f
    Surface(
        shape = NarviveShape.Md,
        color = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
        ),
        modifier = Modifier.height(24.dp).clickable(enabled = enabled, onClick = onClick),
    ) {
        Box(Modifier.padding(horizontal = 8.dp), contentAlignment = Alignment.Center) {
            Text(
                text,
                style = MaterialTheme.typography.labelSmall,
                color = (if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant).copy(alpha = contentAlpha),
            )
        }
    }
}

@Composable
private fun ChatBubbleRow(
    message: AiMessage,
    isStreamingTail: Boolean,
    saved: Boolean,
    isLastAssistant: Boolean,
    onSaveAsNote: () -> Unit,
    onRegenerate: () -> Unit,
) {
    val clipboard = LocalClipboardManager.current
    val isUser = message.role == "user"

    Column(Modifier.fillMaxWidth(), horizontalAlignment = if (isUser) Alignment.End else Alignment.Start) {
        Row(verticalAlignment = Alignment.Top) {
            if (!isUser) {
                Box(Modifier.size(28.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary), contentAlignment = Alignment.Center) {
                    Icon(Icons.Rounded.AutoAwesome, null, tint = Color.White, modifier = Modifier.size(15.dp))
                }
                Spacer(Modifier.width(8.dp))
            }
            Card(
                shape = NarviveShape.Md,
                colors = CardDefaults.cardColors(
                    containerColor = if (isUser) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                ),
                modifier = Modifier.widthIn(max = 320.dp),
            ) {
                if (!isUser && isStreamingTail) {
                    Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(message.content, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
                        StreamingCursor(MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                } else {
                    Text(
                        message.content,
                        modifier = Modifier.padding(12.dp),
                        color = if (isUser) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
        }
        // ---------- AI 回答操作行（B4.3，原型屏10/15） ----------
        if (!isUser && message.content.isNotBlank() && !isStreamingTail) {
            Row(Modifier.padding(start = 36.dp, top = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                ActionItem(
                    icon = if (saved) Icons.Rounded.Check else Icons.Rounded.PushPin,
                    label = if (saved) stringResource(R.string.chat_action_saved) else stringResource(R.string.chat_action_save_as_note),
                    enabled = !saved,
                    onClick = onSaveAsNote,
                )
                Spacer(Modifier.width(12.dp))
                ActionItem(Icons.Rounded.ContentCopy, stringResource(R.string.chat_action_copy), enabled = true) {
                    clipboard.setText(AnnotatedString(message.content))
                }
                if (isLastAssistant) {
                    Spacer(Modifier.width(12.dp))
                    ActionItem(Icons.Rounded.Refresh, stringResource(R.string.chat_action_regenerate), enabled = true, onClick = onRegenerate)
                }
            }
        }
    }
}

@Composable
private fun ActionItem(icon: ImageVector, label: String, enabled: Boolean, onClick: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.clickable(enabled = enabled, onClick = onClick).padding(vertical = 4.dp),
    ) {
        Icon(
            icon, null, Modifier.size(13.dp),
            tint = if (enabled) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.primary,
        )
        Spacer(Modifier.width(3.dp))
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = if (enabled) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.primary,
        )
    }
}

/** 未配置引导卡（B4.4，原型屏32：BYOK 说明 + 平台链接 + 去设置按钮） */
@Composable
private fun AiOnboardingCard(onOpenAiSettings: () -> Unit, modifier: Modifier = Modifier) {
    val uriHandler = LocalUriHandler.current
    val platforms = listOf(
        Triple("DeepSeek", stringResource(R.string.chat_onboarding_platform_deepseek_desc), "https://platform.deepseek.com"),
        Triple("OpenAI", "platform.openai.com", "https://platform.openai.com"),
        Triple("Gemini", stringResource(R.string.chat_onboarding_platform_gemini_desc), "https://aistudio.google.com"),
    )
    LazyColumn(modifier.padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item { Spacer(Modifier.height(4.dp)) }
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.AutoAwesome, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(22.dp))
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.chat_onboarding_title), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            }
        }
        item {
            Text(
                stringResource(R.string.chat_onboarding_body),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                lineHeight = 18.sp,
            )
        }
        item {
            Text(
                stringResource(R.string.chat_onboarding_pick_platform),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
        items(platforms.size) { i ->
            val (name, desc, url) = platforms[i]
            Surface(
                shape = NarviveShape.Md,
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                modifier = Modifier.fillMaxWidth().clickable { uriHandler.openUri(url) },
            ) {
                Row(Modifier.padding(horizontal = 12.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(name, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
                        Text(desc, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Text(stringResource(R.string.chat_onboarding_get_key), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
                }
            }
        }
        item {
            Button(onClick = onOpenAiSettings, modifier = Modifier.fillMaxWidth().height(46.dp)) {
                Text(stringResource(R.string.chat_onboarding_go_settings))
            }
        }
        item {
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                Text(
                    stringResource(R.string.chat_onboarding_skip_hint),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        item { Spacer(Modifier.height(8.dp)) }
    }
}
