package com.narvive.app.service.font

import android.content.Context
import com.narvive.app.domain.model.FontInfo
import com.narvive.app.domain.model.FontInfo.Companion.isAvailable
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request

/**
 * 字体目录仓库：从 Gitee 拉取 fonts.json，解析并过滤「当前可下载」的字体，
 * 失败时回退到本地缓存，保证离线可看目录。
 */
@Singleton
class FontCatalogRepository @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val json = Json { ignoreUnknownKeys = true }
    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

    /** 最近一次成功加载的目录（内存缓存，ReaderViewModel 同步解析用） */
    @Volatile
    var lastLoaded: List<FontInfo>? = null
        private set

    private val cacheFile: File
        get() = File(FontStorage.dir(context), "catalog.json")

    /**
     * 拉取目录：网络优先，失败回退本地缓存。仅返回当前可下载（≤10MiB 或带 downloadUrl）的字体。
     */
    suspend fun loadCatalog(): List<FontInfo> = withContext(Dispatchers.IO) {
        val remote = runCatching { fetchRemote() }.getOrNull()
        if (remote != null) {
            lastLoaded = remote
            runCatching {
                cacheFile.parentFile?.mkdirs()
                cacheFile.writeText(json.encodeToString(remote))
            }
            return@withContext remote
        }
        lastLoaded?.let { return@withContext it }
        runCatching {
            if (!cacheFile.isFile) return@withContext emptyList()
            json.decodeFromString<List<FontInfo>>(cacheFile.readText())
        }.getOrNull().orEmpty().also { lastLoaded = it }
    }

    suspend fun fontById(id: String): FontInfo? =
        loadCatalog().firstOrNull { it.id == id }

    private fun fetchRemote(): List<FontInfo> {
        val request = Request.Builder().url(FontInfo.GITEE_RAW_BASE + "/fonts.json").build()
        client.newCall(request).execute().use { resp ->
            if (!resp.isSuccessful) throw IllegalStateException("HTTP ${resp.code}")
            val body = resp.body?.string() ?: throw IllegalStateException("empty body")
            return json.decodeFromString<List<FontInfo>>(body).filter(::isAvailable)
        }
    }
}