package com.narvive.app.service.font

import android.content.Context
import android.graphics.Typeface
import androidx.compose.ui.text.font.FontFamily
import com.narvive.app.domain.model.FontInfo
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 已解析的字体（文件已下载存在）：
 * familyName 用于 CSS @font-face / font-family；file 用于 Compose Typeface 与本地 HTTP 服务。
 */
data class FontSpec(
    val id: String,
    val familyName: String,
    val fileName: String,
    val file: File,
)

/** 单个 @font-face 声明所需信息（供 Readium Configuration 声明 + 注入 CSS 使用） */
data class ReaderFontFace(
    val familyName: String,
    val url: String,
)
/** 阅读器当前生效的中英字体组合（null = 该系统默认） */
data class ReaderFontConfig(
    val cjk: FontSpec? = null,
    val latin: FontSpec? = null,
) {
    val hasCustom: Boolean get() = cjk != null || latin != null
}

/** 字体解析：id → 已下载文件 / CSS 注入串 / Compose FontFamily */
@Singleton
class FontResolver @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    /** 解析字体 id；system 或未下载/未知 id 返回 null */
    fun specOf(id: String, catalog: List<FontInfo>): FontSpec? {
        if (id == SYSTEM_FONT_ID) return null
        val info = catalog.firstOrNull { it.id == id } ?: return null
        val file = File(FontStorage.dir(context), info.fileName)
        if (!file.isFile) return null
        return FontSpec(id = info.id, familyName = info.family, fileName = info.fileName, file = file)
    }

    /**
     * 生成 EPUB 用注入 CSS：@font-face（指向本地 HTTP 服务）+ 字体栈。
     * 中英文同时生效：CSS 字体栈按字形回退，中文走 cjk、拉丁走 latin。
     */
    fun buildEpubFontCss(cjk: FontSpec?, latin: FontSpec?, baseUrl: String): String? {
        val faces = buildList {
            cjk?.let { add(fontFace(it, baseUrl)) }
            latin?.let { add(fontFace(it, baseUrl)) }
        }
        if (faces.isEmpty()) return null
        val stack = listOfNotNull(cjk?.familyName, latin?.familyName, "serif").joinToString(", ")
        // 注意：返回纯 CSS 规则（不带 <style> 标签），EpubViewer 会把整段写入 <style> 元素的 textContent
        // 覆盖常见文本容器（仅 body 可能被 EPUB 作者样式覆盖导致不生效）
        return faces.joinToString("\n") + "\nhtml, body, p, div, span, li, blockquote, h1, h2, h3, h4, h5, h6, a, em, strong { font-family: $stack !important; }"
    }

    private fun fontFace(spec: FontSpec, baseUrl: String): String {
        val format = if (spec.fileName.endsWith(".otf", ignoreCase = true)) "opentype" else "truetype"
        return "@font-face { font-family: \"${spec.familyName}\"; src: url(\"$baseUrl/fonts/${spec.fileName}\") format(\"$format\"); font-style: normal; font-weight: 400; }"
    }

    companion object {
        /** 「系统默认」字体 id */
        const val SYSTEM_FONT_ID = "system"
    }
}

/**
 * TXT（Compose）字体解析。
 *
 * 双字体限制说明（重要）：
 * Compose 文本引擎把 FontFamily 解析为【单个 Typeface】（按字重/风格匹配），不做逐字形回退，
 * 因此无法像 CSS 字体栈那样同时应用中英两个下载字体。这里采用：
 * - 选中了中文字体 → 以中文体为主字体（中文体通常自带拉丁字形，缺字走系统回退）；
 * - 仅选中英文字体 → 以英文体为主字体，中文走系统回退；
 * - 都没选 → 系统衬线（与历史默认一致）。
 * 英文字体在 EPUB 中通过 CSS 字体栈完整生效。
 */
fun resolveTxtFontFamily(cjk: FontSpec?, latin: FontSpec?): FontFamily {
    val primary = cjk ?: latin ?: return FontFamily.Serif
    return FontFamily(Typeface.createFromFile(primary.file))
}