package com.narvive.app.ui.screen.rewrite

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.narvive.app.R
import com.narvive.app.domain.model.Annotation
import com.narvive.app.domain.model.AnnotationType
import com.narvive.app.domain.model.Book
import com.narvive.app.domain.repository.AnnotationRepository
import com.narvive.app.domain.repository.BookshelfRepository
import com.narvive.app.service.ai.AiMessage
import com.narvive.app.service.ai.AiService
import com.narvive.app.service.ai.FallbackChain
import com.narvive.app.service.ai.PromptRenderer
import com.narvive.app.service.ai.PromptService
import com.narvive.app.service.ai.PromptTemplates
import com.narvive.app.service.reader.ChapterTextExtractor
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.io.File
import java.util.UUID
import javax.inject.Inject

data class RewriteUiState(
    val resultText: String? = null,
    val isLoading: Boolean = false,
    val providerName: String = "",
    val error: String? = null,
    /** 当前章标题（「原文 · 第三章」eyebrow 用） */
    val chapterTitle: String = "",
    /** 本章上下文字符数（发送范围说明条用；0=不可用） */
    val chapterContextChars: Int = 0,
    /** 生成本次结果所用的指令（对比页 chip/指令卡/导出用） */
    val instructionUsed: String? = null,
    /** 本次结果落库的 REWRITE 标注 id（删除改写用） */
    val savedAnnotationId: String? = null,
    /** 已另存为普通笔记（按钮态「✓ 已存笔记」） */
    val noteSaved: Boolean = false,
)

