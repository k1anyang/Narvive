package com.narvive.app.service.font

import android.content.Context
import java.io.File

/** 下载字体在本机文件系统中的存放位置（filesDir/fonts） */
object FontStorage {
    fun dir(context: Context): File =
        File(context.filesDir, "fonts").apply { mkdirs() }
}