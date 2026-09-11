package com.narvive.app.ui.screen.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.PushPin
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.narvive.app.R
import com.narvive.app.domain.model.Book
import com.narvive.app.service.ai.AiMessage
import com.narvive.app.ui.components.TabPageScaffold
import com.narvive.app.ui.components.StreamingCursor
import com.narvive.app.ui.message.text
import com.narvive.app.ui.screen.library.BookCoverImage
import com.narvive.app.ui.theme.NarviveShape
import com.narvive.app.ui.theme.SemanticColors

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun GlobalChatScreen(
    onOpenAiSettings: () -> Unit,
    onOpenBookDetail: (String) -> Unit = {},
    onOpenBookAt: (String, String?) -> Unit = { _, _ -> },
    viewModel: GlobalChatViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    var showHistory by remember { mutableStateOf(false) }
    var showBookPicker by remember { mutableStateOf(false) }
    var deleteTarget by remember { mutableStateOf<GlobalConversationInfo?>(null) }
    var renameTarget by remember { mutableStateOf<GlobalConversationInfo?>(null) }
    var renameText by remember { mutableStateOf("") }
    var inputText by remember { mutableStateOf("") }
    var recentCollapsed by remember { mutableStateOf(true) }
    val listState = rememberLazyListState()
    val clipboard = LocalClipboardManager.current
    val untitledLabel = stringResource(R.string.chat_untitled_conversation)
    val readingReportPrompt = stringResource(R.string.chat_prompt_reading_report)
    val recommendPrompt = stringResource(R.string.chat_prompt_recommend)
    // 模板形式（含 %1$s 书名占位符），点击时用书名现拼 —— 见 handleSuggestion
    val summarizeTemplate = stringResource(R.string.chat_prompt_summarize_book)

    LaunchedEffect(Unit) { viewModel.init() }
    var lastMessageCount by remember { mutableStateOf(0) }
    LaunchedEffect(uiState.messages.size) {
        val lastIdx = uiState.messages.lastIndex
        if (lastIdx >= 0) {
            if (lastMessageCount == 0) listState.scrollToItem(lastIdx)
            else listState.animateScrollToItem(lastIdx)
        }
        lastMessageCount = uiState.messages.size
    }

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

    renameTarget?.let { conv ->
        AlertDialog(
            onDismissRequest = { renameTarget = null },
            title = { Text(stringResource(R.string.chat_rename_conversation_title)) },
            text = { OutlinedTextField(renameText, { renameText = it }, singleLine = true, modifier = Modifier.fillMaxWidth()) },
            confirmButton = {
                TextButton(onClick = { viewModel.renameConversation(conv.id, renameText); renameTarget = null }, enabled = renameText.isNotBlank()) { Text(stringResource(R.string.chat_action_save)) }
            },
            dismissButton = { TextButton(onClick = { renameTarget = null }) { Text(stringResource(R.string.chat_action_cancel)) } },
        )
    }

    if (showHistory) {
        HistorySheet(
            uiState = uiState,
            onLoad = { viewModel.loadConversation(it); showHistory = false },
            onNew = { viewModel.startNewConversation(); showHistory = false },
            onTogglePin = viewModel::togglePin,
            onRename = { renameText = it.title; renameTarget = it },
            onDelete = { deleteTarget = it },
            onDismiss = { showHistory = false },
        )
    }

    TabPageScaffold(
        title = "AI",
        actions = {
            if (uiState.isConfigured) {
                IconButton(onClick = { showHistory = true }) { Icon(Icons.Rounded.History, stringResource(R.string.chat_conversation_history)) }
            }
        },
    ) { padding ->
        if (!uiState.isConfigured) {
            GlobalAiOnboarding(
                onOpenAiSettings,
                Modifier.fillMaxSize().padding(padding).imePadding(),
            )
        } else {
            Column(Modifier.fillMaxSize().padding(padding).imePadding()) {
                uiState.error?.let { err ->
                    Surface(
                        shape = NarviveShape.Sm,
                        color = MaterialTheme.colorScheme.errorContainer,
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp).clickable(onClick = viewModel::clearError),
                    ) {
                        Text(
                            err,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        )
                    }
                }

                // 空会话：个性化问候 + 建议卡片
                if (uiState.messages.isEmpty()) {
                    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
                        // 表情后缀在渲染时追加（偏好开启时才加），避免把文案固化成 String
                        val greetingText = withEmoji(uiState.greeting.text(), uiState.useEmoji)
                        if (greetingText.isNotBlank()) {
                            Text(greetingText, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        }
                        if (uiState.suggestions.isNotEmpty()) {
                            Spacer(Modifier.height(8.dp))
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                uiState.suggestions.forEach { s ->
                                    SuggestionCard(s, uiState.useEmoji) {
                                        handleSuggestion(s, uiState, viewModel, onOpenBookAt, readingReportPrompt, recommendPrompt, summarizeTemplate)
                                    }
                                }
                            }
                        }
                    }
                }

                // 最近在读（默认收纳，点卡片直达书籍详情）
                val recent = uiState.books.filter { it.lastReadAt > 0 }.take(4)
                if (recent.isNotEmpty()) {
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)
                            .clickable { recentCollapsed = !recentCollapsed },
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(stringResource(R.string.chat_recently_read), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
                        Icon(
                            if (recentCollapsed) Icons.Rounded.KeyboardArrowDown else Icons.Rounded.KeyboardArrowUp,
                            contentDescription = if (recentCollapsed) stringResource(R.string.chat_expand) else stringResource(R.string.chat_collapse),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                    if (!recentCollapsed) {
                        LazyRow(
                            Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            contentPadding = PaddingValues(vertical = 4.dp),
                        ) {
                            items(recent, key = { it.id }) { book ->
                                RecentBookCard(book) { onOpenBookDetail(book.id) }
                            }
                        }
                    }
                }

                // 上下文条（@选书 + 标签 + 隐私提示）
                ContextBar(
                    uiState = uiState,
                    onOpenPicker = { showBookPicker = true },
                    onRemoveBook = viewModel::toggleBookContext,
                    onRemoveAllBooks = { viewModel.setAllBooksContext(false) },
                )

                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxWidth().weight(1f).padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    item { Spacer(Modifier.height(2.dp)) }
                    itemsIndexed(uiState.messages.filter { it.role != "system" }) { _, msg ->
                        val rawIndex = uiState.messages.indexOfFirst { it === msg }
                        GlobalChatBubble(
                            message = msg,
                            isStreamingTail = uiState.isStreaming && rawIndex == uiState.messages.lastIndex,
                            isLastAssistant = msg.role == "assistant" && rawIndex == uiState.messages.indexOfLast { it.role == "assistant" },
                            onCopy = { clipboard.setText(AnnotatedString(msg.content)) },
                            onRegenerate = viewModel::regenerate,
                        )
                    }
                    item { Spacer(Modifier.height(4.dp)) }
                }

                // 选书弹层（内联，不抢焦点、不收起键盘）
                if (showBookPicker) {
                    BookPickerOverlay(
                        books = uiState.books,
                        selectedIds = uiState.contextBookIds,
                        allBooks = uiState.allBooksContext,
                        onToggleBook = viewModel::toggleBookContext,
                        onToggleAllBooks = viewModel::setAllBooksContext,
                        onClear = viewModel::clearContext,
                        onDone = { showBookPicker = false },
                    )
                }

                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    OutlinedTextField(
                        value = inputText,
                        onValueChange = { it ->
                            if (it.endsWith("@")) {
                                inputText = it.dropLast(1)
                                showBookPicker = true
                            } else {
                                inputText = it
                            }
                        },
                        modifier = Modifier.weight(1f),
                        placeholder = { Text(stringResource(R.string.chat_global_input_placeholder)) },
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
        }
    }
}

