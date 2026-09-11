package com.narvive.app.data.repository

import com.narvive.app.data.local.dao.BookDao
import com.narvive.app.data.local.dao.CollectionDao
import com.narvive.app.data.local.entity.BookCollectionCrossRef
import com.narvive.app.data.local.entity.BookEntity
import com.narvive.app.data.local.entity.CollectionEntity
import com.narvive.app.domain.model.Book
import com.narvive.app.domain.model.Collection
import com.narvive.app.domain.repository.BookshelfRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BookshelfRepositoryImpl @Inject constructor(
    private val bookDao: BookDao,
    private val collectionDao: CollectionDao,
) : BookshelfRepository {

    override fun observeAllBooks(): Flow<List<Book>> =
        bookDao.observeAll().map { list -> list.map { it.toDomain() } }

    override fun observeBook(id: String): Flow<Book?> =
        bookDao.observeById(id).map { it?.toDomain() }

    override suspend fun getBook(id: String): Book? =
        bookDao.getById(id)?.toDomain()

    override suspend fun getBookByHash(hash: String): Book? =
        bookDao.getByHash(hash)?.toDomain()

    override suspend fun insertOrUpdate(book: Book) {
        bookDao.insert(book.toEntity())
    }

    override suspend fun deleteBook(id: String) {
        bookDao.deleteById(id)
    }

    override suspend fun updateProgress(id: String, progress: Float, locatorJson: String, chapterTitle: String?) {
        val entity = bookDao.getById(id) ?: return
        bookDao.update(
            entity.copy(
                progress = progress,
                currentLocator = locatorJson,
                currentChapter = chapterTitle,
                lastReadAt = System.currentTimeMillis(),
            )
        )
    }

    override suspend fun updateReadingSettings(id: String, fontSize: Int, lineHeight: Float, mode: String) {
        val entity = bookDao.getById(id) ?: return
        bookDao.update(
            entity.copy(
                fontSize = fontSize,
                lineHeight = lineHeight,
                readingMode = mode,
            )
        )
    }

    override suspend fun updateBookMeta(id: String, title: String, author: String?, description: String?) {
        val entity = bookDao.getById(id) ?: return
        bookDao.update(entity.copy(title = title, author = author, description = description))
    }

    override fun observeCollections(): Flow<List<Collection>> =
        collectionDao.observeAll().map { list ->
            val counts = collectionDao.collectionCounts().associate { it.id to it.cnt }
            list.map { Collection(it.id, it.name, counts[it.id] ?: 0, it.createdAt) }
        }

    override fun observeCollectionMembership(): Flow<Map<String, Set<String>>> =
        collectionDao.observeCrossRefs().map { refs ->
            refs.groupBy { it.bookId }.mapValues { (_, v) -> v.map { it.collectionId }.toSet() }
        }

    override suspend fun createCollection(name: String) {
        collectionDao.insert(CollectionEntity(id = UUID.randomUUID().toString(), name = name))
    }

    override suspend fun renameCollection(id: String, name: String) {
        collectionDao.rename(id, name)
    }

    override suspend fun deleteCollection(id: String) {
        collectionDao.deleteById(id)
    }

    override suspend fun addBookToCollection(bookId: String, collectionId: String) {
        collectionDao.addBookToCollection(BookCollectionCrossRef(bookId, collectionId))
    }

    override suspend fun removeBookFromCollection(bookId: String, collectionId: String) {
        collectionDao.removeBookFromCollection(bookId, collectionId)
    }

    override fun observeBooksByCollection(collectionId: String): Flow<List<Book>> =
        bookDao.observeByCollection(collectionId).map { list -> list.map { it.toDomain() } }

    private fun BookEntity.toDomain() = Book(
        id = id, title = title, author = author, coverPath = coverPath,
        filePath = filePath, format = format, fileHash = fileHash,
        description = description, totalPages = totalPages, currentChapter = currentChapter,
        currentLocator = currentLocator, progress = progress,
        readingTheme = readingTheme, fontSize = fontSize,
        lineHeight = lineHeight, readingMode = readingMode,
        importedAt = importedAt, lastReadAt = lastReadAt,
        isFinished = isFinished,
    )

    private fun Book.toEntity() = BookEntity(
        id = id, title = title, author = author, coverPath = coverPath,
        filePath = filePath, format = format, fileHash = fileHash,
        description = description, totalPages = totalPages, currentChapter = currentChapter,
        currentLocator = currentLocator, progress = progress,
        readingTheme = readingTheme, fontSize = fontSize,
        lineHeight = lineHeight, readingMode = readingMode,
        importedAt = importedAt, lastReadAt = lastReadAt,
        isFinished = isFinished,
    )
}
