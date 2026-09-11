package com.narvive.app.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "roleplay_messages",
    foreignKeys = [
        ForeignKey(
            entity = RoleplaySessionEntity::class,
            parentColumns = ["id"],
            childColumns = ["sessionId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index("sessionId"),
    ],
)
data class RoleplayMessageEntity(
    @PrimaryKey
    val id: String,                       // UUID
    val sessionId: String,
    val role: String,                     // USER | CHARACTER
    val content: String,
    val savedAsAnnotationId: String?,      // if saved to notes
    val createdAt: Long = System.currentTimeMillis(),
)
