package com.narvive.app.ui.screen.stats

import android.content.Context
import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.narvive.app.R
import com.narvive.app.core.AppLang
import com.narvive.app.domain.model.Book
import com.narvive.app.domain.model.ReadingSession
import com.narvive.app.domain.repository.BookshelfRepository
import com.narvive.app.domain.repository.ReadingRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import javax.inject.Inject

data class ChartPoint(val label: String, val minutes: Float, val isCurrent: Boolean)

data class StatsUiState(
    val todayMinutes: Int = 0,
    val weekMinutes: Int = 0,
    val consecutiveDays: Int = 0,
    val booksFinished: Int = 0,
    val chartPoints: List<ChartPoint> = emptyList(),
    val chartRange: ChartRange = ChartRange.DAY,
    /** 图表标题。动态日期文案在 ViewModel 内按语言解析，故为 String 而非资源 id。 */
    val chartLabel: String = "",
    val canGoBack: Boolean = false,
    val canGoForward: Boolean = false,
    val finishedBooks: List<FinishedBook> = emptyList(),
    val showAllFinished: Boolean = false,
)

data class FinishedBook(val title: String, val author: String, val dateLabel: String, val coverPath: String?)

/** 范围标签用字符串资源 id（ViewModel 非 Composable，由界面侧 `stringResource(range.labelRes)` 解析）。 */
enum class ChartRange(@StringRes val labelRes: Int) {
    DAY(R.string.stats_range_day),
    WEEK(R.string.stats_range_week),
    MONTH(R.string.stats_range_month),
}

