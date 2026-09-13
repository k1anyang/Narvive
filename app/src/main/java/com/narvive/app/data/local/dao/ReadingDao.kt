package com.narvive.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.narvive.app.data.local.entity.ChapterSummaryCacheEntity
import com.narvive.app.data.local.entity.ReadingSessionEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ReadingDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSession(session: ReadingSessionEntity)

    @Query("UPDATE reading_sessions SET endAt = :endAt, durationMs = :durationMs, pagesRead = :pagesRead WHERE id = :id")
    suspend fun finishSession(id: String, endAt: Long, durationMs: Long, pagesRead: Int)

    @Query("SELECT * FROM reading_sessions WHERE bookId = :bookId ORDER BY startAt DESC")
    fun observeByBook(bookId: String): Flow<List<ReadingSessionEntity>>

    @Query("SELECT * FROM reading_sessions WHERE startAt >= :since ORDER BY startAt DESC")
    fun observeSince(since: Long): Flow<List<ReadingSessionEntity>>

    @Query("SELECT * FROM reading_sessions WHERE endAt IS NOT NULL")
    fun observeCompleted(): Flow<List<ReadingSessionEntity>>

    @Query("SELECT startAt FROM reading_sessions WHERE id = :id")
    suspend fun getSessionStartAt(id: String): Long?

    // chapter summary cache
    @Query("SELECT * FROM chapter_summary_cache WHERE bookId = :bookId AND chapterHref = :chapterHref LIMIT 1")
    suspend fun getSummary(bookId: String, chapterHref: String): ChapterSummaryCacheEntity?

    /** 全书检索用：取本书已缓存的全部章节摘要（按写入顺序，即阅读/提问顺序） */
    @Query("SELECT * FROM chapter_summary_cache WHERE bookId = :bookId ORDER BY createdAt ASC")
    suspend fun getSummaries(bookId: String): List<ChapterSummaryCacheEntity>

    @Query("SELECT COUNT(*) FROM chapter_summary_cache WHERE bookId = :bookId")
    suspend fun countSummaries(bookId: String): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSummary(summary: ChapterSummaryCacheEntity)
}
