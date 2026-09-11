package com.narvive.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.narvive.app.data.local.entity.GlobalConversationEntity
import com.narvive.app.data.local.entity.GlobalMessageEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface GlobalAiDao {
    @Query("SELECT * FROM global_ai_conversations ORDER BY pinned DESC, updatedAt DESC")
    fun observeConversations(): Flow<List<GlobalConversationEntity>>

    @Query("SELECT * FROM global_ai_conversations ORDER BY pinned DESC, updatedAt DESC")
    suspend fun getConversations(): List<GlobalConversationEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertConversation(conversation: GlobalConversationEntity)

    @Query("UPDATE global_ai_conversations SET updatedAt = :updatedAt WHERE id = :id")
    suspend fun touchConversation(id: String, updatedAt: Long = System.currentTimeMillis())

    @Query("UPDATE global_ai_conversations SET title = :title WHERE id = :id")
    suspend fun renameConversation(id: String, title: String)

    @Query("UPDATE global_ai_conversations SET pinned = :pinned WHERE id = :id")
    suspend fun setPinned(id: String, pinned: Boolean)

    @Query("DELETE FROM global_ai_conversations WHERE id = :id")
    suspend fun deleteConversation(id: String)

    @Query("SELECT * FROM global_ai_messages WHERE conversationId = :conversationId ORDER BY createdAt ASC")
    fun observeMessages(conversationId: String): Flow<List<GlobalMessageEntity>>

    @Query("SELECT * FROM global_ai_messages WHERE conversationId = :conversationId ORDER BY createdAt ASC")
    suspend fun getMessages(conversationId: String): List<GlobalMessageEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMessage(message: GlobalMessageEntity)
}
