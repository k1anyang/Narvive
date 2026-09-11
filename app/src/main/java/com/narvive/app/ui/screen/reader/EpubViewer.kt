package com.narvive.app.ui.screen.reader

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Color as AndroidColor
import android.net.Uri
import android.view.ActionMode
import android.view.Menu
import android.view.View
import android.view.ViewGroup
import android.webkit.JavascriptInterface
import android.util.Log
import android.webkit.WebView
import android.webkit.WebSettings
import android.net.http.SslError
import android.view.KeyEvent
import android.webkit.SslErrorHandler
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebViewClient
import androidx.fragment.app.FragmentActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.IosShare
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.viewinterop.AndroidView
import androidx.fragment.app.FragmentContainerView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import org.readium.r2.navigator.DecorableNavigator
import org.readium.r2.navigator.epub.EpubNavigatorFactory
import org.readium.r2.navigator.epub.EpubNavigatorFragment
import org.readium.r2.navigator.epub.EpubPreferences
import org.readium.r2.navigator.epub.css.Length
import org.readium.r2.navigator.epub.css.RsProperties
import org.readium.r2.navigator.input.InputListener
import org.readium.r2.navigator.input.TapEvent
import org.readium.r2.navigator.util.BaseActionModeCallback
import org.readium.r2.shared.ExperimentalReadiumApi
import org.readium.r2.shared.publication.Locator
import org.readium.r2.shared.publication.Publication
import org.readium.r2.shared.util.AbsoluteUrl
import org.readium.r2.shared.util.asset.AssetRetriever
import org.readium.r2.shared.util.http.DefaultHttpClient
import org.readium.r2.streamer.PublicationOpener
import org.readium.r2.streamer.parser.DefaultPublicationParser
import java.io.File
import java.io.ByteArrayInputStream
import java.nio.charset.Charset
import com.narvive.app.R
import com.narvive.app.domain.model.Annotation
import com.narvive.app.service.reader.EpubReaderController
import com.narvive.app.service.font.ReaderFontFace
import com.narvive.app.service.reader.ReadSettings

/** ReadSettings → Readium EpubPreferences 映射 */
@OptIn(ExperimentalReadiumApi::class)
private fun ReadSettings.toEpubPreferences(customFamilyName: String? = null): EpubPreferences {
    val bg = readerPageBackground(this).toArgb()
    val ink = readerInkColor(this).toArgb()
    return EpubPreferences(
        // 主题色始终注入（保证全部主题/夜间模式的背景与字色生效；WebView 底色也由 effectiveBackgroundColor 对齐）。
        // 出版方样式开启时，浅色主题下由 ReadiumCSS-after.css 的剥离规则被移除来保留出版方形状背景。
        backgroundColor = org.readium.r2.navigator.preferences.Color(bg),
        textColor = org.readium.r2.navigator.preferences.Color(ink),
        fontSize = (fontSize / 16.0).coerceIn(0.75, 1.625),
        lineHeight = lineHeight.toDouble().coerceIn(1.0, 3.0),
        paragraphSpacing = paragraphSpacing.toDouble().coerceIn(0.0, 3.0),
        paragraphIndent = firstLineIndent.toDouble().coerceIn(0.0, 4.0),
        // 出版方样式：true=尊重出版方排版（不注入覆盖 CSS）；false=阅读器全权接管
        publisherStyles = this.publisherStyles,
        // 左右边距由 Readium pageMargins 控制（WebView 内 body padding，分页器视口宽度不变）；
        // 基值 24px 由 Configuration.readiumCssRsProperties.pageGutter 固定，与 TXT 的 24dp × 倍率一致
        pageMargins = horizontalMargin.toDouble().coerceIn(0.0, 2.0),
        textAlign = if (alignment == "left") org.readium.r2.navigator.preferences.TextAlign.LEFT
            else org.readium.r2.navigator.preferences.TextAlign.JUSTIFY,
        // 出版方样式开启时不覆盖字体（fontFamily=null），保留出版方艺术字/字形
        fontFamily = if (publisherStyles) null else org.readium.r2.navigator.preferences.FontFamily(
            customFamilyName ?: if (fontFamily == "source_sans") "sans-serif" else "serif"
        ),
        theme = when {
            nightMode || theme == "black" -> org.readium.r2.navigator.preferences.Theme.DARK
            theme == "sepia" -> org.readium.r2.navigator.preferences.Theme.SEPIA
            else -> org.readium.r2.navigator.preferences.Theme.LIGHT
        },
        // 上下模式：Readium 切换单列连续滚动；平移/无：分栏列分页
        scroll = pageFlipAnimation == "updown",
    )
}

// ── 选区信号 JS（P2：仅发「选区已变化」信号，不再做 DOM 扁平化/文本匹配） ──
// 主路径 = Readium selectionActionModeCallback；此脚本作为兜底（个别 ROM 不触发 ActionMode）
// 与「手柄拖动实时跟随」信号源：Kotlin 侧统一收敛到 navigator.currentSelection() 读 CFI Locator。

private const val EPUB_SELECTION_SIGNAL_BRIDGE = "NrviveSelBridge"

private val EPUB_SELECTION_SIGNAL_JS = """
(function(){
  if(window.__nrvSelInit)return;window.__nrvSelInit=1;
  var debounce=null;
  var hadSel=false;
  document.addEventListener('selectionchange',function(){
    clearTimeout(debounce);
    debounce=setTimeout(function(){
      var sel=window.getSelection();
      var nonEmpty=sel&&!sel.isCollapsed&&sel.toString().trim();
      if(nonEmpty){
        hadSel=true;
        try{ window.__SEL_BRIDGE__.onSelectionChanged(); }catch(e){}
      } else if(hadSel){
        // 原生选区被清除（点击他处/取消/清空）：通知 Kotlin 复位 selectionActive
        hadSel=false;
        try{ window.__SEL_BRIDGE__.onSelectionCleared(); }catch(e){}
      }
    },120);
  });
})();
""".replace("__SEL_BRIDGE__", EPUB_SELECTION_SIGNAL_BRIDGE)

// ── 长按图片 JS（需求：长按 <img> 上报 src 查看大图） ──
private const val EPUB_IMAGE_LONG_PRESS_JS = """
(function(){
  if(window.__nrvImgInit)return;window.__nrvImgInit=1;
  var timer=null;
  function clear(){ if(timer){clearTimeout(timer);timer=null;} }
  document.addEventListener('touchstart', function(e){
    var t=e.target;
    while(t && t!==document.body && t.tagName!=='IMG' && t.tagName!=='image'){ t=t.parentElement; }
    if(t && (t.tagName==='IMG' || t.tagName==='image')){
      var src = t.getAttribute('src') || t.getAttribute('xlink:href') || '';
      if(src){
        clear();
        timer=setTimeout(function(){ try{ window.NrviveSelBridge.onImageLongPress(src); }catch(err){} }, 600);
      }
    }
  }, true);
  document.addEventListener('touchend', clear, true);
  document.addEventListener('touchmove', clear, true);
})();
"""

// ── 全文搜索闪烁 JS（需求7）：把匹配关键词临时包进 <mark data-nrv-flash> 高亮，3 秒后自清除 ──
private fun epubSearchFlashJs(query: String): String = """
(function(q){
  if(!q)return;
  function clear(){
    try{
      document.querySelectorAll('mark[data-nrv-flash]').forEach(function(m){
        var p=m.parentNode; while(m.firstChild){p.insertBefore(m.firstChild,m);} p.removeChild(m);
      });
    }catch(e){}
  }
  try{
    clear();
    var w=document.createTreeWalker(document.body,NodeFilter.SHOW_TEXT,null,false);
    var nodes=[]; while(w.nextNode()){nodes.push(w.currentNode);}
    nodes.forEach(function(node){
      var t=node.nodeValue||''; var idx=t.toLowerCase().indexOf(q.toLowerCase());
      if(idx>=0){
        var r=document.createRange();
        r.setStart(node,idx); r.setEnd(node,idx+q.length);
        try{
          var mark=document.createElement('mark');
          mark.setAttribute('data-nrv-flash','1');
          mark.style.backgroundColor='rgba(255,213,79,0.85)';
          mark.style.color='inherit';
          r.surroundContents(mark);
        }catch(e){}
      }
    });
    setTimeout(clear, 3000);
  }catch(e){}
})(${org.json.JSONObject.quote(query)});
"""

/**
 * EPUB 阅读视图 — Readium 3.3.0 NavigatorFragment 经 AndroidView 嵌入 Compose。
 *
 * P2：选区走 Readium SelectableNavigator（WebView 原生手柄/放大镜 + selectionActionModeCallback 抑制系统菜单）；
 *      高亮走 Readium DecorableNavigator.applyDecorations（CFI 锚定 + 自动重锚 + onDecorationActivated 点按）。
 */
