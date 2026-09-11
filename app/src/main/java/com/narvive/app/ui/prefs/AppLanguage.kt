package com.narvive.app.ui.prefs

import androidx.annotation.StringRes
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import com.narvive.app.R

/**
 * 界面语言（App 外壳的语言，不影响阅读正文与 AI 输出语言策略）。
 *
 * 持久化完全交给 AppCompat：`AppCompatDelegate.setApplicationLocales()` 在
 * Android 13+ 写入系统 per-app locale，在更低版本写入 AppCompat 自己的
 * SharedPreferences，因此这里**不**再往 NarviveDataStore 里存一份，避免双源冲突。
 *
 * 读取用 [current]（同步、无 IO）：`localeTags` 为空表示尚未设置过，
 * 此时回退到 [ZH_HANS]——与改造前的纯中文行为一致。
 */
enum class AppLanguage(
    /** BCP-47 语言标签，直接用于 LocaleListCompat 与 locales_config.xml */
    val tag: String,
    /** 选择器上的展示名，用该语言自己的写法（简体中文 / 繁體中文 / English） */
    @StringRes val labelRes: Int,
) {
    ZH_HANS("zh-Hans", R.string.settings_language_zh_hans),
    ZH_HANT("zh-Hant", R.string.settings_language_zh_hant),
    EN("en", R.string.settings_language_en),
    ;

    companion object {
        /** 默认语言：首次启动、或读取不到任何已保存偏好时使用 */
        val DEFAULT = ZH_HANS

        /**
         * 从系统/AppCompat 已保存的 per-app locale 解析当前语言。
         *
         * 取首个 locale 的主语言 + 文字体系（script）判定：
         * - `zh` + `Hant` → [ZH_HANT]
         * - `zh` + 其他   → [ZH_HANS]
         * - `en`          → [EN]
         * - 其他未支持语言 → [DEFAULT]
         */
        fun current(): AppLanguage {
            val locale = AppCompatDelegate.getApplicationLocales()[0] ?: return DEFAULT
            return when (locale.language) {
                "zh" -> if (locale.script == "Hant") ZH_HANT else ZH_HANS
                "en" -> EN
                else -> DEFAULT
            }
        }

        /**
         * 应用语言。AppCompat 会自行持久化，并在需要时重创建 Activity
         * （界面文案随之更新；Android 13+ 同时同步到系统「应用语言」设置）。
         */
        fun apply(language: AppLanguage) {
            AppCompatDelegate.setApplicationLocales(
                LocaleListCompat.forLanguageTags(language.tag),
            )
        }
    }
}