@HiltViewModel
class StatsViewModel @Inject constructor(
    private val bookshelfRepo: BookshelfRepository,
    private val readingRepo: ReadingRepository,
    @ApplicationContext private val appContext: Context,
) : ViewModel() {

    private val _uiState = MutableStateFlow(StatsUiState())
    val uiState: StateFlow<StatsUiState> = _uiState.asStateFlow()

    private var sessions: List<ReadingSession> = emptyList()
    private var books: List<Book> = emptyList()

    // 导航光标：记录当前正在查看的日期/周/月起始毫秒
    private var cursorDay: Long = dayStart(0)           // 今日
    private var cursorWeek: Long = weekStartMillis()    // 本周一
    private var cursorMonth: Long = monthStartMillis()  // 本月

    /**
     * 日期格式随界面语言变化。
     *
     * 中文用「M月d日」这类中文日期写法；英文改用 Locale 感知的短格式（Aug 19 / Aug 19, 2026），
     * 否则英文界面里会出现「8月19日」这类中英混排。
     */
    private val isEnglish: Boolean
        get() = appContext.resources.configuration.locales[0]?.language == "en"

    private val monthFmt: SimpleDateFormat
        get() = if (isEnglish) SimpleDateFormat("MMM", Locale.getDefault()) else SimpleDateFormat("M月", Locale.getDefault())
    private val dateFmt: SimpleDateFormat
        get() = if (isEnglish) SimpleDateFormat("MMM d", Locale.getDefault()) else SimpleDateFormat("M月d日", Locale.getDefault())
    private val shortFmt: SimpleDateFormat
        get() = SimpleDateFormat("M/d", Locale.getDefault())

    /** 非 Composable 场景取文案 */
    private fun tr(@StringRes id: Int, vararg args: Any): String = appContext.getString(id, *args)

    // 6 个月前（导航回溯的硬限制）
    private val sixMonthsAgo: Long by lazy {
        val cal = Calendar.getInstance()
        cal.add(Calendar.MONTH, -6)
        dayStartOf(cal.timeInMillis)
    }

    init {
        viewModelScope.launch {
            val since = sixMonthsAgo
            combine(
                readingRepo.observeSessionsSince(since),
                bookshelfRepo.observeAllBooks(),
            ) { s, b -> s to b }
                .catch { }
                .collect { (s, b) ->
                    sessions = s; books = b
                    recompute()
                }
        }
    }

    fun setChartRange(range: ChartRange) { _uiState.update { it.copy(chartRange = range) }; recompute() }

    /** 日模式导航：delta = -1（前一天）/ +1（后一天） */
    fun navigateDay(delta: Int) {
        cursorDay += delta * DAY_MS
        if (cursorDay < sixMonthsAgo) cursorDay = sixMonthsAgo
        if (cursorDay > dayStart(0)) cursorDay = dayStart(0)
        recompute()
    }

    /** 周模式导航：delta = -1（前一周）/ +1（后一周） */
    fun navigateWeek(delta: Int) {
        cursorWeek += delta * 7 * DAY_MS
        if (cursorWeek < sixMonthsAgo) cursorWeek = sixMonthsAgo
        if (cursorWeek > weekStartMillis()) cursorWeek = weekStartMillis()
        recompute()
    }

    /** 月模式导航：delta = -1（前一月）/ +1（后一月） */
    fun navigateMonth(delta: Int) {
        val cal = Calendar.getInstance().apply { timeInMillis = cursorMonth }
        cal.add(Calendar.MONTH, delta)
        cursorMonth = cal.timeInMillis
        if (cursorMonth < sixMonthsAgo) cursorMonth = sixMonthsAgo
        val thisMonth = monthStartMillis()
        if (cursorMonth > thisMonth) cursorMonth = thisMonth
        recompute()
    }

    fun toggleShowAllFinished() { _uiState.update { it.copy(showAllFinished = !it.showAllFinished) } }

    private fun recompute() {
        val sessions = sessions; val books = books
        val todayStart = dayStart(0)
        val weekStart = weekStartMillis()

        fun duration(s: ReadingSession): Long =
            s.durationMs ?: s.endAt?.let { (it - s.startAt).coerceAtLeast(0) } ?: 0L

        val todayMs = sessions.filter { it.startAt >= todayStart }.sumOf(::duration)
        val weekMs = sessions.filter { it.startAt >= weekStart }.sumOf(::duration)

        // 连续天数
        val activeDays: Set<Long> = sessions.map { dayStartOf(it.startAt) }.toSet()
        var streak = 0
        run {
            var cursor = if (activeDays.contains(todayStart)) todayStart else todayStart - DAY_MS
            if (activeDays.contains(cursor)) {
                while (activeDays.contains(cursor)) { streak++; cursor -= DAY_MS }
            }
        }

        // 读完（日期格式本身已按语言取自资源：中文「M 月 d 日」/ 英文「MMM d, yyyy」）
        val dateFormat = SimpleDateFormat(tr(R.string.stats_vm_date_month_day), Locale.getDefault())
        val finished = books
            .filter { it.isFinished || it.progress >= 0.95f }
            .sortedByDescending { it.lastReadAt }
            .map { FinishedBook(it.title, it.author ?: tr(R.string.stats_vm_unknown_author), dateFormat.format(Date(it.lastReadAt)), it.coverPath) }

        val range = _uiState.value.chartRange
        val result = buildChart(sessions, range, ::duration)

        _uiState.update {
            it.copy(
                todayMinutes = (todayMs / 60000).toInt(),
                weekMinutes = (weekMs / 60000).toInt(),
                consecutiveDays = streak,
                booksFinished = finished.size,
                finishedBooks = finished,
                chartPoints = result.points,
                chartLabel = result.label,
                canGoBack = result.canBack,
                canGoForward = result.canForward,
            )
        }
    }

    // ── 图表构建 ──

    private data class ChartResult(
        val points: List<ChartPoint>,
        val label: String,
        val canBack: Boolean,
        val canForward: Boolean,
    )

    private fun buildChart(
        sessions: List<ReadingSession>,
        range: ChartRange,
        duration: (ReadingSession) -> Long,
    ): ChartResult {
        if (sessions.isEmpty()) return ChartResult(emptyList(), labelFor(range), false, false)

        return when (range) {
            ChartRange.DAY -> buildDayChart(sessions, duration)
            ChartRange.WEEK -> buildWeekChart(sessions, duration)
            ChartRange.MONTH -> buildMonthChart(sessions, duration)
        }
    }

    /** 日模式：24 小时柱状图（0-23 点） */
    private fun buildDayChart(
        sessions: List<ReadingSession>,
        duration: (ReadingSession) -> Long,
    ): ChartResult {
        val dayStart = cursorDay
        val dayEnd = dayStart + DAY_MS
        val todayStart = dayStart(0)

        val buckets = FloatArray(24)
        sessions.forEach { s ->
            if (s.startAt >= dayStart && s.startAt < dayEnd) {
                val cal = Calendar.getInstance().apply { timeInMillis = s.startAt }
                val hour = cal.get(Calendar.HOUR_OF_DAY)
                buckets[hour] += duration(s) / 60000f
            }
        }

        val isToday = dayStart == todayStart
        val points = (0..23).map { h ->
            ChartPoint("${h}:00", buckets[h], isCurrent = isToday && h == Calendar.getInstance().get(Calendar.HOUR_OF_DAY))
        }
        val label = if (isToday) tr(R.string.stats_vm_today) else dateFmt.format(Date(dayStart))
        val canBack = dayStart > sixMonthsAgo
        val canForward = !isToday
        return ChartResult(points, label, canBack, canForward)
    }

    /** 周模式：7 天柱状图（周一→周日） */
    private fun buildWeekChart(
        sessions: List<ReadingSession>,
        duration: (ReadingSession) -> Long,
    ): ChartResult {
        val weekStart = cursorWeek
        val todayStart = dayStart(0)

        val points = (0..6).map { d ->
            val dayStartMs = weekStart + d * DAY_MS
            val dayEndMs = dayStartMs + DAY_MS
            val mins = sessions.filter { it.startAt >= dayStartMs && it.startAt < dayEndMs }.sumOf(duration) / 60000f
            ChartPoint(shortFmt.format(Date(dayStartMs)), mins, isCurrent = dayStartMs == todayStart)
        }

        val weekEnd = weekStart + 6 * DAY_MS
        val thisWeek = weekStartMillis()
        val label = if (weekStart == thisWeek) tr(R.string.stats_vm_last_week) else "${shortFmt.format(Date(weekStart))} - ${shortFmt.format(Date(weekEnd))}"
        val canBack = weekStart > sixMonthsAgo
        val canForward = weekStart < thisWeek
        return ChartResult(points, label, canBack, canForward)
    }

    /** 月模式：按天柱状图（1 日→月末） */
    private fun buildMonthChart(
        sessions: List<ReadingSession>,
        duration: (ReadingSession) -> Long,
    ): ChartResult {
        val cal = Calendar.getInstance().apply { timeInMillis = cursorMonth }
        val year = cal.get(Calendar.YEAR)
        val month = cal.get(Calendar.MONTH)
        cal.set(Calendar.DAY_OF_MONTH, 1)
        val monthStart = cal.timeInMillis
        cal.add(Calendar.MONTH, 1)
        val monthEnd = cal.timeInMillis
        val daysInMonth = ((monthEnd - monthStart) / DAY_MS).toInt()

        val todayStart = dayStart(0)

        val points = (0 until daysInMonth).map { d ->
            val dayStartMs = monthStart + d * DAY_MS
            val dayEndMs = dayStartMs + DAY_MS
            val mins = sessions.filter { it.startAt >= dayStartMs && it.startAt < dayEndMs }.sumOf(duration) / 60000f
            ChartPoint(tr(R.string.stats_vm_day_of_month, d + 1), mins, isCurrent = dayStartMs == todayStart)
        }

        val thisMonth = monthStartMillis()
        val label = if (monthStart == thisMonth) tr(R.string.stats_vm_last_month) else tr(R.string.stats_vm_year_month, year, monthFmt.format(Date(monthStart)))
        val canBack = monthStart > sixMonthsAgo
        val canForward = monthStart < thisMonth
        return ChartResult(points, label, canBack, canForward)
    }

    // ── 标签 ──

    private fun labelFor(range: ChartRange): String = when (range) {
        ChartRange.DAY -> tr(R.string.stats_vm_today)
        ChartRange.WEEK -> tr(R.string.stats_vm_last_week)
        ChartRange.MONTH -> tr(R.string.stats_vm_last_month)
    }

    // ── 工具 ──

    private fun dayStart(offsetDays: Int): Long {
        val cal = Calendar.getInstance()
        cal.set(Calendar.HOUR_OF_DAY, 0); cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0); cal.set(Calendar.MILLISECOND, 0)
        cal.add(Calendar.DAY_OF_YEAR, offsetDays)
        return cal.timeInMillis
    }

    private fun dayStartOf(timeMillis: Long): Long {
        val cal = Calendar.getInstance()
        cal.timeInMillis = timeMillis
        cal.set(Calendar.HOUR_OF_DAY, 0); cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0); cal.set(Calendar.MILLISECOND, 0)
        return cal.timeInMillis
    }

    private fun weekStartMillis(): Long {
        val cal = Calendar.getInstance()
        cal.set(Calendar.HOUR_OF_DAY, 0); cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0); cal.set(Calendar.MILLISECOND, 0)
        val dow = cal.get(Calendar.DAY_OF_WEEK)
        val diff = if (dow == Calendar.SUNDAY) -6 else Calendar.MONDAY - dow
        cal.add(Calendar.DAY_OF_YEAR, diff)
        return cal.timeInMillis
    }

    private fun monthStartMillis(): Long {
        val cal = Calendar.getInstance()
        cal.set(Calendar.DAY_OF_MONTH, 1)
        cal.set(Calendar.HOUR_OF_DAY, 0); cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0); cal.set(Calendar.MILLISECOND, 0)
        return cal.timeInMillis
    }

    companion object { private const val DAY_MS = 24L * 60 * 60 * 1000 }
}
