package com.narvive.app.service.font

import android.content.Context
import com.narvive.app.R
import com.narvive.app.domain.model.FontInfo
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request

/** 单款字体的下载状态 */
sealed interface FontDownloadState {
    /** 未下载 */
    data object Idle : FontDownloadState

    /** 下载中，progress ∈ 0..1 */
    data class Downloading(val progress: Float) : FontDownloadState

    /** 下载失败，可重试 */
    data class Error(val message: String) : FontDownloadState

    /** 已下载完成（文件存在且大小校验通过） */
    data object Ready : FontDownloadState
}

/**
 * 字体下载管理器（进程内常驻）：
 * - OkHttp 流式下载，支持断点续传（Range + .part 临时文件，完成后改名）
 * - 完成后按 catalog 的 fileSize 校验大小
 * - 进度/状态以 StateFlow 暴露，删除 = 取消任务并移除文件
 */
@Singleton
class FontDownloadManager @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .followRedirects(true) // Gitee 附件/raw 会 302 到 CDN
        .build()

    private val states = ConcurrentHashMap<String, MutableStateFlow<FontDownloadState>>()
    private val jobs = ConcurrentHashMap<String, Job>()
    private val _allStates = MutableStateFlow<Map<String, FontDownloadState>>(emptyMap())

    val allStates: Flow<Map<String, FontDownloadState>> = _allStates.asStateFlow()

    /** 最近一次已知目录（按 id），供磁盘状态初始化 */
    @Volatile
    private var lastKnownItems: Map<String, FontInfo> = emptyMap()

    private fun fontsDir(): File = FontStorage.dir(context)

    private fun targetFile(item: FontInfo): File = File(fontsDir(), item.fileName)

    /** 已下载完成的判定：文件存在且大小与目录一致 */
    fun isDownloaded(item: FontInfo): Boolean {
        val f = targetFile(item)
        return f.isFile && f.length() == item.fileSize
    }

    fun fileFor(item: FontInfo): File? = targetFile(item).takeIf { it.isFile }

    /**
     * 目录加载完成后调用：记录目录并按磁盘文件初始化各字体状态
     * （App 重启后已下载的字体仍显示「已下载」）。
     */
    fun refreshFromDisk(items: List<FontInfo>) {
        lastKnownItems = items.associateBy { it.id }
        items.forEach { item ->
            val flow = states.computeIfAbsent(item.id) { MutableStateFlow(FontDownloadState.Idle) }
            if (flow.value == FontDownloadState.Idle && isDownloaded(item)) {
                flow.value = FontDownloadState.Ready
                updateMap(item.id, flow.value)
            }
        }
    }

    /** 开始（或断点续传）下载；已在下载中则忽略 */
    fun download(item: FontInfo) {
        if (jobs.containsKey(item.id)) return
        lastKnownItems = lastKnownItems + (item.id to item)
        val flow = stateFlow(item.id)
        val job = scope.launch {
            flow.value = FontDownloadState.Downloading(0f)
            updateMap(item.id, flow.value)
            try {
                val target = targetFile(item)
                if (target.isFile && target.length() == item.fileSize) {
                    flow.value = FontDownloadState.Ready
                    updateMap(item.id, flow.value)
                    return@launch
                }
                val part = File(fontsDir(), item.fileName + ".part")
                val resumeFrom = if (part.isFile) part.length() else 0L
                val reqBuilder = Request.Builder().url(item.resolvedDownloadUrl)
                if (resumeFrom > 0L) reqBuilder.header("Range", "bytes=$resumeFrom-")
                client.newCall(reqBuilder.build()).execute().use { resp ->
                    if (resp.code != 200 && resp.code != 206) {
                        throw IOException(context.getString(R.string.font_dl_http_error, resp.code))
                    }
                    val body = resp.body ?: throw IOException(context.getString(R.string.font_dl_empty_response))
                    val total = item.fileSize
                    var downloaded = if (resp.code == 206) resumeFrom else 0L
                    if (resp.code != 206) part.delete() // 服务器忽略 Range，重新全量下载
                    val buf = ByteArray(64 * 1024)
                    val out = FileOutputStream(part, resp.code == 206)
                    try {
                        while (true) {
                            val read = body.source().read(buf)
                            if (read == -1) break
                            out.write(buf, 0, read)
                            downloaded += read
                            if (total > 0) {
                                flow.value = FontDownloadState.Downloading((downloaded.toFloat() / total).coerceIn(0f, 1f))
                                updateMap(item.id, flow.value)
                            }
                        }
                    } finally {
                        out.flush()
                        out.close()
                    }
                    if (total > 0 && downloaded != total) {
                        throw IOException(context.getString(R.string.font_dl_incomplete, downloaded, total))
                    }
                    // 目标文件若已存在（可能是历史损坏/旧版本），先删除再改名，避免 renameTo 失败
                    target.delete()
                    if (!part.renameTo(target)) {
                        throw IOException(context.getString(R.string.font_dl_write_failed))
                    }
                }
                flow.value = FontDownloadState.Ready
                updateMap(item.id, flow.value)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                flow.value = FontDownloadState.Error(e.message ?: context.getString(R.string.font_dl_download_failed))
                updateMap(item.id, flow.value)
            } finally {
                jobs.remove(item.id)
            }
        }
        jobs[item.id] = job
    }

    /** 取消下载并删除已下载文件，释放空间 */
    fun delete(item: FontInfo) {
        jobs.remove(item.id)?.cancel()
        runCatching { targetFile(item).delete() }
        runCatching { File(fontsDir(), item.fileName + ".part").delete() }
        val flow = states[item.id] ?: return
        flow.value = FontDownloadState.Idle
        updateMap(item.id, flow.value)
    }

    private fun stateFlow(id: String): MutableStateFlow<FontDownloadState> =
        states.computeIfAbsent(id) { MutableStateFlow(FontDownloadState.Idle) }

    private fun updateMap(id: String, state: FontDownloadState) {
        _allStates.value = _allStates.value + (id to state)
    }
}