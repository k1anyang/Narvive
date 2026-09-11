package com.narvive.app.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "annotations",
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
        Index(value = ["bookId", "type"]),       // 复合索引：按书+类型筛选/翻译查重
        Index(value = ["bookId", "selectedText", "targetLang"]), // 翻译查重精准索引
    ],
)
data class AnnotationEntity(
    @PrimaryKey
    val id: String,                       // UUID
    val bookId: String,
    val locatorJson: String,              // Readium Locator JSON
    val selectedText: String,
    val type: String,                     // HIGHLIGHT | NOTE | TRANSLATION | REWRITE
    val color: Long?,                     // ARGB color (for highlights)
    val note: String?,                    // user note text
    val translation: String?,             // translated version
    val rewrittenText: String?,           // AI rewritten version
    val rewriteInstruction: String?,      // e.g. "更文学化"
    val providerId: String?,              // which AI provider was used
    val targetLang: String = "zh",        // translation target language (dedupe key for TRANSLATION)
    val chapterTitle: String = "",         // 保存时章名（展示用）
    val progress: Float? = null,           // 保存时全书进度 0..1（展示用）
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
)
