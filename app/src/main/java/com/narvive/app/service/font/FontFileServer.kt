package com.narvive.app.service.font

import android.content.Context
import android.util.Log
import android.webkit.WebResourceResponse
import com.narvive.app.R
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.BufferedReader
import java.io.ByteArrayInputStream
import java.io.File
import java.io.InputStreamReader
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.net.URLDecoder
import java.util.concurrent.ConcurrentHashMap
import java.security.KeyStore
import javax.inject.Inject
import javax.inject.Singleton
import javax.net.ssl.KeyManagerFactory
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLServerSocket
import kotlin.concurrent.thread

private const val TAG = "NarviveFontServer"

/** 自签名证书口令（仅本地开发证书，随 APK 打包） */
private const val KEYSTORE_PASSWORD = "narvive2026"

/**
 * 极简本地 HTTPS 文件服务器（仅监听 127.0.0.1 随机端口，自签名证书），
 * 供 EPUB WebView 通过 https://127.0.0.1:port/fonts/<fileName> 加载 filesDir/fonts 下的字体。
 *
 * 为什么必须 HTTPS：Readium 以 WebViewAssetLoader 提供 https://readium_package/... 页面，
 * 从 https 页面加载 http 子资源属于混合内容，实测 MIXED_CONTENT_ALWAYS_ALLOW 对
 * WebViewAssetLoader 虚拟域不生效（浏览器不发请求）。改用 https 后：
 * - 页面 https → 字体 https：无混合内容；
 * - 自签名证书由 EpubViewer 包装 WebViewClient 的 onReceivedSslError 放行；
 * - 跨源仍需要 CORS（响应已带 Access-Control-Allow-Origin: *）。
 */
