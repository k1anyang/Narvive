package com.narvive.app.service

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

data class StorageInfo(
    val booksBytes: Long = 0L,
    val booksCount: Int = 0,
    val coversBytes: Long = 0L,
    val coversCount: Int = 0,
    val fontsBytes: Long = 0L,
    val fontsCount: Int = 0,
    val cacheBytes: Long = 0L,
) {
    val totalBytes: Long get() = booksBytes + coversBytes + fontsBytes + cacheBytes
}

/** 存储统计与缓存清理：books / covers / fonts / cache 四类 */
@Singleton
class StorageService @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    fun compute(): StorageInfo {
        val books = dirInfo(File(context.filesDir, "books"))
        val covers = dirInfo(File(context.filesDir, "covers"))
        val fonts = dirInfo(File(context.filesDir, "fonts"))
        val cache = dirInfo(context.cacheDir)
        return StorageInfo(
            booksBytes = books.first, booksCount = books.second,
            coversBytes = covers.first, coversCount = covers.second,
            fontsBytes = fonts.first, fontsCount = fonts.second,
            cacheBytes = cache.first,
        )
    }

    /** 清理 cacheDir 全部内容（临时导出/恢复暂存/图片与 WebView 缓存），返回释放字节数 */
    fun clearCache(): Long {
        val cacheDir = context.cacheDir
        val freed = dirSize(cacheDir)
        cacheDir.listFiles()?.forEach { file ->
            runCatching { if (file.isDirectory) file.deleteRecursively() else file.delete() }
        }
        return freed
    }

    private fun dirInfo(dir: File): Pair<Long, Int> {
        var bytes = 0L
        var count = 0
        dir.walkTopDown().forEach { f ->
            if (f.isFile) {
                bytes += f.length()
                count++
            }
        }
        return bytes to count
    }

    private fun dirSize(dir: File): Long =
        dir.walkTopDown().filter { it.isFile }.sumOf { it.length() }
}
