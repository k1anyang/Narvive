package com.narvive.app.service

import android.net.Uri
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 跨组件传递「系统分享/打开方式」接收到的书籍 URI。
 * MainActivity 在 onCreate/onNewIntent 写入，LibraryViewModel 消费后清空。
 */
@Singleton
class ExternalImportBus @Inject constructor() {
    private val _pendingUri = MutableStateFlow<Uri?>(null)
    val pendingUri: StateFlow<Uri?> = _pendingUri.asStateFlow()

    fun request(uri: Uri) { _pendingUri.value = uri }
    fun consume() { _pendingUri.value = null }
}
