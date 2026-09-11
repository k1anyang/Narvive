package com.narvive.app.service.reader

import kotlinx.coroutines.flow.StateFlow

/**
 * 统一阅读器抽象 — UI 层只面向此接口编程。
 * 三种格式（EPUB/PDF/TXT）共享同一套 HUD/设置/标注/AI 交互。
 */
interface ReaderController {
    /** 当前进度 0.0–1.0 */
    val progress: StateFlow<Float>

    /** 当前章节标题 */
    val currentChapter: StateFlow<String>

    /** 当前页 Locator JSON */
    val currentLocator: StateFlow<String?>

    /** 是否正在加载 */
    val isLoading: StateFlow<Boolean>

    /** 打开书籍 */
    suspend fun open(filePath: String)

    /** 跳转到指定 Locator */
    suspend fun goToLocator(locatorJson: String)

    /** 上一页 */
    suspend fun previousPage()

    /** 下一页 */
    suspend fun nextPage()

    /** 搜索（EPUB 走 Readium search，TXT/PDF 自研） */
    suspend fun search(query: String, onResult: (List<SearchResult>) -> Unit)

    /** 由 locator 实时计算全局进度（0–1）；无法解析返回 null（书签进度实时匹配用） */
    fun progressOfLocator(locatorJson: String): Float? = null

    /** 判断当前 locator 是否位于书签附近（同页/同块），书签指示与书签切换用 */
    fun isNearBookmark(currentLocator: String?, bookmarkLocator: String?): Boolean = false

    /** 进度（0–1）→ 所属章节标题；未知返回 null（拖动进度条提示用） */
    fun chapterTitleAtProgress(progress: Float): String? = null

    /** returns chapter title only if progress is at the title page of a chapter; null otherwise */
    fun chapterTitleAtChapterStart(progress: Float): String? = null

    /** current spine index in readingOrder; -1 when unknown */
    fun currentSpineIndex(): Int = -1

    /** total count of spine items in readingOrder */
    fun spineItemCount(): Int = 0

    /** locator JSON for the start of spine item [spineIndex]; null if out of bounds */
    fun locatorForSpineItem(spineIndex: Int): String? = null

    /** display title for spine item (prefers TOC title, falls back to spine title) */
    fun chapterTitleForSpineItem(spineIndex: Int): String? = null

    /** 应用阅读设置 */
    suspend fun applySettings(settings: ReadSettings)

    /** 当前章纯文本（AI 面板「本章」上下文范围用）；无章节概念或不可用时返回 null */
    suspend fun currentChapterText(): String?

    /** 释放资源 */
    suspend fun close()
}

data class SearchResult(
    val locatorJson: String,
    val excerpt: String,
    val chapterTitle: String?,
    /** 命中点在全书的进度 0.0–1.0（搜索卡片"38%"用） */
    val progressPercent: Float = 0f,
)

/** 目录条目（阅读器目录抽屉 / 详情页目录 BottomSheet 共用） */
data class TocItem(
    val title: String,
    val locatorJson: String,
    val pageLabel: String = "",
    /** 层级（EPUB 目录子节缩进用，0=顶层） */
    val depth: Int = 0,
)

data class ReadSettings(
    val theme: String = "paper",           // paper|sepia|green|dark|black|custom
    val fontSize: Int = 17,                // sp
    val lineHeight: Float = 1.4f,
    val margin: Int = 24,                  // dp
    val fontFamily: String = "source_serif", // 旧字段（保留做迁移兼容；新逻辑请用 fontFamilyCjk / fontFamilyLatin）
    val fontFamilyCjk: String = "system",    // 中文字体 id（system = 系统默认衬线）
    val fontFamilyLatin: String = "system",  // 英文字体 id（system = 系统默认衬线）
    val alignment: String = "justify",     // left|justify
    val brightness: Float = 1f,            // 0.1–1.0
    val paragraphSpacing: Float = 0.0f,    // 段间距 em 0–3.0（0=无段间距；1.0 = 一个字高，与行高无关）
    val firstLineIndent: Float = 2f,       // 首行缩进 0–4（单位 em，2=缩进两字符）
    val publisherStyles: Boolean = true,   // EPUB：true=尊重出版方排版（行距/段距/缩进由出版方控制）；false=阅读器全权接管
    val verticalMargin: Float = 0.8f,      // 上下边距 0–2.0（倍率，实际 = 24dp × 值）
    val horizontalMargin: Float = 1f,      // 左右边距 0–2.0（倍率，实际 = 24dp × 值）
    val followSystemBrightness: Boolean = false, // 跟随系统亮度
    val eyeProtection: Boolean = false,    // 护眼模式（暖色遮罩）
    val pageFlipAnimation: String = "slide", // slide|updown|none 翻页动画
    val autoFlipSpeedSeconds: Int = 30,    // 自动翻页速度（秒/页），10..120，步进 5
    val volumeKeyPageTurn: Boolean = true,  // 音量键翻页/滚动
    val bookOpenAnimation: Boolean = false, // 图书打开动画（暂未开放）
    val screenOffMinutes: Int = 5,          // 屏幕常亮分钟数：0=常亮；N=交互后保持系统禁止自动息屏 N 分钟
    val restReminderEnabled: Boolean = false, // 休息提醒开关
    val restReminderMinutes: Int = 30,      // 休息提醒分钟数（15/30/45/60）
    val progressDisplayMode: String = "percentage", // percentage | page
    val showTopInfo: Boolean = true,        // 顶部信息区（章节名）
    val showBottomInfo: Boolean = true,     // 底部信息区（进度/时间/电量）
    val batteryPercent: Boolean = false,    // 电量显示：false=图标，true=百分比
    /** 日/夜间独立状态：夜间固定 #0B1622 背景 + #56768A 字体，不匹配任何色标 */
    val nightMode: Boolean = false,
    /** 自定义主题（theme == "custom"）使用的字体色，ARGB */
    val customInkColor: Long = 0xFF5B4636L,
    /** 自定义主题（theme == "custom"）使用的背景色，ARGB */
    val customBgColor: Long = 0xFFF7F0E1L,
)
