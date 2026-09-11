package com.narvive.app.service

import android.content.Context
import android.net.Uri
import com.narvive.app.R
import com.narvive.app.data.datastore.NarviveDataStore
import com.narvive.app.data.keystore.WebDavStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import okhttp3.Credentials
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.RequestBody.Companion.asRequestBody
import java.io.File
import java.text.SimpleDateFormat
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import javax.xml.parsers.DocumentBuilderFactory

data class RemoteBackup(
    val name: String,
    val size: Long,
    val modified: Long,
)

/**
 * WebDAV 云同步：把备份 ZIP 上传到用户自己的服务器，并可列出/下载恢复。
 * 密码存 WebDavStore（EncryptedSharedPreferences），其余配置存 DataStore。
 */
@Singleton
class WebDavService @Inject constructor(
    @ApplicationContext private val context: Context,
    private val dataStore: NarviveDataStore,
    private val webDavStore: WebDavStore,
    private val backupService: BackupService,
) {
    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(90, TimeUnit.SECONDS)
        .writeTimeout(90, TimeUnit.SECONDS)
        .build()

    @Volatile
    private var lastAutoSyncAt = 0L

    private data class Config(
        val enabled: Boolean,
        val url: String,
        val username: String,
        val password: String,
        val remotePath: String,
    )

    private suspend fun loadConfig(): Config? = withContext(Dispatchers.IO) {
        val url = dataStore.webdavUrl.first().trim()
        if (!(url.startsWith("http://") || url.startsWith("https://"))) return@withContext null
        Config(
            enabled = dataStore.webdavEnabled.first(),
            url = url,
            username = dataStore.webdavUsername.first(),
            password = webDavStore.getPassword() ?: "",
            remotePath = dataStore.webdavRemotePath.first().trim().ifBlank { "Narvive" },
        )
    }

    /** 测试连接：MKCOL 远程目录（已存在返回 405 也视为成功） */
    suspend fun testConnection(): Result<String> = withContext(Dispatchers.IO) {
        val config = loadConfig() ?: return@withContext Result.failure(Exception(context.getString(R.string.webdav_service_missing_url)))
        try {
            val dirUrl = dirUrl(config)
            val request = authenticated(Request.Builder().url(dirUrl).method("MKCOL", null), config).build()
            client.newCall(request).execute().use { r ->
                if (r.isSuccessful || r.code == 405 || r.code == 409) {
                    Result.success(context.getString(R.string.webdav_service_connection_ok))
                } else {
                    Result.failure(Exception("HTTP ${r.code}"))
                }
            }
        } catch (e: Exception) {
            Result.failure(Exception(e.message ?: context.getString(R.string.webdav_service_connection_failed)))
        }
    }

    /** 立即同步：导出备份 ZIP → 上传远程 → 仅保留最近 5 个 */
    suspend fun syncNow(): Result<String> = withContext(Dispatchers.IO) {
        val config = loadConfig() ?: return@withContext Result.failure(Exception(context.getString(R.string.webdav_service_missing_url)))
        if (!config.enabled) return@withContext Result.failure(Exception(context.getString(R.string.webdav_service_not_enabled)))
        val stamp = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.getDefault()).format(Date())
        val localFile = File(context.cacheDir, "NarviveBackup-$stamp.zip")
        try {
            val uri = Uri.fromFile(localFile)
            backupService.exportBackupTo(uri).getOrElse {
                return@withContext Result.failure(Exception(context.getString(R.string.webdav_service_backup_failed, it.message)))
            }
            putFile(config, "$stamp.zip", localFile)
            pruneOld(config, keep = 5)
            Result.success(context.getString(R.string.webdav_service_synced, "$stamp.zip"))
        } catch (e: Exception) {
            Result.failure(Exception(e.message ?: context.getString(R.string.webdav_service_sync_failed)))
        } finally {
            localFile.delete()
        }
    }

    /** 列出远程备份（按修改时间倒序） */
    suspend fun listBackups(): Result<List<RemoteBackup>> = withContext(Dispatchers.IO) {
        val config = loadConfig() ?: return@withContext Result.failure(Exception(context.getString(R.string.webdav_service_missing_url)))
        try {
            val dirUrl = dirUrl(config)
            val request = authenticated(Request.Builder().url(dirUrl).method("PROPFIND", propfindBody()), config).build()
            client.newCall(request).execute().use { r ->
                if (!r.isSuccessful) return@withContext Result.failure(Exception("HTTP ${r.code}"))
                val xml = r.body?.string().orEmpty()
                Result.success(parsePropfind(xml))
            }
        } catch (e: Exception) {
            Result.failure(Exception(e.message ?: context.getString(R.string.webdav_service_read_failed)))
        }
    }

    /** 下载远端备份并恢复 */
    suspend fun restore(name: String): Result<Int> = withContext(Dispatchers.IO) {
        val config = loadConfig() ?: return@withContext Result.failure(Exception(context.getString(R.string.webdav_service_missing_url)))
        val safeName = name.substringAfterLast('/').ifBlank { name }
        val localFile = File(context.cacheDir, "webdav_restore_$safeName")
        try {
            val url = "${fileUrl(config)}/${safeName}"
            val request = authenticated(Request.Builder().url(url).get(), config).build()
            client.newCall(request).execute().use { r ->
                if (!r.isSuccessful) return@withContext Result.failure(Exception("HTTP ${r.code}"))
                r.body?.byteStream()?.use { input ->
                    localFile.outputStream().use { output -> input.copyTo(output) }
                } ?: return@withContext Result.failure(Exception(context.getString(R.string.webdav_service_empty_download)))
            }
            backupService.importBackupFromFile(localFile)
        } catch (e: Exception) {
            Result.failure(Exception(e.message ?: context.getString(R.string.webdav_service_restore_failed)))
        } finally {
            localFile.delete()
        }
    }

    /** 自动同步（应用退到后台时调用）：未启用/未配置则直接跳过 */
    suspend fun autoSyncIfEnabled() {
        val config = loadConfig() ?: return
        if (!config.enabled || !dataStore.webdavAutoSync.first()) return
        val now = System.currentTimeMillis()
        if (now - lastAutoSyncAt < 60_000L) return
        lastAutoSyncAt = now
        runCatching { syncNow() }
    }

    // ── 内部实现 ──

    private fun dirUrl(config: Config): String {
        val base = config.url.trimEnd('/')
        return base + "/" + config.remotePath.trim('/')
    }

    private fun fileUrl(config: Config): String {
        val base = config.url.trimEnd('/')
        val path = config.remotePath.trim('/')
        return if (path.isEmpty()) base else "$base/$path"
    }

    private fun authenticated(builder: Request.Builder, config: Config): Request.Builder =
        builder.header("Authorization", Credentials.basic(config.username, config.password))

    private fun putFile(config: Config, name: String, file: File) {
        // 确保目录存在（404/405 均忽略）
        runCatching {
            val mk = authenticated(Request.Builder().url(dirUrl(config)).method("MKCOL", null), config).build()
            client.newCall(mk).execute().use { it.close() }
        }
        val url = "${fileUrl(config)}/$name"
        val body = file.asRequestBody("application/zip".toMediaType())
        val request = authenticated(Request.Builder().url(url).put(body), config).build()
        client.newCall(request).execute().use { r ->
            if (!r.isSuccessful) throw IllegalStateException(context.getString(R.string.webdav_service_upload_failed, r.code))
        }
    }

    private fun pruneOld(config: Config, keep: Int) {
        val backups = runCatching { parsePropfindOf(config) }.getOrDefault(emptyList())
        if (backups.size <= keep) return
        backups.sortedByDescending { it.modified }.drop(keep).forEach { old ->
            runCatching {
                val url = "${fileUrl(config)}/${old.name}"
                val request = authenticated(Request.Builder().url(url).delete(), config).build()
                client.newCall(request).execute().use { it.close() }
            }
        }
    }

    private fun parsePropfindOf(config: Config): List<RemoteBackup> {
        val request = authenticated(Request.Builder().url(dirUrl(config)).method("PROPFIND", propfindBody()), config).build()
        client.newCall(request).execute().use { r ->
            if (!r.isSuccessful) return emptyList()
            return parsePropfind(r.body?.string().orEmpty())
        }
    }

    private fun propfindBody(): okhttp3.RequestBody =
        """<?xml version="1.0" encoding="utf-8"?>
            <d:propfind xmlns:d="DAV:">
              <d:prop>
                <d:getcontentlength/>
                <d:getlastmodified/>
                <d:resourcetype/>
              </d:prop>
            </d:propfind>""".trimIndent().toRequestBody("application/xml".toMediaType())

    private fun parsePropfind(xml: String): List<RemoteBackup> {
        val result = mutableListOf<RemoteBackup>()
        runCatching {
            val factory = DocumentBuilderFactory.newInstance()
            factory.isNamespaceAware = true
            val doc = factory.newDocumentBuilder()
                .parse(xml.byteInputStream(Charsets.UTF_8))
            val responses = doc.getElementsByTagNameNS("DAV:", "response")
            for (i in 0 until responses.length) {
                val resp = responses.item(i) ?: continue
                val href = childElement(resp as? org.w3c.dom.Element, "href")?.textContent.orEmpty()
                    .substringAfterLast('/')
                if (href.isBlank() || !href.endsWith(".zip", ignoreCase = true)) continue
                val propstat = childElement(resp as? org.w3c.dom.Element, "propstat")
                val prop = propstat?.let { childElement(it, "prop") }
                var size = 0L
                var modified = 0L
                prop?.let { propEl ->
                    childElements(propEl).forEach { el ->
                        when (el.localName) {
                            "getcontentlength" -> size = el.textContent.trim().toLongOrNull() ?: 0L
                            "getlastmodified" -> modified = runCatching {
                                ZonedDateTime.parse(el.textContent.trim(), DateTimeFormatter.RFC_1123_DATE_TIME)
                                    .toInstant().toEpochMilli()
                            }.getOrDefault(0L)
                        }
                    }
                }
                result += RemoteBackup(name = href, size = size, modified = modified)
            }
        }
        return result.sortedByDescending { it.modified }
    }

    private fun childElements(parent: org.w3c.dom.Element?): List<org.w3c.dom.Element> {
        if (parent == null) return emptyList()
        val out = mutableListOf<org.w3c.dom.Element>()
        for (i in 0 until parent.childNodes.length) {
            (parent.childNodes.item(i) as? org.w3c.dom.Element)?.let { out += it }
        }
        return out
    }

    private fun childElement(parent: org.w3c.dom.Element?, localName: String): org.w3c.dom.Element? =
        childElements(parent).firstOrNull { it.localName == localName }
}
