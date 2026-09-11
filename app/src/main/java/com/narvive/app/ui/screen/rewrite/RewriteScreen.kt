package com.narvive.app.ui.screen.rewrite

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.narvive.app.R
import com.narvive.app.ui.components.EightSegmentLoading
import com.narvive.app.ui.theme.NarviveShape
import com.narvive.app.ui.theme.SemanticColors
import kotlin.math.roundToInt

/**
 * 改写/续写（B4.9）：指令输入阶段对齐原型屏33（预设指令+自定义+发送范围说明），
 * 结果阶段对齐原型「改写对比」屏（原文/改写对比 + 指令卡 + 切换原文/存为笔记/导出/删除）。
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun RewriteScreen(
    bookId: String,
    originalText: String,
    mode: String, // "rewrite" or "continue"
    onBackClick: () -> Unit,
    viewModel: RewriteViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    val isRewrite = mode == "rewrite"
    val kindLabel = if (isRewrite) stringResource(R.string.chat_mode_rewrite) else stringResource(R.string.chat_mode_continue)
    var instruction by rememberSaveable { mutableStateOf("") }
    var showOriginal by rememberSaveable { mutableStateOf(true) }
    val context = LocalContext.current

    LaunchedEffect(bookId, originalText) { viewModel.init(bookId, originalText, mode) }

    val presets = if (isRewrite) {
        listOf(
            stringResource(R.string.chat_rewrite_preset_inner_psychology),
            stringResource(R.string.chat_rewrite_preset_first_person),
            stringResource(R.string.chat_rewrite_preset_more_literary),
            stringResource(R.string.chat_rewrite_preset_tragic_ending),
        )
    } else {
        listOf(
            stringResource(R.string.chat_continue_preset_natural),
            stringResource(R.string.chat_continue_preset_conflict),
            stringResource(R.string.chat_continue_preset_new_character),
            stringResource(R.string.chat_rewrite_preset_tragic_ending),
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (uiState.resultText != null) stringResource(R.string.chat_rewrite_compare_title, kindLabel) else kindLabel) },
                navigationIcon = { IconButton(onClick = onBackClick) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, stringResource(R.string.chat_action_back)) } },
                actions = {
                    if (uiState.resultText != null) {
                        IconButton(onClick = {
                            val clipboard = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                            clipboard.setPrimaryClip(android.content.ClipData.newPlainText("rewrite", uiState.resultText))
                            Toast.makeText(context, context.getString(R.string.chat_toast_copied), Toast.LENGTH_SHORT).show()
                        }) { Icon(Icons.Rounded.ContentCopy, stringResource(R.string.chat_action_copy)) }
                    }
                },
            )
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp)) {

            if (uiState.resultText == null) {
                // ---------- 指令输入阶段（原型屏33） ----------
                Spacer(Modifier.height(12.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        stringResource(if (isRewrite) R.string.chat_rewrite_selected_paragraph else R.string.chat_continue_selected_paragraph),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.weight(1f),
                    )
                    Surface(
                        shape = NarviveShape.Md,
                        color = MaterialTheme.colorScheme.primaryContainer,
                    ) {
                        Text(
                            stringResource(R.string.chat_selection_chars, originalText.length),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp),
                        )
                    }
                }
                Spacer(Modifier.height(12.dp))

                // 选区原文卡（衬线）
                if (uiState.chapterTitle.isNotBlank()) {
                    Text(uiState.chapterTitle, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(6.dp))
                }
                Text(
                    originalText,
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f), NarviveShape.Md)
                        .padding(14.dp),
                    style = MaterialTheme.typography.bodyMedium.copy(lineHeight = MaterialTheme.typography.bodyMedium.lineHeight * 1.3),
                )
                Spacer(Modifier.height(16.dp))

                // 预设指令
                Text(stringResource(R.string.chat_presets_label), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(8.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    presets.forEach { preset ->
                        FilterChip(
                            selected = instruction == preset,
                            onClick = { instruction = preset },
                            label = { Text(preset, style = MaterialTheme.typography.labelMedium) },
                        )
                    }
                }
                Spacer(Modifier.height(4.dp))

                OutlinedTextField(
                    value = instruction,
                    onValueChange = { instruction = it },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text(stringResource(if (isRewrite) R.string.chat_rewrite_custom_placeholder else R.string.chat_continue_custom_placeholder)) },
                    singleLine = false,
                    maxLines = 3,
                )
                Spacer(Modifier.height(14.dp))

                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedButton(
                        onClick = onBackClick,
                        modifier = Modifier.weight(1f).height(44.dp),
                    ) { Text(stringResource(R.string.chat_action_cancel)) }
                    Button(
                        onClick = { viewModel.execute(instruction) },
                        modifier = Modifier.weight(2f).height(44.dp),
                        enabled = instruction.isNotBlank() && !uiState.isLoading,
                    ) {
                        if (uiState.isLoading) {
                            EightSegmentLoading(Modifier.size(18.dp), color = MaterialTheme.colorScheme.onPrimary)
                            Spacer(Modifier.width(6.dp))
                            Text(stringResource(if (isRewrite) R.string.chat_generate_rewrite else R.string.chat_generate_continue))
                        } else {
                            Text(stringResource(if (isRewrite) R.string.chat_generate_rewrite else R.string.chat_generate_continue))
                        }
                    }
                }
                Spacer(Modifier.height(12.dp))

                // 发送范围说明（原型：「本次将发送：选区 42 字 + 本章前文摘要（约 1.2k token）」）
                Text(
                    sendScopeText(originalText.length, uiState.chapterContextChars),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
            } else {
                // ---------- 对比阶段（原型「改写对比」屏） ----------
                Spacer(Modifier.height(12.dp))
                if (showOriginal) {
                    val currentChapterLabel = stringResource(R.string.chat_current_chapter)
                    Text(
                        stringResource(R.string.chat_original_with_chapter, uiState.chapterTitle.ifBlank { currentChapterLabel }),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        originalText,
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f), NarviveShape.Md)
                            .padding(14.dp),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    // 分隔带 chip：✍️ 改写 · {指令}
                    Spacer(Modifier.height(16.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        val instructionPreview = uiState.instructionUsed?.let { if (it.length > 10) it.take(10) + "…" else it } ?: ""
                        HorizontalDivider(Modifier.weight(1f))
                        Surface(
                            shape = NarviveShape.Md,
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                            modifier = Modifier.padding(horizontal = 8.dp).border(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.3f), NarviveShape.Md),
                        ) {
                            Text(
                                stringResource(R.string.chat_rewrite_instruction_chip, kindLabel, instructionPreview),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp),
                            )
                        }
                        HorizontalDivider(Modifier.weight(1f))
                    }
                    Spacer(Modifier.height(16.dp))
                } else {
                    Spacer(Modifier.height(4.dp))
                }

                Text(
                    stringResource(if (isRewrite) R.string.chat_rewrite_result_label else R.string.chat_continue_result_label),
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary,
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    uiState.resultText ?: "",
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f), NarviveShape.Md)
                        .border(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.25f), NarviveShape.Md)
                        .padding(14.dp),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Spacer(Modifier.weight(1f))
                    Text(uiState.providerName, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Spacer(Modifier.height(16.dp))

                // 指令卡
                Text(stringResource(R.string.chat_instruction_label), style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(6.dp))
                Text(
                    "\"${uiState.instructionUsed ?: ""}\"",
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f), NarviveShape.Sm)
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(20.dp))

                // 操作按钮（导出 / 删除）
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedButton(onClick = { viewModel.exportTxt() }, modifier = Modifier.weight(1f).height(42.dp)) {
                        Text(stringResource(R.string.chat_export_txt), style = MaterialTheme.typography.labelLarge)
                    }
                    OutlinedButton(
                        onClick = {
                            viewModel.deleteRewrite()
                            Toast.makeText(context, context.getString(R.string.chat_toast_deleted_kind, kindLabel), Toast.LENGTH_SHORT).show()
                        },
                        enabled = uiState.savedAnnotationId != null,
                        modifier = Modifier.weight(1f).height(42.dp),
                    ) {
                        Text(stringResource(R.string.chat_delete_kind, kindLabel), style = MaterialTheme.typography.labelLarge, color = SemanticColors.Danger)
                    }
                }
            }

            // Error
            uiState.error?.let {
                Spacer(Modifier.height(12.dp))
                Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }
            Spacer(Modifier.height(32.dp))
        }
    }
}

/** 发送范围说明文案：中文字符≈0.67 token 粗估 */
@Composable
private fun sendScopeText(selectionChars: Int, chapterContextChars: Int): String {
    if (chapterContextChars <= 0) return stringResource(R.string.chat_rewrite_send_scope_selection, selectionChars)
    val sentCtx = chapterContextChars.coerceAtMost(2000)
    val est = (sentCtx / 1.5).roundToInt()
    val tokenStr = if (est >= 1000) "%.1fk".format(est / 1000.0) else "$est"
    return stringResource(R.string.chat_rewrite_send_scope_with_context, selectionChars, tokenStr)
}
