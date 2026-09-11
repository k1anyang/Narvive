package com.narvive.app.ui.screen.chat

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.narvive.app.R
import com.narvive.app.ui.message.text
import com.narvive.app.ui.theme.NarviveShape

/**
 * 角色卡预览/编辑（原型屏 character-card）：AI 自动抽取四字段，可微调，
 * 「开始对话」保存会话并进入聊天室。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CharacterCardScreen(
    bookId: String,
    characterName: String,
    sessionId: String?,
    onBackClick: () -> Unit,
    onStartChat: (sessionId: String) -> Unit,
    viewModel: CharacterCardViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()

    LaunchedEffect(bookId, characterName, sessionId) { viewModel.init(bookId, characterName, sessionId) }
    LaunchedEffect(uiState.startedSessionId) {
        uiState.startedSessionId?.let(onStartChat)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (uiState.loaded) stringResource(R.string.chat_character_card_with_name, uiState.characterName) else stringResource(R.string.chat_character_card_title), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold) },
                navigationIcon = { IconButton(onClick = onBackClick) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, stringResource(R.string.chat_action_back)) } },
            )
        },
    ) { padding ->
        if (!uiState.loaded) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
            }
        } else {
            Column(
                Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp),
            ) {
                Spacer(Modifier.height(12.dp))

            // ---------- 头部卡：头像 + 名称 + 书·章节 + 抽取标识 ----------
            Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                Column(Modifier.fillMaxWidth().padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    RoleplayAvatar(uiState.characterName, size = 72.dp)
                    Spacer(Modifier.height(12.dp))
                    Text(uiState.characterName, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Text(
                        "${uiState.bookTitle} · ${uiState.chapterLabel.text()}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(10.dp))
                    Surface(shape = NarviveShape.Md, color = MaterialTheme.colorScheme.primaryContainer) {
                        Text(
                            stringResource(R.string.chat_character_card_auto_badge),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp),
                        )
                    }
                }
            }

            Spacer(Modifier.height(16.dp))

            if (uiState.isExtracting) {
                Row(Modifier.fillMaxWidth().padding(vertical = 24.dp), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.chat_extracting_character), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }

            uiState.error?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                Spacer(Modifier.height(8.dp))
            }

            // ---------- 四字段 ----------
            val card = uiState.card
            FieldCard(stringResource(R.string.chat_field_identity), card?.identity ?: "", uiState.isExtracting) { v ->
                card?.let { viewModel.updateCard(it.copy(identity = v)) }
            }
            FieldCard(stringResource(R.string.chat_field_personality), card?.personality ?: "", uiState.isExtracting) { v ->
                card?.let { viewModel.updateCard(it.copy(personality = v)) }
            }
            FieldCard(stringResource(R.string.chat_field_tone), card?.tone ?: "", uiState.isExtracting) { v ->
                card?.let { viewModel.updateCard(it.copy(tone = v)) }
            }
            Card(Modifier.fillMaxWidth().padding(bottom = 12.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                Column(Modifier.padding(16.dp)) {
                    Text(stringResource(R.string.chat_field_knowledge_boundary), style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(6.dp))
                    val defaultBoundary = stringResource(R.string.chat_knowledge_boundary_default, uiState.chapterLabel.text())
                    Surface(shape = NarviveShape.Sm, color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)) {
                        Text(
                            card?.knowledgeBoundary?.ifBlank { defaultBoundary } ?: "",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            lineHeight = 18.sp,
                            modifier = Modifier.padding(10.dp),
                        )
                    }
                }
            }

            // ---------- 说明卡 ----------
            Card(
                Modifier.fillMaxWidth().padding(bottom = 16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)),
            ) {
                Row(Modifier.padding(14.dp)) {
                    Icon(Icons.Rounded.Info, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(
                        stringResource(R.string.chat_character_card_hint),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        lineHeight = 17.sp,
                    )
                }
            }

            OutlinedButton(
                onClick = { viewModel.extract() },
                enabled = !uiState.isExtracting,
                modifier = Modifier.fillMaxWidth().height(42.dp),
            ) {
                Icon(Icons.Rounded.Refresh, null, Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text(stringResource(R.string.chat_refresh_character_knowledge))
            }
            Spacer(Modifier.height(10.dp))
            Button(
                onClick = viewModel::startChat,
                enabled = uiState.card != null && !uiState.isExtracting,
                modifier = Modifier.fillMaxWidth().height(48.dp),
            ) { Text(stringResource(if (uiState.editMode) R.string.chat_continue_conversation else R.string.chat_start_conversation)) }
                Spacer(Modifier.height(24.dp))
            }
        }
    }
}

@Composable
private fun FieldCard(label: String, value: String, disabled: Boolean, onChange: (String) -> Unit) {
    Card(Modifier.fillMaxWidth().padding(bottom = 12.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(Modifier.padding(16.dp)) {
            Text(label, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(6.dp))
            OutlinedTextField(
                value = value,
                onValueChange = onChange,
                enabled = !disabled,
                modifier = Modifier.fillMaxWidth(),
                textStyle = MaterialTheme.typography.bodySmall,
            )
        }
    }
}