@OptIn(ExperimentalReadiumApi::class)
@Composable
fun EpubViewer(
    filePath: String,
    initialLocator: String?,
    settings: ReadSettings,
    controller: EpubReaderController,
    onCenterTap: () -> Unit,
    /** P2：选区回调（Readium currentSelection）：text + CFI Locator JSON + root px 矩形 */
    onEpubSelection: ((text: String, locatorJson: String, anchorRect: Rect) -> Unit)? = null,
    /** P2：WebView 原生选区被清除（ActionMode 结束/点击他处） */
    onSelectionCleared: (() -> Unit)? = null,
    /** P2：点按已有高亮（Readium onDecorationActivated）：annotationId + root px 矩形/触点 */
    onHighlightTapId: ((annotationId: String, rect: Rect?, point: Offset?) -> Unit)? = null,
    /** 点按翻译红下划线（Readium onDecorationActivated）：annotationId */
    onTranslationTapId: ((annotationId: String) -> Unit)? = null,
    /** P2：高亮装饰数据源（VM annotations 经 requestHighlightRefresh 流入） */
    annotations: List<Annotation> = emptyList(),
    /** 全文搜索跳转后的闪烁目标（EPUB 走 WebView JS 文本匹配） */
    searchFlash: SearchFlash? = null,
    /** 自定义字体注入 CSS（@font-face + 字体栈，指向本地 HTTP 服务）；null = 使用系统字体 */
    fontCss: String? = null,
    /** 主自定义字体族名（优先中文字体，其次英文字体）；用于 Readium fontFamily 偏好，null = 系统字体 */
    fontFamilyName: String? = null,
    /** @font-face 声明列表（familyName + 本地服务器 URL）；用于 Readium Configuration 声明 */
    fontFaces: List<ReaderFontFace> = emptyList(),
    /** 本地字体文件响应；用于 WebViewClient 直接拦截字体请求，避免被 Readium 当作 EPUB 资源 */
    fontResponse: ((String) -> WebResourceResponse?)? = null,
    /** 音量键滚动桥（scroll 模式下注册，翻页=滚动一屏） */
    registerScrollBridge: (((Int) -> Unit) -> Unit)? = null,
    /** 自动翻页桥：返回 true=已翻到下一页，false=已到最后一页 */
    registerAutoFlipTurn: ((() -> Boolean) -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val ctx = LocalContext.current
    val activity = ctx as? FragmentActivity ?: return
    val containerId = remember { View.generateViewId() }
    val tag = "epub_${filePath.hashCode()}"
    val density = ctx.resources.displayMetrics.density
    val safeVerticalMargin = settings.verticalMargin.coerceIn(0f, 2f)
    // 选区气泡坐标换算（P0）：容器视图 + 其 root 坐标，把 navigator 视口坐标换算为阅读根容器 px（与 TXT localToRoot 语义一致）
    var containerView by remember { mutableStateOf<View?>(null) }
    var containerCoords by remember { mutableStateOf<LayoutCoordinates?>(null) }
    // P2：WebView 原生选区激活标志（TouchListener 锁轴豁免用）
    var isSelecting by remember { mutableStateOf(false) }
    // P2：延迟清除 Job（拖柄/二次拖选期间 ActionMode 可能瞬时销毁重建，需延迟确认后才复位）
    var clearSelectionJob by remember { mutableStateOf<Job?>(null) }
    // 长按图片查看大图的图片 src（JS 桥回传）
    var pressedImageUrl by remember { mutableStateOf<String?>(null) }
    // 从 publication 解析出的图片字节（readium_package 虚拟地址无法被 Coil/HTTP 访问，需直接读资源）
    var pressedImageBytes by remember { mutableStateOf<ByteArray?>(null) }
    val latestOnEpubSelection by rememberUpdatedState(onEpubSelection)
    val latestOnSelectionCleared by rememberUpdatedState(onSelectionCleared)
    val latestOnHighlightTapId by rememberUpdatedState(onHighlightTapId)
    val latestOnTranslationTapId by rememberUpdatedState(onTranslationTapId)
    val scope = rememberCoroutineScope()
    // 最新设置供 onPageLoaded 等早期回调读取，避免闭包捕获过期值
    val latestSettings by rememberUpdatedState(settings)
    // 最新字体 CSS（供 onPageLoaded 等回调读取，避免闭包捕获过期值）
    val latestFontCss by rememberUpdatedState(fontCss)
    // 首帧注入 CSS：在 WebViewClient 拦截 HTML 响应时直接写入 <head>，让自定义字体/间距
    // 在第一次布局就生效，避免大跨度跳转（冷加载页面）时"默认排版 → 注入重排"的闪动。
    val firstPaintCssProvider: () -> String? = {
        if (latestSettings.publisherStyles) {
            null
        } else {
            val parts = mutableListOf<String>()
            latestFontCss?.let { parts.add("<style id=\"nrv-font-css\">$it</style>") }
            parts.add("<style id=\"nrv-spacing-css\">${buildReaderCss(latestSettings)}</style>")
            if (parts.isEmpty()) null else parts.joinToString("")
        }
    }
    // 出版方样式开启时删除 ReadiumCSS 的两条"用户颜色注入后强制剥离出版方颜色"规则：
    // 1) --USER__backgroundColor 背景剥离（保留形状背景）；2) --USER__textColor 文字继承（保留出版方文字颜色）。
    // 主题底色/字色仍由 --USER__ 根规则生效；此删除仅用于首帧优化，JS removeProperty 为稳健兜底。
    val stripBackgroundRuleProvider: () -> Boolean = { latestSettings.publisherStyles }
    // 滚底续读防抖（章节页加载/滚动共用）
    var lastScrollContinueTime by remember { mutableLongStateOf(0L) }
    // 回上一章末尾时记录目标章节 href，用于在新 WebView 显示后立即落到末尾，避免先显示顶部再跳底
    var pendingScrollEndHref by remember { mutableStateOf<String?>(null) }

    /** 上下滚动模式：当前 WebView 已滚到底时自动续读下一 spine */
    fun maybeContinueScroll(wv: WebView) {
        if (latestSettings.pageFlipAnimation != "updown") return
        // 用户主动跳转/重建后短暂抑制自动续章：跳转落点恰在章尾或恢复位置在章尾时，
        // 不应被 onPageLoaded 的延迟检查误推进下一章（偶发错误跳转/返回进度漂移的根因）
        if (System.currentTimeMillis() - controller.lastUserJumpAt < 1500) return
        // 以 controller 记录的最新 WebView 为准，旧章节的滚动回调不会误判新章节
        val target = controller.currentWebView ?: wv
        val vh = target.height
        val contentH = target.contentHeight
        if (vh <= 0 || contentH <= 0) return
        if (target.scrollY + vh >= contentH - 20) {
            val now = System.currentTimeMillis()
            if (now - lastScrollContinueTime > 800) {
                lastScrollContinueTime = now
                val cur = controller.currentSpineIndex()
                val count = controller.spineItemCount()
                if (cur in 0 until count - 1) {
                    pendingScrollEndHref = null
                    controller.locatorForSpineItem(cur + 1)?.let { loc ->
                        scope.launch { controller.goToLocator(loc) }
                    }
                }
            }
        }
    }

    /** 回上一章末尾时，在目标 WebView 可见后直接落到末尾；最多重试几次等待内容高度可用 */
    fun scrollToEndOnce(wv: WebView, attempts: Int = 3) {
        if (attempts <= 0) return
        val target = controller.currentWebView ?: wv
        if (target.height <= 0 || target.contentHeight <= 0) {
            target.postDelayed({ scrollToEndOnce(target, attempts - 1) }, 40)
            return
        }
        target.scrollTo(0, (target.contentHeight - target.height).coerceAtLeast(0))
    }

    /** 读取 WebView 原生选区（CFI Locator + 视口矩形）并换算 root px 上报（主路径与兜底信号共用） */
    suspend fun reportCurrentSelection() {
        val sel = controller.currentSelection() ?: return
        val rectF = sel.rect ?: return
        val text = sel.locator.text.highlight.orEmpty().takeIf { it.isNotBlank() } ?: return
        // 实测：Readium currentSelection 的 rect 已是 navigator 视口 Android px（无需 ×density），
        // 直接加容器 root 偏移即为阅读根坐标
        val rootOff = containerCoords?.localToRoot(Offset.Zero) ?: Offset.Zero
        val anchorRect = Rect(
            rootOff.x + rectF.left,
            rootOff.y + rectF.top,
            rootOff.x + rectF.right,
            rootOff.y + rectF.bottom,
        )
        latestOnEpubSelection?.invoke(text, sel.locator.toJSON().toString(), anchorRect)
    }

    /** 延迟确认原生选区是否真的被清除。拖柄 / 二次拖选期间，WebView 的 ActionMode 可能
     *  瞬时销毁重建（onDestroyActionMode → onCreateActionMode），直接复位会把正在调整的选区
     *  抹掉。这里延迟 200ms，若仍存在非空选区则只重报选区，否则才真正复位 VM 选区激活态。 */
    fun scheduleSelectionCleared() {
        clearSelectionJob?.cancel()
        clearSelectionJob = scope.launch {
            kotlinx.coroutines.delay(200)
            val sel = controller.currentSelection()
            val hasSelection = sel != null && sel.locator.text.highlight.orEmpty().isNotBlank()
            if (hasSelection) {
                reportCurrentSelection()
            } else {
                latestOnSelectionCleared?.invoke()
            }
        }
    }

    /** 给当前 WebView 安装选区信号桥（兜底 + 手柄拖动跟随）；章切换后新 WebView 也需要重新安装 */
    @SuppressLint("SetJavaScriptEnabled")
    fun installWebViewBridge(wv: WebView) {
        controller.currentWebView = wv
        wv.settings.javaScriptEnabled = true
        prepareWebViewForFonts(wv, fontResponse, firstPaintCssProvider, stripBackgroundRuleProvider)
        // 防止 WebView 抢走焦点，否则音量键事件会被 WebView 截走，Compose 根裁判收不到
        wv.isFocusable = false
        wv.isFocusableInTouchMode = false
        val bridge = object {
            @JavascriptInterface
            fun onSelectionChanged() {
                // 仅作信号：实际数据统一从 navigator.currentSelection() 读取
                scope.launch { reportCurrentSelection() }
            }

            @JavascriptInterface
            fun onSelectionCleared() {
                // 原生选区被清除：延迟确认后复位（ActionMode 路径同样走此延迟，双路径幂等）
                scheduleSelectionCleared()
            }

            @JavascriptInterface
            fun onImageLongPress(src: String) {
                if (src.isNotBlank()) scope.launch { pressedImageUrl = src }
            }
        }
        wv.addJavascriptInterface(bridge, EPUB_SELECTION_SIGNAL_BRIDGE)
        wv.evaluateJavascript(EPUB_SELECTION_SIGNAL_JS, null)
        // 长按图片查看大图：用 WebView 原生长按 + hitTest 判断是否点在图片上（更可靠）
        wv.setOnLongClickListener {
            val hit = wv.hitTestResult
            if (hit != null && (hit.type == WebView.HitTestResult.IMAGE_TYPE || hit.type == WebView.HitTestResult.SRC_IMAGE_ANCHOR_TYPE)) {
                val url = hit.extra
                if (!url.isNullOrBlank()) {
                    scope.launch { pressedImageUrl = url }
                    return@setOnLongClickListener true // 已处理，不再进入文本选区
                }
            }
            false // 非图片：交给 Readium 文本选区
        }
    }

    /** P2：自定义选区 ActionMode —— 空菜单（禁止系统弹出菜单）+ 选区读取 + 激活标志 */
    val selectionActionModeCallback = remember {
        object : BaseActionModeCallback() {
            override fun onCreateActionMode(mode: ActionMode?, menu: Menu?): Boolean {
                menu?.clear()
                isSelecting = true
                clearSelectionJob?.cancel() // 瞬时重建：取消待定的清除
                scope.launch { reportCurrentSelection() }
                return true
            }

            override fun onDestroyActionMode(mode: ActionMode) {
                isSelecting = false
                scheduleSelectionCleared()
            }
        }
    }

    var publication by remember { mutableStateOf<Publication?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var navigatorFragment by remember { mutableStateOf<EpubNavigatorFragment?>(null) }
    var webView by remember { mutableStateOf<WebView?>(null) }
    var lastWebViewHref by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(filePath) {
        withContext(Dispatchers.IO) {
            val file = File(filePath)
            if (!file.exists()) {
                error = ctx.getString(R.string.viewer_error_file_not_found, filePath)
                return@withContext
            }
            try {
                val httpClient = DefaultHttpClient()
                val assetRetriever = AssetRetriever(ctx.contentResolver, httpClient)
                val opener = PublicationOpener(
                    publicationParser = DefaultPublicationParser(ctx, httpClient, assetRetriever, null),
                )
                val assetResult = assetRetriever.retrieve(file)
                val asset = assetResult.getOrNull()
                val pub = if (asset != null) opener.open(asset, allowUserInteraction = false).getOrNull() else null
                publication = pub
                if (pub == null) {
                    error = ctx.getString(R.string.viewer_error_epub_parse)
                } else {
                    controller.attachPublication(pub)
                    controller.computeTotalChars()
                }
            } catch (e: Exception) {
                error = ctx.getString(R.string.viewer_error_epub_open, e.message.orEmpty())
            }
        }
    }

    val startLoc: Locator? = remember(initialLocator) {
        // 从字体/更多设置等全屏页返回时，ReaderScreen 会被销毁重组、fragment 重建；
        // controller 保留着正确的 currentLocator，优先用它恢复，避免回退到陈旧的 book.currentLocator
        // 导致 slide/none 回第一章、updown 回错章或切章失效。
        val restoreJson = controller.currentLocator.value?.takeIf { it.isNotBlank() } ?: initialLocator
        try {
            restoreJson?.takeIf { it.isNotBlank() }?.let { Locator.fromJSON(JSONObject(it)) }
        } catch (_: Exception) { null }
    }

    val err = error
    if (err != null) {
        androidx.compose.material3.Text(
            text = err, color = androidx.compose.material3.MaterialTheme.colorScheme.error, modifier = modifier,
        )
        return
    }
    val pub = publication
    if (pub == null) {
        // 解析期间用与阅读背景同色的占位，消除“两段式 loading + 默认主题色文字”的闪动
        Box(modifier = modifier.background(readerPageBackground(settings)), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                androidx.compose.material3.CircularProgressIndicator(
                    color = readerInkColor(settings).copy(alpha = 0.3f),
                    strokeWidth = 2.dp,
                )
                androidx.compose.material3.Text(
                    stringResource(R.string.viewer_loading_book),
                    color = readerInkColor(settings).copy(alpha = 0.35f),
                )
            }
        }
        return
    }

    AndroidView(
        factory = { c ->
            FragmentContainerView(c).also {
                it.id = containerId
                it.layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
                containerView = it
            }
        },
        // 上下边距由 Compose 层 padding 控制（24dp × 倍率，与 TXT 一致）；
        // 左右边距必须走 Readium pageMargins（WebView 内 body padding），否则改边距会改变分页器视口宽度，
        // 触发 Readium 按比例重算 scrollX，看起来像“页面在移动”而不是文字边距变化
        modifier = modifier
            .padding(
                top = (24 * safeVerticalMargin).dp,
                bottom = (24 * safeVerticalMargin).dp,
            )
            .onGloballyPositioned { containerCoords = it },
    )

    // ── NavigatorFragment 创建与注入 ──
    LaunchedEffect(pub, containerId) {
        val fm = activity.supportFragmentManager
        var frag = fm.findFragmentByTag(tag) as? EpubNavigatorFragment
        if (frag == null) {
            try {
                val factory = EpubNavigatorFactory(pub).createFragmentFactory(
                    initialLocator = startLoc,
                    initialPreferences = settings.toEpubPreferences(fontFamilyName),
                    listener = null,
                    paginationListener = object : EpubNavigatorFragment.PaginationListener {
                        override fun onPageChanged(pageIndex: Int, totalPages: Int, locator: Locator) {
                            controller.onPageChanged(locator, pageIndex, totalPages)
                        }
                        override fun onPageLoaded() {
                            // 每页加载完成后向所有 WebView 注入字体 @font-face + 字体栈
                            val css = latestFontCss
                            val view = navigatorFragment.safeView() ?: return
                            if (css != null) {
                                injectFontCss(findAllWebViews(view), css, fontResponse, firstPaintCssProvider, stripBackgroundRuleProvider)
                            }
                            // 出版方样式：新页加载后 Readium setCSSProperties 会写入 --USER__backgroundColor，
                            // 浅色主题下移除它（保留形状背景）并以 --RS__ 变量保持主题底色
                            applyPublisherTheme(
                                findAllWebViews(view),
                                if (latestSettings.publisherStyles) buildPublisherThemeCss(latestSettings) else null,
                            )
                            // 上下滚动模式：短章（内容不足一屏）没有滚动事件，加载完成后主动检查一次滚底续读
                            if (latestSettings.pageFlipAnimation == "updown") {
                                // 新章节 WebView 一旦加载完成就立即补齐间距 CSS，避免先按默认排版显示、再延迟重排造成闪烁
                                if (!latestSettings.publisherStyles) {
                                    injectSpacingCss(findAllWebViews(view), buildReaderCss(latestSettings))
                                }
                                // onPageLoaded 时 controller.currentHref 可能仍是旧章，优先按可见 WebView 定位
                                val wv = findCurrentWebView(view, null)
                                if (wv != null) {
                                    // 新章节 WebView 已加载时立即切换到它，避免继续监听旧章节
                                    if (wv !== webView) {
                                        webView = wv
                                        installWebViewBridge(wv)
                                    }
                                    pendingScrollEndHref?.let { targetHref ->
                                        if (wv.matchesHref(targetHref)) {
                                            pendingScrollEndHref = null
                                            scrollToEndOnce(wv)
                                        }
                                    }
                                    wv.postDelayed({ maybeContinueScroll(wv) }, 160)
                                    wv.postDelayed({ maybeContinueScroll(wv) }, 420)
                                }
                            } else {
                                // slide/none 分页模式：主题切换等 submitPreferences 会刷新 WebView，
                                // 页面加载完成后必须重新注入间距 CSS，否则原 <style> 丢失导致排版回退。
                                if (!latestSettings.publisherStyles) {
                                    injectSpacingCss(findAllWebViews(view), buildReaderCss(latestSettings))
                                }
                            }
                        }
                    },
                    configuration = EpubNavigatorFragment.Configuration {
                        // 固定左右边距基准为 24px：pageMargins 是倍率，TXT 语义为 24dp × 倍率
                        readiumCssRsProperties = RsProperties(pageGutter = Length.Px(24.0))
                        // 上下滚动模式下禁止 Readium 因斜向手势触发左右资源切章动画；
                        // 该配置仅对 scrollMode 生效，slide/none 分页模式不受影响
                        disablePageTurnsWhileScrolling = true
                        // 关闭 Readium 自带上下 padding（含 paged 模式 vertical_padding），
                        // 上下边界统一由 Compose 层 safeTop/safeBottom + verticalMargin 控制，与 TXT 一致
                        shouldApplyInsetsPadding = false
                        // P2：自定义选区菜单（空菜单 = 禁止系统弹出菜单，保留原生手柄/放大镜）
                        this.selectionActionModeCallback = selectionActionModeCallback
                        if (fontFaces.isNotEmpty()) {
                            val primary = fontFaces.first()
                            val alternates = fontFaces.drop(1).map { org.readium.r2.navigator.preferences.FontFamily(it.familyName) }
                            fontFaces.forEach { face ->
                                addFontFamilyDeclaration(
                                    fontFamily = org.readium.r2.navigator.preferences.FontFamily(face.familyName),
                                    alternates = if (face == primary) alternates else emptyList(),
                                ) {
                                    addFontFace {
                                        addSource(org.readium.r2.shared.util.Url(face.url)!!)
                                    }
                                }
                            }
                        }
                    },
                )
                frag = factory.instantiate(activity.classLoader, EpubNavigatorFragment::class.java.name) as EpubNavigatorFragment
                fm.beginTransaction().replace(containerId, frag, tag).commit()
            } catch (e: Exception) {
                error = ctx.getString(R.string.viewer_error_init, e.message.orEmpty())
            }
        }
        frag?.let { fragment ->
            navigatorFragment = fragment
            controller.attachNavigator(fragment)
            fragment.addInputListener(object : InputListener {
                override fun onTap(event: TapEvent): Boolean {
                    val sw = fragment.safeView()?.width?.toFloat() ?: 0f
                    val x = event.point.x
                    return when {
                        // 左边缘 → 上一页
                        x < sw * 0.3f -> {
                            scope.launch { controller.previousPage() }
                            true
                        }
                        // 右边缘 → 下一页
                        x > sw * 0.7f -> {
                            scope.launch { controller.nextPage() }
                            true
                        }
                        // 中心 → 切 HUD
                        else -> {
                            onCenterTap()
                            true
                        }
                    }
                }
            })
        }
    }

    // ── WebView 查找 + JS Bridge 注入（G1.1/G1.2）──
    LaunchedEffect(navigatorFragment) {
        val frag = navigatorFragment ?: return@LaunchedEffect
        lastWebViewHref = null
        // 稍等 fragment view 就绪
        kotlinx.coroutines.delay(200)
        val view = frag.safeView() ?: return@LaunchedEffect
        val wv = findCurrentWebView(view, controller.currentHref())
        if (wv != null) {
            webView = wv
            installWebViewBridge(wv)
            fontCss?.let { injectFontCss(findAllWebViews(view), it, fontResponse, firstPaintCssProvider, stripBackgroundRuleProvider) }
        }
    }

    // 章切换后 R2ViewPager 会使用新的 WebView，必须重新定位当前可见 WebView 并重装监听/Bridge
    val currentLocatorJson by controller.currentLocator.collectAsState()
    LaunchedEffect(currentLocatorJson, navigatorFragment) {
        val frag = navigatorFragment ?: return@LaunchedEffect
        val href = controller.currentHref() ?: return@LaunchedEffect
        if (href == lastWebViewHref) return@LaunchedEffect
        lastWebViewHref = href
        kotlinx.coroutines.delay(80)
        val view = frag.safeView() ?: return@LaunchedEffect
        val wv = findCurrentWebView(view, href)
        if (wv != null && wv !== webView) {
            webView = wv
            installWebViewBridge(wv)
        }
    }

    // 兜底轮询：R2ViewPager 过渡期间可能暂时找不到新 WebView，
    // 低频校正当前 WebView，确保滚底监听和手势监听最终挂到当前章
    LaunchedEffect(settings.pageFlipAnimation, navigatorFragment) {
        if (settings.pageFlipAnimation != "updown") return@LaunchedEffect
        while (true) {
            val href = controller.currentHref()
            val current = controller.currentWebView
            // 已绑定且仍匹配当前章节时跳过视图树搜索，降低开销
            if (current == null || !current.matchesHref(href)) {
                val frag = navigatorFragment
                if (frag != null && frag.isAdded && !frag.isDetached) {
                    val view = frag.view
                    if (view != null) {
                        val wv = findCurrentWebView(view, href)
                        if (wv != null && wv !== webView) {
                            webView = wv
                            installWebViewBridge(wv)
                        } else if (wv != null) {
                            controller.currentWebView = wv
                        }
                    }
                }
            }
            kotlinx.coroutines.delay(1000)
        }
    }

    // ── 自动翻页桥：无论手动翻页模式如何，均提供「瞬时下一页」能力；结尾由全局页码判断 ──
    LaunchedEffect(controller) {
        registerAutoFlipTurn?.invoke {
            val total = controller.globalTotalPages.value
            val current = controller.globalCurrentPage.value
            val atEnd = if (total > 0) current >= total else controller.progress.value >= 0.999f
            if (atEnd) {
                false
            } else {
                scope.launch { controller.nextPageInstant() }
                true
            }
        }
    }

    // ── 翻页动画触摸处理（settings 变化时重新绑定）──
    // AndroidView 的 Compose interop 会把未消费的 move 事件转发给 WebView。
    // slide 模式必须放行水平拖动，由 R2WebView 原生完成 1:1 跟手与松手 snap；
    // 根裁判只在非 slide / 非 TXT 分页模式下消费水平事件。
    LaunchedEffect(settings.pageFlipAnimation, webView) {
        val wv = webView ?: return@LaunchedEffect
        when (settings.pageFlipAnimation) {
            "slide" -> {
                wv.setOnTouchListener(null)
            }
            "updown" -> {
                // 上下滚动：音量键滚动一屏 + 滚底自动续读下一章
                var lastTopContinueTime = 0L
                registerScrollBridge?.invoke { direction ->
                    wv.post {
                        // 直接读 controller 记录的最新 WebView，避免每次按键都遍历视图树
                        val target = controller.currentWebView ?: wv
                        val cur = controller.currentSpineIndex()
                        val count = controller.spineItemCount()
                        val atTop = target.scrollY <= 0
                        val atBottom = target.height > 0 && target.contentHeight > 0 &&
                            target.scrollY + target.height >= target.contentHeight - 20
                        val now = System.currentTimeMillis()
                        when {
                            direction < 0 && atTop && cur > 0 -> {
                                if (now - lastTopContinueTime > 800) {
                                    lastTopContinueTime = now
                                    // 抑制回退后立即自动续读回当前章
                                    lastScrollContinueTime = now
                                    val targetIdx = cur - 1
                                    pendingScrollEndHref = controller.hrefForSpineItem(targetIdx)
                                    controller.locatorForSpineItemEnd(targetIdx)?.let { loc ->
                                        scope.launch { controller.goToLocator(loc) }
                                    }
                                }
                            }
                            direction > 0 && atBottom && cur in 0 until count - 1 -> {
                                if (now - lastScrollContinueTime > 800) {
                                    lastScrollContinueTime = now
                                    pendingScrollEndHref = null
                                    controller.locatorForSpineItem(cur + 1)?.let { loc ->
                                        scope.launch { controller.goToLocator(loc) }
                                    }
                                }
                            }
                            else -> {
                                target.scrollBy(0, (target.height * 0.9f * direction).toInt())
                                // 只有向下滚动才检查滚底续读；向上滚动不应误触发进下一章
                                if (direction > 0) maybeContinueScroll(target)
                            }
                        }
                    }
                }
                // 上下模式锁定 x 轴：水平方向吞掉事件，垂直方向放行给 WebView 滚动；
                // 章首下拉回上一章末尾、章尾上拉进下一章开头，实现双向连续阅读
                var downX = 0f
                var downY = 0f
                var downScrollY = 0
                val horizontalSlop = density * 6
                val topContinueSlop = density * 24
                wv.setOnTouchListener { _, e ->
                    // P2：原生选区激活期间全部放行（手柄拖动/调整不被锁轴逻辑吞掉）
                    if (isSelecting) return@setOnTouchListener false
                    when (e.actionMasked) {
                        android.view.MotionEvent.ACTION_DOWN -> {
                            downX = e.x; downY = e.y; downScrollY = wv.scrollY; false
                        }
                        android.view.MotionEvent.ACTION_MOVE -> {
                            val dx = e.x - downX
                            val dy = e.y - downY
                            if (kotlin.math.abs(dx) > horizontalSlop && kotlin.math.abs(dx) > kotlin.math.abs(dy)) {
                                true
                            } else if (
                                wv.scrollY <= 0 && downScrollY <= 0 &&
                                dy > topContinueSlop && dy > kotlin.math.abs(dx)
                            ) {
                                val now = System.currentTimeMillis()
                                if (now - lastTopContinueTime > 800) {
                                    lastTopContinueTime = now
                                    // 抑制回退后立即自动续读回当前章
                                    lastScrollContinueTime = now
                                    val cur = controller.currentSpineIndex()
                                    if (cur > 0) {
                                        val targetIdx = cur - 1
                                        pendingScrollEndHref = controller.hrefForSpineItem(targetIdx)
                                        controller.locatorForSpineItemEnd(targetIdx)?.let { loc ->
                                            scope.launch { controller.goToLocator(loc) }
                                        }
                                    }
                                }
                                true
                            } else if (
                                wv.height > 0 && wv.contentHeight > 0 &&
                                wv.scrollY + wv.height >= wv.contentHeight - 20 &&
                                downScrollY + wv.height >= wv.contentHeight - 20 &&
                                dy < -topContinueSlop && -dy > kotlin.math.abs(dx)
                            ) {
                                val now = System.currentTimeMillis()
                                if (now - lastScrollContinueTime > 800) {
                                    lastScrollContinueTime = now
                                    val cur = controller.currentSpineIndex()
                                    val count = controller.spineItemCount()
                                    if (cur in 0 until count - 1) {
                                        pendingScrollEndHref = null
                                        controller.locatorForSpineItem(cur + 1)?.let { loc ->
                                            scope.launch { controller.goToLocator(loc) }
                                        }
                                    }
                                }
                                true
                            } else {
                                false
                            }
                        }
                        else -> false
                    }
                }
                wv.post { maybeContinueScroll(wv) }
            }
            else -> {
                var downX = 0f
                var downY = 0f
                wv.setOnTouchListener { _, e ->
                    // P2：原生选区激活期间全部放行（手柄拖动/调整不被锁轴逻辑吞掉）
                    if (isSelecting) return@setOnTouchListener false
                    when (e.actionMasked) {
                        android.view.MotionEvent.ACTION_DOWN -> { downX = e.x; downY = e.y; false }
                        android.view.MotionEvent.ACTION_MOVE -> {
                            val dx = e.x - downX
                            val dy = e.y - downY
                            kotlin.math.abs(dx) > 0f && kotlin.math.abs(dx) > kotlin.math.abs(dy)
                        }
                        else -> false
                    }
                }
            }
        }
    }

    // ── 自定义字体 CSS 注入（@font-face + 字体栈，指向本地 HTTP 服务）──
    // Readium 分页模式存在多个 WebView（每页一个），必须向全部 WebView 注入
    LaunchedEffect(fontCss, navigatorFragment) {
        val frag = navigatorFragment ?: return@LaunchedEffect
        val css = fontCss ?: return@LaunchedEffect
        kotlinx.coroutines.delay(250)
        val view = frag.safeView() ?: return@LaunchedEffect
        injectFontCss(findAllWebViews(view), css, fontResponse, firstPaintCssProvider, stripBackgroundRuleProvider)
    }
    // ── 设置即改即存（Fragment 生命周期保护）──
    LaunchedEffect(settings, navigatorFragment) {
        val frag = navigatorFragment ?: return@LaunchedEffect
        if (!frag.isAdded || frag.isDetached) return@LaunchedEffect
        kotlinx.coroutines.delay(50) // 等待 fragment view 稳定
        if (frag.isAdded && !frag.isDetached && frag.view != null) {
            frag.submitPreferences(settings.toEpubPreferences(fontFamilyName))
            // submitPreferences 会让 Readium 的 setCSSProperties 重新写入 --USER__backgroundColor，
            // 出版方样式（浅色主题）下需再次移除，避免形状背景被剥离规则抹掉
            applyPublisherTheme(
                findAllWebViews(frag.view),
                if (settings.publisherStyles) buildPublisherThemeCss(settings) else null,
            )
        }
    }

    // ── 间距 CSS 注入（行距/段距/首行缩进/上下左右边距，统一为 em + 24dp×倍率，与 TXT 一致；实时生效）──
    LaunchedEffect(
        settings.lineHeight, settings.paragraphSpacing, settings.firstLineIndent,
        settings.verticalMargin, settings.horizontalMargin, settings.publisherStyles, navigatorFragment,
    ) {
        val frag = navigatorFragment ?: return@LaunchedEffect
        kotlinx.coroutines.delay(80)
        val view = frag.safeView() ?: return@LaunchedEffect
        val webViews = findAllWebViews(view)
        if (settings.publisherStyles) {
            removeSpacingCss(webViews)
            removeFontCss(webViews)
            applyPublisherTheme(webViews, buildPublisherThemeCss(settings))
        } else {
            applyPublisherTheme(webViews, null)
            injectSpacingCss(webViews, buildReaderCss(settings))
        }
    }

    // ── 高亮装饰（P2：Readium 官方装饰层，CFI 锚定 + 自动重锚 + 增量更新）──
    val pendingHighlights by controller.pendingHighlights.collectAsState()
    val latestPendingHighlights by rememberUpdatedState(pendingHighlights)
    LaunchedEffect(pendingHighlights) {
        controller.applyHighlightDecorations(pendingHighlights)
    }

    // ── 翻译红下划线装饰 ──
    val pendingTranslations by controller.pendingTranslations.collectAsState()
    val latestPendingTranslations by rememberUpdatedState(pendingTranslations)
    LaunchedEffect(pendingTranslations) {
        controller.applyTranslationDecorations(pendingTranslations)
    }

    // ── 全文搜索闪烁（需求7）：跳转后对匹配关键词临时高亮 3 秒（JS 内 setTimeout 自清除） ──
    LaunchedEffect(searchFlash) {
        val flash = searchFlash ?: return@LaunchedEffect
        if (flash.query.isBlank()) return@LaunchedEffect
        kotlinx.coroutines.delay(500) // 等跳转目标页加载
        val frag = navigatorFragment ?: return@LaunchedEffect
        val view = frag.safeView() ?: return@LaunchedEffect
        val js = epubSearchFlashJs(flash.query)
        findAllWebViews(view).forEach { wv ->
            runCatching { wv.evaluateJavascript(js, null) }
        }
    }

    // ── 高亮点按监听 + attach 后补应用（P2）──
    // 注意：addDecorationListener 内部会访问 viewModel → requireActivity()，
    // 而 fragment 事务 commit() 是异步的，组合期 fragment 尚未 attach，直接注册会抛
    // "not attached to an activity" 崩溃。必须轮询等待 attach 完成后再注册；
    // 同时补应用一次装饰（启动早期 pendingHighlights 变化可能因未 attach 被跳过）。
    LaunchedEffect(navigatorFragment) {
        val frag = navigatorFragment ?: return@LaunchedEffect
        var waits = 0
        while ((!frag.isAdded || frag.isDetached) && waits < 100) {
            kotlinx.coroutines.delay(50)
            waits++
        }
        if (!frag.isAdded || frag.isDetached) return@LaunchedEffect
        val listener = object : DecorableNavigator.Listener {
            override fun onDecorationActivated(event: DecorableNavigator.OnActivatedEvent): Boolean {
                val rectF = event.rect
                val pointF = event.point
                val id = event.decoration.id
                // 装饰回调线程不确定：坐标换算与 Compose 状态读取回主线程
                scope.launch {
                    // 实测：装饰事件 rect/point 已是 navigator 视口 Android px，直接加容器 root 偏移
                    val rootOff = containerCoords?.localToRoot(Offset.Zero) ?: Offset.Zero
                    val rect = rectF?.let {
                        Rect(
                            rootOff.x + it.left,
                            rootOff.y + it.top,
                            rootOff.x + it.right,
                            rootOff.y + it.bottom,
                        )
                    }
                    val point = pointF?.let { Offset(rootOff.x + it.x, rootOff.y + it.y) }
                    latestOnHighlightTapId?.invoke(id, rect, point)
                }
                return true
            }
        }
        frag.addDecorationListener(EpubReaderController.HIGHLIGHT_DECORATION_GROUP, listener)
        // 翻译红下划线点按监听
        val translationListener = object : DecorableNavigator.Listener {
            override fun onDecorationActivated(event: DecorableNavigator.OnActivatedEvent): Boolean {
                val id = event.decoration.id
                scope.launch { latestOnTranslationTapId?.invoke(id) }
                return true
            }
        }
        frag.addDecorationListener(EpubReaderController.TRANSLATION_DECORATION_GROUP, translationListener)
        // attach 完成后补应用一次当前高亮/翻译（navigator 侧自动重锚，重复提交幂等）
        controller.applyHighlightDecorations(latestPendingHighlights)
        controller.applyTranslationDecorations(latestPendingTranslations)
        try {
            awaitCancellation()
        } finally {
            runCatching { frag.removeDecorationListener(listener) }
            runCatching { frag.removeDecorationListener(translationListener) }
        }
    }

    // ── 翻页/章切换后重新注入 CSS（P2：选区/高亮不再需要 JS 重注入）──
    LaunchedEffect(currentLocatorJson, settings.pageFlipAnimation) {
        if (settings.pageFlipAnimation == "updown") return@LaunchedEffect
        kotlinx.coroutines.delay(200)
        val view = navigatorFragment.safeView()
        if (view != null) {
            fontCss?.let { injectFontCss(findAllWebViews(view), it, fontResponse, firstPaintCssProvider, stripBackgroundRuleProvider) }
            if (!settings.publisherStyles) {
                injectSpacingCss(findAllWebViews(view), buildReaderCss(settings))
            }
            applyPublisherTheme(
                findAllWebViews(view),
                if (settings.publisherStyles) buildPublisherThemeCss(settings) else null,
            )
        }
    }

    // ── 长按图片查看大图：把 readium_package 虚拟地址还原成 publication 资源字节 ──
    LaunchedEffect(pressedImageUrl) {
        val url = pressedImageUrl ?: return@LaunchedEffect
        pressedImageBytes = withContext(Dispatchers.IO) { resolveEpubImageBytes(publication, url) }
    }
    pressedImageUrl?.let { url ->
        ImagePreviewDialog(url = url, bytes = pressedImageBytes, context = ctx, onDismiss = { pressedImageUrl = null; pressedImageBytes = null })
    }

    DisposableEffect(filePath) {
        onDispose {
            try {
                activity.supportFragmentManager.findFragmentByTag(tag)?.let { f ->
                    activity.supportFragmentManager.beginTransaction().remove(f).commit()
                }
            } catch (_: Exception) {}
            try { publication?.close() } catch (_: Exception) {}
        }
    }
}

@Composable
private fun ImagePreviewDialog(url: String, bytes: ByteArray?, context: Context, onDismiss: () -> Unit) {
    val scope = rememberCoroutineScope()
    val bitmap = remember(url, bytes) {
        bytes?.let { android.graphics.BitmapFactory.decodeByteArray(it, 0, it.size) }
    }
    androidx.compose.ui.window.Dialog(
        onDismissRequest = onDismiss,
        properties = androidx.compose.ui.window.DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false,
        ),
    ) {
        Column(
            Modifier.fillMaxSize().background(Color.Black),
        ) {
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                if (bitmap != null) {
                    coil.compose.AsyncImage(
                        model = bitmap,
                        contentDescription = stringResource(R.string.viewer_image_content_description),
                        modifier = Modifier.fillMaxSize(),
                        contentScale = androidx.compose.ui.layout.ContentScale.Fit,
                    )
                } else {
                    coil.compose.AsyncImage(
                        model = url,
                        contentDescription = stringResource(R.string.viewer_image_content_description),
                        modifier = Modifier.fillMaxSize(),
                        contentScale = androidx.compose.ui.layout.ContentScale.Fit,
                    )
                }
            }
            Row(
                Modifier.fillMaxWidth().navigationBarsPadding().padding(vertical = 8.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                androidx.compose.material3.IconButton(onClick = onDismiss) {
                    Icon(Icons.Rounded.Close, stringResource(R.string.viewer_action_close), tint = Color.White)
                }
                Spacer(Modifier.width(28.dp))
                androidx.compose.material3.IconButton(onClick = { scope.launch { saveImage(context, url, bytes) } }) {
                    Icon(Icons.Rounded.Download, stringResource(R.string.viewer_action_save), tint = Color.White)
                }
                Spacer(Modifier.width(28.dp))
                androidx.compose.material3.IconButton(onClick = { scope.launch { shareImage(context, url, bytes) } }) {
                    Icon(Icons.Rounded.IosShare, stringResource(R.string.viewer_action_share), tint = Color.White)
                }
            }
        }
    }
}

