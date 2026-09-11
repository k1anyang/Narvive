package com.narvive.app.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "reading_sessions",
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
data class ReadingSessionEntity(
    @PrimaryKey
    val id: String,                       // UUID
    val bookId: String,
    val startAt: Long,
    val endAt: Long?,
    val durationMs: Long?,
    val pagesRead: Int = 0,
)
