package com.narvive.app

import android.app.Application
import android.util.Log
import java.io.File
import java.io.PrintWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 简易崩溃日志——写入 app 私有目录 crash/ 下，不依赖第三方库。
 * 产线可用 Firebase Crashlytics 替换。
 */
object CrashHandler {

    private const val TAG = "NarviveCrash"

    fun init(app: Application) {
        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            Log.e(TAG, "未捕获异常", throwable)
            val dir = File(app.filesDir, "crash")
            dir.mkdirs()
            // 含毫秒 + 原子递增后缀，防同秒多崩溃文件名冲突
            val ts = SimpleDateFormat("yyyyMMdd-HHmmssSSS", Locale.getDefault()).format(Date())
            var file = File(dir, "crash-$ts.log")
            var suffix = 1
            while (file.exists()) {
                file = File(dir, "crash-${ts}_${suffix}.log")
                suffix++
            }
            try {
                PrintWriter(file).use { pw ->
                    pw.println("Narvive Crash Report")
                    pw.println("Time: $ts")
                    pw.println("Thread: ${thread.name}")
                    pw.println()
                    throwable.printStackTrace(pw)
                }
            } catch (_: Exception) {}
            defaultHandler?.uncaughtException(thread, throwable)
        }
    }
}
