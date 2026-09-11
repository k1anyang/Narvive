package com.narvive.app.data.datastore

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.narviveDataStore: DataStore<Preferences> by preferencesDataStore(name = "narvive_prefs")

@Singleton
class NarviveDataStore @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val ds = context.narviveDataStore

    // Reading default theme
    val readingTheme: Flow<String> = ds.data.map {
        it[KEY_READING_THEME] ?: "paper"
    }

    suspend fun setReadingTheme(theme: String) {
        ds.edit { it[KEY_READING_THEME] = theme }
    }

    // 阅读页日/夜间状态（进入阅读页时仍会按主页外观重置默认值）
    val nightMode: Flow<Boolean> = ds.data.map {
        it[KEY_NIGHT_MODE] ?: false
    }

    suspend fun setNightMode(enabled: Boolean) {
        ds.edit { it[KEY_NIGHT_MODE] = enabled }
    }

    // 自定义阅读主题颜色（ARGB）
    val customInkColor: Flow<Long> = ds.data.map {
        it[KEY_CUSTOM_INK_COLOR] ?: 0xFF5B4636L
    }

    suspend fun setCustomInkColor(color: Long) {
        ds.edit { it[KEY_CUSTOM_INK_COLOR] = color and 0xFFFFFFFFL }
    }

    val customBgColor: Flow<Long> = ds.data.map {
        it[KEY_CUSTOM_BG_COLOR] ?: 0xFFF7F0E1L
    }

    suspend fun setCustomBgColor(color: Long) {
        ds.edit { it[KEY_CUSTOM_BG_COLOR] = color and 0xFFFFFFFFL }
    }

    // Font size
    val defaultFontSize: Flow<Int> = ds.data.map {
        it[KEY_FONT_SIZE] ?: 17
    }

    suspend fun setDefaultFontSize(fontSize: Int) {
        ds.edit { it[KEY_FONT_SIZE] = fontSize }
    }

    // Line height
    val defaultLineHeight: Flow<Float> = ds.data.map {
        it[KEY_LINE_HEIGHT] ?: 1.4f
    }

    suspend fun setDefaultLineHeight(lineHeight: Float) {
        ds.edit { it[KEY_LINE_HEIGHT] = lineHeight }
    }

    // Margin (页边距 dp)
    val defaultMargin: Flow<Int> = ds.data.map {
        it[KEY_MARGIN] ?: 24
    }

    suspend fun setDefaultMargin(margin: Int) {
        ds.edit { it[KEY_MARGIN] = margin }
    }

    // Alignment (left | justify)
    val defaultAlignment: Flow<String> = ds.data.map {
        it[KEY_ALIGNMENT] ?: "justify"
    }

    suspend fun setDefaultAlignment(alignment: String) {
        ds.edit { it[KEY_ALIGNMENT] = alignment }
    }

    // Paragraph spacing (段距倍数 0.5–2.0)
    val defaultParagraphSpacing: Flow<Float> = ds.data.map {
        it[KEY_PARA_SPACING] ?: 0.0f
    }

    suspend fun setDefaultParagraphSpacing(spacing: Float) {
        ds.edit { it[KEY_PARA_SPACING] = spacing }
    }

    // First-line indent（首行缩进 em，0–4）
    val defaultFirstLineIndent: Flow<Float> = ds.data.map {
        it[KEY_FIRST_LINE_INDENT] ?: 2f
    }

    suspend fun setDefaultFirstLineIndent(indent: Float) {
        ds.edit { it[KEY_FIRST_LINE_INDENT] = indent }
    }

    // EPUB 出版方样式开关（true=尊重出版方排版；false=阅读器全权接管）
    val publisherStyles: Flow<Boolean> = ds.data.map {
        it[KEY_PUBLISHER_STYLES] ?: true
    }

    suspend fun setPublisherStyles(enabled: Boolean) {
        ds.edit { it[KEY_PUBLISHER_STYLES] = enabled }
    }

    // Vertical margin（上下边距 倍率 × 24dp，0–2.0）
    val defaultVerticalMargin: Flow<Float> = ds.data.map {
        it[KEY_VERTICAL_MARGIN] ?: 0.8f
    }

    suspend fun setDefaultVerticalMargin(margin: Float) {
        ds.edit { it[KEY_VERTICAL_MARGIN] = margin }
    }

    // Horizontal margin（左右边距 倍率 × 24dp，0–2.0）
    val defaultHorizontalMargin: Flow<Float> = ds.data.map {
        it[KEY_HORIZONTAL_MARGIN] ?: 1f
    }

    suspend fun setDefaultHorizontalMargin(margin: Float) {
        ds.edit { it[KEY_HORIZONTAL_MARGIN] = margin }
    }

    // Font family (source_serif | serif | source_sans)
    val defaultFontFamily: Flow<String> = ds.data.map {
        it[KEY_FONT_FAMILY] ?: "source_serif"
    }

    suspend fun setDefaultFontFamily(family: String) {
        ds.edit { it[KEY_FONT_FAMILY] = family }
    }

    // Font family CJK / Latin（中英双字体独立设置；system = 系统默认）
    val fontFamilyCjk: Flow<String> = ds.data.map {
        it[KEY_FONT_FAMILY_CJK] ?: SYSTEM_FONT_ID
    }

    suspend fun setFontFamilyCjk(fontId: String) {
        ds.edit { it[KEY_FONT_FAMILY_CJK] = fontId }
    }

    val fontFamilyLatin: Flow<String> = ds.data.map {
        it[KEY_FONT_FAMILY_LATIN] ?: SYSTEM_FONT_ID
    }

    suspend fun setFontFamilyLatin(fontId: String) {
        ds.edit { it[KEY_FONT_FAMILY_LATIN] = fontId }
    }

    // Appearance: follow system (true) or override
    val darkTheme: Flow<String> = ds.data.map {
        it[KEY_DARK_THEME] ?: "system"
    }

    suspend fun setDarkTheme(mode: String) { // system | light | dark
        ds.edit { it[KEY_DARK_THEME] = mode }
    }

    // Appearance theme: 外观主题（色彩气质，与明暗模式正交，docs/DESIGN.md §2）
    val appearanceTheme: Flow<String> = ds.data.map {
        it[KEY_APPEARANCE_THEME] ?: "sky"
    }

    suspend fun setAppearanceTheme(themeId: String) { // paper_ink | sky | pine | plum
        ds.edit { it[KEY_APPEARANCE_THEME] = themeId }
    }

    // Library view mode
    val libraryViewMode: Flow<String> = ds.data.map {
        it[KEY_LIBRARY_VIEW] ?: "grid"
    }

    suspend fun setLibraryViewMode(mode: String) { // grid | list
        ds.edit { it[KEY_LIBRARY_VIEW] = mode }
    }

    // Library sort order
    val librarySortOrder: Flow<String> = ds.data.map {
        it[KEY_LIBRARY_SORT] ?: "LAST_READ"
    }

    suspend fun setLibrarySortOrder(order: String) {
        ds.edit { it[KEY_LIBRARY_SORT] = order }
    }

    // Library sort direction（null = 跟随排序键默认方向；asc | desc）
    val librarySortDirection: Flow<String?> = ds.data.map {
        it[KEY_LIBRARY_SORT_DIRECTION]
    }

    suspend fun setLibrarySortDirection(direction: String) {
        ds.edit { it[KEY_LIBRARY_SORT_DIRECTION] = direction }
    }

    // Library grid columns（网格视图每行数量 1..4）
    val gridColumns: Flow<Int> = ds.data.map {
        it[KEY_GRID_COLUMNS] ?: 2
    }

    suspend fun setGridColumns(columns: Int) {
        ds.edit { it[KEY_GRID_COLUMNS] = columns.coerceIn(1, 4) }
    }

    // Last backup time
    val lastBackupTime: Flow<Long> = ds.data.map {
        it[KEY_LAST_BACKUP] ?: 0L
    }

    suspend fun setLastBackupTime(time: Long) {
        ds.edit { it[KEY_LAST_BACKUP] = time }
    }

    // ── WebDAV 配置 ──

    val webdavEnabled: Flow<Boolean> = ds.data.map {
        it[KEY_WEBDAV_ENABLED] ?: false
    }

    suspend fun setWebdavEnabled(enabled: Boolean) {
        ds.edit { it[KEY_WEBDAV_ENABLED] = enabled }
    }

    val webdavUrl: Flow<String> = ds.data.map {
        it[KEY_WEBDAV_URL] ?: ""
    }

    suspend fun setWebdavUrl(url: String) {
        ds.edit { it[KEY_WEBDAV_URL] = url }
    }

    val webdavUsername: Flow<String> = ds.data.map {
        it[KEY_WEBDAV_USERNAME] ?: ""
    }

    suspend fun setWebdavUsername(username: String) {
        ds.edit { it[KEY_WEBDAV_USERNAME] = username }
    }

    val webdavRemotePath: Flow<String> = ds.data.map {
        it[KEY_WEBDAV_REMOTE_PATH] ?: "Narvive"
    }

    suspend fun setWebdavRemotePath(path: String) {
        ds.edit { it[KEY_WEBDAV_REMOTE_PATH] = path }
    }

    val webdavAutoSync: Flow<Boolean> = ds.data.map {
        it[KEY_WEBDAV_AUTO_SYNC] ?: false
    }

    suspend fun setWebdavAutoSync(enabled: Boolean) {
        ds.edit { it[KEY_WEBDAV_AUTO_SYNC] = enabled }
    }

    // AI Providers 列表（JSON 序列化，不含 API Key）
    val providersJson: Flow<String?> = ds.data.map {
        it[KEY_PROVIDERS_JSON]
    }

    suspend fun setProvidersJson(json: String) {
        ds.edit { it[KEY_PROVIDERS_JSON] = json }
    }

    // AI Prompt 模板（JSON map：模板 id -> 用户自定义文案）
    val promptsJson: Flow<String?> = ds.data.map {
        it[KEY_PROMPTS_JSON]
    }

    suspend fun setPromptsJson(json: String) {
        ds.edit { it[KEY_PROMPTS_JSON] = json }
    }

    // ── AI 偏好（主 AI 页客制化；enabled/mode/profile） ──

    val aiPrefEnabled: Flow<Boolean> = ds.data.map {
        it[KEY_AI_PREF_ENABLED] ?: false
    }

    suspend fun setAiPrefEnabled(enabled: Boolean) {
        ds.edit { it[KEY_AI_PREF_ENABLED] = enabled }
    }

    val aiPrefMode: Flow<String> = ds.data.map {
        it[KEY_AI_PREF_MODE] ?: "auto"
    }

    suspend fun setAiPrefMode(mode: String) {
        ds.edit { it[KEY_AI_PREF_MODE] = mode }
    }

    val aiPrefProfileJson: Flow<String?> = ds.data.map {
        it[KEY_AI_PREF_PROFILE_JSON]
    }

    suspend fun setAiPrefProfileJson(json: String?) {
        ds.edit { prefs ->
            if (json == null) prefs.remove(KEY_AI_PREF_PROFILE_JSON) else prefs[KEY_AI_PREF_PROFILE_JSON] = json
        }
    }

    // ── 亮度 / 护眼 ──

    val defaultBrightness: Flow<Float> = ds.data.map {
        it[KEY_BRIGHTNESS] ?: 1f
    }

    suspend fun setDefaultBrightness(brightness: Float) {
        ds.edit { it[KEY_BRIGHTNESS] = brightness }
    }

    val defaultFollowSystemBrightness: Flow<Boolean> = ds.data.map {
        it[KEY_FOLLOW_SYSTEM_BRIGHTNESS] ?: false
    }

    suspend fun setDefaultFollowSystemBrightness(follow: Boolean) {
        ds.edit { it[KEY_FOLLOW_SYSTEM_BRIGHTNESS] = follow }
    }

    val defaultEyeProtection: Flow<Boolean> = ds.data.map {
        it[KEY_EYE_PROTECTION] ?: false
    }

    suspend fun setDefaultEyeProtection(enabled: Boolean) {
        ds.edit { it[KEY_EYE_PROTECTION] = enabled }
    }

    // 翻页动画（slide | updown | none）
    val defaultPageFlipAnimation: Flow<String> = ds.data.map {
        it[KEY_PAGE_FLIP_ANIMATION] ?: "slide"
    }

    suspend fun setDefaultPageFlipAnimation(mode: String) {
        ds.edit { it[KEY_PAGE_FLIP_ANIMATION] = mode }
    }

    // 自动翻页速度（秒/页，10..120，步进 5）
    val defaultAutoFlipSpeedSeconds: Flow<Int> = ds.data.map {
        it[KEY_AUTO_FLIP_SPEED_SECONDS] ?: 30
    }

    suspend fun setDefaultAutoFlipSpeedSeconds(seconds: Int) {
        ds.edit { it[KEY_AUTO_FLIP_SPEED_SECONDS] = seconds.coerceIn(10, 120) }
    }

    // ── 更多设置（全局阅读偏好） ──

    val volumeKeyPageTurn: Flow<Boolean> = ds.data.map {
        it[KEY_VOLUME_KEY_PAGE_TURN] ?: true
    }

    suspend fun setVolumeKeyPageTurn(enabled: Boolean) {
        ds.edit { it[KEY_VOLUME_KEY_PAGE_TURN] = enabled }
    }

    val bookOpenAnimation: Flow<Boolean> = ds.data.map {
        it[KEY_BOOK_OPEN_ANIMATION] ?: false
    }

    suspend fun setBookOpenAnimation(enabled: Boolean) {
        ds.edit { it[KEY_BOOK_OPEN_ANIMATION] = enabled }
    }

    val screenOffMinutes: Flow<Int> = ds.data.map {
        it[KEY_SCREEN_OFF_MINUTES] ?: 5
    }

    suspend fun setScreenOffMinutes(minutes: Int) {
        ds.edit { it[KEY_SCREEN_OFF_MINUTES] = minutes.coerceIn(0, 15) }
    }

    val restReminderEnabled: Flow<Boolean> = ds.data.map {
        it[KEY_REST_REMINDER_ENABLED] ?: false
    }

    suspend fun setRestReminderEnabled(enabled: Boolean) {
        ds.edit { it[KEY_REST_REMINDER_ENABLED] = enabled }
    }

    val restReminderMinutes: Flow<Int> = ds.data.map {
        it[KEY_REST_REMINDER_MINUTES] ?: 30
    }

    suspend fun setRestReminderMinutes(minutes: Int) {
        ds.edit { it[KEY_REST_REMINDER_MINUTES] = minutes.coerceIn(15, 60) }
    }

    val progressDisplayMode: Flow<String> = ds.data.map {
        it[KEY_PROGRESS_DISPLAY_MODE] ?: "percentage"
    }

    suspend fun setProgressDisplayMode(mode: String) {
        ds.edit { it[KEY_PROGRESS_DISPLAY_MODE] = mode }
    }

    val showTopInfo: Flow<Boolean> = ds.data.map {
        it[KEY_SHOW_TOP_INFO] ?: true
    }

    suspend fun setShowTopInfo(enabled: Boolean) {
        ds.edit { it[KEY_SHOW_TOP_INFO] = enabled }
    }

    val showBottomInfo: Flow<Boolean> = ds.data.map {
        it[KEY_SHOW_BOTTOM_INFO] ?: true
    }

    suspend fun setShowBottomInfo(enabled: Boolean) {
        ds.edit { it[KEY_SHOW_BOTTOM_INFO] = enabled }
    }

    val batteryPercent: Flow<Boolean> = ds.data.map {
        it[KEY_BATTERY_PERCENT] ?: false
    }

    suspend fun setBatteryPercent(enabled: Boolean) {
        ds.edit { it[KEY_BATTERY_PERCENT] = enabled }
    }

    companion object {
        private val KEY_READING_THEME = stringPreferencesKey("reading_theme")
        private val KEY_NIGHT_MODE = booleanPreferencesKey("night_mode")
        private val KEY_CUSTOM_INK_COLOR = longPreferencesKey("custom_ink_color")
        private val KEY_CUSTOM_BG_COLOR = longPreferencesKey("custom_bg_color")
        private val KEY_FONT_SIZE = intPreferencesKey("font_size")
        private val KEY_LINE_HEIGHT = floatPreferencesKey("line_height")
        private val KEY_MARGIN = intPreferencesKey("margin")
        private val KEY_ALIGNMENT = stringPreferencesKey("alignment")
        private val KEY_PARA_SPACING = floatPreferencesKey("paragraph_spacing")
        private val KEY_FIRST_LINE_INDENT = floatPreferencesKey("first_line_indent")
        private val KEY_PUBLISHER_STYLES = booleanPreferencesKey("publisher_styles")
        private val KEY_VERTICAL_MARGIN = floatPreferencesKey("vertical_margin")
        private val KEY_HORIZONTAL_MARGIN = floatPreferencesKey("horizontal_margin")
        private val KEY_FONT_FAMILY = stringPreferencesKey("font_family")
        private val KEY_FONT_FAMILY_CJK = stringPreferencesKey("font_family_cjk")
        private val KEY_FONT_FAMILY_LATIN = stringPreferencesKey("font_family_latin")

        /** 系统默认字体 id（不使用下载字体，走系统自带衬线） */
        const val SYSTEM_FONT_ID = "system"
        private val KEY_DARK_THEME = stringPreferencesKey("dark_theme")
        private val KEY_APPEARANCE_THEME = stringPreferencesKey("appearance_theme")
        private val KEY_LIBRARY_VIEW = stringPreferencesKey("library_view")
        private val KEY_LIBRARY_SORT = stringPreferencesKey("library_sort")
        private val KEY_LIBRARY_SORT_DIRECTION = stringPreferencesKey("library_sort_direction")
        private val KEY_GRID_COLUMNS = intPreferencesKey("grid_columns")
        private val KEY_LAST_BACKUP = longPreferencesKey("last_backup")
        private val KEY_WEBDAV_ENABLED = booleanPreferencesKey("webdav_enabled")
        private val KEY_WEBDAV_URL = stringPreferencesKey("webdav_url")
        private val KEY_WEBDAV_USERNAME = stringPreferencesKey("webdav_username")
        private val KEY_WEBDAV_REMOTE_PATH = stringPreferencesKey("webdav_remote_path")
        private val KEY_WEBDAV_AUTO_SYNC = booleanPreferencesKey("webdav_auto_sync")
        private val KEY_PROVIDERS_JSON = stringPreferencesKey("ai_providers_json")
        private val KEY_PROMPTS_JSON = stringPreferencesKey("ai_prompts_json")
        private val KEY_AI_PREF_ENABLED = booleanPreferencesKey("ai_pref_enabled")
        private val KEY_AI_PREF_MODE = stringPreferencesKey("ai_pref_mode")
        private val KEY_AI_PREF_PROFILE_JSON = stringPreferencesKey("ai_pref_profile_json")
        private val KEY_BRIGHTNESS = floatPreferencesKey("brightness")
        private val KEY_FOLLOW_SYSTEM_BRIGHTNESS = booleanPreferencesKey("follow_system_brightness")
        private val KEY_EYE_PROTECTION = booleanPreferencesKey("eye_protection")
        private val KEY_PAGE_FLIP_ANIMATION = stringPreferencesKey("page_flip_animation")
        private val KEY_AUTO_FLIP_SPEED_SECONDS = intPreferencesKey("auto_flip_speed_seconds")
        private val KEY_VOLUME_KEY_PAGE_TURN = booleanPreferencesKey("volume_key_page_turn")
        private val KEY_BOOK_OPEN_ANIMATION = booleanPreferencesKey("book_open_animation")
        private val KEY_SCREEN_OFF_MINUTES = intPreferencesKey("screen_off_minutes")
        private val KEY_REST_REMINDER_ENABLED = booleanPreferencesKey("rest_reminder_enabled")
        private val KEY_REST_REMINDER_MINUTES = intPreferencesKey("rest_reminder_minutes")
        private val KEY_PROGRESS_DISPLAY_MODE = stringPreferencesKey("progress_display_mode")
        private val KEY_SHOW_TOP_INFO = booleanPreferencesKey("show_top_info")
        private val KEY_SHOW_BOTTOM_INFO = booleanPreferencesKey("show_bottom_info")
        private val KEY_BATTERY_PERCENT = booleanPreferencesKey("battery_percent")
    }
}
