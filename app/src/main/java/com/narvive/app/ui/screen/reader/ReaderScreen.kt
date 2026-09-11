package com.narvive.app.ui.screen.reader

import android.app.Activity
import android.graphics.Bitmap
import android.content.BroadcastReceiver
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.BatteryManager
import android.os.Handler
import android.os.Looper
import android.view.PixelCopy
import android.view.WindowManager
import androidx.activity.compose.BackHandler
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.List
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Bookmark
import androidx.compose.material.icons.rounded.Brightness2
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.UnfoldMore
import androidx.compose.material.icons.rounded.WbIncandescent
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.narvive.app.R
import com.narvive.app.service.font.resolveTxtFontFamily
import com.narvive.app.service.reader.EpubReaderController
import com.narvive.app.service.reader.ReadSettings
import com.narvive.app.service.reader.TxtReaderController
import com.narvive.app.ui.message.text
import com.narvive.app.ui.screen.chat.ChatContent
import com.narvive.app.ui.screen.chat.ChatViewModel
import com.narvive.app.ui.screen.chat.GraphSheetDialog
import com.narvive.app.ui.screen.chat.QuickCommand
import com.narvive.app.domain.repository.AiConversationInfo
import com.narvive.app.ui.screen.reader.components.AaPanel
import com.narvive.app.ui.screen.reader.components.AnchoredBubble
import com.narvive.app.ui.screen.reader.components.CustomThemeBottomSheet
import com.narvive.app.ui.screen.reader.components.CustomSpacingBottomSheet
import com.narvive.app.ui.screen.reader.components.BrightnessPanel
import com.narvive.app.ui.screen.reader.components.HighlightMenuBubble
import com.narvive.app.ui.screen.reader.components.InBookSearchOverlay
import com.narvive.app.ui.screen.reader.components.SelectionBubble
import com.narvive.app.ui.screen.reader.components.TocBottomSheet
import com.narvive.app.ui.screen.reader.components.TranslationCard
import com.narvive.app.ui.screen.reader.selection.SelectionController
import com.narvive.app.ui.screen.reader.selection.SelectionOverlay
import com.narvive.app.ui.screen.reader.selection.SelectionTarget
import com.narvive.app.ui.theme.NarviveMotion
import com.narvive.app.ui.theme.NarviveShape
import com.narvive.app.ui.theme.SemanticColors
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import kotlin.time.Duration.Companion.seconds
import kotlin.math.abs
import kotlin.math.max