@Singleton
class FontFileServer @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    @Volatile
    private var serverSocket: SSLServerSocket? = null

    @Volatile
    private var running = false

    private val lock = Any()

    private val sslContext: SSLContext by lazy { buildSslContext() }

    /** 启动（若未启动）并返回基础 URL，例如 https://127.0.0.1:37821 */
    fun baseUrl(): String {
        ensureStarted()
        return "https://127.0.0.1:${serverSocket!!.localPort}"
    }

    /**
     * 直接返回给 Readium WebView 的字体响应。
     *
     * Readium 的 shouldInterceptRequest 会把非 readium_assets 域名都当作 EPUB 内部资源，
     * 因此不能让 WebView 真的发起本地 HTTPS 请求；这里直接构造 WebResourceResponse，
     * 在 EpubViewer 的 WebViewClient 中优先返回。
     */
    fun responseFor(name: String): WebResourceResponse? {
        if (name.isBlank() || name.contains("..") || name.contains('/') || name.contains('\\')) {
            return null
        }
        val file = File(FontStorage.dir(context), name)
        if (!file.isFile) return null

        val bytes = file.readBytes()
        val served = servedBytes(file, bytes)
        val mime = if (name.endsWith(".otf", ignoreCase = true)) "font/otf" else "font/ttf"
        val headers = mapOf(
            "Access-Control-Allow-Origin" to "*",
            "Access-Control-Allow-Private-Network" to "true",
            "Cache-Control" to "no-cache",
        )
        return WebResourceResponse(
            mime,
            null,
            200,
            "OK",
            headers,
            ByteArrayInputStream(served),
        )
    }

    private fun buildSslContext(): SSLContext {
        val ks = KeyStore.getInstance("PKCS12")
        context.resources.openRawResource(R.raw.narvive_fonts).use { ins ->
            ks.load(ins, KEYSTORE_PASSWORD.toCharArray())
        }
        val kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm())
        kmf.init(ks, KEYSTORE_PASSWORD.toCharArray())
        return SSLContext.getInstance("TLS").apply {
            init(kmf.keyManagers, null, null)
        }
    }

    private fun ensureStarted() {
        if (running) return
        synchronized(lock) {
            if (running) return
            val factory = sslContext.serverSocketFactory
            val socket = factory.createServerSocket(0, 16, InetAddress.getByName("127.0.0.1")) as SSLServerSocket
            serverSocket = socket
            running = true
            thread(isDaemon = true, name = "font-file-server") {
                while (running) {
                    try {
                        val client = socket.accept()
                        thread(isDaemon = true, name = "font-file-conn") {
                            try {
                                handle(client)
                            } catch (e: Exception) {
                                Log.w(TAG, "connection error: ${e.message}")
                            }
                        }
                    } catch (_: Exception) {
                        if (running) break
                    }
                }
            }
        }
    }

    private fun handle(client: Socket) {
        client.use { c ->
            val reader = BufferedReader(InputStreamReader(c.getInputStream(), Charsets.UTF_8))
            val requestLine = reader.readLine() ?: return
            val parts = requestLine.split(" ")
            if (parts.size < 2) return
            val method = parts[0]
            val path = parts[1]
            // 读取并丢弃请求头
            while (true) {
                val line = reader.readLine() ?: break
                if (line.isEmpty()) break
            }
            Log.d(TAG, "req: $method $path")
            if (method == "OPTIONS") {
                // CORS / Private Network Access 预检：https 页面访问 127.0.0.1 私有网络必须响应
                val head = buildString {
                    append("HTTP/1.1 204 No Content\r\n")
                    append("Access-Control-Allow-Origin: *\r\n")
                append("Access-Control-Allow-Private-Network: true\r\n")
                    append("Access-Control-Allow-Methods: GET, HEAD, OPTIONS\r\n")
                    append("Access-Control-Allow-Headers: *\r\n")
                    append("Access-Control-Allow-Private-Network: true\r\n")
                    append("Access-Control-Max-Age: 86400\r\n")
                    append("Connection: close\r\n")
                    append("\r\n")
                }
                val out = c.getOutputStream()
                out.write(head.toByteArray(Charsets.UTF_8))
                out.flush()
                return
            }
            if (method != "GET" && method != "HEAD") {
                respond(c, 405, "text/plain", "Method Not Allowed".toByteArray())
                return
            }
            val name = URLDecoder.decode(path.removePrefix("/fonts/").substringBefore('?'), "UTF-8")
            if (name.isBlank() || name.contains("..") || name.contains('/') || name.contains('\\')) {
                respond(c, 400, "text/plain", "Bad Request".toByteArray())
                return
            }
            Log.d(TAG, "font request: $name")
            val file = File(FontStorage.dir(context), name)
            if (!file.isFile) {
                respond(c, 404, "text/plain", "Not Found".toByteArray())
                return
            }
            val bytes = file.readBytes()
            // Serve-time repair: drop out-of-range STAT AxisValue records (Chromium/OTS rejects the whole font otherwise). Disk file is untouched.
            val served = servedBytes(file, bytes)
            val mime = if (name.endsWith(".otf", ignoreCase = true)) "font/otf" else "font/ttf"
            val head = buildString {
                append("HTTP/1.1 200 OK\r\n")
                append("Content-Type: $mime\r\n")
                append("Content-Length: ${served.size}\r\n")
                append("Accept-Ranges: bytes\r\n")
                append("Access-Control-Allow-Origin: *\r\n")
                append("Access-Control-Allow-Private-Network: true\r\n")
                append("Cache-Control: no-cache\r\n")
                append("Connection: close\r\n")
                append("\r\n")
            }
            val out = c.getOutputStream()
            out.write(head.toByteArray(Charsets.UTF_8))
            if (method != "HEAD") out.write(served)
            out.flush()
        }
    }

    /** Serve-time STAT repair cache: key = absPath|size|mtime; only caches repaired results so pagination requests do not re-parse. */
    private val repairedCache = ConcurrentHashMap<String, ByteArray>()

    private fun servedBytes(file: File, bytes: ByteArray): ByteArray {
        val key = "${file.absolutePath}|${file.length()}|${file.lastModified()}"
        repairedCache[key]?.let { return it }
        val repaired = FontStatRepair.repairIfNeeded(bytes)
        if (repaired !== bytes) {
            repairedCache[key] = repaired
            Log.i(TAG, "STAT repaired in-memory: ${file.name} ${bytes.size} -> ${repaired.size} bytes")
        }
        return repaired
    }

    private fun respond(socket: Socket, code: Int, mime: String, body: ByteArray) {
        val reason = when (code) {
            400 -> "Bad Request"; 404 -> "Not Found"; 405 -> "Method Not Allowed"; else -> "Error"
        }
        val head = "HTTP/1.1 $code $reason\r\nContent-Type: $mime\r\nContent-Length: ${body.size}\r\nConnection: close\r\n\r\n"
        val out = socket.getOutputStream()
        out.write(head.toByteArray(Charsets.UTF_8))
        out.write(body)
        out.flush()
    }
}