private fun fetchImageBytes(url: String): ByteArray? = runCatching {
    if (url.startsWith("data:")) {
        android.util.Base64.decode(url.substringAfter("base64,"), android.util.Base64.DEFAULT)
    } else {
        val conn = java.net.URL(url).openConnection() as java.net.HttpURLConnection
        conn.connectTimeout = 10000
        conn.readTimeout = 10000
        conn.inputStream.use { it.readBytes() }
    }
}.getOrNull()

/**
 * 把长按命中的图片地址还原成 publication 内的资源字节。
 * EPUB 图片在 WebView 里由 Readium 以 `https://readium_package/<path>` 虚拟主机提供，
 * Coil/OkHttp 无法解析该域名，必须直接经 publication 读取。
 */
private suspend fun resolveEpubImageBytes(publication: Publication?, url: String): ByteArray? {
    if (publication == null) return null
    if (url.startsWith("data:")) {
        return runCatching {
            android.util.Base64.decode(url.substringAfter("base64,", ""), android.util.Base64.DEFAULT)
        }.getOrNull()
    }
    val bare = url.substringBefore('#')
    // 外部网络图片：交给 Coil 兜底，这里只处理 EPUB 包内资源（readium_package 虚拟主机 / 相对路径）
    if (bare.startsWith("http://") || bare.startsWith("https://")) {
        if (!bare.startsWith("https://readium_package/") && !bare.startsWith("http://readium_package/")) return null
    }
    val absolute = when {
        bare.startsWith("http://") || bare.startsWith("https://") -> bare
        else -> "https://readium_package/" + bare.removePrefix("/")
    }
    return runCatching {
        val packageBase = AbsoluteUrl("https://readium_package/") ?: return@runCatching null
        val abs = AbsoluteUrl(absolute) ?: return@runCatching null
        val href = packageBase.relativize(abs)
        val resource = publication.get(href) ?: return@runCatching null
        resource.read().getOrNull()
    }.getOrNull()
}

