package com.narvive.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.narvive.app.data.local.entity.AiConversationEntity
import com.narvive.app.data.local.entity.AiMessageEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface AiDao {
    // Conversations
    @Query("SELECT * FROM ai_conversations WHERE bookId = :bookId ORDER BY updatedAt DESC")
    fun observeByBook(bookId: String): Flow<List<AiConversationEntity>>

    /** 一次性读取（历史会话列表/恢复最近会话用） */
    @Query("SELECT * FROM ai_conversations WHERE bookId = :bookId ORDER BY updatedAt DESC")
    suspend fun getByBook(bookId: String): List<AiConversationEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertConversation(conversation: AiConversationEntity)

    @Query("UPDATE ai_conversations SET updatedAt = :updatedAt WHERE id = :id")
    suspend fun touchConversation(id: String, updatedAt: Long = System.currentTimeMillis())

    @Query("DELETE FROM ai_conversations WHERE id = :id")
    suspend fun deleteConversation(id: String)

    // Messages
    @Query("SELECT * FROM ai_messages WHERE conversationId = :conversationId ORDER BY createdAt ASC")
    fun observeMessages(conversationId: String): Flow<List<AiMessageEntity>>

    /** 一次性读取（恢复会话历史用） */
    @Query("SELECT * FROM ai_messages WHERE conversationId = :conversationId ORDER BY createdAt ASC")
    suspend fun getMessages(conversationId: String): List<AiMessageEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMessage(message: AiMessageEntity)

    @Query("DELETE FROM ai_messages WHERE id = :id")
    suspend fun deleteMessage(id: String)
}
