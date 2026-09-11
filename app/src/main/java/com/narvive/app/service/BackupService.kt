package com.narvive.app.service

import android.content.Context
import android.net.Uri
import com.narvive.app.R
import com.narvive.app.data.datastore.NarviveDataStore
import com.narvive.app.data.local.dao.AnnotationDao
import com.narvive.app.data.local.dao.BookDao
import com.narvive.app.data.local.dao.BookmarkDao
import com.narvive.app.data.local.dao.CollectionDao
import com.narvive.app.data.local.entity.AnnotationEntity
import com.narvive.app.data.local.entity.BookCollectionCrossRef
import com.narvive.app.data.local.entity.BookEntity
import com.narvive.app.data.local.entity.BookmarkEntity
import com.narvive.app.data.local.entity.CollectionEntity
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 备份与恢复 — NarviveBackup-yyyyMMdd.zip
 * 结构：manifest.json + data.json + books/（原始书籍文件）+ covers/（封面）
 * 安全约定：API Key 绝不入包。
 */
@Singleton
class BackupService @Inject constructor(
    @ApplicationContext private val context: Context,
    private val bookDao: BookDao,
    private val annotationDao: AnnotationDao,
    private val bookmarkDao: BookmarkDao,
    private val collectionDao: CollectionDao,
    private val dataStore: NarviveDataStore,
) {
    private val json = Json { prettyPrint = true; ignoreUnknownKeys = true }

    @Serializable
    data class Manifest(val version: Int, val date: String, val app: String = "narvive")

    @Serializable
    data class BackupBook(
        val id: String, val title: String, val author: String?, val format: String,
        val fileHash: String?, val totalPages: Int?, val currentChapter: String?,
        val currentLocator: String?, val progress: Float, val readingTheme: String,
        val fontSize: Int, val lineHeight: Float, val readingMode: String,
        val importedAt: Long, val lastReadAt: Long, val isFinished: Boolean,
        val bookFile: String, val coverFile: String?,
    )

    @Serializable
    data class BackupAnnotation(
        val id: String, val bookId: String, val locatorJson: String, val selectedText: String,
        val type: String, val color: Long?, val note: String?, val translation: String?,
        val rewrittenText: String?, val rewriteInstruction: String?, val providerId: String?,
        val createdAt: Long, val updatedAt: Long,
    )

    @Serializable
    data class BackupBookmark(
        val id: String, val bookId: String, val locatorJson: String,
        val chapterTitle: String?, val previewText: String?, val createdAt: Long,
    )

    @Serializable
    data class BackupCollection(val id: String, val name: String, val createdAt: Long)

    @Serializable
    data class BackupCrossRef(val bookId: String, val collectionId: String)

    /** DataStore 偏好快照（H6：备份补入设置项，API Key 遵循不入包安全约定） */
    @Serializable
    data class BackupSettings(
        val libraryViewMode: String = "grid",
        val librarySortOrder: String = "LAST_READ",
        val defaultMargin: Int = 24,
        val defaultFontFamily: String = "source_serif",
        val defaultAlignment: String = "justify",
        val defaultParagraphSpacing: Float = 1.0f,
    )

    @Serializable
    data class BackupData(
        val books: List<BackupBook>,
        val annotations: List<BackupAnnotation>,
        val bookmarks: List<BackupBookmark>,
        val collections: List<BackupCollection>,
        val crossRefs: List<BackupCrossRef>,
        val settings: BackupSettings = BackupSettings(),
    )

    // ── 导出（写入指定 URI，供 SAF CreateDocument 使用）──

    suspend fun exportBackupTo(uri: Uri): Result<String> = withContext(Dispatchers.IO) {
        try {
            val dateStr = SimpleDateFormat("yyyyMMdd", Locale.getDefault()).format(Date())
            val books = bookDao.getAll()
            val annotations = annotationDao.getAll()
            val bookmarks = bookmarkDao.getAll()
            val collections = collectionDao.getAll()
            val crossRefs = collectionDao.getAllCrossRefs()

            context.contentResolver.openOutputStream(uri)?.use { stream ->
                ZipOutputStream(stream.buffered()).use { zos ->
                    // manifest.json
                    zos.putNextEntry(ZipEntry("manifest.json"))
                    zos.write(json.encodeToString(Manifest.serializer(), Manifest(BACKUP_VERSION, dateStr)).toByteArray())
                    zos.closeEntry()

                    // data.json
                    val data = BackupData(
                        books = books.map { b ->
                            BackupBook(
                                id = b.id, title = b.title, author = b.author, format = b.format,
                                fileHash = b.fileHash, totalPages = b.totalPages,
                                currentChapter = b.currentChapter, currentLocator = b.currentLocator,
                                progress = b.progress, readingTheme = b.readingTheme,
                                fontSize = b.fontSize, lineHeight = b.lineHeight, readingMode = b.readingMode,
                                importedAt = b.importedAt, lastReadAt = b.lastReadAt, isFinished = b.isFinished,
                                bookFile = "books/${b.id}${extOf(b.filePath)}",
                                coverFile = b.coverPath?.let { "covers/${b.id}${extOf(it)}" },
                            )
                        },
                        annotations = annotations.map {
                            BackupAnnotation(
                                it.id, it.bookId, it.locatorJson, it.selectedText, it.type,
                                it.color, it.note, it.translation, it.rewrittenText,
                                it.rewriteInstruction, it.providerId, it.createdAt, it.updatedAt,
                            )
                        },
                        bookmarks = bookmarks.map {
                            BackupBookmark(it.id, it.bookId, it.locatorJson, it.chapterTitle, it.previewText, it.createdAt)
                        },
                        collections = collections.map { BackupCollection(it.id, it.name, it.createdAt) },
                        crossRefs = crossRefs.map { BackupCrossRef(it.bookId, it.collectionId) },
                        settings = BackupSettings(
                            libraryViewMode = dataStore.libraryViewMode.first(),
                            librarySortOrder = dataStore.librarySortOrder.first(),
                            defaultMargin = dataStore.defaultMargin.first(),
                            defaultFontFamily = dataStore.defaultFontFamily.first(),
                            defaultAlignment = dataStore.defaultAlignment.first(),
                            defaultParagraphSpacing = dataStore.defaultParagraphSpacing.first(),
                        ),
                    )
                    zos.putNextEntry(ZipEntry("data.json"))
                    zos.write(json.encodeToString(BackupData.serializer(), data).toByteArray())
                    zos.closeEntry()

                    // 书籍文件与封面
                    books.forEach { b ->
                        runCatching {
                            val f = File(b.filePath)
                            if (f.exists()) {
                                zos.putNextEntry(ZipEntry("books/${b.id}${extOf(b.filePath)}"))
                                f.inputStream().use { it.copyTo(zos) }
                                zos.closeEntry()
                            }
                        }
                        b.coverPath?.let { cover ->
                            runCatching {
                                val f = File(cover)
                                if (f.exists()) {
                                    zos.putNextEntry(ZipEntry("covers/${b.id}${extOf(cover)}"))
                                    f.inputStream().use { it.copyTo(zos) }
                                    zos.closeEntry()
                                }
                            }
                        }
                    }
                }
            } ?: return@withContext Result.failure(Exception(context.getString(R.string.backup_service_create_failed)))

            dataStore.setLastBackupTime(System.currentTimeMillis())
            val fileName = "NarviveBackup-$dateStr.zip"
            Result.success(fileName)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // ── 导入 ──

    /**
     * 恢复备份：校验 manifest 版本 → 解包 → 按 fileHash 去重合并。
     * 冲突规则：同 hash 跳过；同书名不同 hash 保留两者、书名加「（导入）」后缀。
     * 返回实际导入的书籍数量。
     */
    suspend fun importBackup(uri: Uri): Result<Int> = withContext(Dispatchers.IO) {
        val input = context.contentResolver.openInputStream(uri)
            ?: return@withContext Result.failure(Exception(context.getString(R.string.backup_service_read_failed)))
        restoreFromStream(input)
    }

    /** 从本地文件恢复（WebDAV 下载后调用） */
    suspend fun importBackupFromFile(file: File): Result<Int> = withContext(Dispatchers.IO) {
        if (!file.isFile) return@withContext Result.failure(Exception(context.getString(R.string.backup_service_file_missing)))
        restoreFromStream(file.inputStream().buffered())
    }

    private suspend fun restoreFromStream(input: InputStream): Result<Int> = withContext(Dispatchers.IO) {
        val stagingDir = File(context.cacheDir, "restore_${UUID.randomUUID()}")
        try {
            stagingDir.mkdirs()
            var manifestRaw: String? = null
            var dataRaw: String? = null

            // 第一遍：JSON 读入内存，文件落暂存区
            ZipInputStream(input.buffered()).use { zis ->
                var entry = zis.nextEntry
                while (entry != null) {
                    when {
                        entry.name == "manifest.json" -> manifestRaw = zis.readBytes().toString(Charsets.UTF_8)
                        entry.name == "data.json" -> dataRaw = zis.readBytes().toString(Charsets.UTF_8)
                        entry.name.startsWith("books/") || entry.name.startsWith("covers/") -> {
                            val out = File(stagingDir, entry.name.replace("/", "_"))
                            FileOutputStream(out).use { zis.copyTo(it) }
                        }
                    }
                    zis.closeEntry()
                    entry = zis.nextEntry
                }
            }

            // 校验 manifest 版本
            val manifest = manifestRaw?.let {
                runCatching { json.decodeFromString(Manifest.serializer(), it) }.getOrNull()
            } ?: return@withContext Result.failure(Exception(context.getString(R.string.backup_service_missing_manifest)))
            if (manifest.version > BACKUP_VERSION) {
                return@withContext Result.failure(Exception(context.getString(R.string.backup_service_version_too_new, manifest.version, BACKUP_VERSION)))
            }

            val data = dataRaw?.let {
                runCatching { json.decodeFromString(BackupData.serializer(), it) }.getOrNull()
            } ?: return@withContext Result.failure(Exception(context.getString(R.string.backup_service_data_parse_failed)))

            // 合并：id 重映射（备份 id → 新 id），避免与现有数据主键冲突
            val bookIdMap = mutableMapOf<String, String>()
            val collectionIdMap = mutableMapOf<String, String>()
            var importedCount = 0

            val existingCollections = collectionDao.getAll()
            data.collections.forEach { c ->
                val existing = existingCollections.firstOrNull { it.name == c.name }
                if (existing != null) {
                    collectionIdMap[c.id] = existing.id
                } else {
                    val newId = UUID.randomUUID().toString()
                    collectionDao.insert(CollectionEntity(id = newId, name = c.name, createdAt = c.createdAt))
                    collectionIdMap[c.id] = newId
                }
            }

            val booksDir = File(context.filesDir, "books").also { it.mkdirs() }
            val coversDir = File(context.filesDir, "covers").also { it.mkdirs() }

            data.books.forEach { b ->
                // 同 hash → 已存在，跳过（但仍记录 id 映射到现有书，保证标注归属正确）
                val dup = b.fileHash?.let { bookDao.getByHash(it) }
                if (dup != null) {
                    bookIdMap[b.id] = dup.id
                    return@forEach
                }

                val newId = UUID.randomUUID().toString()
                bookIdMap[b.id] = newId

                // 书籍文件
                val stagedBook = File(stagingDir, b.bookFile.replace("/", "_"))
                val bookExt = extOf(b.bookFile)
                val destBook = File(booksDir, "$newId$bookExt")
                if (stagedBook.exists()) stagedBook.renameTo(destBook)

                // 封面
                var coverPath: String? = null
                b.coverFile?.let { cf ->
                    val stagedCover = File(stagingDir, cf.replace("/", "_"))
                    if (stagedCover.exists()) {
                        val destCover = File(coversDir, "$newId${extOf(cf)}")
                        stagedCover.renameTo(destCover)
                        coverPath = destCover.absolutePath
                    }
                }

                // 同名不同 hash → 保留两者，书名加后缀
                val titleConflict = bookDao.getByTitle(b.title).isNotEmpty()
                val title = if (titleConflict) "${b.title}（导入）" else b.title

                bookDao.insert(
                    BookEntity(
                        id = newId, title = title, author = b.author,
                        coverPath = coverPath, filePath = destBook.absolutePath,
                        format = b.format, fileHash = b.fileHash,
                        totalPages = b.totalPages, currentChapter = b.currentChapter,
                        currentLocator = b.currentLocator, progress = b.progress.coerceIn(0f, 1f),
                        readingTheme = b.readingTheme, fontSize = b.fontSize,
                        lineHeight = b.lineHeight, readingMode = b.readingMode,
                        importedAt = b.importedAt, lastReadAt = b.lastReadAt,
                        isFinished = b.isFinished,
                    )
                )
                importedCount++
            }

            // 标注/书签：重映射 bookId + 新主键；跳过 bookId 无法映射（备份不完整时防御）
            data.annotations.forEach { a ->
                val mappedBook = bookIdMap[a.bookId] ?: return@forEach
                annotationDao.insert(
                    AnnotationEntity(
                        id = UUID.randomUUID().toString(), bookId = mappedBook,
                        locatorJson = a.locatorJson, selectedText = a.selectedText, type = a.type,
                        color = a.color, note = a.note, translation = a.translation,
                        rewrittenText = a.rewrittenText, rewriteInstruction = a.rewriteInstruction,
                        providerId = a.providerId, createdAt = a.createdAt, updatedAt = a.updatedAt,
                    )
                )
            }
            data.bookmarks.forEach { bm ->
                val mappedBook = bookIdMap[bm.bookId] ?: return@forEach
                bookmarkDao.insert(
                    BookmarkEntity(
                        id = UUID.randomUUID().toString(), bookId = mappedBook,
                        locatorJson = bm.locatorJson, chapterTitle = bm.chapterTitle,
                        previewText = bm.previewText, createdAt = bm.createdAt,
                    )
                )
            }
            data.crossRefs.forEach { ref ->
                val mappedBook = bookIdMap[ref.bookId]
                val mappedCollection = collectionIdMap[ref.collectionId]
                if (mappedBook != null && mappedCollection != null) {
                    collectionDao.addBookToCollection(BookCollectionCrossRef(mappedBook, mappedCollection))
                }
            }

            // H6：恢复 DataStore 设置（不含 API Key，符合安全约定）
            data.settings.let { s ->
                dataStore.setLibraryViewMode(s.libraryViewMode)
                dataStore.setLibrarySortOrder(s.librarySortOrder)
                dataStore.setDefaultMargin(s.defaultMargin)
                dataStore.setDefaultFontFamily(s.defaultFontFamily)
                dataStore.setDefaultAlignment(s.defaultAlignment)
                dataStore.setDefaultParagraphSpacing(s.defaultParagraphSpacing)
            }

            Result.success(importedCount)
        } catch (e: Exception) {
            Result.failure(e)
        } finally {
            stagingDir.deleteRecursively()
        }
    }

    private fun extOf(path: String): String {
        val ext = path.substringAfterLast('.', "")
        return if (ext.isEmpty() || ext.length > 5) "" else ".$ext"
    }

    companion object {
        const val BACKUP_VERSION = 1
    }
}