private suspend fun saveImage(context: Context, url: String, resolvedBytes: ByteArray?) {
    kotlinx.coroutines.withContext(Dispatchers.IO) {
        val bytes = resolvedBytes ?: fetchImageBytes(url) ?: return@withContext
        val bitmap = android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: return@withContext
        val name = "narvive_${System.currentTimeMillis()}.jpg"
        val uri = runCatching {
            android.provider.MediaStore.Images.Media.insertImage(context.contentResolver, bitmap, name, "Narvive")
        }.getOrNull()
        kotlinx.coroutines.withContext(Dispatchers.Main) {
            if (uri != null) android.widget.Toast.makeText(context, context.getString(R.string.viewer_image_saved), android.widget.Toast.LENGTH_SHORT).show()
            else android.widget.Toast.makeText(context, context.getString(R.string.viewer_image_save_failed), android.widget.Toast.LENGTH_SHORT).show()
        }
    }
}

private suspend fun shareImage(context: Context, url: String, resolvedBytes: ByteArray?) {
    kotlinx.coroutines.withContext(Dispatchers.IO) {
        val bytes = resolvedBytes ?: fetchImageBytes(url) ?: return@withContext
        val file = java.io.File(context.cacheDir, "narvive_share_${System.currentTimeMillis()}.jpg")
        file.writeBytes(bytes)
        val uri = androidx.core.content.FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val intent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
            type = "image/jpeg"
            putExtra(android.content.Intent.EXTRA_STREAM, uri)
            addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        kotlinx.coroutines.withContext(Dispatchers.Main) {
            context.startActivity(android.content.Intent.createChooser(intent, context.getString(R.string.viewer_share_image_title)).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK))
        }
    }
}

