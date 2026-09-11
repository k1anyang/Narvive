package com.narvive.app.ui.screen.reader

import android.content.Context
import android.content.res.Configuration
import android.webkit.WebResourceResponse
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import com.narvive.app.data.datastore.NarviveDataStore
import com.narvive.app.domain.model.Annotation
import com.narvive.app.domain.model.AnnotationType
import com.narvive.app.domain.model.Book
import com.narvive.app.R
import com.narvive.app.ui.message.UiMessage
import com.narvive.app.domain.model.FontInfo
import com.narvive.app.domain.model.Bookmark
import com.narvive.app.domain.repository.AnnotationRepository
import com.narvive.app.domain.repository.BookmarkRepository
import com.narvive.app.domain.repository.BookshelfRepository
import com.narvive.app.domain.repository.ReadingRepository
import com.narvive.app.service.ai.AiService
import com.narvive.app.service.ai.FallbackChain
import com.narvive.app.service.ai.PromptRenderer
import com.narvive.app.service.ai.PromptService
import com.narvive.app.service.ai.PromptTemplates
import com.narvive.app.service.ai.TranslationCache
import com.narvive.app.service.reader.EpubReaderController
import com.narvive.app.service.font.FontCatalogRepository
import com.narvive.app.service.font.FontDownloadManager
import com.narvive.app.service.font.FontFileServer
import com.narvive.app.service.font.FontResolver
import com.narvive.app.service.font.ReaderFontConfig
import com.narvive.app.service.font.ReaderFontFace
import com.narvive.app.service.reader.ReaderController
import com.narvive.app.service.reader.ReaderControllerFactory
import com.narvive.app.service.reader.ReadSettings
import com.narvive.app.service.reader.SearchResult
import com.narvive.app.service.reader.TocItem
import com.narvive.app.service.reader.TxtReaderController
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

/** 翻译结果（即时反馈弹卡；管理卡片见 TranslationCard） */
data class TranslationResult(
    val source: String,
    val translation: String,
    val fromCache: Boolean,
)

/** 选区状态：精确到章内字符偏移（半开区间 [start,end)），气泡按 root px 几何定位。
 *  EPUB 下 chapterIndex/start/end 无意义，由 locatorOverride 承载定位 JSON */
data class SelectionState(
    val chapterIndex: Int,
    val start: Int,          // 章内原文起偏移（含）
    val end: Int,            // 章内原文止偏移（不含）
    val text: String,
    /** 最近一次交互（松手点/拖柄点）所在行的矩形（root px；气泡基准定位用） */
    val anchorRect: Rect = Rect.Zero,
    /** 最近一次交互所在行的末端点（root px；箭头指向用） */
    val anchorLineEnd: Offset = Offset.Zero,
    /** EPUB 选区定位 JSON；非空时 selectionLocator 走此值 */
    val locatorOverride: String? = null,
)

/** 高亮点按菜单的定位锚（root px）：点按处所在行 */
data class HighlightMenuAnchor(
    val lineRect: Rect = Rect.Zero,
    val lineEnd: Offset = Offset.Zero,
)

/** 全文搜索跳转后的闪烁目标（TXT 精确到章内字符区间） */
data class SearchFlash(
    val query: String,
    val txtChapter: Int = -1,
    val txtStart: Int = -1,
    val txtEnd: Int = -1,
)

data class ReaderUiState(
    val book: Book? = null,
    val isHudVisible: Boolean = false,
    val isAaPanelOpen: Boolean = false,
    val isTocOpen: Boolean = false,
    val isBrightnessPanelOpen: Boolean = false,
    /** 大跨度跳转遮罩：跳转期间盖住阅读区，等目标页排版稳定后淡出（掩盖字体/间距闪动） */
    val isJumpMaskVisible: Boolean = false,
    /** 自动翻页模式：开启后临时按「分页 + none」渲染，退出后恢复手动翻页设置 */
    val isAutoFlipActive: Boolean = false,
    /** 自动翻页底栏是否呼出（呼出即暂停） */
    val isAutoFlipPaused: Boolean = false,
    /** 自动翻页翻页序号：每次自动翻页 +1，UI 据此从顶部重启扫描线 */
    val autoFlipTurnEpoch: Int = 0,
    val selection: SelectionState? = null,
    /** 选区激活态：根手势裁判豁免（不翻页/不切 HUD/不下拉书签），容器锁定滚动 */
    val selectionActive: Boolean = false,
    val readSettings: ReadSettings = ReadSettings(),
    val progress: Float = 0f,
    val pageTotal: Int = 0,
    val currentPage: Int = 1,
    val currentChapter: String = "",
    val isLoading: Boolean = true,
    val tocItems: List<TocItem> = emptyList(),
    val bookmarks: List<Bookmark> = emptyList(),
    val isBookmarked: Boolean = false,
    val annotations: List<Annotation> = emptyList(),
    val translation: TranslationResult? = null,
    /** 点按已有高亮 → 高亮菜单目标 */
    val tappedHighlight: Annotation? = null,
    /** 高亮菜单定位锚（root px）：点按处所在行 */
    val tappedHighlightAnchor: HighlightMenuAnchor = HighlightMenuAnchor(),
    /** 点按翻译标注 → 翻译管理卡目标 */
    val tappedTranslation: Annotation? = null,
    /** 翻译管理卡的缓存命中标识 */
    val translationFromCache: Boolean = false,
    /** 笔记输入框：pendingNoteText 非空时打开（值为选中文本） */
    val noteInputForSelection: Boolean = false,
    val noteInputForAnnotation: Annotation? = null,
    val isSearchOpen: Boolean = false,
    val isSearching: Boolean = false,
    val searchResults: List<SearchResult> = emptyList(),
    /** 全文搜索跳转后的闪烁目标（3 秒后清除） */
    val searchFlash: SearchFlash? = null,
    /** 操作类错误（翻译失败/未配置 Provider 等）：底部弹窗提示，不影响阅读界面 */
    val error: String? = null,
    /** 致命错误（书籍不存在/文件损坏/打开失败）：整页错误兜底 */
    /**
     * 致命错误（打开书籍失败）。
     *
     * 类型是 [UiMessage] 而非 String：它会让阅读器显示为**阻断式错误页**并一直停留，
     * 是本项目唯一「长期可见」的错误文案，必须随语言变化。详见 docs/i18n.md §2.1。
     */
    val fatalError: UiMessage? = null,
    /** 位置提示浮层 */
    val showPositionTip: Boolean = false,
    val previousLocator: String? = null,
    val tipChapterTitle: String = "",
    val tipProgress: Float = 0f,
    /** 中英字体解析结果（文件已下载时非空） */
    val fontConfig: ReaderFontConfig = ReaderFontConfig(),
    /** EPUB 注入 CSS（含 @font-face 与字体栈），无自定义字体时为 null */
    val fontCss: String? = null,
    /** EPUB 用 @font-face 声明列表（familyName + 本地服务器 URL） */
    val fontFaces: List<ReaderFontFace> = emptyList(),
)

