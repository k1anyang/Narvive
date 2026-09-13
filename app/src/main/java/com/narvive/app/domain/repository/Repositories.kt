package com.narvive.app.domain.repository

import com.narvive.app.domain.model.Annotation
import com.narvive.app.domain.model.AnnotationType
import com.narvive.app.domain.model.Book
import com.narvive.app.domain.model.Bookmark
import com.narvive.app.domain.model.Collection
import com.narvive.app.domain.model.ReadingSession
import com.narvive.app.domain.model.RoleplayMessage
import com.narvive.app.domain.model.RoleplaySession
import kotlinx.coroutines.flow.Flow

interface BookshelfRepository {
    fun observeAllBooks(): Flow<List<Book>>
    fun observeBook(id: String): Flow<Book?>
    suspend fun getBook(id: String): Book?
    suspend fun getBookByHash(hash: String): Book?
    suspend fun insertOrUpdate(book: Book)
    suspend fun deleteBook(id: String)
    suspend fun updateProgress(id: String, progress: Float, locatorJson: String, chapterTitle: String?)
    suspend fun updateReadingSettings(id: String, fontSize: Int, lineHeight: Float, mode: String)
    suspend fun updateBookMeta(id: String, title: String, author: String?, description: String?)

    fun observeCollections(): Flow<List<Collection>>
    fun observeCollectionMembership(): Flow<Map<String, Set<String>>> // bookId → collectionIds
    suspend fun createCollection(name: String)
    suspend fun renameCollection(id: String, name: String)
    suspend fun deleteCollection(id: String)
    suspend fun addBookToCollection(bookId: String, collectionId: String)
    suspend fun removeBookFromCollection(bookId: String, collectionId: String)
    fun observeBooksByCollection(collectionId: String): Flow<List<Book>>
}

interface AnnotationRepository {
    fun observeByBook(bookId: String): Flow<List<Annotation>>
    fun observeAll(): Flow<List<Annotation>>
    fun observeByType(type: AnnotationType): Flow<List<Annotation>>
    suspend fun getById(id: String): Annotation?
    suspend fun countByBook(bookId: String): Int
    suspend fun insertOrUpdate(annotation: Annotation)
    suspend fun delete(id: String)
    suspend fun findTranslation(bookId: String, text: String, targetLang: String = "zh"): String?
}

interface BookmarkRepository {
    fun observeByBook(bookId: String): Flow<List<Bookmark>>
    suspend fun insert(bookmark: Bookmark)
    suspend fun delete(id: String)
}

interface ReadingRepository {
    /** 开始一条阅读会话，返回会话 id（用于结束时回写） */
    suspend fun startSession(bookId: String): String
    suspend fun endSession(sessionId: String, pagesRead: Int)
    fun observeSessionsSince(since: Long): Flow<List<ReadingSession>>
    fun observeSessionsByBook(bookId: String): Flow<List<ReadingSession>>
    suspend fun cacheSummary(bookId: String, chapterHref: String, summary: String, providerId: String?)
    suspend fun getCachedSummary(bookId: String, chapterHref: String): String?

    /**
     * 本书已缓存的章节摘要，按写入顺序返回。
     *
     * 全书问答的检索索引就是它——摘要是「用户实际问过的章节」增量积累起来的，
     * 不做预生成，因此不会为了一本书一次性打出几百次模型调用。
     */
    suspend fun getCachedSummaries(bookId: String): List<CachedChapterSummary>

    suspend fun cachedSummaryCount(bookId: String): Int
}

/** 章节摘要缓存条目（chapterHref 对 TXT 是 locator JSON，对 EPUB 是 spine href） */
data class CachedChapterSummary(
    val chapterHref: String,
    val summary: String,
)

/** AI 会话存储：围绕本书对话（ai_conversations/ai_messages）+ 角色扮演（roleplay_sessions/roleplay_messages） */
interface AiChatRepository {
    /** 创建 AI 对话会话，返回 id */
    suspend fun createConversation(bookId: String, title: String): String
    suspend fun touchConversation(id: String)
    suspend fun addAiMessage(conversationId: String, role: String, content: String, contextType: String?, contextSnapshot: String?)

    /** 本书全部会话（按最近更新倒序），历史会话列表用 */
    suspend fun getConversations(bookId: String): List<AiConversationInfo>
    /** 某会话的全部消息（按时间正序），恢复历史用 */
    suspend fun getAiMessages(conversationId: String): List<AiChatMessageInfo>
    suspend fun deleteConversation(id: String)

    fun observeRoleplaySessions(bookId: String): Flow<List<RoleplaySession>>
    suspend fun getRoleplaySession(id: String): RoleplaySession?
    suspend fun saveRoleplaySession(session: RoleplaySession)
    suspend fun touchRoleplaySession(id: String)
    suspend fun deleteRoleplaySession(id: String)
    suspend fun getRoleplayMessages(sessionId: String): List<RoleplayMessage>
    suspend fun addRoleplayMessage(message: RoleplayMessage)
}

/** AI 会话摘要（历史会话列表条目） */
data class AiConversationInfo(val id: String, val title: String, val updatedAt: Long)

/** AI 会话消息（恢复历史用；role 为小写 "user"/"assistant"，与 service.ai.AiMessage 对齐） */
data class AiChatMessageInfo(val role: String, val content: String, val contextType: String?, val contextSnapshot: String?)