@Composable
fun ReaderScreen(
    bookId: String,
    onBackClick: () -> Unit,
    overrideLocator: String? = null,
    onRewriteClick: (text: String, mode: String) -> Unit = { _, _ -> },
    onRoleplayClick: () -> Unit = {},
    onOpenAiSettings: () -> Unit = {},
    onOpenFontSettings: () -> Unit = {},
    onOpenMoreSettings: () -> Unit = {},
    viewModel: ReaderViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    // 自定义间距窗口（AaPanel 第二行「自定义」按钮弹出，置于 Aa 面板之上）
    var isSpacingSheetOpen by remember { mutableStateOf(false) }
    // 自定义主题窗口（AaPanel 第三行「自定义」按钮弹出，置于 Aa 面板之上）
    var isThemeSheetOpen by remember { mutableStateOf(false) }
    // Aa 面板关闭时同步关闭自定义间距窗口，避免下次打开面板时自动弹窗
    LaunchedEffect(uiState.isAaPanelOpen) {
        if (!uiState.isAaPanelOpen) {
            isSpacingSheetOpen = false
            isThemeSheetOpen = false
        }
    }

    // ── 返回前恢复系统栏并立即出栈 ──
    val exitContext = LocalContext.current
    var exitInProgress by remember { mutableStateOf(false) }
    fun requestExit() {
        if (exitInProgress) return
        exitInProgress = true
        viewModel.saveProgress()
        (exitContext as? Activity)?.window?.let { w ->
            WindowCompat.getInsetsController(w, w.decorView).show(WindowInsetsCompat.Type.systemBars())
        }
        onBackClick()
    }

    // 兜底：拦截系统返回（OnBackPressedDispatcher 路径）
    BackHandler {
        when {
            uiState.selectionActive -> viewModel.clearSelection()
            uiState.isTocOpen -> viewModel.toggleToc()
            uiState.isAutoFlipActive -> viewModel.stopAutoFlip()
            uiState.isHudVisible -> viewModel.hideHud()
            else -> requestExit()
        }
    }

    LaunchedEffect(bookId) { viewModel.loadBook(bookId, overrideLocator) }

    // ── 主题色：底层瞬时切到目标色，旧色由全屏遮罩统一淡出，保证 HUD/边距/正文背景同步 ──
    val targetPageBg = readerPageBackground(uiState.readSettings)
    val targetInk = readerInkColor(uiState.readSettings)
    val pageBg = targetPageBg
    val barBg = Color(pageBg.red * 0.85f, pageBg.green * 0.85f, pageBg.blue * 0.85f)
    val ink = targetInk

    val currentTheme = themeColorSignature(uiState.readSettings)
    var previousTheme by remember { mutableStateOf(currentTheme) }
    var previousSettings by remember { mutableStateOf(uiState.readSettings) }
    var themeTransitionBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var capturedTheme by remember { mutableStateOf<String?>(null) }
    // 首次加载完成标记：区分「进入阅读页时默认设置→实际设置」与「用户主动切换主题」。
    // 前者绝不能触发旧色遮罩，否则深色模式进入时会把默认浅色纸白当成“旧色”闪一帧。
    var initialLoadDone by remember { mutableStateOf(false) }
    LaunchedEffect(uiState.book) {
        if (uiState.book != null && !initialLoadDone) {
            initialLoadDone = true
            previousTheme = currentTheme
            previousSettings = uiState.readSettings
        }
    }

    // 兜底旧色遮罩：仅在首次加载完成之后、且确实发生主题切换（截图失败兜底）时启用。
    val fallbackOverlayColor = remember(currentTheme, initialLoadDone) {
        if (initialLoadDone && capturedTheme != currentTheme && previousTheme != currentTheme) {
            readerPageBackground(previousSettings)
        } else null
    }
    val fallbackOverlayAlpha = remember(currentTheme) {
        Animatable(if (fallbackOverlayColor != null) 1f else 0f)
    }
    LaunchedEffect(currentTheme) {
        if (capturedTheme == currentTheme) {
            capturedTheme = null
            return@LaunchedEffect
        }
        if (fallbackOverlayColor != null) {
            previousSettings = uiState.readSettings
            previousTheme = currentTheme
            delay(60)
            fallbackOverlayAlpha.animateTo(0f, tween(NarviveMotion.Medium))
        }
    }

    // 截图遮罩：bitmap 就绪后由新 Animatable(1f) 从第一帧盖住旧画面
    val themeBitmapAlpha = remember(themeTransitionBitmap) {
        Animatable(if (themeTransitionBitmap != null) 1f else 0f)
    }
    LaunchedEffect(themeTransitionBitmap) {
        val bmp = themeTransitionBitmap ?: return@LaunchedEffect
        delay(60)
        themeBitmapAlpha.animateTo(0f, tween(NarviveMotion.Medium))
        if (themeTransitionBitmap === bmp) {
            themeTransitionBitmap = null
            bmp.recycle()
        }
    }

    uiState.fatalError?.let { errMsg ->
        ErrorFallback(message = errMsg.text(), onBack = onBackClick)
        return
    }

    // 加载中占位：使用外观主题背景（明暗感知）。不能用阅读主题 pageBg——此刻 readSettings 仍是默认
    // 浅色「paper」，会在读取到用户实际阅读设置（夜间/深色主题）前闪一帧浅色纸白。
    if (uiState.book == null) {
        Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                CircularProgressIndicator(color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f), strokeWidth = 2.dp)
                Spacer(Modifier.height(16.dp))
                Text(stringResource(R.string.reader_opening_book), color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f), style = MaterialTheme.typography.bodySmall)
            }
        }
        return
    }
    val book = uiState.book!!
    val context = LocalContext.current
    val density = LocalDensity.current

    // 自动翻页是独立模式：开启期间临时按「分页 + none」渲染，ReadSettings.pageFlipAnimation 保持不变，
    // 退出自动翻页后恢复手动模式（含上下滚动）。
    val effectivePageFlip = if (uiState.isAutoFlipActive) "none" else uiState.readSettings.pageFlipAnimation
    val effectiveSettings = uiState.readSettings.copy(pageFlipAnimation = effectivePageFlip)

    // ---------- AI 面板状态 ----------
    val chatViewModel: ChatViewModel = hiltViewModel(key = "reader-ai")
    val chatUiState by chatViewModel.uiState.collectAsState()
    var aiSheetOpen by remember { mutableStateOf(false) }
    var showChatHistory by remember { mutableStateOf(false) }
    var chatDeleteTarget by remember { mutableStateOf<AiConversationInfo?>(null) }
    val coroutineScope = rememberCoroutineScope()
    val openAiSheet: (String?) -> Unit = { selection ->
        chatViewModel.init(
            bookId = bookId,
            selectedText = selection,
            chapterTitle = uiState.currentChapter.ifBlank { null },
            locatorJson = viewModel.currentLocatorJson(),
            chapterTextProvider = { viewModel.currentChapterText() },
        )
        aiSheetOpen = true
    }

    // 亮度：跟随系统时清除覆盖，否则手动调节
    val activity = context as? Activity

    // 截图 crossfade：切换前抓旧画面，底层瞬时切换后由旧截图整屏淡出，文字/图标不会重新“浮现”
    fun startCapturedThemeSwitch(newSettings: ReadSettings, apply: () -> Unit) {
        val newTheme = themeColorSignature(newSettings)
        val w = activity?.window
        if (w == null) {
            apply()
            return
        }
        val decor = w.decorView
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && decor.width > 0 && decor.height > 0) {
            val bmp = Bitmap.createBitmap(decor.width, decor.height, Bitmap.Config.ARGB_8888)
            try {
                PixelCopy.request(w, bmp, { result ->
                    if (result == PixelCopy.SUCCESS) {
                        capturedTheme = newTheme
                        previousTheme = newTheme
                        previousSettings = newSettings
                        themeTransitionBitmap?.recycle()
                        themeTransitionBitmap = bmp
                    } else {
                        bmp.recycle()
                    }
                    apply()
                }, Handler(Looper.getMainLooper()))
            } catch (_: Exception) {
                if (!bmp.isRecycled) {
                    bmp.recycle()
                }
                apply()
            }
        } else {
            apply()
        }
    }

    LaunchedEffect(uiState.readSettings.brightness, uiState.readSettings.followSystemBrightness) {
        activity?.window?.let { w ->
            val lp = w.attributes
            if (uiState.readSettings.followSystemBrightness) {
                lp.screenBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
            } else {
                lp.screenBrightness = uiState.readSettings.brightness.coerceIn(0.01f, 1f)
            }
            w.attributes = lp
        }
    }
    DisposableEffect(Unit) {
        onDispose {
            activity?.window?.let { w ->
                val lp = w.attributes
                lp.screenBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
                w.attributes = lp
            }
        }
    }
    // ── 沉浸模式：系统栏自动隐藏 ──
    val window = (context as? Activity)?.window
    var barsFirstApplied by remember { mutableStateOf(false) }
    LaunchedEffect(uiState.isHudVisible, uiState.isAaPanelOpen) {
        val show = uiState.isHudVisible || uiState.isAaPanelOpen
        // 首次应用（进入阅读器 / 从「字体」「更多设置」返回重建）时若要隐藏，
        // 延迟到返回动画结束后再隐藏，避免系统栏收起与 NavHost fadeIn 叠加造成闪动
        if (!barsFirstApplied) {
            barsFirstApplied = true
            if (!show) delay(320)
        }
        window?.let { w ->
            val controller = WindowCompat.getInsetsController(w, w.decorView)
            // 粘性沉浸：状态栏/导航栏隐藏，仅上/下边缘有意滑出才短暂显示并自动收回；
            // 左/右侧边返回手势不再连带状态栏
            controller.systemBarsBehavior =
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            if (show) controller.show(WindowInsetsCompat.Type.systemBars())
            else controller.hide(WindowInsetsCompat.Type.systemBars())
        }
    }
    DisposableEffect(Unit) {
        onDispose {
            // 离开阅读器时重置覆盖层状态，并立即恢复系统栏：
            // 立即恢复可让返回目标页（笔记/详情）首帧就带正确的状态栏 inset，避免标题从左上角跳回
            viewModel.closeAaPanel()
            viewModel.hideHud()
            window?.let { w ->
                WindowCompat.getInsetsController(w, w.decorView).show(WindowInsetsCompat.Type.systemBars())
            }
        }
    }

    var rootHeightPx by remember { mutableFloatStateOf(0f) }
    var rootWidthPx by remember { mutableFloatStateOf(0f) }
    // 阅读根容器 LayoutCoordinates（选区 hit-test 的 root 坐标基准）
    var rootCoords by remember { mutableStateOf<LayoutCoordinates?>(null) }
    // 当前 TXT 容器上报的 SelectionTarget（SelectionOverlay 数据源）
    var selectionTarget by remember { mutableStateOf<SelectionTarget?>(null) }
    // TXT 分页模式点击/横滑桥（pager 内部 pointerInput 在本 Compose 版本不启动，由根裁判转发）
    var txtPagedTapBridge by remember { mutableStateOf<((Float, Float) -> Unit)?>(null) }
    var txtPagedFlickBridge by remember { mutableStateOf<((Int) -> Unit)?>(null) }
    // TXT 滚动模式点击桥（item 内 detectTapGestures 同样不启动）：返回 true=已处理（翻译/高亮）
    var txtTapBridge by remember { mutableStateOf<((Float, Float) -> Boolean)?>(null) }
    // 选区气泡实际矩形（根裁判据此放行气泡上的点按）
    var selectionBubbleBounds by remember { mutableStateOf<androidx.compose.ui.geometry.Rect?>(null) }
    LaunchedEffect(uiState.selection) {
        if (uiState.selection == null) selectionBubbleBounds = null
    }
    // 选区状态机（根裁判驱动；SelectionOverlay 纯渲染）
    val selectionScrollScope = rememberCoroutineScope()
    val selectionController = remember(selectionTarget) {
        selectionTarget?.let { target ->
            SelectionController(
                target = target,
                onSelectionUpdated = viewModel::onSelectionUpdated,
                onActiveChanged = viewModel::setSelectionActive,
            ).also { it.scrollScope = selectionScrollScope }
        }
    }
    // 外部清除（HUD 打开/自动翻页等）同步回 Idle
    LaunchedEffect(uiState.selectionActive) {
        selectionController?.resetIfExternalCleared(uiState.selectionActive)
    }
    // 沉浸信息
    var batteryLevel by remember { mutableIntStateOf(-1) }
    var currentTimeString by remember { mutableStateOf("") }
    var lastVolumeTime by remember { mutableLongStateOf(0L) }  // 音量键防抖
    // ── 独立防抖 ──
    var lastPageTurnTime by remember { mutableLongStateOf(0L) }
    var lastBookmarkTime by remember { mutableLongStateOf(0L) }
    // ── 屏幕关闭时间：不显示遮罩，只控制是否允许系统自动息屏 ──
    var lastInteractionAt by remember { mutableLongStateOf(System.currentTimeMillis()) }
    val screenOffMinutesState = rememberUpdatedState(uiState.readSettings.screenOffMinutes)
    val isAutoFlipActiveState = rememberUpdatedState(uiState.isAutoFlipActive)
    fun syncKeepScreenOn() {
        val w = activity?.window ?: return
        val minutes = screenOffMinutesState.value
        val keepAlive = isAutoFlipActiveState.value ||
            minutes <= 0 ||
            System.currentTimeMillis() - lastInteractionAt < minutes * 60_000L
        if (keepAlive) {
            w.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        } else {
            w.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }
    LaunchedEffect(screenOffMinutesState.value, isAutoFlipActiveState.value) {
        while (true) {
            syncKeepScreenOn()
            delay(1000)
        }
    }
    DisposableEffect(Unit) {
        onDispose {
            activity?.window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }
    // ── 休息提醒 ──
    var restSeconds by remember { mutableLongStateOf(0L) }
    var showRestReminder by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(1000)
            val readingActive = !uiState.isHudVisible && !uiState.isAaPanelOpen &&
                !uiState.isTocOpen && !uiState.isSearchOpen &&
                !uiState.isBrightnessPanelOpen && !uiState.isAutoFlipPaused
            if (uiState.readSettings.restReminderEnabled && readingActive) {
                restSeconds++
                val minutes = uiState.readSettings.restReminderMinutes
                if (minutes > 0 && restSeconds >= minutes * 60L) {
                    restSeconds = 0
                    showRestReminder = true
                }
            }
        }
    }
    // ── 书签下拉 ──
    var isPulling by remember { mutableStateOf(false) }
    var pullDistance by remember { mutableFloatStateOf(0f) }
    val haptic = LocalHapticFeedback.current
    var hapticTriggered by remember { mutableStateOf(false) }
    val thresholdPx = with(density) { 60.dp.toPx() }
    val focusRequester = remember { FocusRequester() }

    // ── 自动翻页扫描线 ──
    val scanProgress = remember { Animatable(0f) }
    val scanHandledEpoch = remember { mutableIntStateOf(-1) }
    val autoFlipSpeed = uiState.readSettings.autoFlipSpeedSeconds.coerceIn(10, 120)
    LaunchedEffect(uiState.isAutoFlipActive, uiState.isAutoFlipPaused, uiState.autoFlipTurnEpoch, autoFlipSpeed) {
        if (!uiState.isAutoFlipActive) {
            scanHandledEpoch.intValue = -1
            scanProgress.snapTo(0f)
            return@LaunchedEffect
        }
        // 底栏呼出即暂停：保持扫描线当前位置不动
        if (uiState.isAutoFlipPaused) return@LaunchedEffect
        if (scanHandledEpoch.intValue != uiState.autoFlipTurnEpoch) {
            scanHandledEpoch.intValue = uiState.autoFlipTurnEpoch
            scanProgress.snapTo(0f)
        }
        val remainingMs = ((1f - scanProgress.value) * autoFlipSpeed * 1000).toInt().coerceAtLeast(1)
        scanProgress.animateTo(1f, tween(remainingMs, easing = LinearEasing))
        if (uiState.isAutoFlipActive && !uiState.isAutoFlipPaused) {
            lastInteractionAt = System.currentTimeMillis()   // 重置屏幕常亮计时器
            viewModel.autoFlipRequestTurn()
        }
    }

    // 电池监听
    DisposableEffect(context) {
        val filter = IntentFilter(Intent.ACTION_BATTERY_CHANGED)
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context?, intent: Intent?) {
                val level = intent?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
                val scale = intent?.getIntExtra(BatteryManager.EXTRA_SCALE, 100) ?: 100
                batteryLevel = (level * 100 / scale).coerceIn(0, 100)
            }
        }
        context.registerReceiver(receiver, filter)
        onDispose { context.unregisterReceiver(receiver) }
    }

    // 系统时间
    LaunchedEffect(Unit) {
        val fmt = DateTimeFormatter.ofPattern("HH:mm")
        while (true) {
            currentTimeString = LocalTime.now().format(fmt)
            delay(30.seconds)
        }
    }

    // ═══════════════════════════════════════
    //  主 Box：整体下移（书签下拉动画）
    // ═══════════════════════════════════════
    val isScrollModeState = rememberUpdatedState(effectivePageFlip == "updown")
    // EPUB 平移动画：横向拖动交给 Readium 原生 WebView（R2WebView 自带 1:1 跟手 + snap），根裁判不消费
    val epubSlideState = rememberUpdatedState(
        book.format == "EPUB" && effectivePageFlip == "slide"
    )
    // EPUB 上下滚动：x 轴必须锁定，根裁判消费水平拖动，避免 Readium 在章节首尾左右切章
    val epubUpDownState = rememberUpdatedState(
        book.format == "EPUB" && effectivePageFlip == "updown"
    )
    val epubNoneState = rememberUpdatedState(
        book.format == "EPUB" && effectivePageFlip == "none"
    )
    // 选区激活态（手势中实时豁免：根裁判不翻页/不切 HUD/不下拉书签）
    val selectionActiveState = rememberUpdatedState(uiState.selectionActive)
    // EPUB：点按统一交给 Readium（翻页/装饰点按/中心 HUD），根裁判不处理点按，避免装饰点按误触发翻页/系统栏
    val isEpubBookState = rememberUpdatedState(book.format == "EPUB")
    // TXT 分页 none 模式（横滑翻页由根裁判桥转发）
    val txtPagedNoneState = rememberUpdatedState(
        book.format == "TXT" && effectivePageFlip == "none"
    )
    // TXT 选区（根裁判驱动 SelectionController）
    val txtSelectionState = rememberUpdatedState(book.format == "TXT")
    val selectionControllerState = rememberUpdatedState(selectionController)
    val selectionBubbleBoundsState = rememberUpdatedState(selectionBubbleBounds)
    val txtTapBridgeState = rememberUpdatedState(txtTapBridge)
    // TXT 分页模式（slide/none）：翻页与点击交给 TxtPagedViewer 内部 HorizontalPager 处理，
    // 根裁判只保留垂直下拉书签，水平/点击全部穿透
    val txtPagedState = rememberUpdatedState(
        uiState.book?.format == "TXT" && effectivePageFlip != "updown"
    )
    // 阅读区安全区：即使 verticalMargin=0，也不侵入沉浸信息（顶部章名/底部进度电池）
    val safeTop = 56.dp
    val safeBottom = 32.dp
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(pageBg)
            .onSizeChanged { rootHeightPx = it.height.toFloat(); rootWidthPx = it.width.toFloat() }
            .onGloballyPositioned { rootCoords = it }
            .graphicsLayer { translationY = pullDistance }
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) {
                        awaitPointerEvent(PointerEventPass.Initial)
                        lastInteractionAt = System.currentTimeMillis()
                        syncKeepScreenOn()
                    }
                }
            }
            // 注意：key 不能包含 selectionActive——begin() 会置位该状态，若 key 含它会导致
            // pointerInput 在长按触发瞬间重启、杀死正在进行的 awaitEachGesture（后续 drag/up 丢失）。
            // selectionActive 的豁免由循环内 selectionActiveState 运行时检查承担。
            .pointerInput(uiState.isHudVisible, uiState.isAutoFlipActive, uiState.tappedTranslation, uiState.tappedHighlight) {
                if (uiState.isHudVisible || uiState.isAutoFlipActive || uiState.tappedTranslation != null || uiState.tappedHighlight != null) return@pointerInput

                val clickSlop = with(density) { 16.dp.toPx() }
                val flickThreshold = with(density) { 200.dp.toPx() }
                val pageSwipeRatio = 0.12f
                val bookmarkThreshold = with(density) { 60.dp.toPx() }
                val tapTimeoutMs = 450L
                val edgeRatio = 0.22f
                val maxPullPx = rootHeightPx * 0.18f

                awaitEachGesture {
                    val down = awaitFirstDown(pass = PointerEventPass.Initial, requireUnconsumed = false)
                    val downTime = System.currentTimeMillis()
                    val downX = down.position.x
                    var dx = 0f; var dy = 0f
                    var isDrag = false; var isHorizontal = false
                    var cancelled = false
                    var peakVelocityX = 0f
                    var lastVelocityX = downX
                    var lastVelocityTime = downTime
                    val isUpDown = isScrollModeState.value
                    val isTxtPaged = txtPagedState.value
                    val isEpubSlide = epubSlideState.value
                    val isEpubUpDown = epubUpDownState.value
                    val isEpubNone = epubNoneState.value
                    // ── TXT 选区手势状态（根裁判驱动 SelectionController）──
                    val selCtrl = selectionControllerState.value
                    val isTxtBook = txtSelectionState.value
                    var selDrag = false
                    var longPressTriggered = false
                    var swallowGesture = false
                    val longPressMs = 480L
                    val selEdgePx = with(density) { 48.dp.toPx() }
                    val selScrollStepPx = with(density) { 2.dp.toPx() }
                    val selFlipCornerPx = with(density) { 96.dp.toPx() }

                    // Active 态（选区已存在）：气泡优先放行，其次手柄拖拽 / 选区保持 / 点外收起
                    if (isTxtBook && selCtrl != null && selCtrl.active) {
                        val inBubble = selectionBubbleBoundsState.value
                            ?.let { it.contains(down.position) } == true
                        if (!inBubble) {
                            when (val which = selCtrl.downInActive(down.position)) {
                                2, 3 -> {
                                    selCtrl.beginHandleDrag(which)
                                    selDrag = true
                                    cancelled = true // 选区手势：跳过后续翻页/HUD/点击
                                    down.consume()
                                }
                                1 -> {
                                    // 点按选区内：吞掉本次手势，保持选区
                                    swallowGesture = true
                                }
                                else -> {
                                    // 点按选区外：收起选区（吞掉本次点按）
                                    selCtrl.dismiss()
                                    down.consume()
                                    return@awaitEachGesture
                                }
                            }
                        }
                    }

                    do {
                        val event = awaitPointerEvent(pass = PointerEventPass.Initial)
                        val change = event.changes.firstOrNull() ?: break

                        // 选区内点按：整段吞掉
                        if (swallowGesture) {
                            change.consume()
                            continue
                        }

                        // 选区拖拽（长按触发后的扩选/拖柄）
                        if (selDrag && selCtrl != null) {
                            if (change.pressed) {
                                selCtrl.drag(change.position)
                                if (isUpDown) {
                                    // 上下滚动：上下边缘低速自动滚动续选
                                    val topEdgeY = with(density) { 56.dp.toPx() }
                                    val bottomEdgeY = rootHeightPx - with(density) { 32.dp.toPx() }
                                    val dir = when {
                                        change.position.y > bottomEdgeY - selEdgePx -> 1
                                        change.position.y < topEdgeY + selEdgePx -> -1
                                        else -> 0
                                    }
                                    if (dir != 0) selCtrl.autoScrollStart(selScrollStepPx * dir) else selCtrl.autoScrollStop()
                                } else if (isTxtPaged) {
                                    // 分页(slide/none)：仅左上角(上一页)/右下角(下一页)翻页，进度满才翻
                                    val dir = when {
                                        change.position.x < selFlipCornerPx && change.position.y < selFlipCornerPx -> -1
                                        change.position.x > size.width - selFlipCornerPx &&
                                            change.position.y > size.height - selFlipCornerPx -> 1
                                        else -> 0
                                    }
                                    if (dir != 0) selCtrl.flipHoldStart(dir) else selCtrl.flipHoldStop()
                                }
                            } else {
                                selCtrl.up()
                                selCtrl.autoScrollStop()
                                selCtrl.flipHoldStop()
                                selDrag = false
                            }
                            change.consume()
                            continue
                        }

                        // 选区激活（外部清除等）：放弃本次手势（不翻页/不切 HUD/不下拉书签）
                        if (selectionActiveState.value) {
                            cancelled = true
                            isPulling = false; pullDistance = 0f; hapticTriggered = false
                            break
                        }
                        dx = change.position.x - downX
                        dy = change.position.y - down.position.y

                        if (isEpubNone && abs(dx) > 0f && abs(dx) > abs(dy)) {
                            change.consume()
                        }

                        if (!isDrag && max(abs(dx), abs(dy)) > clickSlop) {
                            isDrag = true
                            isHorizontal = abs(dx) > abs(dy)
                        }

                        // TXT 长按检测：按住 ≥480ms 且位移 ≤ slop → 字符级选区
                        if (isTxtBook && selCtrl != null && !selCtrl.active &&
                            !longPressTriggered && !isDrag && change.pressed
                        ) {
                            val held = System.currentTimeMillis() - downTime
                            if ((change.position - down.position).getDistance() <= clickSlop && held >= longPressMs) {
                                longPressTriggered = true
                                val hit = selCtrl.hitTest(down.position)
                                if (hit != null) {
                                    selCtrl.begin(hit)
                                    selDrag = true
                                    cancelled = true // 放弃根裁判后续手势判定
                                    change.consume()
                                    continue
                                }
                            }
                        }

                        if (isDrag && isHorizontal && !isUpDown && !isTxtPaged && !isEpubSlide) {
                            val now = System.currentTimeMillis()
                            val dt = (now - lastVelocityTime).toFloat()
                            if (dt > 0) {
                                val instantVx = abs((change.position.x - lastVelocityX) / dt * 1000f)
                                if (instantVx > peakVelocityX) peakVelocityX = instantVx
                            }
                            lastVelocityX = change.position.x
                            lastVelocityTime = now
                        }

                        if (isDrag) {
                            when {
                                isHorizontal && isUpDown && isEpubUpDown -> {
                                    // 上下滚动模式下锁定 x 轴：消费水平拖动但不翻页
                                    change.consume()
                                }
                                isHorizontal && !isUpDown && !isTxtPaged && !isEpubSlide -> {
                                    change.consume()
                                }
                                !isHorizontal -> {
                                    if (isUpDown) { /* 上下模式：垂直穿透滚动，不消费 */ }
                                    else {
                                        change.consume()
                                        val raw = dy.coerceAtLeast(0f)
                                        pullDistance = raw / (1f + raw / maxPullPx)
                                        if (!hapticTriggered && pullDistance >= bookmarkThreshold) {
                                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                            hapticTriggered = true
                                        }
                                        isPulling = true
                                    }
                                }
                            }
                        }
                    } while (event.changes.any { it.pressed })

                    if (swallowGesture) return@awaitEachGesture
                    if (cancelled) return@awaitEachGesture
                    val duration = System.currentTimeMillis() - downTime
                    val width = size.width

                    if (isDrag) {
                        if (isHorizontal && !isUpDown && !isTxtPaged && !isEpubSlide) {
                            if (abs(dx) > width * pageSwipeRatio || peakVelocityX > flickThreshold) {
                                if (System.currentTimeMillis() - lastPageTurnTime > 120) {
                                    lastPageTurnTime = System.currentTimeMillis()
                                    if (dx > 0) viewModel.previousPage() else viewModel.nextPage()
                                }
                            }
                        } else if (isHorizontal && isTxtPaged && txtPagedNoneState.value) {
                            // TXT 分页 none 模式：横滑经桥转发翻页
                            if (abs(dx) > width * pageSwipeRatio) {
                                if (System.currentTimeMillis() - lastPageTurnTime > 120) {
                                    lastPageTurnTime = System.currentTimeMillis()
                                    txtPagedFlickBridge?.invoke(if (dx > 0) -1 else 1)
                                }
                            }
                        } else if (!isHorizontal && !isUpDown) {
                            if (pullDistance >= bookmarkThreshold) {
                                if (System.currentTimeMillis() - lastBookmarkTime > 300) {
                                    lastBookmarkTime = System.currentTimeMillis()
                                    viewModel.toggleBookmark()
                                }
                            }
                            isPulling = false; pullDistance = 0f; hapticTriggered = false
                        }
                    } else {
                        if (duration < tapTimeoutMs && !isEpubBookState.value) {
                            if (isUpDown) {
                                // 上下模式：优先交 TXT 点击桥（翻译/高亮命中），未命中则切 HUD
                                val handled = txtTapBridgeState.value?.invoke(downX, down.position.y) == true
                                if (!handled) viewModel.toggleHud()
                            } else if (isTxtPaged) {
                                // TXT 分页模式：点击经桥转发给 TxtPagedViewer（边缘翻页/HUD/高亮/翻译）
                                txtPagedTapBridge?.invoke(downX, down.position.y)
                            } else {
                                when {
                                    downX < width * edgeRatio -> {
                                        if (System.currentTimeMillis() - lastPageTurnTime > 120) {
                                            lastPageTurnTime = System.currentTimeMillis()
                                            viewModel.previousPage()
                                        }
                                    }
                                    downX > width * (1 - edgeRatio) -> {
                                        if (System.currentTimeMillis() - lastPageTurnTime > 120) {
                                            lastPageTurnTime = System.currentTimeMillis()
                                            viewModel.nextPage()
                                        }
                                    }
                                    else -> {
                                        viewModel.toggleHud()
                                    }
                                }
                            }
                        }
                    }
                }
            }
            .focusRequester(focusRequester)
            .focusable()

            .onKeyEvent { event ->
                when (event.key) {
                    Key.VolumeDown, Key.VolumeUp -> {
                        if (uiState.isHudVisible || uiState.isAutoFlipActive || uiState.selectionActive ||
                            !uiState.readSettings.volumeKeyPageTurn
                        ) {
                            return@onKeyEvent false
                        }
                        val now = System.currentTimeMillis()
                        if (now - lastVolumeTime < 200L) return@onKeyEvent true
                        lastVolumeTime = now
                        val dir = if (event.key == Key.VolumeDown) 1 else -1
                        viewModel.volumeScroll(dir)
                        true
                    }
                    else -> false
                }
            },
    ) {
        // ── 黑色遮罩（反偏移抵消外层下移，始终在顶部）──
        if (isPulling && pullDistance > 0f) {
            val thresholdReached = pullDistance >= thresholdPx
            val maskH = with(density) { pullDistance.toDp() }
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(maskH)
                    .graphicsLayer { translationY = -pullDistance }
                    .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.8f)),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    stringResource(if (uiState.isBookmarked) R.string.reader_release_to_remove_bookmark else R.string.reader_release_to_add_bookmark),
                    color = if (thresholdReached) Color.White else Color.White.copy(alpha = 0.4f),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(start = with(density) { (rootWidthPx * 0.4f).coerceAtLeast(0f).toDp() }),
                )
            }
        }

        // ═══════════════════════════════════════
        //  阅读区域（按格式分发）
        // ═══════════════════════════════════════
        val controller = viewModel.getController()
        val txtFontFamily = remember(uiState.fontConfig) { resolveTxtFontFamily(uiState.fontConfig.cjk, uiState.fontConfig.latin) }
        when (book.format) {
            "TXT" -> {
                if (controller is TxtReaderController) {
                    if (effectivePageFlip == "updown") {
                        // 上下滚动模式：沿用 LazyColumn 渲染
                        TxtViewer(controller = controller, settings = uiState.readSettings,
                            overrideFontFamily = txtFontFamily,
                            annotations = uiState.annotations,
                            onCenterTap = { viewModel.clearSelection() }, // 仅清除选区，HUD 由根裁判统一处理
                            onHighlightTap = { a, anchor -> viewModel.onHighlightTapped(a, anchor) },
                            onTranslationTap = viewModel::onTranslationTapped,
                            registerScrollBridge = { viewModel.txtScrollBridge = it },
                            rootCoords = rootCoords,
                            selectionActive = uiState.selectionActive,
                            onTargetReady = { selectionTarget = it },
                            registerTapBridge = { txtTapBridge = it },
                            searchFlash = uiState.searchFlash,
                            modifier = Modifier.fillMaxSize().padding(top = safeTop, bottom = safeBottom))
                    } else {
                        // 平移(slide) / 无(none) 分页模式：真排版分页 + HorizontalPager
                        TxtPagedViewer(controller = controller, settings = effectiveSettings,
                            annotations = uiState.annotations,
                            fontFamily = txtFontFamily,
                            onToggleHud = viewModel::toggleHud,
                            onHighlightTap = { a, anchor -> viewModel.onHighlightTapped(a, anchor) },
                            onTranslationTap = viewModel::onTranslationTapped,
                            registerScrollBridge = { viewModel.txtScrollBridge = it },
                            registerAutoFlipTurn = { viewModel.autoFlipTurnBridge = it },
                            isAutoFlipActive = uiState.isAutoFlipActive,
                            rootCoords = rootCoords,
                            selectionActive = uiState.selectionActive,
                            onTargetReady = { selectionTarget = it },
                            registerTapBridge = { txtPagedTapBridge = it },
                            registerFlickBridge = { txtPagedFlickBridge = it },
                            searchFlash = uiState.searchFlash,
                            modifier = Modifier.fillMaxSize().padding(top = safeTop, bottom = safeBottom))
                    }
                }
            }
            "EPUB" -> {
                if (controller is EpubReaderController) {
                    EpubViewer(filePath = book.filePath, initialLocator = book.currentLocator,
                        settings = effectiveSettings, controller = controller,
                        fontCss = uiState.fontCss,
                        fontFamilyName = uiState.fontConfig.cjk?.familyName ?: uiState.fontConfig.latin?.familyName,
                        fontFaces = uiState.fontFaces,
                        fontResponse = viewModel::epubFontResponse,
                        registerScrollBridge = { viewModel.txtScrollBridge = it },
                        registerAutoFlipTurn = { viewModel.autoFlipTurnBridge = it },
                        onCenterTap = { viewModel.toggleHud() }, // EPUB 中心点按由 Readium 转发切 HUD
                        onEpubSelection = { text, locatorJson, rect -> viewModel.onEpubSelection(text, locatorJson, rect) },
                        onSelectionCleared = viewModel::clearSelection,
                        onHighlightTapId = { id, rect, point ->
                            uiState.annotations.find { it.id == id }?.let { ann ->
                                val anchor = if (rect != null) {
                                    HighlightMenuAnchor(
                                        lineRect = rect,
                                        lineEnd = point ?: Offset(rect.right, rect.center.y),
                                    )
                                } else HighlightMenuAnchor()
                                viewModel.onHighlightTapped(ann, anchor)
                            }
                        },
                        onTranslationTapId = { id -> viewModel.onTranslationTapId(id) },
                        annotations = uiState.annotations,
                        searchFlash = uiState.searchFlash,
                        modifier = Modifier.fillMaxSize().padding(top = safeTop, bottom = safeBottom))
                } else {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(stringResource(R.string.reader_loading), color = ink.copy(alpha = 0.5f))
                    }
                }
            }
            else -> {
                Box(Modifier.fillMaxSize().padding(horizontal = (24 * uiState.readSettings.horizontalMargin).dp), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(book.title, style = MaterialTheme.typography.headlineMedium, color = ink, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(8.dp))
                        Text(book.author ?: "", style = MaterialTheme.typography.bodyLarge, color = ink.copy(alpha = 0.6f))
                        Spacer(Modifier.height(32.dp))
                        Text(stringResource(R.string.reader_pdf_not_available), style = MaterialTheme.typography.bodyMedium, color = ink.copy(alpha = 0.4f), textAlign = TextAlign.Center)
                    }
                }
            }
        }

        // ── 选区渲染层（TXT：updown/slide/none 三模式共用；自动翻页模式不启用）──
        if (book.format == "TXT" && !uiState.isAutoFlipActive) {
            selectionController?.let { controller ->
                SelectionOverlay(
                    controller = controller,
                    selectionColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.30f),
                    modifier = Modifier.fillMaxSize(),
                    showFlipCorners = effectivePageFlip != "updown",
                )
            }
        }

        // 上下滚动模式：在正文上下边界增加背景羽化，避免文字生硬地出现/消失
        if (effectivePageFlip == "updown") {
            val safeVerticalMargin = uiState.readSettings.verticalMargin.coerceIn(0f, 2f)
            val topTextBoundary = safeTop + (24 * safeVerticalMargin).dp
            val bottomTextBoundary = safeBottom + (24 * safeVerticalMargin).dp
            val edgeFadeHeight = 20.dp
            Box(
                Modifier
                    .align(Alignment.TopCenter)
                    .offset(y = topTextBoundary)
                    .fillMaxWidth()
                    .height(edgeFadeHeight)
                    .blur(6.dp)
                    .background(Brush.verticalGradient(listOf(pageBg, Color.Transparent))),
            )
            Box(
                Modifier
                    .align(Alignment.BottomCenter)
                    .offset(y = -bottomTextBoundary)
                    .fillMaxWidth()
                    .height(edgeFadeHeight)
                    .blur(6.dp)
                    .background(Brush.verticalGradient(listOf(Color.Transparent, pageBg))),
            )
        }

        // ── 自动翻页扫描线：从文字区顶部线性刷到底 ──
        if (uiState.isAutoFlipActive) {
            val scanTopPx = with(density) { safeTop.toPx() }
            val scanBottomPx = (rootHeightPx - with(density) { safeBottom.toPx() }).coerceAtLeast(scanTopPx)
            val scanY = scanTopPx + scanProgress.value * (scanBottomPx - scanTopPx)
            Canvas(Modifier.fillMaxSize()) {
                drawLine(
                    color = ink.copy(alpha = 0.5f),
                    start = Offset(0f, scanY),
                    end = Offset(size.width, scanY),
                    strokeWidth = 2.dp.toPx(),
                )
            }
        }

        // ── 自动翻页手势层：吞掉所有触摸（禁手动翻页/选区/HUD），点击切换自动翻页底栏 ──
        if (uiState.isAutoFlipActive) {
            Box(
                Modifier
                    .fillMaxSize()
                    .pointerInput(Unit) {
                        awaitEachGesture {
                            val down = awaitFirstDown(pass = PointerEventPass.Main, requireUnconsumed = false)
                            down.consume()
                            val downTime = System.currentTimeMillis()
                            val slop = with(density) { 16.dp.toPx() }
                            var moved = false
                            do {
                                val event = awaitPointerEvent(pass = PointerEventPass.Main)
                                val change = event.changes.firstOrNull() ?: break
                                change.consume()
                                if ((change.position - down.position).getDistance() > slop) moved = true
                            } while (event.changes.any { it.pressed })
                            if (!moved && System.currentTimeMillis() - downTime < 450L) {
                                viewModel.toggleAutoFlipBar()
                            }
                        }
                    }
            )
        }

        // ── 书签丝带（反偏移，始终在右 1/8 处，渲染在阅读内容之上）──
        val ribbonVisible = uiState.isBookmarked || isPulling
        if (!uiState.isHudVisible && ribbonVisible) {
            val ribbonAlpha = if (isPulling) (pullDistance / thresholdPx).coerceIn(0f, 1f)
                .let { it * 0.7f + 0.3f } else 1f
            val ribbonBase = MaterialTheme.colorScheme.error
            val ribbonTop = SemanticColors.Danger
            Box(
                Modifier
                    .fillMaxWidth()
                    .graphicsLayer { translationY = -pullDistance }
                    .padding(end = with(density) { (rootWidthPx / (8.dp.toPx())).coerceAtLeast(0f).toDp() }),
                contentAlignment = Alignment.TopEnd,
            ) {
                Canvas(
                    Modifier.size(18.dp, 48.dp)
                        .graphicsLayer { alpha = ribbonAlpha },
                ) {
                    val w = size.width; val h = size.height; val ny = h * 0.72f
                    val path = Path().apply {
                        moveTo(0f, 0f); lineTo(w, 0f); lineTo(w, ny)
                        lineTo(w / 2f, h); lineTo(0f, ny); close()
                    }
                    drawPath(path, ribbonBase)
                    drawPath(path, Brush.verticalGradient(listOf(ribbonTop, ribbonBase), 0f, h))
                }
            }
        }

        // 沉浸信息覆盖层（在 reader 之上渲染，HUD 下隐藏）
        if (!uiState.isHudVisible) {
            val overlayDim = ink.copy(alpha = 0.45f)
            if (uiState.readSettings.showTopInfo) {
                Text(
                    uiState.currentChapter.ifBlank { book.currentChapter ?: book.title },
                    color = overlayDim, style = MaterialTheme.typography.labelMedium, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.align(Alignment.TopStart).padding(start = 20.dp, top = 30.dp),
                )
            }
            if (uiState.readSettings.showBottomInfo) {
                val progressLabel = if (uiState.readSettings.progressDisplayMode == "page" && uiState.pageTotal > 0) {
                    "${uiState.currentPage}/${uiState.pageTotal}"
                } else {
                    "%.2f%%".format(uiState.progress * 100)
                }
                Text(
                    progressLabel,
                    color = overlayDim, style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier.align(Alignment.BottomStart).navigationBarsPadding().padding(start = 20.dp, bottom = 8.dp)
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.align(Alignment.BottomEnd).navigationBarsPadding().padding(end = 20.dp, bottom = 8.dp),
                ) {
                    Text(currentTimeString, color = overlayDim, style = MaterialTheme.typography.labelMedium)
                    Spacer(Modifier.width(6.dp))
                    if (uiState.readSettings.batteryPercent) {
                        Text("${batteryLevel.coerceIn(0, 100)}%", color = overlayDim, style = MaterialTheme.typography.labelMedium)
                    } else {
                        Canvas(Modifier.size(22.dp, 11.dp)) {
                            val bw = size.width; val bh = size.height; val capW = 1.5.dp.toPx()
                            drawRoundRect(overlayDim, Offset.Zero, Size(bw - capW, bh), CornerRadius(2.dp.toPx()), style = Stroke(1.5.dp.toPx()))
                            drawRoundRect(overlayDim, Offset(bw - capW, bh * 0.25f), Size(capW, bh * 0.5f), CornerRadius(1.dp.toPx()))
                            if (batteryLevel > 0) {
                                val fillW = (bw - capW - 3.dp.toPx()) * batteryLevel / 100f
                                drawRoundRect(overlayDim, Offset(1.5.dp.toPx(), 1.5.dp.toPx()), Size(fillW.coerceAtLeast(0f), bh - 3.dp.toPx()), CornerRadius(1.dp.toPx()))
                            }
                        }
                    }
                }
            }
        }

        // HUD 可见时阻止触摸事件穿透到阅读内容 ──
        if (uiState.isHudVisible) {
            Box(
                Modifier
                    .fillMaxSize()
                    .pointerInput(Unit) {
                        awaitPointerEventScope {
                            while (true) {
                                val event = awaitPointerEvent(PointerEventPass.Main)
                                event.changes.forEach { it.consume() }
                            }
                        }
                    }
            )
        }

        // ── 大跨度跳转遮罩：跳转期间盖住阅读区（目标页字体/间距异步就位），定位完成后淡出 ──
        AnimatedVisibility(
            visible = uiState.isJumpMaskVisible,
            enter = fadeIn(tween(0)),
            exit = fadeOut(tween(NarviveMotion.Medium)),
        ) {
            Box(Modifier.fillMaxSize().background(pageBg))
        }

        val isDarkNow = uiState.readSettings.nightMode
        Column(Modifier.fillMaxSize()) {
            // ── HUD 顶栏：从上方向下弹出 ──
            AnimatedVisibility(
                visible = uiState.isHudVisible,
                enter = slideInVertically(animationSpec = tween(NarviveMotion.Medium, easing = NarviveMotion.EasingStandard), initialOffsetY = { -it }) + fadeIn(tween(NarviveMotion.Medium, easing = NarviveMotion.EasingStandard)),
                exit = slideOutVertically(animationSpec = tween(NarviveMotion.Fast, easing = NarviveMotion.EasingStandard), targetOffsetY = { -it }) + fadeOut(tween(NarviveMotion.Fast, easing = NarviveMotion.EasingStandard)),
            ) {
                ReaderTopBar(isBookmarked = uiState.isBookmarked, pageBg = barBg, ink = ink,
                    onSearchClick = viewModel::openSearch, onBookmarkClick = viewModel::toggleBookmark,
                    onBackClick = { requestExit() })
            }

            // ── 中间区域：点击空白关闭 HUD ──
            AnimatedVisibility(
                visible = uiState.isHudVisible,
                modifier = Modifier.weight(1f).fillMaxWidth(),
                enter = fadeIn(tween(NarviveMotion.Fast, easing = NarviveMotion.EasingStandard)),
                exit = fadeOut(tween(NarviveMotion.Fast, easing = NarviveMotion.EasingStandard)),
            ) {
                Box(Modifier.fillMaxSize()) {
                    Box(
                        Modifier.fillMaxSize()
                            .clickable(
                                interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                                indication = null,
                            ) { viewModel.hideHud() }
                    )
                    if (uiState.showPositionTip) {
                        PositionTipBar(
                            chapterTitle = uiState.tipChapterTitle,
                            progress = uiState.tipProgress,
                            displayMode = uiState.readSettings.progressDisplayMode,
                            pageTotal = uiState.pageTotal,
                            onBack = viewModel::goBackToPreviousPosition,
                            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 8.dp),
                        )
                    }
                }
            }

            // ── HUD 底栏：从下方向上弹出 ──
            AnimatedVisibility(
                visible = uiState.isHudVisible,
                enter = slideInVertically(animationSpec = tween(NarviveMotion.Medium, easing = NarviveMotion.EasingStandard), initialOffsetY = { it }) + fadeIn(tween(NarviveMotion.Medium, easing = NarviveMotion.EasingStandard)),
                exit = slideOutVertically(animationSpec = tween(NarviveMotion.Fast, easing = NarviveMotion.EasingStandard), targetOffsetY = { it }) + fadeOut(tween(NarviveMotion.Fast, easing = NarviveMotion.EasingStandard)),
            ) {
                ReaderBottomBar(progress = uiState.progress, pageBg = barBg, ink = ink, isDarkTheme = isDarkNow,
                    onSeek = viewModel::seekTo,
                    onSeekTip = { viewModel.showSeekTip(it) },
                    onPrevChapter = viewModel::previousChapter,
                    onNextChapter = viewModel::nextChapter,
                    onAaClick = { viewModel.hideHud(); viewModel.toggleAaPanel() },
                    onTocClick = {
                        viewModel.hideHud()
                        coroutineScope.launch {
                            delay(180)
                            viewModel.toggleToc()
                        }
                    },
                    onThemeToggle = {
                        val nextSettings = uiState.readSettings.copy(nightMode = !isDarkNow)
                        startCapturedThemeSwitch(nextSettings) {
                            viewModel.applySettings(nextSettings)
                        }
                    },
                    onAiClick = { viewModel.hideHud(); openAiSheet(null) },
                    onBrightnessClick = { viewModel.hideHud(); viewModel.toggleBrightnessPanel() })
            }
        }

        // ── 自动翻页底栏（呼出即暂停；退出按钮结束自动翻页）──
        AnimatedVisibility(
            visible = uiState.isAutoFlipActive && uiState.isAutoFlipPaused,
            modifier = Modifier.align(Alignment.BottomCenter),
            enter = slideInVertically(animationSpec = tween(NarviveMotion.Medium, easing = NarviveMotion.EasingStandard), initialOffsetY = { it }) + fadeIn(tween(NarviveMotion.Medium, easing = NarviveMotion.EasingStandard)),
            exit = slideOutVertically(animationSpec = tween(NarviveMotion.Fast, easing = NarviveMotion.EasingStandard), targetOffsetY = { it }) + fadeOut(tween(NarviveMotion.Fast, easing = NarviveMotion.EasingStandard)),
        ) {
            AutoFlipBar(
                speedSeconds = autoFlipSpeed,
                barBg = barBg,
                ink = ink,
                onSpeedDown = { viewModel.adjustAutoFlipSpeed(-5) },
                onSpeedUp = { viewModel.adjustAutoFlipSpeed(5) },
                onExit = viewModel::stopAutoFlip,
            )
        }

        // 选区气泡（动态适配：基准行末端 + 箭头指向 + 自动翻转 + 水平防切边）
        uiState.selection?.let { sel ->
            AnchoredBubble(
                anchorRect = sel.anchorRect,
                anchorLineEnd = sel.anchorLineEnd,
                viewport = Size(rootWidthPx, rootHeightPx),
                onBoundsChanged = { selectionBubbleBounds = it },
                modifier = Modifier.fillMaxSize(),
            ) {
                SelectionBubble(selectedText = sel.text, onDismiss = viewModel::clearSelection,
                    onHighlight = { color -> viewModel.highlightSelection(color.toArgb().toLong() and 0xFFFFFFFFL) },
                    onNote = viewModel::openNoteInputForSelection,
                    onCopy = { (context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(ClipData.newPlainText("Narvive", sel.text)); viewModel.clearSelection() },
                    onTranslate = viewModel::translateSelection,
                    onAskAi = { openAiSheet(sel.text); viewModel.clearSelection() },
                    onRewrite = { val t = sel.text; viewModel.clearSelection(); onRewriteClick(t, "rewrite") },
                    onContinue = { val t = sel.text; viewModel.clearSelection(); onRewriteClick(t, "continue") })
            }
        }

        // 高亮点按菜单（动态适配定位：点按处所在行末端为锚点）
        uiState.tappedHighlight?.let { annotation ->
            Box(Modifier.fillMaxSize().clickable(interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }, indication = null) { viewModel.dismissHighlightMenu() })
            AnchoredBubble(
                anchorRect = uiState.tappedHighlightAnchor.lineRect,
                anchorLineEnd = uiState.tappedHighlightAnchor.lineEnd,
                viewport = Size(rootWidthPx, rootHeightPx),
                modifier = Modifier.fillMaxSize(),
            ) {
                HighlightMenuBubble(annotation = annotation,
                    onChangeColor = { color -> viewModel.changeHighlightColor(annotation, color.toArgb().toLong() and 0xFFFFFFFFL) },
                    onAddNote = { viewModel.openNoteInputForAnnotation(annotation) },
                    onDelete = { viewModel.deleteAnnotation(annotation) },
                    onCopy = {
                        (context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager)
                            .setPrimaryClip(ClipData.newPlainText("Narvive", annotation.selectedText))
                        viewModel.dismissHighlightMenu()
                    },
                    onTranslate = { viewModel.translateAnnotationText(annotation) },
                    onAskAi = { openAiSheet(annotation.selectedText); viewModel.dismissHighlightMenu() },
                    onRewrite = { val t = annotation.selectedText; viewModel.dismissHighlightMenu(); onRewriteClick(t, "rewrite") },
                    onContinue = { val t = annotation.selectedText; viewModel.dismissHighlightMenu(); onRewriteClick(t, "continue") })
            }
        }

        // 翻译管理卡
        uiState.tappedTranslation?.let { annotation ->
            Box(Modifier.fillMaxSize().clickable(interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }, indication = null) { viewModel.dismissTranslationCard() })
            Box(Modifier.fillMaxWidth().align(Alignment.BottomCenter).navigationBarsPadding().padding(16.dp)) {
                TranslationCard(annotation = annotation, fromCache = uiState.translationFromCache,
                    onRetranslate = { viewModel.retranslate(annotation) },
                    onAskAi = { openAiSheet(annotation.selectedText); viewModel.dismissTranslationCard() },
                    onDelete = { viewModel.deleteAnnotation(annotation) })
            }
        }

        // ── 护眼模式：全屏暖色遮罩（透传触摸，不影响交互）──
        if (uiState.readSettings.eyeProtection) {
            Box(Modifier.fillMaxSize().background(Color(0x22FFB74D)))
        }

        // ── 主题切换过渡遮罩：优先用旧画面截图整屏淡出，文字/图标不会重新浮现 ──
        val transitionBitmap = themeTransitionBitmap
        if (transitionBitmap != null) {
            val imageBitmap = remember(transitionBitmap) { transitionBitmap.asImageBitmap() }
            Canvas(
                Modifier
                    .fillMaxSize()
                    .graphicsLayer { alpha = themeBitmapAlpha.value },
            ) {
                drawImage(
                    image = imageBitmap,
                    dstSize = IntSize(size.width.toInt(), size.height.toInt()),
                )
            }
        } else {
            fallbackOverlayColor?.let { oldColor ->
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(oldColor.copy(alpha = fallbackOverlayAlpha.value)),
                )
            }
        }

        // HUD 收起后把焦点还给阅读根节点，确保音量键事件持续回到 Compose 根裁判
        LaunchedEffect(uiState.isHudVisible) {
            if (!uiState.isHudVisible) focusRequester.requestFocus()
        }
    }
    // 设置 面板
    val applyReaderSettings: (ReadSettings) -> Unit = { newSettings ->
        if (hasThemeColorChange(uiState.readSettings, newSettings)) {
            startCapturedThemeSwitch(newSettings) { viewModel.applySettings(newSettings) }
        } else {
            viewModel.applySettings(newSettings)
        }
    }
    if (uiState.isAaPanelOpen) {
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(onDismissRequest = viewModel::toggleAaPanel, sheetState = sheetState) {
            AaPanel(
                settings = uiState.readSettings,
                onApply = applyReaderSettings,
                onFontClick = {
                    coroutineScope.launch {
                        sheetState.hide()
                        onOpenFontSettings()
                    }
                },
                onCustomSpacingClick = { isSpacingSheetOpen = true },
                onCustomThemeClick = { isThemeSheetOpen = true },
                onMoreSettingsClick = {
                    coroutineScope.launch {
                        sheetState.hide()
                        onOpenMoreSettings()
                    }
                },
                isAutoFlipActive = uiState.isAutoFlipActive,
                onAutoFlipToggle = { enabled ->
                    if (enabled) {
                        coroutineScope.launch {
                            sheetState.hide()
                            viewModel.startAutoFlip()
                        }
                    } else {
                        viewModel.stopAutoFlip()
                    }
                },
                isEpub = uiState.book?.format == "EPUB",
            )
        }
        // 自定义间距窗口：置于 Aa 面板之上，Aa 面板关闭时一并消失
        if (isSpacingSheetOpen) {
            CustomSpacingBottomSheet(
                settings = uiState.readSettings,
                onApply = viewModel::applySettings,
                onDismiss = { isSpacingSheetOpen = false },
                publisherStylesActive = uiState.book?.format == "EPUB" && uiState.readSettings.publisherStyles,
            )
        }
        // 自定义主题窗口：置于 Aa 面板之上，Aa 面板关闭时一并消失
        if (isThemeSheetOpen) {
            CustomThemeBottomSheet(
                settings = uiState.readSettings,
                onApply = applyReaderSettings,
                onDismiss = { isThemeSheetOpen = false },
            )
        }
    }
    // 亮度面板
    if (uiState.isBrightnessPanelOpen) {
        ModalBottomSheet(onDismissRequest = viewModel::toggleBrightnessPanel, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
            BrightnessPanel(settings = uiState.readSettings, onApply = viewModel::applySettings)
        }
    }

    // 目录 —— 左侧面板（固定 85% 屏幕宽度）
    BackHandler(enabled = uiState.isTocOpen) { viewModel.toggleToc() }
    val tocOnClose: () -> Unit = { viewModel.toggleToc() }
    Box(Modifier.fillMaxSize()) {
        // 半透明遮罩：淡入淡出
        AnimatedVisibility(
            visible = uiState.isTocOpen,
            modifier = Modifier.fillMaxSize(),
            enter = fadeIn(tween(NarviveMotion.Fast, easing = NarviveMotion.EasingStandard)),
            exit = fadeOut(tween(NarviveMotion.Fast, easing = NarviveMotion.EasingStandard)),
        ) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.4f))
                    .clickable(
                        interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                        indication = null,
                    ) { tocOnClose() },
            )
        }

        // 侧边面板：线性滑入滑出
        AnimatedVisibility(
            visible = uiState.isTocOpen,
            modifier = Modifier
                .align(Alignment.CenterStart)
                .fillMaxHeight()
                .fillMaxWidth(0.85f),
            enter = slideInHorizontally(
                animationSpec = tween(NarviveMotion.Medium, easing = NarviveMotion.EasingStandard),
                initialOffsetX = { -it },
            ) + fadeIn(tween(NarviveMotion.Medium, easing = NarviveMotion.EasingStandard)),
            exit = slideOutHorizontally(
                animationSpec = tween(NarviveMotion.Fast, easing = NarviveMotion.EasingStandard),
                targetOffsetX = { -it },
            ) + fadeOut(tween(NarviveMotion.Fast, easing = NarviveMotion.EasingStandard)),
        ) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(pageBg)
                    .clickable(
                        interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                        indication = null,
                    ) { /* 消费点击，不做任何事 */ },
            ) {
                Column(Modifier.fillMaxSize()) {
                    // 书籍信息头部：封面 + 书名 + 作者 + 关闭按钮
                    Row(
                        Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 40.dp, bottom = 20.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        // 封面
                        val coverPath = book.coverPath
                        if (coverPath != null) {
                            coil.compose.AsyncImage(
                                model = coverPath,
                                contentDescription = book.title,
                                modifier = Modifier.size(48.dp).clip(NarviveShape.BookCover),
                            )
                        } else {
                            Box(
                                Modifier.size(48.dp).background(ink.copy(alpha = 0.1f), NarviveShape.BookCover),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    book.title.take(1),
                                    color = ink.copy(alpha = 0.35f),
                                    style = MaterialTheme.typography.headlineSmall,
                                )
                            }
                        }
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(book.title, color = ink, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            book.author?.let { author ->
                                Text(author, color = ink.copy(alpha = 0.55f), style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                        }
                    }
                    TocBottomSheet(
                        chapters = uiState.tocItems,
                        bookmarks = uiState.bookmarks,
                        onChapterClick = { locator -> viewModel.jumpToLocator(locator); viewModel.hideHud(); tocOnClose() },
                        onBookmarkClick = { locator -> viewModel.jumpToLocator(locator); viewModel.hideHud(); tocOnClose() },
                        annotations = uiState.annotations,
                        onAnnotationClick = { locator -> viewModel.jumpToLocator(locator); viewModel.hideHud(); tocOnClose() },
                        currentChapterIndex = viewModel.currentTocIndex(),
                        pageTotal = uiState.pageTotal,
                        progressDisplayMode = uiState.readSettings.progressDisplayMode,
                        ink = ink,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }

    // AI 面板
    if (aiSheetOpen) {
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = false)
        val closeSheet: () -> Unit = { coroutineScope.launch { sheetState.hide() }.invokeOnCompletion { aiSheetOpen = false } }
        ModalBottomSheet(onDismissRequest = { aiSheetOpen = false }, sheetState = sheetState) {
            Column(Modifier.fillMaxHeight()) {
                Row(Modifier.fillMaxWidth().clickable { coroutineScope.launch { sheetState.expand() } }.padding(horizontal = 16.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.AutoAwesome, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(R.string.reader_ai_assistant), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    if (chatUiState.providerName.isNotBlank()) {
                        Spacer(Modifier.width(8.dp))
                        Surface(shape = NarviveShape.Md, color = MaterialTheme.colorScheme.primaryContainer) {
                            Text(chatUiState.providerName, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp))
                        }
                    }
                    Spacer(Modifier.weight(1f))
                    IconButton(onClick = { showChatHistory = true }) { Icon(Icons.Rounded.History, stringResource(R.string.reader_chat_history)) }
                    IconButton(onClick = { coroutineScope.launch { sheetState.expand() } }) { Icon(Icons.Rounded.UnfoldMore, stringResource(R.string.reader_expand)) }
                    IconButton(onClick = closeSheet) { Icon(Icons.Rounded.Close, stringResource(R.string.reader_close)) }
                }
                ChatContent(uiState = chatUiState, onScopeChange = chatViewModel::setScope, onSend = chatViewModel::sendMessage,
                    onQuickCommand = { cmd ->
                        when (cmd) {
                            QuickCommand.REWRITE -> chatUiState.selectionText?.let { text -> coroutineScope.launch { sheetState.hide() }.invokeOnCompletion { aiSheetOpen = false; onRewriteClick(text, "rewrite") } }
                            QuickCommand.CONTINUE -> chatUiState.selectionText?.let { text -> coroutineScope.launch { sheetState.hide() }.invokeOnCompletion { aiSheetOpen = false; onRewriteClick(text, "continue") } }
                            QuickCommand.ROLEPLAY -> coroutineScope.launch {
                                sheetState.hide()
                                aiSheetOpen = false
                                viewModel.saveProgressNow() // 先落库再跳转，角色卡才能拿到最新进度
                                onRoleplayClick()
                            }
                            QuickCommand.RELATIONSHIP_GRAPH -> chatViewModel.generateRelationshipGraph()
                            QuickCommand.TIMELINE -> chatViewModel.generateTimeline()
                            else -> chatViewModel.sendQuickCommand(cmd)
                        }
                    },
                    onSaveAsNote = chatViewModel::saveAsNote, onRegenerate = chatViewModel::regenerate, onDismissError = chatViewModel::clearError,
                    onOpenAiSettings = { coroutineScope.launch { sheetState.hide() }.invokeOnCompletion { aiSheetOpen = false; onOpenAiSettings() } },
                    modifier = Modifier.fillMaxWidth().weight(1f))
            }
        }
    }

    // 人物关系图 / 时间轴演进图 弹窗
    GraphSheetDialog(
        state = chatUiState.graphSheet,
        loading = chatUiState.graphLoading,
        onDismiss = chatViewModel::dismissGraphSheet,
    )

    // AI 助手历史会话（新建 / 删除）
    if (showChatHistory) {
        ModalBottomSheet(onDismissRequest = { showChatHistory = false }, sheetState = rememberModalBottomSheetState()) {
            Column(Modifier.padding(bottom = 24.dp)) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.reader_history_sessions), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                    TextButton(onClick = { chatViewModel.startNewConversation(); showChatHistory = false }) {
                        Icon(Icons.Rounded.Add, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(stringResource(R.string.reader_new_conversation))
                    }
                }
                Spacer(Modifier.height(8.dp))
                if (chatUiState.conversations.isEmpty()) {
                    Text(stringResource(R.string.reader_no_history), Modifier.padding(horizontal = 20.dp, vertical = 16.dp), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    LazyColumn(Modifier.heightIn(max = 400.dp)) {
                        items(chatUiState.conversations, key = { it.id }) { conv ->
                            Row(
                                Modifier.fillMaxWidth().clickable { chatViewModel.loadConversation(conv.id); showChatHistory = false }.padding(horizontal = 20.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Column(Modifier.weight(1f)) {
                                    Text(
                                        conv.title.ifBlank { stringResource(R.string.reader_untitled_conversation) },
                                        style = MaterialTheme.typography.bodyLarge,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        color = if (conv.id == chatUiState.activeConversationId) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                                    )
                                    if (conv.id == chatUiState.activeConversationId) {
                                        Text(stringResource(R.string.reader_current), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                                    }
                                }
                                Box(
                                    Modifier.size(32.dp).clip(CircleShape).background(SemanticColors.Danger).clickable { chatDeleteTarget = conv },
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Icon(Icons.Rounded.Delete, stringResource(R.string.reader_delete), tint = Color.White, modifier = Modifier.size(18.dp))
                                }
                            }
                        }
                    }
                }
            }
        }
    }
    chatDeleteTarget?.let { conv ->
        AlertDialog(
            onDismissRequest = { chatDeleteTarget = null },
            title = { Text(stringResource(R.string.reader_delete_conversation_title)) },
            text = { Text(stringResource(R.string.reader_delete_conversation_body, conv.title.ifBlank { stringResource(R.string.reader_untitled_conversation) })) },
            confirmButton = {
                TextButton(onClick = { chatViewModel.deleteConversation(conv.id); chatDeleteTarget = null }) {
                    Text(stringResource(R.string.reader_delete), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { chatDeleteTarget = null }) { Text(stringResource(R.string.reader_cancel)) } },
        )
    }

    // 笔记
    if (uiState.noteInputForSelection || uiState.noteInputForAnnotation != null) {
        var noteText by remember(uiState.noteInputForAnnotation) { mutableStateOf(uiState.noteInputForAnnotation?.note ?: "") }
        AlertDialog(onDismissRequest = viewModel::dismissNoteInput,
            title = { Text(stringResource(if (uiState.noteInputForAnnotation != null) R.string.reader_edit_note else R.string.reader_add_note)) },
            text = { OutlinedTextField(value = noteText, onValueChange = { noteText = it }, placeholder = { Text(stringResource(R.string.reader_note_placeholder)) }, minLines = 3, modifier = Modifier.fillMaxWidth()) },
            confirmButton = { TextButton(onClick = { viewModel.saveNote(noteText) }) { Text(stringResource(R.string.reader_save)) } },
            dismissButton = { TextButton(onClick = viewModel::dismissNoteInput) { Text(stringResource(R.string.reader_cancel)) } })
    }

    // 翻译
    uiState.translation?.let { result ->
        AlertDialog(onDismissRequest = viewModel::dismissTranslation,
            title = { Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.reader_translate), style = MaterialTheme.typography.titleMedium)
                if (result.fromCache) { Spacer(Modifier.size(8.dp)); Text(stringResource(R.string.reader_cache_hit), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary) }
            }},
            text = { Column {
                Text(result.source, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 4, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.height(8.dp)); Text(result.translation, style = MaterialTheme.typography.bodyLarge)
                Spacer(Modifier.height(8.dp)); Text(stringResource(R.string.reader_translation_hint), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }},
            confirmButton = { TextButton(onClick = viewModel::dismissTranslation) { Text(stringResource(R.string.reader_close)) } })
    }

    // 搜索
    if (uiState.isSearchOpen) {
        InBookSearchOverlay(isSearching = uiState.isSearching, results = uiState.searchResults,
            onSearch = viewModel::performSearch,
            onResultClick = { result -> viewModel.jumpToSearchResult(result) },
            onClose = viewModel::closeSearch)
    }

    // 错误
    uiState.error?.let { error ->
        AlertDialog(onDismissRequest = viewModel::clearError,
            title = { Text(stringResource(R.string.reader_tip)) }, text = { Text(error) },
            confirmButton = { TextButton(onClick = viewModel::clearError) { Text(stringResource(R.string.reader_got_it)) } })
    }

    // 休息提醒
    if (showRestReminder) {
        AlertDialog(
            onDismissRequest = {
                showRestReminder = false
                restSeconds = 0
            },
            title = { Text(stringResource(R.string.reader_rest_reminder_title)) },
            text = { Text(stringResource(R.string.reader_rest_reminder_body, uiState.readSettings.restReminderMinutes)) },
            confirmButton = {
                TextButton(onClick = {
                    showRestReminder = false
                    restSeconds = 0
                }) { Text(stringResource(R.string.reader_got_it)) }
            },
        )
    }

}

@Composable
private fun ReaderThemeToggleIcon(isDarkTheme: Boolean, tint: Color) {
    Box(Modifier.size(24.dp), contentAlignment = Alignment.Center) {
        Icon(
            Icons.Rounded.Brightness2,
            contentDescription = stringResource(if (isDarkTheme) R.string.reader_switch_to_day else R.string.reader_switch_to_night),
            tint = tint,
            modifier = Modifier.size(24.dp).rotate(45f),
        )
        Canvas(Modifier.size(24.dp)) {
            if (isDarkTheme) {
                drawLine(
                    color = tint,
                    start = Offset(size.width * 0.22f, size.height * 0.22f),
                    end = Offset(size.width * 0.78f, size.height * 0.78f),
                    strokeWidth = 2.dp.toPx(),
                    cap = StrokeCap.Round,
                )
            }
        }
    }
}

// ═══════════════════════════════════════
//  自动翻页底栏
// ═══════════════════════════════════════
@Composable
private fun AutoFlipBar(
    speedSeconds: Int,
    barBg: Color,
    ink: Color,
    onSpeedDown: () -> Unit,
    onSpeedUp: () -> Unit,
    onExit: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(barBg)
            .navigationBarsPadding()
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(36.dp)
                .clip(NarviveShape.Sm)
                .background(ink.copy(alpha = 0.12f))
                .clickable(onClick = onSpeedDown),
            contentAlignment = Alignment.Center,
        ) {
            Text("−", color = ink, style = MaterialTheme.typography.headlineSmall)
        }
        Spacer(Modifier.width(14.dp))
        Text(
            stringResource(R.string.reader_auto_flip_speed_format, speedSeconds),
            color = ink,
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.weight(1f),
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.width(14.dp))
        Box(
            Modifier
                .size(36.dp)
                .clip(NarviveShape.Sm)
                .background(ink.copy(alpha = 0.12f))
                .clickable(onClick = onSpeedUp),
            contentAlignment = Alignment.Center,
        ) {
            Text("+", color = ink, style = MaterialTheme.typography.headlineSmall)
        }
        Spacer(Modifier.width(14.dp))
        TextButton(onClick = onExit) {
            Text(stringResource(R.string.reader_exit_auto_flip), color = ink, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

// ═══════════════════════════════════════
//  HUD 顶栏
// ═══════════════════════════════════════
@Composable
private fun ReaderTopBar(
    isBookmarked: Boolean, pageBg: Color, ink: Color,
    onSearchClick: () -> Unit, onBookmarkClick: () -> Unit, onBackClick: () -> Unit,
) {
    var moreOpen by remember { mutableStateOf(false) }
    Row(Modifier.fillMaxWidth().background(pageBg).statusBarsPadding().padding(horizontal = 8.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onBackClick) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, stringResource(R.string.reader_back), tint = ink) }
        Spacer(Modifier.weight(1f))
        IconButton(onClick = onSearchClick) { Icon(Icons.Rounded.Search, stringResource(R.string.reader_search), tint = ink) }
        Box {
            IconButton(onClick = { moreOpen = true }) { Icon(Icons.Rounded.MoreVert, stringResource(R.string.reader_more), tint = ink) }
            DropdownMenu(expanded = moreOpen, onDismissRequest = { moreOpen = false }) {
                DropdownMenuItem(text = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.Bookmark, null, tint = if (isBookmarked) MaterialTheme.colorScheme.primary else ink, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(if (isBookmarked) R.string.reader_bookmarked else R.string.reader_add_bookmark))
                    }
                }, onClick = { moreOpen = false; onBookmarkClick() })
            }
        }
    }
}