private const val TAG = "NarviveEpub"

// ── 自定义字体注入 JS：<style id="nrv-font-css"> 内容 = @font-face + 字体栈 ──
private fun fontCssInjectJs(css: String): String {
    val quoted = JSONObject.quote(css)
    return """(function(){
  var s = document.getElementById('nrv-font-css');
  var hadFace = false;
  try {
    for (var i = 0; i < document.styleSheets.length; i++) {
      var rules = document.styleSheets[i].cssRules; if (!rules) continue;
      for (var j = 0; j < rules.length; j++) { if (rules[j].type === 5) { hadFace = true; break; } }
      if (hadFace) break;
    }
  } catch (e) {}
  if (!s) { s = document.createElement('style'); s.id = 'nrv-font-css'; document.head.appendChild(s); }
  s.textContent = $quoted;
  var bodyFont = document.body ? getComputedStyle(document.body).fontFamily : 'no-body';
  var rulesNow = s.sheet ? s.sheet.cssRules.length : -1;
  var firstRule = '';
  try { if (s.sheet && s.sheet.cssRules && s.sheet.cssRules[0]) { firstRule = s.sheet.cssRules[0].cssText || ''; } } catch (e) {}
  var csp = '';
  try { var m = document.querySelector('meta[http-equiv="Content-Security-Policy"]'); if (m) { csp = m.getAttribute('content') || ''; } } catch (e) {}
  return JSON.stringify({href: location.href, src: firstRule, csp: csp, hadFace: hadFace, rulesNow: rulesNow, bodyFont: bodyFont});
})();"""
}
// ── 递归收集全部 WebView（Readium 分页模式每页一个 WebView，注入必须覆盖所有）──
private fun findAllWebViews(view: View?): List<WebView> {
    if (view == null) return emptyList()
    if (view is WebView) return listOf(view)
    if (view is ViewGroup) {
        val out = mutableListOf<WebView>()
        for (i in 0 until view.childCount) out += findAllWebViews(view.getChildAt(i))
        return out
    }
    return emptyList()
}

