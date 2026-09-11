package com.narvive.app.ui.message

import android.content.Context
import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource

/**
 * 可本地化的用户可见消息。
 *
 * 背景：ViewModel / Service 层无法直接调用 `stringResource`（非 Composable），
 * 早期实现把中文字面量直接拼进 `String` 返回给 UI，导致界面语言切换后
 * 这些消息永远是中文。本类型把「文案」与「语言」解耦：
 *
 * - [Res]：来自 `strings.xml` 的文案 + 格式化参数，**渲染时才按当前语言解析**，
 *   因此切换语言后所有消息都会立刻跟随。
 * - [Raw]：不可翻译的动态内容——服务端 `error.message`、AI 输出、书名/文件名等。
 *
 * 用法：
 * - Composable 内：`message.text()`
 * - 非 Composable：`message.resolve(context)`
 *
 * 约定：能用 [Res] 的绝不用 [Raw]；[Raw] 只承载本身就来自数据的内容。
 */
sealed interface UiMessage {

    /** 资源文案。[args] 用于 `%1$s` / `%1$d` 等占位符，语序由各语言资源自行决定。 */
    data class Res(@StringRes val id: Int, val args: List<Any> = emptyList()) : UiMessage {
        constructor(@StringRes id: Int, vararg args: Any) : this(id, args.toList())
    }

    /** 不可翻译的动态文本（服务端消息 / AI 输出 / 异常 message / 用户数据）。 */
    data class Raw(val text: String) : UiMessage
}

/** Composable 内解析为当前语言的文案。 */
@Composable
fun UiMessage.text(): String = when (this) {
    is UiMessage.Res -> stringResource(id, *args.toTypedArray())
    is UiMessage.Raw -> text
}

/** 非 Composable 场景解析（如 Service 内直接取文案用于通知）。 */
fun UiMessage.resolve(context: Context): String = when (this) {
    is UiMessage.Res -> context.getString(id, *args.toTypedArray())
    is UiMessage.Raw -> text
}
