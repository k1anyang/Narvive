package com.narvive.app.domain.model

import kotlinx.serialization.Serializable

/**
 * 字体目录条目（来自 Gitee fonts.json，https://gitee.com/kumokk/narvive-fonts）。
 *
 * 下载地址优先级：
 * 1. [downloadUrl] —— 显式直链（如 Gitee 发行版附件 / 其他 CDN）；
 * 2. 缺省时按 Gitee raw 规则拼接 —— https://gitee.com/kumokk/narvive-fonts/raw/master/fonts/<fileName>。
 *
 * 注意：Gitee 对公开仓库 raw 文件 >10MiB 的匿名下载要求登录（403 large file require login）。
 * 因此体积超过 [GITEE_ANONYMOUS_RAW_LIMIT] 且未提供 downloadUrl 的字体在目录层直接过滤，
 * 不会出现在 App 中（等托管方配好 downloadUrl 后自动出现，无需改代码）。
 */
@Serializable
data class FontInfo(
    val id: String,
    val lang: String,            // "zh" | "en"
    val name: String,            // 展示名（中文名）
    val family: String,          // CSS font-family 名称（注入 @font-face 用）
    val category: String = "",
    val designer: String = "",
    val website: String = "",
    val license: String = "",
    val fileName: String,
    val fileSize: Long = 0,
    val version: String = "",
    val previewStyle: String = "serif",
    val downloadUrl: String? = null,
) {
    val isChinese: Boolean get() = lang == "zh"
    val isEnglish: Boolean get() = lang == "en"

    /** 最终下载地址：优先 downloadUrl，否则 Gitee raw 拼接 */
    val resolvedDownloadUrl: String
        get() = downloadUrl ?: "$GITEE_RAW_BASE/fonts/$fileName"

    companion object {
        /** Gitee 匿名 raw 下载上限（10MiB），超过必须走 downloadUrl */
        const val GITEE_ANONYMOUS_RAW_LIMIT: Long = 10L * 1024 * 1024

        const val GITEE_RAW_BASE = "https://gitee.com/kumokk/narvive-fonts/raw/master"

        /** 是否可直接下载：有显式直链，或体积在 Gitee 匿名 raw 限制内 */
        fun isAvailable(item: FontInfo): Boolean =
            item.downloadUrl != null || item.fileSize <= GITEE_ANONYMOUS_RAW_LIMIT
    }
}