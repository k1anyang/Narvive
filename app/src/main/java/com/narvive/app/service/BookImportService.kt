package com.narvive.app.service

import android.content.Context
import android.net.Uri
import android.webkit.MimeTypeMap
import com.narvive.app.domain.model.Book
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jsoup.Jsoup
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest
import java.util.UUID
import java.util.zip.ZipFile
import javax.inject.Inject
import javax.inject.Singleton
import com.narvive.app.R

@Singleton
class BookImportService @Inject constructor(
    @ApplicationContext private val context: Context,
    private val bookDao: com.narvive.app.data.local.dao.BookDao,
) {
    val booksDir: File by lazy {
        File(context.filesDir, "books").also { it.mkdirs() }
    }
    val coversDir: File by lazy {
        File(context.filesDir, "covers").also { it.mkdirs() }
    }

    private fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(8192)
            var bytes: Int
            while (input.read(buffer).also { bytes = it } != -1) {
                digest.update(buffer, 0, bytes)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    /** 导入确认弹窗用：读取文件名/大小/格式/hash 并检测重复，任何异常均转为 error 而非抛出 */
    suspend fun inspectFile(uri: Uri): InspectResult = withContext(Dispatchers.IO) {
        try {
            var fileName = uri.lastPathSegment ?: "unknown"
            var size = 0L
            context.contentResolver.query(uri, arrayOf(
                android.provider.OpenableColumns.DISPLAY_NAME,
                android.provider.OpenableColumns.SIZE,
            ), null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val nameIdx = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                    val sizeIdx = cursor.getColumnIndex(android.provider.OpenableColumns.SIZE)
                    if (nameIdx >= 0) cursor.getString(nameIdx)?.let { fileName = it }
                    if (sizeIdx >= 0 && !cursor.isNull(sizeIdx)) size = cursor.getLong(sizeIdx)
                }
            }
            val mime = context.contentResolver.getType(uri) ?: ""
            val format = detectFormat(fileName, mime)
            if (format == null) {
                return@withContext InspectResult(
                    fileName = fileName, size = size, format = null,
                    duplicate = false, error = context.getString(R.string.import_err_unsupported_format),
                )
            }
            val hash = digestOfStream(context.contentResolver.openInputStream(uri))
            val duplicate = hash != null && bookDao.getByHash(hash) != null
            InspectResult(
                fileName = fileName, size = size, format = format,
                duplicate = duplicate,
                error = if (hash == null) context.getString(R.string.import_err_read_failed) else null,
            )
        } catch (e: Exception) {
            InspectResult(
                fileName = uri.lastPathSegment ?: "unknown", size = 0, format = null,
                duplicate = false, error = e.message ?: context.getString(R.string.import_err_read_failed),
            )
        }
    }

    /** 流式计算 hash（不落盘，供导入预览去重用） */
    private fun digestOfStream(input: java.io.InputStream?): String? {
        if (input == null) return null
        return try {
            val digest = MessageDigest.getInstance("SHA-256")
            input.use { stream ->
                val buffer = ByteArray(8192)
                var bytes: Int
                while (stream.read(buffer).also { bytes = it } != -1) {
                    digest.update(buffer, 0, bytes)
                }
            }
            digest.digest().joinToString("") { "%02x".format(it) }
        } catch (_: Exception) {
            null
        }
    }

    /** 由文件名 + MIME 判定格式；无法判定返回 null */
    fun detectFormat(fileName: String, mime: String): String? = when {
        fileName.endsWith(".epub", ignoreCase = true) -> "EPUB"
        fileName.endsWith(".pdf", ignoreCase = true) -> "PDF"
        fileName.endsWith(".txt", ignoreCase = true) -> "TXT"
        fileName.endsWith(".text", ignoreCase = true) -> "TXT"
        MimeTypeMap.getSingleton().getExtensionFromMimeType(mime) in setOf("txt", "text") -> "TXT"
        else -> null
    }

    suspend fun importBook(uri: Uri): Result<Book> = withContext(Dispatchers.IO) {
        try {
            // 从 ContentResolver 获取真实文件名（SAF documentFile.name 可能为 null）
            var fileName = uri.lastPathSegment ?: "unknown"
            context.contentResolver.query(uri, arrayOf(android.provider.OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val idx = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                    if (idx >= 0) cursor.getString(idx)?.let { fileName = it }
                }
            }

            val format = detectFormat(fileName, context.contentResolver.getType(uri) ?: "")
                ?: return@withContext Result.failure(
                    Exception(context.getString(R.string.import_err_unsupported_format_ext, fileName.substringAfterLast('.'))),
                )
            val bookId = UUID.randomUUID().toString()
            val destFile = File(booksDir, "$bookId.${format.lowercase()}")

            try {
                // Copy
                context.contentResolver.openInputStream(uri)?.use { input ->
                    FileOutputStream(destFile).use { output -> input.copyTo(output) }
                } ?: return@withContext Result.failure(Exception(context.getString(R.string.import_err_read_failed)))

                val fileHash = sha256(destFile)

                // 按文件 hash 去重：已存在则撤销拷贝并提示
                if (bookDao.getByHash(fileHash) != null) {
                    destFile.delete()
                    return@withContext Result.failure(DuplicateBookException(context.getString(R.string.import_err_duplicate, fileName)))
                }

                val (title, author, coverPath, description) = when (format) {
                    "EPUB" -> extractEpub(destFile, bookId, fileName)
                    else -> Quad(fileNameWithoutExt(fileName), null, null, null)
                }

                return@withContext Result.success(
                    Book(
                        id = bookId, title = title, author = author,
                        coverPath = coverPath, filePath = destFile.absolutePath,
                        format = format, fileHash = fileHash,
                        description = description, totalPages = null, currentChapter = null,
                        currentLocator = null, progress = 0f,
                        readingTheme = "paper", fontSize = 17,
                        lineHeight = 1.4f, readingMode = "paged",
                        importedAt = System.currentTimeMillis(),
                        lastReadAt = 0L, // 未读过的书不参与「最近阅读」排序
                        isFinished = false,
                    )
                )
            } catch (e: Exception) {
                destFile.delete() // 失败清理半成品文件
                throw e
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun extractEpub(file: File, bookId: String, fallbackName: String): Quad<String, String?, String?, String?> {
        var title = fileNameWithoutExt(fallbackName)
        var author: String? = null
        var coverPath: String? = null
        var description: String? = null
        try {
            ZipFile(file).use { zip ->
                val opfEntry = zip.entries().asSequence().find { it.name.endsWith(".opf") } ?: return@use
                val opf = Jsoup.parse(zip.getInputStream(opfEntry).bufferedReader().readText())
                val opfDir = opfEntry.name.substringBeforeLast("/", "")

                // Title
                opf.select("dc|title, title").firstOrNull()?.text()?.takeIf { it.isNotBlank() }?.let { title = it }
                // Author
                opf.select("dc|creator, creator").firstOrNull()?.text()?.takeIf { it.isNotBlank() }?.let { author = it }
                // Description
                opf.select("dc|description, description").firstOrNull()?.text()?.takeIf { it.isNotBlank() }?.let { description = it }
                // Cover（解码后压缩为 WebP，节省空间）
                val coverId = opf.select("meta[name=cover]").attr("content")
                    .ifEmpty { opf.select("item[properties~=cover-image]").attr("id") }
                if (coverId.isNotEmpty()) {
                    val href = opf.select("item[id=$coverId]").attr("href")
                    if (href.isNotEmpty()) {
                        val entryName = if (opfDir.isEmpty()) href else "$opfDir/$href"
                        zip.entries().asSequence().find {
                            it.name.equals(entryName, ignoreCase = true) || it.name.endsWith(href, ignoreCase = true)
                        }?.let { coverEntry ->
                            val coverFile = File(coversDir, "$bookId.webp")
                            try {
                                // 解码原图 → WebP 80% 质量（兼顾体积与清晰度）
                                val bitmap = android.graphics.BitmapFactory.decodeStream(zip.getInputStream(coverEntry))
                                if (bitmap != null) {
                                    FileOutputStream(coverFile).use { out ->
                                        bitmap.compress(android.graphics.Bitmap.CompressFormat.WEBP, 80, out)
                                    }
                                    bitmap.recycle()
                                    coverPath = coverFile.absolutePath
                                }
                            } catch (_: Exception) {
                                // WebP 压缩失败 → 保留原始格式兜底
                                val ext = coverEntry.name.substringAfterLast(".", "jpg")
                                val fallback = File(coversDir, "$bookId.$ext")
                                zip.getInputStream(coverEntry).use { i -> FileOutputStream(fallback).use { o -> i.copyTo(o) } }
                                coverPath = fallback.absolutePath
                            }
                        }
                    }
                }
            }
        } catch (_: Exception) { /* fallback to filename */ }
        return Quad(title, author, coverPath, description)
    }

    suspend fun deleteBookFiles(book: Book) = withContext(Dispatchers.IO) {
        try { File(book.filePath).delete() } catch (_: Exception) {}
        book.coverPath?.let { try { File(it).delete() } catch (_: Exception) {} }
    }

    private fun fileNameWithoutExt(name: String): String =
        name.substringBeforeLast(".")
}

/** 重复导入（同 hash 书籍已存在）—— UI 据此给出明确提示 */
class DuplicateBookException(message: String) : Exception(message)

/** 导入预览结果（文件名/大小/格式/重复/错误） */
data class InspectResult(
    val fileName: String,
    val size: Long,
    val format: String?,
    val duplicate: Boolean,
    val error: String?,
)

private data class Quad<A, B, C, D>(val a: A, val b: B, val c: C, val d: D)
