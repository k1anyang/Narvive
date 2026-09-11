package com.narvive.app

import android.app.Application
import android.content.pm.ApplicationInfo
import android.webkit.WebView
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class NarviveApp : Application() {
    override fun onCreate() {
        super.onCreate()
        CrashHandler.init(this)
        // Debug 构建开启 WebView 远程调试（chrome://inspect 查看字体加载/控制台错误）
        if (0 != (applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE)) {
            WebView.setWebContentsDebuggingEnabled(true)
        }
    }
}
