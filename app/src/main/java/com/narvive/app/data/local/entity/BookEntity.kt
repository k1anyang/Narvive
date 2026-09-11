package com.narvive.app.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "books")
data class BookEntity(
    @PrimaryKey
    val id: String,                       // UUID
    val title: String,
    val author: String?,
    val coverPath: String?,               // local file path
    val filePath: String,                 // local file path
    val format: String,                   // EPUB | PDF | TXT
    val fileHash: String?,                // SHA-256 for dedup
    val description: String? = null,      // 简介（EPUB dc:description）
    val totalPages: Int?,
    val currentChapter: String?,           // current chapter title
    val currentLocator: String?,           // Readium Locator JSON
    val progress: Float = 0f,            // 0.0–1.0
    val readingTheme: String = "paper",  // paper|sepia|green|dark|black|custom
    val fontSize: Int = 17,              // sp
    val lineHeight: Float = 1.4f,
    val readingMode: String = "paged",    // paged|scroll
    val importedAt: Long = System.currentTimeMillis(),
    val lastReadAt: Long = 0L,              // 0=未读过（不参与「最近阅读」排序）
    val isFinished: Boolean = false,
)