@HiltViewModel
class ReaderViewModel @Inject constructor(
    private val bookshelfRepo: BookshelfRepository,
    private val readingRepo: ReadingRepository,
    private val annotationRepo: AnnotationRepository,
    private val bookmarkRepo: BookmarkRepository,
    private val translationCache: TranslationCache,
    private val aiService: AiService,
    private val fallbackChain: FallbackChain,
    private val promptService: PromptService,
    private val dataStore: NarviveDataStore,
    @ApplicationContext private val appContext: Context,
    private val fontCatalog: FontCatalogRepository,
    private val fontManager: FontDownloadManager,
    private val fontResolver: FontResolver,
    private val fontFileServer: FontFileServer,
) : ViewModel() {

    private val _uiState = MutableStateFlow(ReaderUiState())
    val uiState: StateFlow<ReaderUiState> = _uiState.asStateFlow()

    private var controller: ReaderController? = null
    private var sessionId: String? = null
    private var loadedBookId: String? = null
    private var pageTurns = 0

    /** TXT 滚动桥：TxtViewer 注册，音量键按屏滚动（滚动模式下 controller 翻页语义=跳章，不适合音量键） */
    var txtScrollBridge: ((direction: Int) -> Unit)? = null

    /** 自动翻页桥：由当前阅读视图注册；返回 true=已翻到下一页，false=已到最后一页 */
    var autoFlipTurnBridge: (() -> Boolean)? = null

    /** AI 面板「本章」上下文：当前章纯文本 */
    suspend fun currentChapterText(): String? = controller?.currentChapterText()

    /** 当前定位 locator JSON（AI 保存笔记回跳用） */
    fun currentLocatorJson(): String? = controller?.currentLocator?.value

    /** EPUB WebView 本地字体请求由 EpubViewer 直接拦截并返回该响应。 */
    fun epubFontResponse(fileName: String): WebResourceResponse? =
        fontFileServer.responseFor(fileName)

    /** 清理专用 scope：onCleared 时 viewModelScope 已取消，收尾工作必须用它 */
    private val cleanupScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    fun loadBook(bookId: String, overrideLocator: String? = null) {
        if (loadedBookId == bookId) return // 防止重组重复打开
        loadedBookId = bookId
        viewModelScope.launch {
            val book = bookshelfRepo.getBook(bookId) ?: run {
                _uiState.update { it.copy(isLoading = false, fatalError = UiMessage.Res(R.string.reader_vm_book_missing)) }
                return@launch
            }
            // 文件存在性预检（避免 controller 内部抛 IO 异常被吞）
            val file = java.io.File(book.filePath)
            if (!file.exists() || file.length() == 0L) {
                _uiState.update { it.copy(isLoading = false, fatalError = UiMessage.Res(R.string.reader_vm_file_missing, book.filePath)) }
                return@launch
            }
            try {
                if (controller == null) controller = ReaderControllerFactory.create(book.format)
                // 设置合成：主题/字体/边距/对齐/段距等取全局默认；字号/行距仍按书籍覆盖
                val homeDark = when (dataStore.darkTheme.first()) {
                    "light" -> false
                    "dark" -> true
                    else -> isSystemDark()
                }
                val settings = ReadSettings(
                    theme = dataStore.readingTheme.first(),
                    nightMode = homeDark,
                    customInkColor = dataStore.customInkColor.first(),
                    customBgColor = dataStore.customBgColor.first(),
                    fontSize = book.fontSize,
                    lineHeight = book.lineHeight,
                    margin = dataStore.defaultMargin.first(),
                    fontFamily = dataStore.defaultFontFamily.first(),
                    fontFamilyCjk = dataStore.fontFamilyCjk.first(),
                    fontFamilyLatin = dataStore.fontFamilyLatin.first(),
                    alignment = dataStore.defaultAlignment.first(),
                    paragraphSpacing = dataStore.defaultParagraphSpacing.first(),
                    firstLineIndent = dataStore.defaultFirstLineIndent.first(),
                    publisherStyles = dataStore.publisherStyles.first(),
                    verticalMargin = dataStore.defaultVerticalMargin.first(),
                    horizontalMargin = dataStore.defaultHorizontalMargin.first(),
                    brightness = dataStore.defaultBrightness.first(),
                    followSystemBrightness = dataStore.defaultFollowSystemBrightness.first(),
                    eyeProtection = dataStore.defaultEyeProtection.first(),
                    pageFlipAnimation = dataStore.defaultPageFlipAnimation.first(),
                    autoFlipSpeedSeconds = dataStore.defaultAutoFlipSpeedSeconds.first(),
                    volumeKeyPageTurn = dataStore.volumeKeyPageTurn.first(),
                    bookOpenAnimation = dataStore.bookOpenAnimation.first(),
                    screenOffMinutes = dataStore.screenOffMinutes.first(),
                    restReminderEnabled = dataStore.restReminderEnabled.first(),
                    restReminderMinutes = dataStore.restReminderMinutes.first(),
                    progressDisplayMode = dataStore.progressDisplayMode.first(),
                    showTopInfo = dataStore.showTopInfo.first(),
                    showBottomInfo = dataStore.showBottomInfo.first(),
                    batteryPercent = dataStore.batteryPercent.first(),
                )
                _uiState.update { it.copy(book = book, readSettings = settings, fatalError = null) }
                val ctrl = controller ?: return@launch
                ctrl.open(book.filePath)
                ctrl.applySettings(settings)
                // 字体解析：加载目录 → 中英字体文件定位 →（EPUB）生成注入 CSS
                launch {
                    val catalog = fontCatalog.loadCatalog()
                    fontManager.refreshFromDisk(catalog)
                    refreshFontConfig(_uiState.value.readSettings, catalog)
                }

                // 中英字体全局默认变化（字体设置页修改后实时同步）
                launch {
                    combine(dataStore.fontFamilyCjk, dataStore.fontFamilyLatin) { cjk, latin -> cjk to latin }
                        .distinctUntilChanged()
                        .collect { (cjk, latin) ->
                            val ns = _uiState.value.readSettings.copy(fontFamilyCjk = cjk, fontFamilyLatin = latin)
                            _uiState.update { it.copy(readSettings = ns) }
                            refreshFontConfig(ns, fontCatalog.lastLoaded.orEmpty())
                            controller?.applySettings(ns)
                        }
                }
                // 更多设置全局默认变化（更多设置页修改后返回阅读页时实时同步，无需重进书籍）
                launch {
                    combine(
                        combine(
                            dataStore.volumeKeyPageTurn,
                            dataStore.bookOpenAnimation,
                            dataStore.screenOffMinutes,
                        ) { a, b, c -> Triple(a, b, c) },
                        combine(
                            dataStore.restReminderEnabled,
                            dataStore.restReminderMinutes,
                            dataStore.progressDisplayMode,
                        ) { a, b, c -> Triple(a, b, c) },
                        combine(
                            dataStore.showTopInfo,
                            dataStore.showBottomInfo,
                            dataStore.batteryPercent,
                        ) { a, b, c -> Triple(a, b, c) },
                        dataStore.publisherStyles,
                    ) { g1, g2, g3, publisherStyles ->
                        val ns = _uiState.value.readSettings.copy(
                            volumeKeyPageTurn = g1.first,
                            bookOpenAnimation = g1.second,
                            screenOffMinutes = g1.third,
                            restReminderEnabled = g2.first,
                            restReminderMinutes = g2.second,
                            progressDisplayMode = g2.third,
                            publisherStyles = publisherStyles,
                            showTopInfo = g3.first,
                            showBottomInfo = g3.second,
                            batteryPercent = g3.third,
                        )
                        _uiState.update { it.copy(readSettings = ns) }
                        // 出版方样式开关会改变 EPUB 字体注入策略（fontCss），需同步刷新
                        refreshFontConfig(ns, fontCatalog.lastLoaded.orEmpty())
                    }.collect { }
                }
            // 采集 controller 状态（进度/章节/locator），驱动 HUD 与书签态
            launch {
                ctrl.progress.collect { p ->
                    // F8：翻页统计口径统一——TXT 滚动由 controller 按 ~2000 字单位自计，
                    // VM 侧仅统计 EPUB 的真实分页变化（滚动模式下 progress 随滚动高频变化，计数会失真）
                    if (ctrl is EpubReaderController && p != _uiState.value.progress) pageTurns++
                    _uiState.update { state ->
                        if (state.showPositionTip) {
                            state.copy(progress = p, tipProgress = p)
                        } else {
                            state.copy(progress = p)
                        }
                    }
                }
            }
            launch { ctrl.currentChapter.collect { ch ->
                _uiState.update { state ->
                    if (state.showPositionTip) {
                        state.copy(currentChapter = ch, tipChapterTitle = ch)
                    } else {
                        state.copy(currentChapter = ch)
                    }
                }
            } }
            launch { ctrl.currentLocator.collect { updateBookmarkState() } }
            // EPUB 跳转遮罩：目标页（href 与跳转目标一致）成为当前页后，稍作稳定再淡出遮罩
            if (ctrl is EpubReaderController) {
                launch {
                    ctrl.currentLocator.collect { locJson ->
                        if (!_uiState.value.isJumpMaskVisible || locJson == null) return@collect
                        val href = runCatching {
                            org.json.JSONObject(locJson).optString("href").substringBefore('#').takeIf { it.isNotBlank() }
                        }.getOrNull() ?: return@collect
                        val target = ctrl.lastJumpTargetHref
                        if (target != null && href != target) return@collect
                        delay(300)
                        _uiState.update { it.copy(isJumpMaskVisible = false) }
                    }
                }
            }

            sessionId = readingRepo.startSession(bookId)

            // 恢复位置：笔记跳转 locator 优先，其次上次阅读位置
            val restoreLocator = overrideLocator ?: book.currentLocator
            if (book.isFinished.not() && restoreLocator != null) {
                ctrl.goToLocator(restoreLocator)
            }

            // TXT：目录来自章节解析
            (ctrl as? TxtReaderController)?.let { txt ->
                _uiState.update {
                    it.copy(tocItems = txt.chapters.mapIndexed { i, c ->
                        TocItem(title = c.title, locatorJson = txt.locatorFor(i, 0))
                    })
                }
                // TXT: estimate pages from character count (800 chars/page rough)
                val txtChars = txt.fullText.length
                val estTotalPages = (txtChars / 800).coerceAtLeast(1)
                _uiState.update { it.copy(pageTotal = estTotalPages) }
                launch { ctrl.progress.collect { p ->
                    val cp = (p * estTotalPages).toInt().coerceIn(1, estTotalPages)
                    _uiState.update { it.copy(currentPage = cp) }
                } }
            }
            if (ctrl is EpubReaderController) {
                launch { ctrl.tocItems.collect { toc -> _uiState.update { it.copy(tocItems = toc) } } }
                // 恢复在册高亮/翻译渲染（翻页/重进后需重新注入 DOM）
                launch {
                    annotationRepo.observeByBook(bookId).collect { list ->
                        ctrl.requestHighlightRefresh(list)
                        ctrl.requestTranslationRefresh(list)
                    }
                }
            // EPUB: collect global page data (lazy from onPageChanged)
            launch { ctrl.globalTotalPages.collect { tp -> _uiState.update { it.copy(pageTotal = tp) } } }
            launch { ctrl.globalCurrentPage.collect { cp -> _uiState.update { it.copy(currentPage = cp) } } }
            }

            // 观察本书书签：进度按 locator 实时重算（排版/间距变化后仍与真实位置匹配）
            launch {
                bookmarkRepo.observeByBook(bookId).collect { list ->
                    val mapped = list.map { bm ->
                        ctrl.progressOfLocator(bm.locatorJson)?.let { bm.copy(progress = it) } ?: bm
                    }
                    _uiState.update { it.copy(bookmarks = mapped) }
                    updateBookmarkState()
                }
            }

            // 观察本书标注（高亮/笔记/翻译内嵌渲染数据源）
            launch {
                annotationRepo.observeByBook(bookId).collect { list ->
                    _uiState.update { it.copy(annotations = list) }
                }
            }

            _uiState.update { it.copy(isLoading = false) }
            } catch (e: Exception) {
                // TXT 空文件/EPUB 解析失败等统一捕获并暴露错误态（不再静默崩溃或白屏）
                android.util.Log.e("ReaderViewModel", "loadBook failed: ${book.title}", e)
                _uiState.update { it.copy(isLoading = false, fatalError = UiMessage.Res(R.string.reader_vm_open_failed, e.message ?: e.javaClass.simpleName)) }
            }
        }
    }

    private fun chapterOfLocator(locatorJson: String?): String? {
        if (locatorJson.isNullOrBlank()) return null
        return runCatching {
            val obj = org.json.JSONObject(locatorJson)
            when {
                obj.has("href") -> {
                    val href = obj.optString("href").substringBefore('#').takeIf { it.isNotBlank() }
                    if (href != null) "epub:$href" else null
                }
                obj.has("chapter") -> "txt:${obj.optInt("chapter")}"
                else -> null
            }
        }.getOrNull()
    }

    private fun updateBookmarkState() {
        // 书签按“当前位置附近”判定（同页/同块）；翻页/滚动离开后，非书签页不再显示书签
        val ctrl = controller ?: return
        val loc = ctrl.currentLocator.value
        val marked = _uiState.value.bookmarks.any { ctrl.isNearBookmark(loc, it.locatorJson) }
        _uiState.update { it.copy(isBookmarked = marked) }
    }

    fun toggleHud() { _uiState.update { it.copy(isHudVisible = !it.isHudVisible, selection = null, selectionActive = false, showPositionTip = false) } }
    fun hideHud() { _uiState.update { it.copy(isHudVisible = false, showPositionTip = false) } }
    fun toggleAaPanel() { _uiState.update { it.copy(isAaPanelOpen = !it.isAaPanelOpen) } }
    fun closeAaPanel() { _uiState.update { it.copy(isAaPanelOpen = false) } }
    fun toggleToc() { _uiState.update { it.copy(isTocOpen = !it.isTocOpen) } }
    fun toggleBrightnessPanel() { _uiState.update { it.copy(isBrightnessPanelOpen = !it.isBrightnessPanelOpen) } }

    // ---------- 自动翻页 ----------

    /** 从 Aa 面板打开自动翻页：关闭所有面板并进入自动翻页模式 */
    fun startAutoFlip() {
        _uiState.update {
            it.copy(
                isAutoFlipActive = true,
                isAutoFlipPaused = false,
                autoFlipTurnEpoch = 0,
                isAaPanelOpen = false,
                isBrightnessPanelOpen = false,
                isHudVisible = false,
                selection = null,
                selectionActive = false,
            )
        }
    }

    fun stopAutoFlip() {
        _uiState.update {
            it.copy(isAutoFlipActive = false, isAutoFlipPaused = false, autoFlipTurnEpoch = 0)
        }
    }

    /** 呼出/收起自动翻页底栏（呼出即暂停，收起即恢复） */
    fun toggleAutoFlipBar() {
        if (!_uiState.value.isAutoFlipActive) return
        _uiState.update { it.copy(isAutoFlipPaused = !it.isAutoFlipPaused) }
    }

    /** 自动翻页速度加减（步进 5，范围 10..120） */
    fun adjustAutoFlipSpeed(delta: Int) {
        val cur = _uiState.value.readSettings.autoFlipSpeedSeconds
        val next = (cur + delta).coerceIn(10, 120)
        _uiState.update { it.copy(readSettings = it.readSettings.copy(autoFlipSpeedSeconds = next)) }
        viewModelScope.launch { dataStore.setDefaultAutoFlipSpeedSeconds(next) }
    }

    /** 扫描线刷到底后请求翻页；由阅读视图桥判断是否还有下一页 */
    fun autoFlipRequestTurn() {
        val bridge = autoFlipTurnBridge
        if (bridge == null) {
            // 桥尚未注册（首次极短竞态）时兜底：EPUB 直接瞬时翻页
            val epub = controller as? EpubReaderController
            if (epub != null) {
                viewModelScope.launch { epub.nextPageInstant() }
                _uiState.update { it.copy(autoFlipTurnEpoch = it.autoFlipTurnEpoch + 1) }
            }
            return
        }
        val advanced = bridge()
        if (advanced) {
            _uiState.update { it.copy(autoFlipTurnEpoch = it.autoFlipTurnEpoch + 1) }
        } else {
            stopAutoFlip()
            android.widget.Toast.makeText(appContext, appContext.getString(R.string.reader_vm_auto_flip_finished), android.widget.Toast.LENGTH_SHORT).show()
        }
    }

    /** volume key: TXT scroll bridge; EPUB navigator page turn */
    fun volumeScroll(direction: Int) {
        val bridge = txtScrollBridge
        // EPUB 上下滚动模式下音量键应滚动一屏，而不是跳章；TXT 三种模式都走各自的滚动/翻页桥
        val isUpDown = _uiState.value.readSettings.pageFlipAnimation == "updown"
        if (bridge != null && (controller is TxtReaderController || isUpDown)) {
            bridge(direction)
        } else if (isUpDown) {
            // 滚动桥尚未注册时忽略音量键，避免退化成跳章
        } else {
            viewModelScope.launch {
                if (direction > 0) controller?.nextPage() else controller?.previousPage()
            }
        }
    }

    fun previousPage() { viewModelScope.launch { controller?.previousPage() } }
    fun nextPage() { viewModelScope.launch { controller?.nextPage() } }

    /** resolve current chapter index from locator (href for EPUB, chapter id for TXT), NOT from title strings */
    fun currentChapterIndex(): Int {
        val toc = _uiState.value.tocItems
        if (toc.isEmpty()) return -1
        val locJson = controller?.currentLocator?.value ?: return -1
        return runCatching {
            val obj = org.json.JSONObject(locJson)
            if (obj.has("chapter")) {
                // TXT: locator {"chapter":i,...}
                obj.optInt("chapter").coerceIn(0, toc.lastIndex)
            } else if (obj.has("href")) {
                // EPUB: use spine index (same-source readingOrder comparison)
                controller?.currentSpineIndex() ?: -1
            } else {
                -1
            }
        }.getOrDefault(-1)
    }

    /** 目录面板当前章索引：TXT 用 locator.chapter；EPUB 复用阅读页左上角章节名逻辑（按标题匹配，覆盖章节中间页） */
    fun currentTocIndex(): Int {
        val toc = _uiState.value.tocItems
        if (toc.isEmpty()) return -1
        val locJson = controller?.currentLocator?.value
        return runCatching {
            val obj = locJson?.let { org.json.JSONObject(it) }
            if (obj?.has("chapter") == true) {
                obj.optInt("chapter").coerceIn(0, toc.lastIndex)
            } else {
                // EPUB：优先按当前章标题匹配（onPageChanged 已对任意页解析出所属章标题）
                val title = _uiState.value.currentChapter
                val byTitle = if (title.isNotBlank()) toc.indexOfFirst { it.title == title } else -1
                if (byTitle >= 0) {
                    byTitle
                } else {
                    // 兜底：href 匹配
                    val href = obj?.optString("href")?.substringBefore('#').orEmpty()
                    toc.indexOfFirst {
                        runCatching { org.json.JSONObject(it.locatorJson).optString("href").substringBefore('#') }
                            .getOrDefault("") == href
                    }
                }
            }
        }.getOrDefault(-1)
    }
    /** 大跨度跳转遮罩：显示主题色遮罩并带兜底超时收起 */
    private var jumpMaskSeq = 0
    private fun showJumpMask() {
        val seq = ++jumpMaskSeq
        _uiState.update { it.copy(isJumpMaskVisible = true) }
        viewModelScope.launch {
            delay(2500)
            if (seq == jumpMaskSeq) _uiState.update { it.copy(isJumpMaskVisible = false) }
        }
    }

    /** 用户主动跳转（章节按钮/目录/笔记回跳）：EPUB 标记时间戳以抑制滚底自动续章。
     *  注：上/下一章等短跨度跳转不使用主题色遮罩（仅进度条大跨度跳转使用）。 */
    private fun launchUserJump(locatorJson: String) {
        viewModelScope.launch {
            val epub = controller as? EpubReaderController
            if (epub != null) {
                epub.goToLocatorUser(locatorJson)
            } else {
                controller?.goToLocator(locatorJson)
            }
        }
    }

    /** previous chapter (spine-based navigation) */
    fun previousChapter() {
        val idx = currentChapterIndex()
        if (idx > 0) {
            val loc = controller?.locatorForSpineItem(idx - 1) ?: return
            savePreviousPosition()
            launchUserJump(loc)
            _uiState.update {
                it.copy(
                    showPositionTip = true,
                    tipChapterTitle = controller?.chapterTitleForSpineItem(idx - 1) ?: "",
                )
            }
        }
    }

    /** next chapter (spine-based navigation) */
    fun nextChapter() {
        val idx = currentChapterIndex()
        val count = controller?.spineItemCount() ?: 0
        if (idx >= 0 && idx < count - 1) {
            val loc = controller?.locatorForSpineItem(idx + 1) ?: return
            savePreviousPosition()
            launchUserJump(loc)
            _uiState.update {
                it.copy(
                    showPositionTip = true,
                    tipChapterTitle = controller?.chapterTitleForSpineItem(idx + 1) ?: "",
                )
            }
        }
    }
    private fun savePreviousPosition() {
        val loc = controller?.currentLocator?.value ?: return
        _uiState.update { it.copy(previousLocator = loc) }
    }
    /** 回退到上次位置（位置提示返回按钮） */
    fun goBackToPreviousPosition() {
        val prev = _uiState.value.previousLocator ?: return
        launchUserJump(prev)
    }

    /** 跳转章节并显示位置提示 */
    private fun jumpToChapter(item: TocItem) {
        launchUserJump(item.locatorJson)
        _uiState.update {
            it.copy(
                showPositionTip = true,
                tipChapterTitle = item.title,
            )
        }
    }

    /** 拖动进度条时显示提示 */
    fun showSeekTip(progress: Float) {
        val loc = controller?.currentLocator?.value
        // chapterTitleAtProgress returns the correct title for any position
        // (spine titles are pre-populated from TOC); null only when spine
        // is still being computed, in which case we show nothing rather
        // than a misleading linear estimate.
        _uiState.update {
            it.copy(
                showPositionTip = true,
                previousLocator = loc ?: it.previousLocator,
                tipProgress = progress,
                tipChapterTitle = controller?.chapterTitleAtProgress(progress) ?: "",
            )
        }
    }
    fun hidePositionTip() {
        _uiState.update { it.copy(showPositionTip = false) }
    }

    fun jumpToLocator(locatorJson: String) {
        launchUserJump(locatorJson)
    }

    /** 进度条拖动跳转（B3.7） */
    fun seekTo(targetProgress: Float) {
        val ctrl = controller ?: return
        viewModelScope.launch {
            when (ctrl) {
                is TxtReaderController -> {
                    val (idx, offset) = ctrl.progressToPosition(targetProgress)
                    ctrl.goToLocator(ctrl.locatorFor(idx, offset))
                }
                is EpubReaderController -> {
                    showJumpMask()
                    ctrl.goToProgression(targetProgress)
                }
                else -> {}
            }
        }
    }

    /** Aa 面板 / 亮度面板即改即存：UI + controller + DataStore 全局默认 + 书籍级覆盖 */
    /** 根据当前中英字体选择刷新 fontConfig 与（EPUB）注入 CSS */
    /** 根据当前中英字体选择刷新 fontConfig、注入 CSS 与 @font-face 声明 */
    private fun refreshFontConfig(settings: ReadSettings, catalog: List<FontInfo>) {
        val config = ReaderFontConfig(
            cjk = fontResolver.specOf(settings.fontFamilyCjk, catalog),
            latin = fontResolver.specOf(settings.fontFamilyLatin, catalog),
        )
        val isEpub = _uiState.value.book?.format == "EPUB"
        var faces: List<ReaderFontFace> = emptyList()
        var css: String? = null
        if (isEpub && config.hasCustom && !settings.publisherStyles) {
            runCatching {
                val base = fontFileServer.baseUrl()
                faces = listOfNotNull(
                    config.cjk?.let { ReaderFontFace(it.familyName, "$base/fonts/${it.fileName}") },
                    config.latin?.let { ReaderFontFace(it.familyName, "$base/fonts/${it.fileName}") },
                )
                css = fontResolver.buildEpubFontCss(config.cjk, config.latin, base)
            }.onFailure { e -> android.util.Log.w("NarviveFont", "build font css failed: ${e.message}") }
        }
        android.util.Log.d(
            "NarviveFont",
            "refreshFontConfig cjk=${settings.fontFamilyCjk} latin=${settings.fontFamilyLatin} " +
                "specCjk=${config.cjk?.familyName} specLatin=${config.latin?.familyName} faces=${faces.size} cssLen=${css?.length}"
        )
        _uiState.update { it.copy(fontConfig = config, fontCss = css, fontFaces = faces) }
    }
    fun applySettings(settings: ReadSettings) {
        _uiState.update { it.copy(readSettings = settings) }
        viewModelScope.launch {
            controller?.applySettings(settings)
            dataStore.setReadingTheme(settings.theme)
            dataStore.setNightMode(settings.nightMode)
            dataStore.setCustomInkColor(settings.customInkColor)
            dataStore.setCustomBgColor(settings.customBgColor)
            dataStore.setDefaultFontSize(settings.fontSize)
            dataStore.setDefaultLineHeight(settings.lineHeight)
            dataStore.setDefaultMargin(settings.margin)
            dataStore.setDefaultFontFamily(settings.fontFamily)
            dataStore.setFontFamilyCjk(settings.fontFamilyCjk)
            dataStore.setFontFamilyLatin(settings.fontFamilyLatin)
            dataStore.setDefaultAlignment(settings.alignment)
            dataStore.setDefaultParagraphSpacing(settings.paragraphSpacing)
            dataStore.setDefaultFirstLineIndent(settings.firstLineIndent)
            dataStore.setPublisherStyles(settings.publisherStyles)
            dataStore.setDefaultVerticalMargin(settings.verticalMargin)
            dataStore.setDefaultHorizontalMargin(settings.horizontalMargin)
            dataStore.setDefaultBrightness(settings.brightness)
            dataStore.setDefaultFollowSystemBrightness(settings.followSystemBrightness)
            dataStore.setDefaultEyeProtection(settings.eyeProtection)
            dataStore.setDefaultPageFlipAnimation(settings.pageFlipAnimation)
            dataStore.setDefaultAutoFlipSpeedSeconds(settings.autoFlipSpeedSeconds)
            dataStore.setVolumeKeyPageTurn(settings.volumeKeyPageTurn)
            dataStore.setBookOpenAnimation(settings.bookOpenAnimation)
            dataStore.setScreenOffMinutes(settings.screenOffMinutes)
            dataStore.setRestReminderEnabled(settings.restReminderEnabled)
            dataStore.setRestReminderMinutes(settings.restReminderMinutes)
            dataStore.setProgressDisplayMode(settings.progressDisplayMode)
            dataStore.setShowTopInfo(settings.showTopInfo)
            dataStore.setShowBottomInfo(settings.showBottomInfo)
            dataStore.setBatteryPercent(settings.batteryPercent)
            val book = _uiState.value.book ?: return@launch
            bookshelfRepo.updateReadingSettings(book.id, settings.fontSize, settings.lineHeight, book.readingMode)
        }
    }

    fun toggleBookmark() {
        val book = _uiState.value.book ?: return
        val ctrl = controller ?: return
        val locator = ctrl.currentLocator.value ?: return
        if (chapterOfLocator(locator) == null) return  // 非法 locator 不处理
        viewModelScope.launch {
            // 书签按位置匹配（同页/同块）：当前位置已有书签则删除，否则在当前位置新增
            val existing = _uiState.value.bookmarks.firstOrNull { ctrl.isNearBookmark(locator, it.locatorJson) }
            if (existing != null) {
                bookmarkRepo.delete(existing.id)
            } else {
                // Extract a short text preview from near the bookmark location
                val preview = runCatching {
                    val text = ctrl.currentChapterText() ?: return@runCatching null
                    val prog = runCatching {
                        org.json.JSONObject(locator).optJSONObject("locations")?.optDouble("progression")
                    }.getOrNull() ?: 0.0
                    val center = (prog * text.length).toInt().coerceIn(0, text.length - 1)
                    val start = (center - 25).coerceAtLeast(0)
                    val end = (center + 50).coerceAtMost(text.length)
                    text.substring(start, end).trim().takeIf { it.isNotBlank() }
                }.getOrNull()
                bookmarkRepo.insert(
                    Bookmark(
                        id = UUID.randomUUID().toString(),
                        bookId = book.id,
                        locatorJson = locator,
                        chapterTitle = _uiState.value.currentChapter.ifEmpty { null },
                        previewText = preview,
                        progress = _uiState.value.progress,
                        createdAt = System.currentTimeMillis(),
                    )
                )
                _uiState.update { it.copy(isBookmarked = true) }
            }
        }
    }

    fun saveProgress() {
        viewModelScope.launch { persistProgress() }
    }

    /** 立即持久化当前进度并等待完成（进入角色对话前调用，确保角色卡读到最新进度） */
    suspend fun saveProgressNow() {
        persistProgress()
    }

    private suspend fun persistProgress() {
        val book = _uiState.value.book ?: return
        val ctrl = controller ?: return
        bookshelfRepo.updateProgress(
            id = book.id,
            progress = ctrl.progress.value,
            locatorJson = ctrl.currentLocator.value ?: book.currentLocator ?: "",
            chapterTitle = ctrl.currentChapter.value.ifEmpty { null },
        )
    }

    // ---------- 选区 ----------

    /** 选区更新（SelectionOverlay 上报；null = 清除选区） */
    fun onSelectionUpdated(selection: SelectionState?) {
        _uiState.update { it.copy(selection = selection, isHudVisible = false) }
    }

    /** 选区激活态开关（SelectionOverlay 上报；驱动根裁判豁免/容器锁定/书签下拉屏蔽） */
    fun setSelectionActive(active: Boolean) {
        _uiState.update { it.copy(selectionActive = active) }
    }

    /** EPUB 选区回调（P2：来自 Readium SelectableNavigator.currentSelection()）。
     *  locatorJson 为标准 Locator JSON（含 CFI），直接作为标注定位持久化；anchorRect 为 root px 几何。 */
    fun onEpubSelection(text: String, locatorJson: String, anchorRect: Rect) {
        if (text.isBlank()) {
            clearSelection()
            return
        }
        _uiState.update {
            it.copy(
                selection = SelectionState(
                    chapterIndex = 0, start = 0, end = 0,
                    text = text,
                    anchorRect = anchorRect,
                    anchorLineEnd = Offset(anchorRect.right, anchorRect.center.y),
                    locatorOverride = locatorJson,
                ),
                // EPUB 原生选区激活期间同样豁免根裁判（不翻页/不下拉书签）
                selectionActive = true,
                isHudVisible = false,
            )
        }
    }

    fun clearSelection() {
        _uiState.update { it.copy(selection = null, selectionActive = false) }
        // EPUB：同步清除 WebView 原生选区（幂等）
        (controller as? EpubReaderController)?.clearSelectionNow()
    }

    private fun selectionLocator(sel: SelectionState): String =
        sel.locatorOverride ?: """{"chapter":${sel.chapterIndex},"start":${sel.start},"end":${sel.end}}"""

    /** 保存标注时附带的当前章名 + 全书进度（需求4 展示元数据） */
    private fun currentAnnotationMeta(): Pair<String, Float?> {
        val chapterTitle = _uiState.value.currentChapter.ifBlank { _uiState.value.book?.currentChapter ?: "" }
        val progress = controller?.progress?.value
        return chapterTitle to progress
    }

    /** 默认高亮色（黄）：加笔记自动附带的高亮用此色 */
    private val defaultHighlightColor: Long = 0xFFFACC15L

    fun highlightSelection(color: Long) {
        val book = _uiState.value.book ?: return
        val sel = _uiState.value.selection ?: return
        viewModelScope.launch {
            val (chapterTitle, progress) = currentAnnotationMeta()
            val ann = Annotation(
                id = UUID.randomUUID().toString(),
                bookId = book.id,
                locatorJson = selectionLocator(sel),
                selectedText = sel.text,
                type = AnnotationType.HIGHLIGHT,
                color = color,
                note = null,
                translation = null,
                rewrittenText = null,
                rewriteInstruction = null,
                providerId = null,
                createdAt = System.currentTimeMillis(),
                updatedAt = System.currentTimeMillis(),
                chapterTitle = chapterTitle,
                progress = progress,
            )
            annotationRepo.insertOrUpdate(ann)
            // EPUB：清除 WebView 原生选区；高亮渲染由 annotations 观察者 → requestHighlightRefresh → applyHighlightDecorations 完成
            (controller as? EpubReaderController)?.clearSelectionNow()
            _uiState.update { it.copy(selection = null, selectionActive = false) }
        }
    }

    fun openNoteInputForSelection() { _uiState.update { it.copy(noteInputForSelection = true) } }

    fun openNoteInputForAnnotation(annotation: Annotation) {
        _uiState.update { it.copy(noteInputForAnnotation = annotation, tappedHighlight = null) }
    }

    fun dismissNoteInput() { _uiState.update { it.copy(noteInputForSelection = false, noteInputForAnnotation = null) } }

    /** 保存笔记：选区新建 NOTE（自动附带默认黄色高亮），或为已有高亮附加笔记 */
    fun saveNote(noteText: String) {
        val book = _uiState.value.book ?: return
        val sel = _uiState.value.selection
        val target = _uiState.value.noteInputForAnnotation
        viewModelScope.launch {
            if (target != null) {
                annotationRepo.insertOrUpdate(target.copy(note = noteText, updatedAt = System.currentTimeMillis()))
            } else if (sel != null) {
                val (chapterTitle, progress) = currentAnnotationMeta()
                annotationRepo.insertOrUpdate(
                    Annotation(
                        id = UUID.randomUUID().toString(),
                        bookId = book.id,
                        locatorJson = selectionLocator(sel),
                        selectedText = sel.text,
                        type = AnnotationType.NOTE,
                        color = defaultHighlightColor,
                        note = noteText,
                        translation = null,
                        rewrittenText = null,
                        rewriteInstruction = null,
                        providerId = null,
                        createdAt = System.currentTimeMillis(),
                        updatedAt = System.currentTimeMillis(),
                        chapterTitle = chapterTitle,
                        progress = progress,
                    )
                )
            }
            _uiState.update { it.copy(selection = null, selectionActive = false, noteInputForSelection = false, noteInputForAnnotation = null) }
            // EPUB：同步清除 WebView 原生选区
            (controller as? EpubReaderController)?.clearSelectionNow()
        }
    }

    // ---------- 高亮菜单 ----------

    fun onHighlightTapped(annotation: Annotation, anchor: HighlightMenuAnchor = HighlightMenuAnchor()) {
        _uiState.update {
            it.copy(
                tappedHighlight = annotation,
                tappedHighlightAnchor = anchor,
                selection = null,
                selectionActive = false,
                isHudVisible = false,
            )
        }
    }

    fun dismissHighlightMenu() { _uiState.update { it.copy(tappedHighlight = null) } }

    fun changeHighlightColor(annotation: Annotation, color: Long) {
        viewModelScope.launch {
            annotationRepo.insertOrUpdate(annotation.copy(color = color, updatedAt = System.currentTimeMillis()))
            // EPUB：annotations 观察者 → applyHighlightDecorations 的 DiffUtil 按 id 增量更新颜色
            _uiState.update { it.copy(tappedHighlight = null) }
        }
    }

    fun deleteAnnotation(annotation: Annotation) {
        viewModelScope.launch {
            annotationRepo.delete(annotation.id)
            _uiState.update { it.copy(tappedHighlight = null, tappedTranslation = null) }
        }
    }

    // ---------- 翻译 ----------

    fun translateSelection() {
        val book = _uiState.value.book ?: return
        val sel = _uiState.value.selection ?: return
        if (sel.text.isBlank()) return
        viewModelScope.launch {
            _uiState.update { it.copy(selection = null, selectionActive = false) }
            // EPUB：同步清除 WebView 原生选区
            (controller as? EpubReaderController)?.clearSelectionNow()
            translateAndShow(book.id, sel)
        }
    }

    /** 高亮菜单「翻译」：对已高亮文本发起翻译（写 TRANSLATION 标注并弹卡），与选区翻译共用链路 */
    fun translateAnnotationText(annotation: Annotation) {
        val book = _uiState.value.book ?: return
        if (annotation.selectedText.isBlank()) return
        viewModelScope.launch {
            _uiState.update { it.copy(tappedHighlight = null) }
            translateAndShow(
                book.id,
                SelectionState(
                    chapterIndex = 0, start = 0, end = 0,
                    text = annotation.selectedText,
                    locatorOverride = annotation.locatorJson,
                ),
            )
        }
    }

    /** 翻译公共链路：缓存 → 降级链 → 写 TRANSLATION 标注 → 弹翻译卡 */
    private suspend fun translateAndShow(bookId: String, sel: SelectionState) {
        val cached = translationCache.getCached(bookId, sel.text)
        if (cached != null) {
            saveTranslationAnnotation(bookId, sel, cached, providerId = null)
            _uiState.update { it.copy(translation = TranslationResult(sel.text, cached, fromCache = true)) }
            return
        }
        val providers = fallbackChain.getEnabledProviders()
        if (providers.isEmpty()) {
            _uiState.update { it.copy(error = appContext.getString(R.string.reader_vm_no_ai_provider)) }
            return
        }
        var done = false
        var lastError: String? = null
        for (provider in providers) {
            if (provider.isDegraded) continue
            val prompt = PromptRenderer.render(
                promptService.get(PromptTemplates.TRANSLATE),
                mapOf("text" to sel.text, "contextBlock" to ""),
            )
            val result = aiService.simpleChat(
                provider = provider,
                messages = listOf(com.narvive.app.service.ai.AiMessage("user", prompt)),
            )
            result.onSuccess { translation ->
                fallbackChain.recordSuccess(provider.id)
                // F2：带定位的 TRANSLATION 标注本身即缓存（findTranslation 按 书+原文+语言 命中），
                // 不再经 TranslationCache.cache 额外写空 locator 记录，消除幽灵标注
                saveTranslationAnnotation(bookId, sel, translation, provider.id)
                _uiState.update { it.copy(translation = TranslationResult(sel.text, translation, fromCache = false)) }
                done = true
            }.onFailure { e ->
                fallbackChain.recordFailure(provider.id)
                lastError = e.message
            }
            if (done) break
        }
        if (!done) {
            _uiState.update { it.copy(error = appContext.getString(R.string.reader_vm_translate_failed, lastError ?: appContext.getString(R.string.reader_vm_all_providers_unavailable))) }
        }
    }

    /** 翻译写为 TRANSLATION 标注 → 红色小字内嵌渲染的数据源。
     *  id 由 书+定位 派生：同一位置重复翻译走 REPLACE，不产生重复内嵌条目 */
    private suspend fun saveTranslationAnnotation(bookId: String, sel: SelectionState, translation: String, providerId: String?) {
        val locator = selectionLocator(sel)
        val (chapterTitle, progress) = currentAnnotationMeta()
        annotationRepo.insertOrUpdate(
            Annotation(
                id = "tr_${bookId}_${locator.hashCode().toString(16)}",
                bookId = bookId,
                locatorJson = locator,
                selectedText = sel.text,
                type = AnnotationType.TRANSLATION,
                color = null,
                note = null,
                translation = translation,
                rewrittenText = null,
                rewriteInstruction = null,
                providerId = providerId,
                createdAt = System.currentTimeMillis(),
                updatedAt = System.currentTimeMillis(),
                chapterTitle = chapterTitle,
                progress = progress,
            )
        )
    }

    /** EPUB 翻译红下划线点按：按 id 查找标注并弹翻译卡 */
    fun onTranslationTapId(annotationId: String) {
        val ann = _uiState.value.annotations.find { it.id == annotationId } ?: return
        onTranslationTapped(ann)
    }

    fun onTranslationTapped(annotation: Annotation) {
        val bookId = _uiState.value.book?.id
        viewModelScope.launch {
            val fromCache = bookId != null && translationCache.getCached(bookId, annotation.selectedText) != null
            _uiState.update {
                it.copy(
                    tappedTranslation = annotation,
                    translationFromCache = fromCache,
                    selection = null,
                    isHudVisible = false,
                )
            }
        }
    }

    fun dismissTranslationCard() { _uiState.update { it.copy(tappedTranslation = null) } }

    /** 重新翻译：跳过缓存强制调用 AI，更新标注与缓存 */
    fun retranslate(annotation: Annotation) {
        if (_uiState.value.book == null) return
        viewModelScope.launch {
            _uiState.update { it.copy(tappedTranslation = null) }
            val providers = fallbackChain.getEnabledProviders()
            if (providers.isEmpty()) {
                _uiState.update { it.copy(error = appContext.getString(R.string.reader_vm_no_ai_provider)) }
                return@launch
            }
            var done = false
            var lastError: String? = null
            for (provider in providers) {
                if (provider.isDegraded) continue
                val prompt = PromptRenderer.render(
                    promptService.get(PromptTemplates.TRANSLATE),
                    mapOf("text" to annotation.selectedText, "contextBlock" to ""),
                )
                val result = aiService.simpleChat(
                    provider = provider,
                    messages = listOf(com.narvive.app.service.ai.AiMessage("user", prompt)),
                )
                result.onSuccess { translation ->
                    fallbackChain.recordSuccess(provider.id)
                    annotationRepo.insertOrUpdate(
                        annotation.copy(translation = translation, providerId = provider.id, updatedAt = System.currentTimeMillis())
                    )
                    done = true
                }.onFailure { e ->
                    fallbackChain.recordFailure(provider.id)
                    lastError = e.message
                }
                if (done) break
            }
            if (!done) {
                _uiState.update { it.copy(error = appContext.getString(R.string.reader_vm_retranslate_failed, lastError ?: appContext.getString(R.string.reader_vm_all_providers_unavailable))) }
            }
        }
    }

    fun dismissTranslation() { _uiState.update { it.copy(translation = null) } }
    fun clearError() { _uiState.update { it.copy(error = null) } }

    // ---------- 书内搜索 ----------

    fun openSearch() { _uiState.update { it.copy(isSearchOpen = true, isHudVisible = false) } }
    fun closeSearch() { _uiState.update { it.copy(isSearchOpen = false, searchResults = emptyList(), isSearching = false) } }

    /** 最近一次搜索关键词（跳转后闪烁匹配区间用） */
    private var lastSearchQuery = ""

    fun performSearch(query: String) {
        val ctrl = controller ?: return
        if (query.isBlank()) {
            _uiState.update { it.copy(searchResults = emptyList(), isSearching = false) }
            return
        }
        lastSearchQuery = query
        viewModelScope.launch {
            _uiState.update { it.copy(isSearching = true) }
            ctrl.search(query) { results ->
                _uiState.update { it.copy(searchResults = results, isSearching = false) }
            }
        }
    }

    /** 点击搜索结果：跳转 + 对匹配内容 3 秒闪烁（TXT 精确到字符区间，EPUB 见 EpubViewer JS） */
    fun jumpToSearchResult(result: SearchResult) {
        val query = lastSearchQuery
        _uiState.update { it.copy(isSearchOpen = false) }
        launchUserJump(result.locatorJson)
        viewModelScope.launch {
            // 等跳转定位完成后再置闪烁，避免分页模式重测在旧位置触发导致跳转失败
            delay(300)
            val flash = if (controller is TxtReaderController) {
                val (ch, off) = runCatching {
                    org.json.JSONObject(result.locatorJson).let { it.optInt("chapter", 0) to it.optInt("offset", 0) }
                }.getOrDefault(0 to 0)
                SearchFlash(query = query, txtChapter = ch, txtStart = off, txtEnd = off + query.length)
            } else {
                SearchFlash(query = query)
            }
            _uiState.update { it.copy(searchFlash = flash) }
            delay(3000)
            _uiState.update { it.copy(searchFlash = null) }
        }
    }

    fun getController(): ReaderController? = controller

    override fun onCleared() {
        super.onCleared()
        // viewModelScope 此时已取消，收尾必须用独立 scope
        val sid = sessionId
        // F8：TXT 取 controller 自维护的滚动单位计数；EPUB 取 VM 侧分页计数
        val turns = (controller as? TxtReaderController)?.pageTurns ?: pageTurns
        val ctrl = controller
        txtScrollBridge = null
        autoFlipTurnBridge = null
        cleanupScope.launch {
            runCatching { persistProgress() }
            if (sid != null) runCatching { readingRepo.endSession(sid, turns) }
            runCatching { ctrl?.close() }
            cleanupScope.cancel()
        }
    }

    private fun isSystemDark(): Boolean {
        val mode = appContext.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
        return mode == Configuration.UI_MODE_NIGHT_YES
    }
}
