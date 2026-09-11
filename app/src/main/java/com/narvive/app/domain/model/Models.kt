package com.narvive.app.domain.model

data class Book(
    val id: String,
    val title: String,
    val author: String?,
    val coverPath: String?,
    val filePath: String,
    val format: String,
    val fileHash: String?,
    val description: String?,
    val totalPages: Int?,
    val currentChapter: String?,
    val currentLocator: String?,
    val progress: Float,
    val readingTheme: String,
    val fontSize: Int,
    val lineHeight: Float,
    val readingMode: String,
    val importedAt: Long,
    val lastReadAt: Long,
    val isFinished: Boolean,
)

data class Annotation(
    val id: String,
    val bookId: String,
    val bookTitle: String = "",
    val locatorJson: String,
    val selectedText: String,
    val type: AnnotationType,
    val color: Long?,
    val note: String?,
    val translation: String?,
    val rewrittenText: String?,
    val rewriteInstruction: String?,
    val providerId: String?,
    val createdAt: Long,
    val updatedAt: Long,
    /** 翻译目标语言（TRANSLATION 类型查重键用；历史数据默认 zh=中文） */
    val targetLang: String = "zh",
    /** 保存时的章名（笔记/高亮/翻译展示用；历史数据为空） */
    val chapterTitle: String = "",
    /** 保存时的全书进度 0..1（展示用；历史数据为空） */
    val progress: Float? = null,
)

enum class AnnotationType {
    HIGHLIGHT,
    NOTE,
    TRANSLATION,
    AI_ANSWER,
    ROLEPLAY,
    REWRITE,
}

data class Bookmark(
    val id: String,
    val bookId: String,
    val locatorJson: String,
    val chapterTitle: String?,
    val previewText: String?,
    val progress: Float? = null,
    val createdAt: Long,
)

data class Collection(
    val id: String,
    val name: String,
    val bookCount: Int = 0,
    val createdAt: Long,
)

data class ReadingSession(
    val id: String,
    val bookId: String,
    val startAt: Long,
    val endAt: Long?,
    val durationMs: Long?,
    val pagesRead: Int,
)

data class CharacterCard(
    val name: String,
    val identity: String,
    val personality: String,
    val tone: String,
    val knowledgeBoundary: String,
)

/** 角色扮演会话（一本书可多会话，按角色分） */
data class RoleplaySession(
    val id: String,
    val bookId: String,
    val characterName: String,
    val characterCard: CharacterCard,
    val lastReadChapter: Int,
    /** 快照章节名（如「第23章 xxx」，抽取/刷新时写入，读新章不变） */
    val lastReadChapterTitle: String = "",
    val createdAt: Long,
    val updatedAt: Long,
)

/** 角色扮演消息（role: USER | CHARACTER） */
data class RoleplayMessage(
    val id: String,
    val sessionId: String,
    val role: String,
    val content: String,
    val savedAsAnnotationId: String?,
    val createdAt: Long,
)
