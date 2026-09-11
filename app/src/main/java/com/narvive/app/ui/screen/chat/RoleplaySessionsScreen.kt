package com.narvive.app.ui.screen.chat

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.narvive.app.R
import com.narvive.app.ui.components.SwipeToDeleteRow
import com.narvive.app.ui.theme.NarviveShape
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** 角色会话列表（B4.8，原型屏39）：多会话卡片 + 知晓至第N章 + 新建 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RoleplaySessionsScreen(
    bookId: String,
    onBackClick: () -> Unit,
    onOpenSession: (sessionId: String) -> Unit,
    onCreateSession: (characterName: String) -> Unit,
    viewModel: RoleplaySessionsViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    var showCreateDialog by remember { mutableStateOf(false) }
    var deleteTarget by remember { mutableStateOf<RoleplaySessionItem?>(null) }

    LaunchedEffect(bookId) { viewModel.init(bookId) }

    deleteTarget?.let { item ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text(stringResource(R.string.chat_delete_roleplay_title)) },
            text = { Text(stringResource(R.string.chat_delete_roleplay_body, item.session.characterName)) },
            confirmButton = {
                TextButton(onClick = { viewModel.deleteSession(item.session.id); deleteTarget = null }) {
                    Text(stringResource(R.string.chat_action_delete), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { deleteTarget = null }) { Text(stringResource(R.string.chat_action_cancel)) } },
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column(horizontalAlignment = Alignment.Start, modifier = Modifier.fillMaxWidth()) {
                        Text(stringResource(R.string.chat_roleplay_sessions_title), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        if (uiState.bookTitle.isNotBlank()) {
                            Text(
                                stringResource(R.string.chat_sessions_count, uiState.bookTitle, uiState.items.size),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                },
                navigationIcon = { IconButton(onClick = onBackClick) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, stringResource(R.string.chat_action_back)) } },
            )
        },
    ) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding).padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item { Spacer(Modifier.height(4.dp)) }
            items(uiState.items, key = { it.session.id }) { item ->
                SwipeToDeleteRow(onDelete = { deleteTarget = item }) {
                    SessionCard(item, onClick = { onOpenSession(item.session.id) })
                }
            }
            item {
                OutlinedButton(
                    onClick = { showCreateDialog = true },
                    modifier = Modifier.fillMaxWidth().height(46.dp),
                ) {
                    Icon(Icons.Rounded.Add, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(R.string.chat_new_roleplay_session))
                }
            }
            item {
                Card(
                    Modifier.fillMaxWidth().padding(vertical = 6.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)),
                ) {
                    Text(
                        stringResource(R.string.chat_roleplay_hints),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        lineHeight = 18.sp,
                        modifier = Modifier.padding(14.dp),
                    )
                }
            }
            item { Spacer(Modifier.height(12.dp)) }
        }
    }

    if (showCreateDialog) {
        var name by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showCreateDialog = false },
            title = { Text(stringResource(R.string.chat_new_roleplay_session)) },
            text = {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    placeholder = { Text(stringResource(R.string.chat_character_name_placeholder)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            },
            confirmButton = {
                TextButton(
                    onClick = { if (name.isNotBlank()) { showCreateDialog = false; onCreateSession(name.trim()) } },
                    enabled = name.isNotBlank(),
                ) { Text(stringResource(R.string.chat_extract_character_card)) }
            },
            dismissButton = {
                TextButton(onClick = { showCreateDialog = false }) { Text(stringResource(R.string.chat_action_cancel)) }
            },
        )
    }
}

@Composable
private fun SessionCard(item: RoleplaySessionItem, onClick: () -> Unit) {
    val chapterNumberLabel = stringResource(R.string.chat_chapter_number, item.session.lastReadChapter + 1)
    Card(
        Modifier.fillMaxWidth().clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            RoleplayAvatar(item.session.characterName, size = 44.dp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(item.session.characterName, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.width(8.dp))
                    Surface(shape = NarviveShape.Md, color = MaterialTheme.colorScheme.primaryContainer) {
                        Text(
                            stringResource(R.string.chat_knowledge_until, item.session.lastReadChapterTitle.ifBlank { chapterNumberLabel }),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                        )
                    }
                }
                Spacer(Modifier.height(3.dp))
                Text(
                    item.preview,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.width(8.dp))
            Text(
                relativeTime(item.session.updatedAt),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
            )
        }
    }
}

@Composable
private fun relativeTime(ts: Long): String {
    val diff = System.currentTimeMillis() - ts
    val hours = diff / 3_600_000
    return when {
        hours < 1 -> stringResource(R.string.chat_time_just_now)
        hours < 24 -> SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(ts))
        hours < 48 -> stringResource(R.string.chat_time_yesterday)
        hours < 24 * 7 -> stringResource(R.string.chat_time_days_ago, hours / 24)
        else -> SimpleDateFormat("MM/dd", Locale.getDefault()).format(Date(ts))
    }
}
