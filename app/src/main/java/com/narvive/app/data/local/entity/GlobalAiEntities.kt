package com.narvive.app.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/** 底部 AI tab 的全局通用对话（不绑定书籍） */
@Entity(tableName = "global_ai_conversations")
data class GlobalConversationEntity(
    @PrimaryKey
    val id: String,
    val title: String,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    /** 置顶会话（置顶优先于更新时间排序） */
    val pinned: Boolean = false,
)

@Entity(
    tableName = "global_ai_messages",
    foreignKeys = [
        ForeignKey(
            entity = GlobalConversationEntity::class,
            parentColumns = ["id"],
            childColumns = ["conversationId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index("conversationId"),
    ],
)
data class GlobalMessageEntity(
    @PrimaryKey
    val id: String,
    val conversationId: String,
    val role: String,             // USER | ASSISTANT
    val content: String,
    val createdAt: Long = System.currentTimeMillis(),
)