/** 字体相关 WebView 准备：放行混合内容 + 包装 WebViewClient 信任本地自签名证书 + 首帧 CSS 注入 */
private fun prepareWebViewForFonts(
    wv: WebView,
    fontResponse: ((String) -> WebResourceResponse?)?,
    firstPaintCssProvider: (() -> String?)?,
    stripBackgroundRuleProvider: (() -> Boolean)?,
) {
    wv.settings.mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
    // 所有 Readium WebView 都禁止抢焦点，否则新章节 WebView 会把音量键事件从 Compose 根裁判截走
    wv.isFocusable = false
    wv.isFocusableInTouchMode = false
    wv.clearFocus()
    val current = wv.webViewClient
    if (current !is NarviveTrustingWebViewClient) {
        wv.webViewClient = NarviveTrustingWebViewClient(
            current ?: WebViewClient(), fontResponse, firstPaintCssProvider, stripBackgroundRuleProvider,
        )
    }
}

/** 包装 Readium 的 WebViewClient：优先拦截本地字体请求，其余委托 Readium；HTML 响应首帧注入阅读器 CSS */
private class NarviveTrustingWebViewClient(
    private val delegate: WebViewClient,
    private val fontResponse: ((String) -> WebResourceResponse?)?,
    private val firstPaintCssProvider: (() -> String?)?,
    private val stripBackgroundRuleProvider: (() -> Boolean)?,
) : WebViewClient() {
    override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean =
        delegate.shouldOverrideUrlLoading(view, request)

    override fun shouldOverrideKeyEvent(view: WebView, event: KeyEvent): Boolean =
        delegate.shouldOverrideKeyEvent(view, event)

    override fun onPageFinished(view: WebView?, url: String?) {
        delegate.onPageFinished(view, url)
    }

    override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse? {
        localFontFileName(request)?.let { fileName ->
            fontResponse?.invoke(fileName)?.let { return it }
            return WebResourceResponse(
                "text/plain",
                "utf-8",
                404,
                "Not Found",
                emptyMap(),
                ByteArrayInputStream(ByteArray(0)),
            )
        }
        val response = delegate.shouldInterceptRequest(view, request)
        if (response != null) {
            // 出版方样式开启：删除 ReadiumCSS 的 --USER__backgroundColor 背景剥离规则，
            // 让出版方元素背景（形状）不被强制透明；主题底色仍由 --USER__ 变量规则生效
            if (stripBackgroundRuleProvider?.invoke() == true && isReadiumCssAfter(request)) {
                return removeUserStripRules(response)
            }
            val css = firstPaintCssProvider?.invoke()
            if (!css.isNullOrBlank() && isHtmlResource(request, response)) {
                return injectCssIntoHtml(response, css)
            }
        }
        return response
    }

    override fun onReceivedSslError(view: WebView?, handler: SslErrorHandler, error: SslError) {
        handler.proceed()
    }

    private fun localFontFileName(request: WebResourceRequest): String? {
        val url = request.url ?: return null
        if (url.host != "127.0.0.1") return null
        if (url.scheme != "https" && url.scheme != "http") return null
        val path = url.path ?: return null
        if (!path.startsWith("/fonts/")) return null
        val name = Uri.decode(path.removePrefix("/fonts/").substringBefore('?'))
        return name.takeUnless {
            it.isBlank() || it.contains("..") || it.contains('/') || it.contains('\\')
        }
    }

    /** 仅对成功的 XHTML/HTML 资源做首帧注入（跳过压缩/图片/样式等） */
    private fun isHtmlResource(request: WebResourceRequest, response: WebResourceResponse): Boolean {
        if (response.statusCode != 200) return false
        if (response.responseHeaders?.get("Content-Encoding") != null) return false
        val mime = response.mimeType.orEmpty().lowercase()
        if (mime.contains("html")) return true
        val path = request.url?.path.orEmpty().lowercase()
        return path.endsWith(".xhtml") || path.endsWith(".html") || path.endsWith(".htm")
    }

    /** ReadiumCSS-after.css（含 rtl/cjk 变体）路径判断 */
    private fun isReadiumCssAfter(request: WebResourceRequest): Boolean =
        request.url?.path.orEmpty().contains("ReadiumCSS-after.css")

    /** 把阅读器 CSS 直接注入 <head>，使自定义字体/间距在第一次布局就生效 */
    private fun injectCssIntoHtml(response: WebResourceResponse, css: String): WebResourceResponse? {
        val stream = response.data ?: return null
        val bytes = try {
            stream.readBytes()
        } catch (_: Exception) {
            null
        } ?: return null
        val encoding = response.encoding?.takeIf { it.isNotBlank() } ?: "utf-8"
        val charset = try {
            Charset.forName(encoding)
        } catch (_: Exception) {
            Charsets.UTF_8
        }
        val html = try {
            String(bytes, charset)
        } catch (_: Exception) {
            String(bytes, Charsets.UTF_8)
        }
        val headMatch = Regex("""<head[^>]*>""", RegexOption.IGNORE_CASE).find(html) ?: return null
        val insertAt = headMatch.range.last + 1
        val injected = html.substring(0, insertAt) + css + html.substring(insertAt)
        return WebResourceResponse(
            response.mimeType ?: "text/html",
            encoding,
            200,
            "OK",
            response.responseHeaders,
            ByteArrayInputStream(injected.toByteArray(charset)),
        )
    }

    /**
     * 删除 ReadiumCSS 中"用户颜色注入后强制剥离出版方颜色"的两条规则（仅用于首帧优化，JS removeProperty 为稳健兜底）：
     * 1) :root[style*="--USER__backgroundColor"] *{background-color:transparent!important}  → 保留出版方形状背景
     * 2) :root[style*="--USER__textColor"] :not(h1)...:not(pre){color:inherit!important}     → 保留出版方文字颜色
     * 保留 --USER__backgroundColor / --USER__textColor 根规则本身（页面主题底色/字色仍生效）。
     */
    private fun removeUserStripRules(response: WebResourceResponse): WebResourceResponse {
        if (response.statusCode != 200) return response
        val stream = response.data ?: return response
        val bytes = try {
            stream.readBytes()
        } catch (_: Exception) {
            null
        } ?: return response
        val encoding = response.encoding?.takeIf { it.isNotBlank() } ?: "utf-8"
        val charset = try {
            Charset.forName(encoding)
        } catch (_: Exception) {
            Charsets.UTF_8
        }
        val cssText = try {
            String(bytes, charset)
        } catch (_: Exception) {
            String(bytes, Charsets.UTF_8)
        }
        val bgStrip = Regex(
            """\s*:root\[style\*="--USER__backgroundColor"\]\s*\*\s*\{background-color:transparent!important\}"""
        )
        val textStrip = Regex(
            """\s*:root\[style\*="--USER__textColor"\]\s+:not\(h1\):not\(h2\):not\(h3\):not\(h4\):not\(h5\):not\(h6\):not\(pre\)\{color:inherit!important\}"""
        )
        val cleaned = textStrip.replace(bgStrip.replace(cssText, ""), "")
        if (cleaned == cssText) return response
        return WebResourceResponse(
            response.mimeType ?: "text/css",
            encoding,
            200,
            "OK",
            response.responseHeaders,
            ByteArrayInputStream(cleaned.toByteArray(charset)),
        )
    }
}
/** 向全部 WebView 注入字体 CSS，并确保混合内容放行 */
private fun injectFontCss(
    webViews: List<WebView>,
    css: String,
    fontResponse: ((String) -> WebResourceResponse?)?,
    firstPaintCssProvider: (() -> String?)?,
    stripBackgroundRuleProvider: (() -> Boolean)?,
) {
    if (webViews.isEmpty() || css.isBlank()) return
    Log.d(TAG, "inject font css len=${css.length} into ${webViews.size} webView(s)")
    val js = fontCssInjectJs(css)
    webViews.forEach { wv ->
        prepareWebViewForFonts(wv, fontResponse, firstPaintCssProvider, stripBackgroundRuleProvider)
        wv.post { wv.evaluateJavascript(js) { res -> Log.d(TAG, "font css diag: $res") } }
    }
}
/** ReadSettings → 注入间距 CSS（与 TXT 语义一致）：
 *  - 行距 = 无单位倍数；段间距 / 首行缩进 = em（相对字号）；
 *  - 上下边距由 Compose 层 padding 控制（24dp × 倍率）；左右边距由 Readium pageMargins 控制。
 *  直接以 !important 覆盖出版方样式，保证 EPUB 下同样生效（不依赖 Readium advanced settings 是否启用）。 */
