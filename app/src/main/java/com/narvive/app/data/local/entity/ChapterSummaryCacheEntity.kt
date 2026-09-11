package com.narvive.app.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "chapter_summary_cache",
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
data class ChapterSummaryCacheEntity(
    @PrimaryKey
    val id: String,                       // auto: "bookId:chapterHref"
    val bookId: String,
    val chapterHref: String,
    val summary: String,
    val providerId: String?,              // which AI provider generated it
    val createdAt: Long = System.currentTimeMillis(),
)