@HiltViewModel
class RewriteViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val aiService: AiService,
    private val fallbackChain: FallbackChain,
    private val promptService: PromptService,
    private val bookshelfRepo: BookshelfRepository,
    private val annotationRepo: AnnotationRepository,
    private val chapterTextExtractor: ChapterTextExtractor,
) : ViewModel() {

    private val _uiState = MutableStateFlow(RewriteUiState())
    val uiState: StateFlow<RewriteUiState> = _uiState.asStateFlow()
    private var book: Book? = null
    private var originalText: String = ""
    private var mode: String = "rewrite"
    /** 本章纯文本（TXT 可取；EPUB/PDF 阅读器外拿不到，为 null） */
    private var chapterText: String? = null
    private var inited = false

    fun init(bookId: String, text: String, rewriteMode: String) {
        if (inited) return
        inited = true
        originalText = text; mode = rewriteMode
        viewModelScope.launch {
            val b = bookshelfRepo.getBook(bookId) ?: return@launch
            book = b
            val chapterTitle = b.currentChapter?.takeIf { it.isNotBlank() } ?: ""
            chapterText = loadChapterText(b)
            _uiState.update {
                it.copy(chapterTitle = chapterTitle, chapterContextChars = chapterText?.length ?: 0)
            }
        }
    }

    fun execute(instruction: String) {
        _uiState.update { it.copy(isLoading = true, error = null, noteSaved = false) }
        viewModelScope.launch {
            val providers = fallbackChain.getEnabledProviders()
            if (providers.isEmpty()) {
                _uiState.update { it.copy(isLoading = false, error = context.getString(R.string.chat_vm_no_provider_short)) }; return@launch
            }
            // 上下文：书名/作者/章节名 + 选区前后文（续写上文1200；改写前后各600，不越章界）
            val ctx = buildContextWindow()
            val prompt = if (mode == "rewrite") {
                PromptRenderer.render(
                    promptService.get(PromptTemplates.REWRITE),
                    mapOf(
                        "originalText" to originalText,
                        "context" to ctx.ifBlank { "（无）" },
                        "instruction" to instruction,
                    ),
                )
            } else {
                PromptRenderer.render(
                    promptService.get(PromptTemplates.CONTINUE),
                    mapOf(
                        "originalText" to originalText,
                        "context" to ctx.ifBlank { "（无）" },
                        "instruction" to instruction.ifBlank { "自然续写" },
                    ),
                )
            }

            for (provider in providers) {
                if (provider.isDegraded) continue
                aiService.simpleChat(provider, listOf(AiMessage("user", prompt)))
                    .onSuccess { result ->
                        fallbackChain.recordSuccess(provider.id)
                        _uiState.update {
                            it.copy(
                                resultText = result, isLoading = false,
                                providerName = provider.name, instructionUsed = instruction,
                            )
                        }
                        saveAnnotation(provider.id, result, instruction)
                        return@launch
                    }
                    .onFailure { fallbackChain.recordFailure(provider.id) }
            }
            _uiState.update { it.copy(isLoading = false, error = context.getString(R.string.chat_vm_all_providers_unavailable_short)) }
        }
    }

    private suspend fun saveAnnotation(providerId: String, result: String, instruction: String) {
        val b = book ?: return
        val id = UUID.randomUUID().toString()
        val ann = Annotation(
            id = id,
            bookId = b.id, locatorJson = b.currentLocator ?: "", selectedText = originalText,
            type = AnnotationType.REWRITE,
            color = null, note = null, translation = null,
            rewrittenText = result, rewriteInstruction = instruction,
            providerId = providerId, createdAt = System.currentTimeMillis(), updatedAt = System.currentTimeMillis(),
        )
        annotationRepo.insertOrUpdate(ann)
        _uiState.update { it.copy(savedAnnotationId = id) }
    }

    /** 将当前结果另存为普通笔记 */
    fun saveResultAsNote() {
        val b = book ?: return
        val result = _uiState.value.resultText ?: return
        if (_uiState.value.noteSaved) return
        viewModelScope.launch {
            val ann = Annotation(
                id = UUID.randomUUID().toString(),
                bookId = b.id, locatorJson = b.currentLocator ?: "", selectedText = originalText,
                type = AnnotationType.NOTE,
                color = null, note = result, translation = null,
                rewrittenText = null, rewriteInstruction = null,
                providerId = null, createdAt = System.currentTimeMillis(), updatedAt = System.currentTimeMillis(),
            )
            annotationRepo.insertOrUpdate(ann)
            _uiState.update { it.copy(noteSaved = true) }
        }
    }

    /** 删除本次改写（移除落库的 REWRITE 标注并回到指令输入态） */
    fun deleteRewrite() {
        val id = _uiState.value.savedAnnotationId ?: return
        viewModelScope.launch {
            annotationRepo.delete(id)
            _uiState.update {
                it.copy(resultText = null, instructionUsed = null, savedAnnotationId = null, noteSaved = false)
            }
        }
    }

    /** 导出 原文+指令+改写 为 .txt 并调起系统分享（FileProvider 模式同 NotesViewModel.exportMarkdown） */
    fun exportTxt() {
        val b = book ?: return
        val result = _uiState.value.resultText ?: return
        val instruction = _uiState.value.instructionUsed ?: ""
        val kindLabel = if (mode == "rewrite") "改写" else "续写"
        viewModelScope.launch {
            val content = buildString {
                appendLine("《${b.title}》 ${kindLabel}导出")
                b.author?.let { appendLine("作者：$it") }
                appendLine("章节：${_uiState.value.chapterTitle}")
                appendLine("$kindLabel 指令：$instruction")
                appendLine()
                appendLine("──────── 原文 ────────")
                appendLine(originalText)
                appendLine()
                appendLine("──────── $kindLabel 文本 ────────")
                appendLine(result)
            }
            runCatching {
                val safeTitle = b.title.replace(Regex("[\\\\/:*?\"<>|]"), "_").take(30)
                val file = File(context.cacheDir, "$kindLabel-$safeTitle-${System.currentTimeMillis()}.txt")
                file.writeText(content, Charsets.UTF_8)
                val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
                val intent = Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_STREAM, uri)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                context.startActivity(Intent.createChooser(intent, "导出$kindLabel").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            }.onFailure {
                _uiState.update { s -> s.copy(error = context.getString(R.string.chat_vm_export_failed, it.message ?: "")) }
            }
        }
    }

    /** 组装上下文：书名/作者/章节名 + 选区前后文（续写上文1200；改写前后各600，天然不越章界） */
    private fun buildContextWindow(): String {
        val ct = chapterText
        val idx = if (originalText.isNotBlank() && ct != null) ct.indexOf(originalText) else -1
        val before = if (ct != null && idx > 0) ct.substring(0, idx) else ct.orEmpty()
        val after = if (ct != null && idx >= 0) ct.substring(idx + originalText.length) else ""
        val window = if (mode == "rewrite") {
            buildString {
                append("上文：").append(before.takeLast(600))
                append("\n下文：").append(after.take(600))
            }
        } else {
            "上文：" + before.takeLast(1200)
        }
        val bookInfo = book?.let {
            buildString {
                append("书籍：《${it.title}》")
                it.author?.let { a -> append("，作者：$a") }
                if (_uiState.value.chapterTitle.isNotBlank()) append("，章节：${_uiState.value.chapterTitle}")
            }
        }.orEmpty()
        return listOf(bookInfo, window).filter { it.isNotBlank() }.joinToString("\n").ifBlank { "（无）" }
    }

    fun clearError() { _uiState.update { it.copy(error = null) } }

    /** 当前章纯文本：TXT + EPUB 均支持（阅读器外解析） */
    private suspend fun loadChapterText(b: Book): String? =
        chapterTextExtractor.currentChapterText(b.format, b.filePath, b.currentLocator)
}
