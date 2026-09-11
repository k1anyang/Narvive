package com.narvive.app.data.repository

import com.narvive.app.data.local.dao.BookmarkDao
import com.narvive.app.data.local.entity.BookmarkEntity
import com.narvive.app.domain.model.Bookmark
import com.narvive.app.domain.repository.BookmarkRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BookmarkRepositoryImpl @Inject constructor(
    private val dao: BookmarkDao,
) : BookmarkRepository {

    override fun observeByBook(bookId: String): Flow<List<Bookmark>> =
        dao.observeByBook(bookId).map { list -> list.map { it.toDomain() } }

    override suspend fun insert(bookmark: Bookmark) {
        dao.insert(bookmark.toEntity())
    }

    override suspend fun delete(id: String) {
        dao.deleteById(id)
    }

    private fun BookmarkEntity.toDomain() = Bookmark(
        id = id, bookId = bookId, locatorJson = locatorJson,
        chapterTitle = chapterTitle, previewText = previewText,
        progress = progress, createdAt = createdAt,
    )

    private fun Bookmark.toEntity() = BookmarkEntity(
        id = id, bookId = bookId, locatorJson = locatorJson,
        chapterTitle = chapterTitle, previewText = previewText,
        progress = progress, createdAt = createdAt,
    )
}