// ═══════════════════════════════════════
//  HUD 底栏
// ═══════════════════════════════════════
@Composable
private fun ReaderBottomBar(
    progress: Float, pageBg: Color, ink: Color, isDarkTheme: Boolean,
    onSeek: (Float) -> Unit, onSeekTip: (Float) -> Unit = {},
    onPrevChapter: () -> Unit, onNextChapter: () -> Unit,
    onAaClick: () -> Unit, onTocClick: () -> Unit,
    onThemeToggle: () -> Unit, onAiClick: () -> Unit,
    onBrightnessClick: () -> Unit,
) {
    var dragProgress by remember { mutableStateOf<Float?>(null) }
    var isWaitingForSeek by remember { mutableStateOf(false) }
    var progressAtSeekStart by remember { mutableFloatStateOf(0f) }
    val shown = dragProgress ?: progress
    LaunchedEffect(progress) {
        if (isWaitingForSeek && dragProgress != null && progress != progressAtSeekStart) {
            dragProgress = null; isWaitingForSeek = false
        }
    }
    Column(Modifier.fillMaxWidth().background(pageBg).navigationBarsPadding().padding(horizontal = 16.dp, vertical = 8.dp)) {
        // 上一章 / 进度条 / 下一章
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onPrevChapter, modifier = Modifier.padding(0.dp)) {
                Text(stringResource(R.string.reader_prev_chapter), color = ink, style = MaterialTheme.typography.bodySmall)
            }
            Slider(
                value = shown.coerceIn(0f, 1f),
                onValueChange = { dragProgress = it; onSeekTip(it) },
                onValueChangeFinished = {
                    dragProgress?.let { progressAtSeekStart = progress; onSeek(it) }
                    isWaitingForSeek = true
                },
                modifier = Modifier.weight(1f).height(24.dp),
            )
            TextButton(onClick = onNextChapter, modifier = Modifier.padding(0.dp)) {
                Text(stringResource(R.string.reader_next_chapter), color = ink, style = MaterialTheme.typography.bodySmall)
            }
        }
        // 功能按钮行
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onTocClick) { Icon(Icons.AutoMirrored.Rounded.List, stringResource(R.string.reader_toc), tint = ink) }
            Spacer(Modifier.weight(1f))
            IconButton(onClick = onBrightnessClick) { Icon(Icons.Rounded.WbIncandescent, stringResource(R.string.reader_brightness), tint = ink) }
            Spacer(Modifier.weight(1f))
            IconButton(onClick = onThemeToggle) {
                ReaderThemeToggleIcon(isDarkTheme = isDarkTheme, tint = ink)
            }
            Spacer(Modifier.weight(1f))
            IconButton(onClick = onAiClick) { Icon(Icons.Rounded.AutoAwesome, "AI", tint = ink) }
            Spacer(Modifier.weight(1f))
            IconButton(onClick = onAaClick) { Icon(Icons.Rounded.Settings, stringResource(R.string.reader_settings), tint = ink) }
        }
    }
}

