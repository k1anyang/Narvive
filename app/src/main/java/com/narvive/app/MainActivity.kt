package com.narvive.app

import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.net.Uri
import android.os.Bundle
import android.os.LocaleList
import androidx.activity.enableEdgeToEdge
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.narvive.app.data.datastore.NarviveDataStore
import com.narvive.app.service.ExternalImportBus
import com.narvive.app.service.WebDavService
import com.narvive.app.ui.navigation.NarviveNavHost
import com.narvive.app.ui.theme.NarviveTheme
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * 单 Activity 入口。
 *
 * 继承 [AppCompatActivity]（而非 FragmentActivity）是为了使用官方 per-app language：
 * `AppCompatDelegate.setApplicationLocales()` 需要 AppCompat 宿主，它会在语言变更时
 * 自动重创建本 Activity，并同步 Android 13+ 的系统「应用语言」设置。
 * 主题见 res/values/themes.xml（Theme.Narvive 已继承 Theme.AppCompat.Light.NoActionBar）。
 */
@AndroidEntryPoint
class MainActivity : AppCompatActivity() {

    @Inject lateinit var importBus: ExternalImportBus
    @Inject lateinit var dataStore: NarviveDataStore
    @Inject lateinit var webDavService: WebDavService

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /**
     * 用已保存的 per-app locale 覆盖 base context。
     *
     * 为什么保留（依赖重建方案下它仍有用）：切换语言时本 Activity 会被系统重建，
     * 重建后的**首帧**必须已经使用新语言的资源。AppCompat 也会做这件事，但在部分
     * 时序下晚于本方法，导致首帧仍按旧 locale 排版、随后文字宽度变化。这里在 attach
     * 阶段同步读一次已持久化的 locale 并应用，保证首帧就是新语言，不等任何异步状态。
     *
     * 读取用 `getApplicationLocales()`（同步、无 IO）。若用户尚未设置过（空列表），
     * 直接沿用系统配置，行为与改造前一致。
     */
    override fun attachBaseContext(newBase: Context) {
        val compat = AppCompatDelegate.getApplicationLocales()
        if (compat.isEmpty) {
            super.attachBaseContext(newBase)
            return
        }
        // LocaleListCompat → LocaleList（框架类型），供 Configuration.setLocales 使用
        val locales = Array(compat.size()) { index -> compat[index] }
        val config = Configuration(newBase.resources.configuration).apply {
            setLocales(LocaleList(*locales))
        }
        super.attachBaseContext(newBase.createConfigurationContext(config))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        handleExternalImport(intent)
        setContent {
            val darkMode by dataStore.darkTheme.collectAsState(initial = "system")
            val appearanceTheme by dataStore.appearanceTheme.collectAsState(initial = "sky")
            val isDark = when (darkMode) {
                "light" -> false
                "dark" -> true
                else -> isSystemInDarkTheme()
            }
            NarviveTheme(darkTheme = isDark, appearanceTheme = appearanceTheme) {
                NarviveNavHost(
                    onReaderExit = {
                        appScope.launch { webDavService.autoSyncIfEnabled() }
                    },
                )
            }
        }
    }

    override fun onStop() {
        super.onStop()
        appScope.launch { webDavService.autoSyncIfEnabled() }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleExternalImport(intent)
    }

    private fun handleExternalImport(intent: Intent?) {
        val uri: Uri? = when (intent?.action) {
            Intent.ACTION_VIEW, Intent.ACTION_SEND -> (intent.data ?: intent.getParcelableExtra<Uri>(Intent.EXTRA_STREAM))
            else -> null
        }
        if (uri != null) importBus.request(uri)
    }
}