private fun buildReaderCss(settings: ReadSettings): String {
    val lh = settings.lineHeight.coerceIn(1.0f, 3.0f)
    val ps = settings.paragraphSpacing.coerceIn(0f, 3f)
    val fi = settings.firstLineIndent.coerceIn(0f, 4f)
    // 出版方样式关闭：所有文字（含标题/pre）跟随主题字色；链接显示专属链接色。
    // 链接色按页面背景明暗选择，深色背景用亮蓝，浅色背景用标准蓝/访问紫。
    val bg = readerPageBackground(settings)
    val lum = 0.299f * bg.red + 0.587f * bg.green + 0.114f * bg.blue
    val (linkHex, visitedHex) = if (lum < 0.5f) "63caff" to "0099e5" else "0057d9" to "8e24aa"
    return """
:root{line-height:${lh}!important}
body,div,li,p{line-height:inherit!important}
p{margin-top:${ps}em!important;margin-bottom:${ps}em!important;text-indent:${fi}em!important}
p *,p:first-letter{text-indent:0!important}
h1+p,h2+p,h3+p,h4+p,h5+p,h6+p,hr+p{text-indent:0!important}
:root :not(a){color:inherit!important}
:root a[href]:link{color:#$linkHex!important}
:root a[href]:visited{color:#$visitedHex!important}
""".trimIndent()
}