/** 夜间阅读模式固定色值：作为独立状态，不匹配任何自定义色标 */
const val NIGHT_READER_BG: Long = 0xFF0B1622L
const val NIGHT_READER_INK: Long = 0xFF56768AL

fun readerPageBackground(theme: String): Color = when (theme) {
    "paper" -> Color(0xFFF5F1E6)
    "green" -> Color(0xFFC7EDCC)
    "dark" -> Color(0xFFAEC6CF)
    "black" -> Color(0xFF2C2C2C)
    else -> Color(0xFFE6D2B5)
}

fun readerInkColor(theme: String): Color = when (theme) {
    "paper" -> Color(0xFF333333)
    "sepia" -> Color(0xFF4A3B2A)
    "green" -> Color(0xFF2F4F2F)
    "dark" -> Color(0xFF2C3E50)
    "black" -> Color(0xFFCCCCCC)
    else -> Color(0xFF333333)
}

fun readerPageBackground(settings: ReadSettings): Color {
    if (settings.nightMode) return Color(NIGHT_READER_BG)
    if (settings.theme == "custom") return Color(settings.customBgColor)
    return readerPageBackground(settings.theme)
}

fun readerInkColor(settings: ReadSettings): Color {
    if (settings.nightMode) return Color(NIGHT_READER_INK)
    if (settings.theme == "custom") return Color(settings.customInkColor)
    return readerInkColor(settings.theme)
}

