package com.narvive.app.service.reader

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import org.readium.r2.shared.ExperimentalReadiumApi
import org.readium.r2.shared.publication.Publication
import org.readium.r2.shared.util.asset.AssetRetriever
import org.readium.r2.shared.util.http.DefaultHttpClient
import org.readium.r2.streamer.PublicationOpener
import org.readium.r2.streamer.parser.DefaultPublicationParser
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/** 全书章节清单的一项（只有标题与稳定键，不含正文） */
data class ChapterRef(val key: String, val title: String)

/** 流式取到的一章正文 */
data class ChapterText(val key: String, val title: String, val text: String)

/**
 * 阅读器外取「当前章纯文本」，供改写/续写等场景使用。
 * TXT：按 locator 的 chapter 索引切章；EPUB：按 locator 的 href 打开对应章节并去标签。
 *
 * 另提供**全书章节**能力（[chapterInventory] / [forEachChapter]）：
 * 章节摘要索引需要遍历全书，而逐章调用 [currentChapterText] 会让 EPUB
 * 每章都重新打开并解析一次 Publication——200 章就是 200 次解析，不可接受。
 * 这里改为「打开一次、逐章流式读」，并且不把整本书正文同时留在内存里。
 */
@OptIn(ExperimentalReadiumApi::class)
@Singleton
class ChapterTextExtractor @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    suspend fun currentChapterText(format: String, filePath: String, locatorJson: String?): String? =
        withContext(Dispatchers.IO) {
            runCatching {
                when (format) {
                    "TXT" -> txtChapter(filePath, locatorJson)
                    "EPUB" -> epubChapter(filePath, locatorJson)
                    else -> null
                }
            }.getOrNull()?.takeIf { it.isNotBlank() }
        }

    /**
     * 全书章节清单（键 + 标题），用于建立索引前显示总数与进度。
     *
     * @return 空表表示该格式不支持或文件不可读
     */
    suspend fun chapterInventory(
        format: String,
        filePath: String,
        maxChapters: Int = MAX_INDEX_CHAPTERS,
    ): List<ChapterRef> = withContext(Dispatchers.IO) {
        runCatching {
            when (format) {
                "TXT" -> TxtReaderController().let { c ->
                    c.open(filePath)
                    c.chapters.take(maxChapters).mapIndexed { i, ch -> ChapterRef(ChapterKey.txt(i), ch.title) }
                }
                "EPUB" -> withPublication(filePath) { pub ->
                    pub.readingOrder.take(maxChapters).map { link ->
                        val href = link.href.toString()
                        ChapterRef(ChapterKey.epub(href), link.title?.trim().orEmpty().ifBlank { href })
                    }
                } ?: emptyList()
                else -> emptyList()
            }
        }.getOrDefault(emptyList())
    }

    /**
     * 逐章流式取正文（EPUB 只打开一次 Publication）。
     *
     * [onChapter] 返回 false 即停止遍历——索引任务被取消或达到上限时用它提前收工。
     * 逐章回调而不是一次性返回列表，是为了避免把整本书正文同时压在内存里。
     */
    suspend fun forEachChapter(
        format: String,
        filePath: String,
        maxChapters: Int = MAX_INDEX_CHAPTERS,
        onChapter: suspend (ChapterText) -> Boolean,
    ) = withContext(Dispatchers.IO) {
        runCatching {
            when (format) {
                "TXT" -> {
                    val controller = TxtReaderController()
                    controller.open(filePath)
                    val full = controller.fullText
                    if (full.isNotEmpty()) {
                        for ((i, ch) in controller.chapters.take(maxChapters).withIndex()) {
                            val from = ch.startOffset.coerceIn(0, full.length)
                            val to = ch.endOffset.coerceIn(from, full.length)
                            val text = full.substring(from, to)
                            if (text.isNotBlank() && !onChapter(ChapterText(ChapterKey.txt(i), ch.title, text))) break
                        }
                    }
                }
                "EPUB" -> withPublication(filePath) { pub ->
                    var emitted = 0
                    for (link in pub.readingOrder) {
                        if (emitted >= maxChapters) break
                        val href = link.href.toString()
                        val text = readChapter(pub, href)
                        emitted++
                        if (text.isBlank()) continue
                        val title = link.title?.trim().orEmpty().ifBlank { href }
                        if (!onChapter(ChapterText(ChapterKey.epub(href), title, text))) break
                    }
                }
            }
        }
    }

    // ── 内部 ──

    private suspend fun txtChapter(filePath: String, locatorJson: String?): String? {
        val controller = TxtReaderController()
        controller.open(filePath)
        val chapters = controller.chapters
        if (chapters.isEmpty() || controller.fullText.isEmpty()) return null
        val chapterIndex = locatorJson?.let {
            runCatching { JSONObject(it).optInt("chapter", 0) }.getOrDefault(0)
        } ?: 0
        val ch = chapters[chapterIndex.coerceIn(0, chapters.lastIndex)]
        val text = controller.fullText
        return text.substring(ch.startOffset.coerceIn(0, text.length), ch.endOffset.coerceIn(0, text.length))
    }

    private suspend fun epubChapter(filePath: String, locatorJson: String?): String? {
        val href = locatorJson?.let { runCatching { JSONObject(it).optString("href") }.getOrNull() }
            ?.takeIf { it.isNotBlank() } ?: return null
        return withPublication(filePath) { pub -> readChapter(pub, href) }
    }

    /** 打开 Publication 执行 [block] 并确保关闭 */
    private suspend inline fun <T> withPublication(filePath: String, block: (Publication) -> T): T? {
        val file = File(filePath)
        if (!file.exists() || file.length() == 0L) return null
        val httpClient = DefaultHttpClient()
        val assetRetriever = AssetRetriever(context.contentResolver, httpClient)
        val opener = PublicationOpener(DefaultPublicationParser(context, httpClient, assetRetriever, null))
        val asset = assetRetriever.retrieve(file).getOrNull() ?: return null
        val publication = opener.open(asset, allowUserInteraction = false).getOrNull() ?: return null
        return try {
            block(publication)
        } finally {
            runCatching { publication.close() }
        }
    }

    private suspend fun readChapter(pub: Publication, href: String): String {
        val link = pub.readingOrder.firstOrNull { it.href.toString() == href } ?: return ""
        val resource = pub.get(link) ?: return ""
        val bytes = resource.read().getOrNull() ?: return ""
        return htmlToText(String(bytes, Charsets.UTF_8))
    }

    /** HTML 去标签：与阅读器内取章文本保持同一套规则，保证字数口径一致 */
    private fun htmlToText(html: String): String = html
        .replace(Regex("<script[\\s\\S]*?</script>"), " ")
        .replace(Regex("<style[\\s\\S]*?</style>"), " ")
        .replace(Regex("<[^>]+>"), " ")
        .replace(Regex("&nbsp;"), " ")
        .replace(Regex("&[a-zA-Z#0-9]+;"), " ")
        .replace(Regex("\\s+"), " ")
        .trim()

    private companion object {
        /** 单本书最多索引多少章：防止畸形文件把索引任务变成无底洞 */
        const val MAX_INDEX_CHAPTERS = 500
    }
}
