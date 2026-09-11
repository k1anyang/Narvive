package com.narvive.app.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "ai_messages",
    foreignKeys = [
        ForeignKey(
            entity = AiConversationEntity::class,
            parentColumns = ["id"],
            childColumns = ["conversationId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index("conversationId"),
    ],
)
data class AiMessageEntity(
    @PrimaryKey
    val id: String,                       // UUID
    val conversationId: String,
    val role: String,                     // USER | ASSISTANT
    val content: String,
    val contextType: String?,             // SELECTION | PAGE | CHAPTER | BOOK
    val contextSnapshot: String?,          // JSON of the context sent
    val savedAsAnnotationId: String?,      // if saved to notes
    val createdAt: Long = System.currentTimeMillis(),
)
