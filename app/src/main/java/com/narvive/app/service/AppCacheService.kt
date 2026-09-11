package com.narvive.app.service

import android.content.Context
import android.webkit.WebView
import coil.Coil
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 全局缓存清理：WebView（阅读页 HTML/字体资源）与 Coil 图片缓存。
 * 删除书籍时勾选「同时删除缓存文件」即调用。
 */
@Singleton
class AppCacheService @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    suspend fun clearAppCaches() {
        // WebView.clearCache 必须在主线程
        withContext(Dispatchers.Main) {
            runCatching { WebView(context).clearCache(true) }
        }
        // Coil 磁盘/内存缓存
        withContext(Dispatchers.IO) {
            runCatching { Coil.imageLoader(context).diskCache?.clear() }
        }
        runCatching { Coil.imageLoader(context).memoryCache?.clear() }
    }
}