fun readerBarBackground(settings: ReadSettings): Color {
    val base = readerPageBackground(settings)
    if (settings.nightMode) return Color(0xFF0D0D0D)
    return Color(
        red = base.red * 0.85f,
        green = base.green * 0.85f,
        blue = base.blue * 0.85f,
    )
}

/** 颜色相关状态签名：主题切换截图/兜底遮罩以此判断是否需要过渡 */
fun themeColorSignature(settings: ReadSettings): String =
    "${settings.theme}|${settings.nightMode}|${settings.customBgColor}|${settings.customInkColor}"

fun hasThemeColorChange(old: ReadSettings, new: ReadSettings): Boolean =
    old.theme != new.theme ||
        old.nightMode != new.nightMode ||
        old.customBgColor != new.customBgColor ||
        old.customInkColor != new.customInkColor

/** HUD 顶栏/底栏背景：在各主题页面背景基础上明显加深，确保与阅读区区分 */
fun readerBarBackground(theme: String): Color {
    val base = readerPageBackground(theme)
    return Color(
        red = base.red * 0.85f,
        green = base.green * 0.85f,
        blue = base.blue * 0.85f,
    )
}

@Composable
    private fun PositionTipBar(
        chapterTitle: String,
        progress: Float,
        displayMode: String = "percentage",
        pageTotal: Int = 0,
        onBack: () -> Unit,
        modifier: Modifier = Modifier,
    ) {
        Box(
            modifier = modifier.fillMaxWidth(),
            contentAlignment = Alignment.Center,
        ) {
            Surface(
                shape = NarviveShape.Lg,
                color = MaterialTheme.colorScheme.scrim.copy(alpha = 0.8f),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 14.dp),
                ) {
                    Icon(
                        Icons.AutoMirrored.Rounded.ArrowBack,
                        contentDescription = "return to previous position",
                        tint = Color.White.copy(alpha = 0.7f),
                        modifier = Modifier.size(22.dp).clickable { onBack() },
                    )
                    Spacer(Modifier.width(16.dp))
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.width(250.dp),
                    ) {
                        Text(
                            chapterTitle.ifBlank { "—" },
                            color = Color.White,
                            style = MaterialTheme.typography.bodyMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Spacer(Modifier.height(2.dp))
                        val progressText = if (displayMode == "page" && pageTotal > 0) {
                            val estPage = (progress * pageTotal).toInt().coerceIn(1, pageTotal)
                            "${estPage}/${pageTotal}"
                        } else {
                            "%.2f%%".format(progress * 100)
                        }
                        Text(
                            progressText,
                            color = Color.White,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }
            }
        }
    }

@Composable
private fun ErrorFallback(message: String, onBack: () -> Unit) {
    Box(Modifier.fillMaxSize().background(readerPageBackground("sepia")), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(32.dp)) {
            Text(stringResource(R.string.reader_cannot_open), style = MaterialTheme.typography.headlineSmall, color = readerInkColor("sepia"))
            Spacer(Modifier.height(12.dp))
            Text(message, style = MaterialTheme.typography.bodySmall, color = readerInkColor("sepia").copy(alpha = 0.7f), textAlign = TextAlign.Center)
            Spacer(Modifier.height(24.dp))
            androidx.compose.material3.Button(onClick = onBack) { Text(stringResource(R.string.reader_back_to_shelf)) }
        }
    }
}
