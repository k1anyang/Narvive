package com.narvive.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.narvive.app.data.local.entity.RoleplayMessageEntity
import com.narvive.app.data.local.entity.RoleplaySessionEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface RoleplayDao {
    @Query("SELECT * FROM roleplay_sessions WHERE bookId = :bookId ORDER BY updatedAt DESC")
    fun observeByBook(bookId: String): Flow<List<RoleplaySessionEntity>>

    @Query("SELECT * FROM roleplay_sessions WHERE id = :id")
    suspend fun getById(id: String): RoleplaySessionEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSession(session: RoleplaySessionEntity)

    @Query("UPDATE roleplay_sessions SET updatedAt = :updatedAt WHERE id = :id")
    suspend fun touchSession(id: String, updatedAt: Long = System.currentTimeMillis())

    @Query("DELETE FROM roleplay_sessions WHERE id = :id")
    suspend fun deleteSession(id: String)

    @Query("SELECT * FROM roleplay_messages WHERE sessionId = :sessionId ORDER BY createdAt ASC")
    fun observeMessages(sessionId: String): Flow<List<RoleplayMessageEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMessage(message: RoleplayMessageEntity)
}
