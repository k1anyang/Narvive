package com.narvive.app.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "roleplay_sessions",
    foreignKeys = [
        ForeignKey(
            entity = BookEntity::class,
            parentColumns = ["id"],
            childColumns = ["bookId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index("bookId"),
    ],
)
data class RoleplaySessionEntity(
    @PrimaryKey
    val id: String,                       // UUID
    val bookId: String,
    val characterName: String,
    val characterCardJson: String,        // JSON: {name, identity, personality, tone, knowledgeBoundary}
    val lastReadChapter: Int = 0,
    /** 快照章节名（如「第23章 xxx」，抽取/刷新时写入，读新章不变） */
    val lastReadChapterTitle: String = "",
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
)
