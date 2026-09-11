package com.narvive.app.data.repository

import com.narvive.app.data.local.dao.ReadingDao
import com.narvive.app.data.local.entity.ChapterSummaryCacheEntity
import com.narvive.app.data.local.entity.ReadingSessionEntity
import com.narvive.app.domain.model.ReadingSession
import com.narvive.app.domain.repository.ReadingRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ReadingRepositoryImpl @Inject constructor(
    private val dao: ReadingDao,
) : ReadingRepository {

    override suspend fun startSession(bookId: String): String {
        val id = UUID.randomUUID().toString()
        dao.insertSession(
            ReadingSessionEntity(
                id = id,
                bookId = bookId,
                startAt = System.currentTimeMillis(),
                endAt = null,
                durationMs = null,
                pagesRead = 0,
            )
        )
        return id
    }

    override suspend fun endSession(sessionId: String, pagesRead: Int) {
        val endAt = System.currentTimeMillis()
        // 会话时长 = 结束时间 - 开始时间（开始时间在插入时已记录）
        val startAt = dao.getSessionStartAt(sessionId) ?: return
        val duration = (endAt - startAt).coerceAtLeast(0L)
        dao.finishSession(sessionId, endAt, duration, pagesRead.coerceAtLeast(0))
    }

    override fun observeSessionsSince(since: Long): Flow<List<ReadingSession>> =
        dao.observeSince(since).map { list -> list.map { it.toDomain() } }

    override fun observeSessionsByBook(bookId: String): Flow<List<ReadingSession>> =
        dao.observeByBook(bookId).map { list -> list.map { it.toDomain() } }

    override suspend fun cacheSummary(bookId: String, chapterHref: String, summary: String, providerId: String?) {
        dao.insertSummary(
            ChapterSummaryCacheEntity(
                id = "$bookId:$chapterHref",
                bookId = bookId,
                chapterHref = chapterHref,
                summary = summary,
                providerId = providerId,
            )
        )
    }

    override suspend fun getCachedSummary(bookId: String, chapterHref: String): String? =
        dao.getSummary(bookId, chapterHref)?.summary

    private fun ReadingSessionEntity.toDomain() = ReadingSession(
        id = id, bookId = bookId, startAt = startAt,
        endAt = endAt, durationMs = durationMs, pagesRead = pagesRead,
    )
}
