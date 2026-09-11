package com.narvive.app.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "bookmarks",
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
data class BookmarkEntity(
    @PrimaryKey
    val id: String,                       // UUID
    val bookId: String,
    val locatorJson: String,              // Readium Locator JSON
    val chapterTitle: String?,
    val previewText: String?,             // first ~30 chars of surrounding text
    val progress: Float? = null,          // 书签位置的全局进度百分比 (0.0 ~ 1.0)
    val createdAt: Long = System.currentTimeMillis(),
)