/**
 * 建议卡片的点击处理。
 *
 * 提示词（日报 / 推荐 / 总结某书）**在这里按当前语言现取**，而不是从 ViewModel 里
 * 拿一个拼好的字符串 —— 后者会在语言切换后停留旧语言。
 */
private fun handleSuggestion(
    s: Suggestion,
    uiState: GlobalChatUiState,
    viewModel: GlobalChatViewModel,
    onOpenBookAt: (String, String?) -> Unit,
    reportPrompt: String,
    recommendPrompt: String,
    summarizeTemplate: String,
) {
    when (val a = s.action) {
        is SuggestionAction.Report -> viewModel.sendMessage(reportPrompt)
        is SuggestionAction.Recommend -> viewModel.sendMessage(recommendPrompt)
        is SuggestionAction.Continue -> {
            val locator = uiState.books.find { it.id == a.bookId }?.currentLocator
            onOpenBookAt(a.bookId, locator)
        }
        is SuggestionAction.AskBook -> {
            val title = uiState.books.find { it.id == a.bookId }?.title ?: ""
            viewModel.clearContext()
            viewModel.toggleBookContext(a.bookId)
            viewModel.sendMessage(String.format(summarizeTemplate, title))
        }
    }
}

/** 按偏好追加表情后缀。仅在渲染时调用，不写入任何状态。 */
private fun withEmoji(text: String, useEmoji: Boolean): String =
    if (useEmoji && text.isNotBlank()) "$text ${GlobalChatViewModel.EMOJI_POOL.random()}" else text

