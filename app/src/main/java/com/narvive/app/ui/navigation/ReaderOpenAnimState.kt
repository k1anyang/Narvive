package com.narvive.app.ui.navigation

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/** 详情页封面在窗口中的位置与尺寸（px）。 */
data class ReaderCoverRect(
    val left: Int,
    val top: Int,
    val width: Int,
    val height: Int,
)

/** 详情页 → 阅读页的“封面展开”动画参数。 */
data class ReaderOpenAnimation(
    val coverPath: String?,
    val title: String,
    val rect: ReaderCoverRect,
)

/**
 * 详情页点击“开始/继续阅读”时写入，Reader 路由创建后一次性消费。
 * 只在用户开启“图书打开动画”时写入，关闭时保持 null，走普通导航转场。
 */
object ReaderOpenAnimState {
    var pending by mutableStateOf<ReaderOpenAnimation?>(null)
        private set

    fun set(coverPath: String?, title: String, rect: ReaderCoverRect) {
        pending = ReaderOpenAnimation(coverPath = coverPath, title = title, rect = rect)
    }

    fun consume(): ReaderOpenAnimation? {
        val value = pending
        pending = null
        return value
    }

    fun clear() {
        pending = null
    }
}
