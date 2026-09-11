package com.narvive.app.service.reader

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import org.readium.r2.shared.ExperimentalReadiumApi
import org.readium.r2.shared.publication.Link
import org.readium.r2.shared.util.asset.AssetRetriever
import org.readium.r2.shared.util.http.DefaultHttpClient
import org.readium.r2.streamer.PublicationOpener
import org.readium.r2.streamer.parser.DefaultPublicationParser
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 目录加载器 — 阅读器外场景（书籍详情页目录 BottomSheet，H2/F5）按格式解析目录。
 * TXT：复用 TxtReaderController 的章节切分；EPUB：Streamer 打开取 tableOfContents（含层级）；
 * PDF：暂无目录概念，返回空表（UI 显示「暂无目录」）。
 */
@OptIn(ExperimentalReadiumApi::class)
@Singleton
class TocLoader @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    suspend fun load(format: String, filePath: String): List<TocItem> = withContext(Dispatchers.IO) {
        runCatching {
            when (format) {
                "TXT" -> loadTxt(filePath)
                "EPUB" -> loadEpub(filePath)
                else -> emptyList()
            }
        }.getOrDefault(emptyList())
    }

    private suspend fun loadTxt(filePath: String): List<TocItem> {
        val controller = TxtReaderController()
        controller.open(filePath)
        return controller.chapters.mapIndexed { i, c ->
            TocItem(title = c.title, locatorJson = controller.locatorFor(i, 0))
        }
    }

    private suspend fun loadEpub(filePath: String): List<TocItem> {
        val file = File(filePath)
        if (!file.exists() || file.length() == 0L) return emptyList()
        val httpClient = DefaultHttpClient()
        val assetRetriever = AssetRetriever(context.contentResolver, httpClient)
        val opener = PublicationOpener(
            publicationParser = DefaultPublicationParser(context, httpClient, assetRetriever, null),
        )
        val asset = assetRetriever.retrieve(file).getOrNull() ?: return emptyList()
        val publication = opener.open(asset, allowUserInteraction = false).getOrNull() ?: return emptyList()
        try {
            val items = mutableListOf<TocItem>()
            fun walk(links: List<Link>, depth: Int) {
                links.forEach { link ->
                    val title = link.title?.trim().orEmpty()
                    if (title.isNotEmpty()) {
                        items.add(TocItem(title = title, locatorJson = epubLocatorJson(link), depth = depth))
                    }
                    if (link.children.isNotEmpty()) walk(link.children, depth + 1)
                }
            }
            walk(publication.tableOfContents, 0)
            return items
        } finally {
            runCatching { publication.close() }
        }
    }

    /** 章首定位：阅读器 goToLocator 走 Locator.fromJSON，progression=0 即章首 */
    private fun epubLocatorJson(link: Link): String = JSONObject().apply {
        put("href", link.href.toString())
        put("type", "application/xhtml+xml")
        link.title?.let { put("title", it) }
        put("locations", JSONObject().apply { put("progression", 0.0) })
    }.toString()
}