/**
 * 出版方样式开启时的主题底色/字色注入（--RS__ 变量）：
 * 配合移除 html 内联的 --USER__backgroundColor 与 --USER__appearance 使用——移除两者后，
 * ReadiumCSS 的 `:root[style*="--USER__backgroundColor"] *{background-color:transparent!important}`、
 * `:root[style*=readium-sepia-on] :not(a){background-color:transparent!important}`、
 * `:root[style*=readium-night-on] :not(a){background-color:transparent!important}` 三条剥离规则均不再命中，
 * 出版方形状背景得以保留；页面底色/字色回退到 --RS__ 变量以保持阅读器主题色。
 * 仅夜间模式返回 null（夜间由 ReadiumCSS 自身 night 规则接管：剥离形状 + 深色，逻辑勿动）。
 */
private fun buildPublisherThemeCss(settings: ReadSettings): String? {
    if (settings.nightMode) return null
    val bg = 0xFFFFFF and readerPageBackground(settings).toArgb()
    val ink = 0xFFFFFF and readerInkColor(settings).toArgb()
    val bgHex = "%06X".format(bg)
    val inkHex = "%06X".format(ink)
    return ":root{--RS__backgroundColor:#$bgHex!important;--RS__textColor:#$inkHex!important}"
}

// ── 间距 CSS 注入：<style id="nrv-spacing-css"> 内容 = 行距/段距/首行缩进 ──
private fun spacingCssInjectJs(css: String): String {
    val quoted = JSONObject.quote(css)
    return """(function(){
  var s = document.getElementById('nrv-spacing-css');
  if (!s) { s = document.createElement('style'); s.id = 'nrv-spacing-css'; document.head.appendChild(s); }
  s.textContent = $quoted;
  return 'ok';
})();"""
}

/** 向全部 WebView 注入上下边距 CSS */
private fun injectSpacingCss(webViews: List<WebView>, css: String) {
    if (webViews.isEmpty() || css.isBlank()) return
    Log.d(TAG, "inject spacing css len=${css.length} into ${webViews.size} webView(s)")
    val js = spacingCssInjectJs(css)
    webViews.forEach { wv ->
        wv.post { wv.evaluateJavascript(js, null) }
    }
}

/** 移除注入的间距 CSS（出版方样式开启时恢复出版方排版，避免残留 !important 覆盖） */
private fun removeSpacingCss(webViews: List<WebView>) {
    if (webViews.isEmpty()) return
    val js = "(function(){var s=document.getElementById('nrv-spacing-css');if(s&&s.parentNode){s.parentNode.removeChild(s);}return 'ok';})();"
    webViews.forEach { wv ->
        wv.post { wv.evaluateJavascript(js, null) }
    }
}

/** 移除注入的字体 CSS（出版方样式开启时恢复出版方字体，避免残留 !important 字体栈覆盖） */
private fun removeFontCss(webViews: List<WebView>) {
    if (webViews.isEmpty()) return
    val js = "(function(){var s=document.getElementById('nrv-font-css');if(s&&s.parentNode){s.parentNode.removeChild(s);}return 'ok';})();"
    webViews.forEach { wv ->
        wv.post { wv.evaluateJavascript(js, null) }
    }
}

/**
 * 出版方样式的主题/形状处理 JS：
 * - css 非空（夜间以外的全部主题）：移除 html 内联的 --USER__backgroundColor / --USER__appearance / --USER__textColor
 *   （分别解除 ReadiumCSS 的背景剥离规则、sepia/night 剥离规则、文字颜色继承规则，保留出版方形状背景与显式文字颜色），
 *   并注入 nrv-theme-css 以 --RS__ 变量保持主题页面底色/字色；
 * - css 为空（夜间模式）：仅移除 nrv-theme-css，--USER__ 与 readium-night-on 保持原样，
 *   由 ReadiumCSS night 规则接管（剥离形状 + 深色，逻辑勿动）。
 * 注意：Readium 的 setCSSProperties 会在每次设置变更/资源加载时重新写入 --USER__backgroundColor / --USER__appearance / --USER__textColor，
 * 因此本脚本需要在每次设置变更、页面加载后重复执行。
 */
private fun publisherThemeApplyJs(css: String?): String {
    return if (css != null) {
        val quoted = JSONObject.quote(css)
        """(function(){
  try { var h = document.documentElement; if (h && h.style) { h.style.removeProperty('--USER__backgroundColor'); h.style.removeProperty('--USER__appearance'); h.style.removeProperty('--USER__textColor'); } } catch (e) {}
  var s = document.getElementById('nrv-theme-css');
  if (!s) { s = document.createElement('style'); s.id = 'nrv-theme-css'; document.head.appendChild(s); }
  s.textContent = $quoted;
  return 'ok';
})();"""
    } else {
        "(function(){var s=document.getElementById('nrv-theme-css');if(s&&s.parentNode){s.parentNode.removeChild(s);}return 'ok';})();"
    }
}

/** 应用出版方样式的主题/形状处理（css=null 时仅做清理） */
private fun applyPublisherTheme(webViews: List<WebView>, css: String?) {
    if (webViews.isEmpty()) return
    val js = publisherThemeApplyJs(css)
    webViews.forEach { wv ->
        wv.post { wv.evaluateJavascript(js, null) }
    }
}

// ── WebView 递归查找 ──

/**
 * 找到当前正在显示的 WebView。
 * R2ViewPager 会预加载相邻 spine 的 WebView，findWebView 返回的第一个并不一定是当前章；
 * 优先按当前 href 匹配 URL，其次按全局可见面积取最大者。
 */
private fun findCurrentWebView(view: View?, href: String?): WebView? {
    val all = findAllWebViews(view)
    if (all.isEmpty()) return null

    if (!href.isNullOrBlank()) {
        all.firstOrNull { it.matchesHref(href) }?.let { return it }
    }

    val visible = all.filter { wv ->
        val r = android.graphics.Rect()
        wv.isShown && wv.getGlobalVisibleRect(r) && r.width() > 0 && r.height() > 0
    }
    return visible.maxByOrNull { wv ->
        val r = android.graphics.Rect()
        wv.getGlobalVisibleRect(r)
        r.width().toLong() * r.height()
    } ?: all.firstOrNull()
}

/** 只在 fragment 已附加且有 view 时返回 view，避免 requireView() 在销毁/未创建阶段崩溃 */
private fun EpubNavigatorFragment?.safeView(): View? =
    this?.takeIf { it.isAdded && !it.isDetached }?.view

/** 判断 WebView 是否仍在加载当前 href 对应章节，用于跳过无效的视图树搜索 */
private fun WebView.matchesHref(href: String?): Boolean {
    val bare = href?.substringBefore('#') ?: return false
    val url = this.url?.substringBefore('#')?.substringBefore('?') ?: return false
    return url.endsWith(bare) || url.endsWith("/${bare.substringAfterLast('/')}")
}
