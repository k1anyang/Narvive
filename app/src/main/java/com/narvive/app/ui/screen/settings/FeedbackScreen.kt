package com.narvive.app.ui.screen.settings

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Email
import androidx.compose.material.icons.rounded.Forum
import androidx.compose.material.icons.rounded.BugReport
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.narvive.app.R
import com.narvive.app.ui.components.TabPageScaffold
import com.narvive.app.ui.prefs.AppLanguage
import com.narvive.app.ui.theme.NarviveShape
import kotlinx.coroutines.launch

private const val DEVELOPER_EMAIL = "k1anyang@163.com"
private const val GITHUB_REPO_URL = "https://github.com/k1anyang/Narvive"

// 反馈入口的固定跳转地址（与 .github/ISSUE_TEMPLATE 对应）
private const val ISSUE_NEW_URL = "$GITHUB_REPO_URL/issues/new/choose"
private const val DISCUSSIONS_URL = "$GITHUB_REPO_URL/discussions"

/**
 * 问题反馈页。
 *
 * 三个渠道：邮箱、GitHub Issue（Bug）、GitHub Discussions（建议/提问）。
 * 底部提供「复制诊断信息」——把版本号与机型等写进剪贴板，用户可直接粘贴到邮件或 Issue，
 * 避免每次都要手打。**刻意不包含任何阅读内容与 API Key**。
 */
@Composable
fun FeedbackScreen(onBackClick: () -> Unit) {
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val copiedText = stringResource(R.string.feedback_diagnostics_copied)

    TabPageScaffold(
        title = stringResource(R.string.feedback_title),
        navigationIcon = { IconButton(onClick = onBackClick) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, stringResource(R.string.common_back)) } },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
        ) {
            // 致谢
            Card(Modifier.fillMaxWidth(), shape = NarviveShape.Md) {
                Column(Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.Favorite, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(
                            stringResource(R.string.feedback_thanks_title),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    Text(
                        stringResource(R.string.feedback_thanks_body),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        lineHeight = 20.sp,
                    )
                }
            }

            Spacer(Modifier.height(16.dp))

            // 渠道 1：邮箱
            FeedbackChannel(
                icon = Icons.Rounded.Email,
                title = stringResource(R.string.feedback_channel_email),
                desc = stringResource(R.string.feedback_channel_email_desc, DEVELOPER_EMAIL),
                onClick = { openUrl(context, "mailto:$DEVELOPER_EMAIL?subject=${Uri.encode("[Narvive] Feedback")}") },
            )
            Spacer(Modifier.height(10.dp))

            // 渠道 2：GitHub Issue
            FeedbackChannel(
                icon = Icons.Rounded.BugReport,
                title = stringResource(R.string.feedback_channel_issue),
                desc = stringResource(R.string.feedback_channel_issue_desc),
                onClick = { openUrl(context, ISSUE_NEW_URL) },
            )
            Spacer(Modifier.height(10.dp))

            // 渠道 3：GitHub Discussions
            FeedbackChannel(
                icon = Icons.Rounded.Forum,
                title = stringResource(R.string.feedback_channel_discussion),
                desc = stringResource(R.string.feedback_channel_discussion_desc),
                onClick = { openUrl(context, DISCUSSIONS_URL) },
            )

            Spacer(Modifier.height(20.dp))

            // 诊断信息
            Card(Modifier.fillMaxWidth(), shape = NarviveShape.Md) {
                Column(Modifier.padding(16.dp)) {
                    Text(
                        stringResource(R.string.feedback_diagnostics_title),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        stringResource(R.string.feedback_diagnostics_body),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        lineHeight = 18.sp,
                    )
                    Spacer(Modifier.height(10.dp))
                    // 预览下将要复制的内容，避免用户不知情地复制了别的东西
                    Text(
                        buildDiagnostics(context),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        lineHeight = 16.sp,
                    )
                    Spacer(Modifier.height(12.dp))
                    Button(
                        onClick = {
                            copyToClipboard(context, buildDiagnostics(context))
                            scope.launch { snackbarHostState.showSnackbar(copiedText) }
                        },
                        modifier = Modifier.fillMaxWidth().height(44.dp),
                    ) {
                        Icon(Icons.Rounded.ContentCopy, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.feedback_diagnostics_copy))
                    }
                }
            }

            Spacer(Modifier.height(20.dp))

            // 撰写指引
            Card(Modifier.fillMaxWidth(), shape = NarviveShape.Md) {
                Column(Modifier.padding(16.dp)) {
                    Text(
                        stringResource(R.string.feedback_guide_title),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        stringResource(R.string.feedback_guide_body),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        lineHeight = 20.sp,
                    )
                }
            }

            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun FeedbackChannel(icon: ImageVector, title: String, desc: String, onClick: () -> Unit) {
    Card(Modifier.fillMaxWidth().clickable(onClick = onClick), shape = NarviveShape.Md) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                Text(desc, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Icon(
                Icons.AutoMirrored.Rounded.OpenInNew,
                null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                modifier = Modifier.size(16.dp),
            )
        }
    }
}

/**
 * 组装诊断信息。
 *
 * 只包含定位问题必需、且**不含隐私**的字段：应用版本、界面语言、机型、Android 版本、ABI。
 * 刻意不含书名、笔记、对话、API Key。
 *
 * 版本号通过 PackageManager 读取，而不是 BuildConfig——本项目未开启
 * `buildFeatures.buildConfig`，本次改动不触碰构建脚本。
 */
private fun buildDiagnostics(context: Context): String = buildString {
    val version = runCatching {
        val info = context.packageManager.getPackageInfo(context.packageName, 0)
        "${info.versionName} (versionCode ${info.longVersionCode})"
    }.getOrDefault("unknown")
    appendLine("Narvive: $version")
    appendLine("Interface language: ${AppLanguage.current().tag}")
    appendLine("Device: ${Build.MANUFACTURER} ${Build.MODEL}")
    appendLine("Android: ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
    append("ABI: ${Build.SUPPORTED_ABIS.firstOrNull() ?: "unknown"}")
}

private fun copyToClipboard(context: Context, text: String) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    clipboard.setPrimaryClip(ClipData.newPlainText("Narvive diagnostics", text))
}

private fun openUrl(context: Context, url: String) {
    runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
}