@Composable
private fun SuggestionCard(s: Suggestion, useEmoji: Boolean, onClick: () -> Unit) {
    // text() 是 @Composable，必须在 composable 作用域解析后再交给 remember；
    // 表情在首次组合时随机取一次并记住 —— 若每次重组都 random，标签会不停跳动。
    val rawLabel = s.label.text()
    val label = remember(s, useEmoji) { withEmoji(rawLabel, useEmoji) }
    Surface(
        shape = NarviveShape.Md,
        color = MaterialTheme.colorScheme.surface,
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier.clickable(onClick = onClick),
    ) {
        Row(Modifier.padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.AutoAwesome, null, Modifier.size(14.dp), tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(6.dp))
            Text(label, style = MaterialTheme.typography.labelMedium)
        }
    }
}

@Composable
private fun ContextBar(
    uiState: GlobalChatUiState,
    onOpenPicker: () -> Unit,
    onRemoveBook: (String) -> Unit,
    onRemoveAllBooks: () -> Unit,
) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Surface(
                shape = NarviveShape.Md,
                color = MaterialTheme.colorScheme.primaryContainer,
                modifier = Modifier.clickable(onClick = onOpenPicker),
            ) {
                Text(stringResource(R.string.chat_context_pick_book), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp))
            }
            Spacer(Modifier.width(8.dp))
            if (uiState.allBooksContext) {
                ContextTag(stringResource(R.string.chat_all_library), onRemove = onRemoveAllBooks)
            }
            uiState.contextBookIds.forEach { id ->
                val title = uiState.books.find { it.id == id }?.title ?: id
                ContextTag(stringResource(R.string.chat_book_title_brackets, title), onRemove = { onRemoveBook(id) })
            }
            if (!uiState.allBooksContext && uiState.contextBookIds.isEmpty()) {
                Text(stringResource(R.string.chat_context_general), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Spacer(Modifier.height(4.dp))
        Text(
            if (uiState.allBooksContext) stringResource(R.string.chat_global_scope_all_books)
            else stringResource(R.string.chat_global_scope_metadata),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun ContextTag(text: String, onRemove: () -> Unit) {
    Surface(
        shape = NarviveShape.Md,
        color = MaterialTheme.colorScheme.primaryContainer,
        modifier = Modifier.padding(end = 6.dp),
    ) {
        Row(Modifier.padding(start = 10.dp, top = 5.dp, bottom = 5.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(text, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Icon(Icons.Rounded.Close, stringResource(R.string.chat_action_remove), tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(14.dp).clickable(onClick = onRemove))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HistorySheet(
    uiState: GlobalChatUiState,
    onLoad: (String) -> Unit,
    onNew: () -> Unit,
    onTogglePin: (String) -> Unit,
    onRename: (GlobalConversationInfo) -> Unit,
    onDelete: (GlobalConversationInfo) -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState()) {
        val untitledLabel = stringResource(R.string.chat_untitled_conversation)
        Column(Modifier.padding(bottom = 24.dp)) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.chat_conversation_history), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                TextButton(onClick = onNew) {
                    Icon(Icons.Rounded.Add, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(stringResource(R.string.chat_new_conversation))
                }
            }
            Spacer(Modifier.height(8.dp))
            if (uiState.conversations.isEmpty()) {
                Text(stringResource(R.string.chat_history_empty_conversations), Modifier.padding(horizontal = 20.dp, vertical = 16.dp), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                LazyColumn(Modifier.heightIn(max = 420.dp)) {
                    items(uiState.conversations, key = { it.id }) { conv ->
                        Row(
                            Modifier.fillMaxWidth().clickable { onLoad(conv.id) }.padding(horizontal = 20.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(
                                    conv.title.ifBlank { untitledLabel },
                                    style = MaterialTheme.typography.bodyLarge,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    color = if (conv.id == uiState.activeConversationId) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                                )
                                Text(relativeTime(conv.updatedAt), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Box(
                                Modifier.size(32.dp).clip(CircleShape).clickable { onTogglePin(conv.id) },
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(Icons.Rounded.PushPin, stringResource(R.string.chat_action_pin), tint = if (conv.pinned) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
                            }
                            Box(
                                Modifier.size(32.dp).clip(CircleShape).clickable { onRename(conv) },
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(Icons.Rounded.Edit, stringResource(R.string.chat_action_rename), tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
                            }
                            Box(
                                Modifier.size(32.dp).clip(CircleShape).background(SemanticColors.Danger).clickable { onDelete(conv) },
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

@Composable
private fun BookPickerOverlay(
    books: List<Book>,
    selectedIds: List<String>,
    allBooks: Boolean,
    onToggleBook: (String) -> Unit,
    onToggleAllBooks: (Boolean) -> Unit,
    onClear: () -> Unit,
    onDone: () -> Unit,
) {
    Surface(
        shape = NarviveShape.Md,
        color = MaterialTheme.colorScheme.surfaceVariant,
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp).padding(bottom = 4.dp),
    ) {
        Column {
            Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.chat_pick_context_books), style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                TextButton(onClick = onClear) { Text(stringResource(R.string.chat_action_clear)) }
                TextButton(onClick = onDone) { Text(stringResource(R.string.chat_action_done)) }
            }
            Row(
                Modifier.fillMaxWidth().clickable { onToggleAllBooks(!allBooks) }.padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(stringResource(R.string.chat_all_library), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                if (allBooks) Icon(Icons.Rounded.Check, null, tint = MaterialTheme.colorScheme.primary)
            }
            HorizontalDivider()
            LazyColumn(Modifier.fillMaxWidth().heightIn(max = 200.dp)) {
                items(books, key = { it.id }) { book ->
                    Row(
                        Modifier.fillMaxWidth().clickable { onToggleBook(book.id) }.padding(horizontal = 16.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(stringResource(R.string.chat_book_title_brackets, book.title), style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                        if (book.id in selectedIds) Icon(Icons.Rounded.Check, null, tint = MaterialTheme.colorScheme.primary)
                    }
                    HorizontalDivider()
                }
            }
        }
    }
}

@Composable
private fun RecentBookCard(book: Book, onClick: () -> Unit) {
    Column(
        Modifier.width(64.dp).clip(NarviveShape.Sm).clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        BookCoverImage(book, Modifier.size(48.dp, 64.dp).clip(NarviveShape.Xs))
        Spacer(Modifier.height(4.dp))
        Text(book.title, style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center)
    }
}

@Composable
private fun GlobalChatBubble(
    message: AiMessage,
    isStreamingTail: Boolean,
    isLastAssistant: Boolean,
    onCopy: () -> Unit,
    onRegenerate: () -> Unit,
) {
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
        if (!isUser && message.content.isNotBlank() && !isStreamingTail) {
            Row(Modifier.padding(start = 36.dp, top = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                BubbleAction(Icons.Rounded.ContentCopy, stringResource(R.string.chat_action_copy), onCopy)
                if (isLastAssistant) {
                    Spacer(Modifier.width(12.dp))
                    BubbleAction(Icons.Rounded.Refresh, stringResource(R.string.chat_action_regenerate), onRegenerate)
                }
            }
        }
    }
}

@Composable
private fun BubbleAction(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, onClick: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.clickable(onClick = onClick).padding(vertical = 4.dp),
    ) {
        Icon(icon, null, Modifier.size(13.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.width(3.dp))
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/**
 * 全局 AI 的未配置引导（空状态）。
 *
 * 排版要点（与 docs/DESIGN.md §12 空状态规范对齐）：
 * - 外层 Box 负责整体居中，内层 Column **左对齐**：多行文本有共同左基准才易扫读，
 *   居中文本在 3 行以上时每行起点都不同，是公认的可读性陷阱。
 * - `widthIn(max = 360.dp)` 限制正文行宽：不限宽时英文单行可达 300dp（40+ 字符）
 *   并贴到屏幕边缘，中文因显式换行只有两行，同一套布局在两种语言下形态完全不同。
 * - 主按钮 `fillMaxWidth` 占满列宽，成为唯一主行动点（§3：同屏 primary 不超过 1 个）。
 */
@Composable
private fun GlobalAiOnboarding(onOpenAiSettings: () -> Unit, modifier: Modifier = Modifier) {
    Box(modifier, contentAlignment = Alignment.Center) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 360.dp)
                .padding(horizontal = 32.dp),
        ) {
            Icon(
                Icons.Rounded.AutoAwesome,
                null,
                Modifier.size(48.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
            )
            Spacer(Modifier.height(16.dp))
            Text(
                stringResource(R.string.chat_no_provider_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                stringResource(R.string.chat_global_onboarding_body),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                lineHeight = 21.sp,
            )
            Spacer(Modifier.height(24.dp))
            Button(onClick = onOpenAiSettings, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.chat_global_onboarding_settings))
            }
        }
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
