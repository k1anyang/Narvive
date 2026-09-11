package com.narvive.app.service.reader

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import org.readium.r2.shared.ExperimentalReadiumApi
import org.readium.r2.shared.util.asset.AssetRetriever
import org.readium.r2.shared.util.http.DefaultHttpClient
import org.readium.r2.streamer.PublicationOpener
import org.readium.r2.streamer.parser.DefaultPublicationParser
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 阅读器外取「当前章纯文本」，供改写/续写等场景使用。
 * TXT：按 locator 的 chapter 索引切章；EPUB：按 locator 的 href 打开对应章节并去标签。
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
        val file = File(filePath)
        if (!file.exists() || file.length() == 0L) return null
        val httpClient = DefaultHttpClient()
        val assetRetriever = AssetRetriever(context.contentResolver, httpClient)
        val opener = PublicationOpener(DefaultPublicationParser(context, httpClient, assetRetriever, null))
        val asset = assetRetriever.retrieve(file).getOrNull() ?: return null
        val publication = opener.open(asset, allowUserInteraction = false).getOrNull() ?: return null
        try {
            val link = publication.readingOrder.firstOrNull { it.href.toString() == href } ?: return null
            val resource = publication.get(link) ?: return null
            val bytes = resource.read().getOrNull() ?: return null
            val html = String(bytes, Charsets.UTF_8)
            return html
                .replace(Regex("<script[\\s\\S]*?</script>"), " ")
                .replace(Regex("<style[\\s\\S]*?</style>"), " ")
                .replace(Regex("<[^>]+>"), " ")
                .replace(Regex("&nbsp;"), " ")
                .replace(Regex("&[a-zA-Z#0-9]+;"), " ")
                .replace(Regex("\\s+"), " ")
                .trim()
        } finally {
            runCatching { publication.close() }
        }
    }
}
