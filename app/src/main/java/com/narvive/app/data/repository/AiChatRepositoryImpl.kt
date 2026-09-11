package com.narvive.app.data.repository

import com.narvive.app.data.local.dao.AiDao
import com.narvive.app.data.local.dao.RoleplayDao
import com.narvive.app.data.local.entity.AiConversationEntity
import com.narvive.app.data.local.entity.AiMessageEntity
import com.narvive.app.data.local.entity.RoleplayMessageEntity
import com.narvive.app.data.local.entity.RoleplaySessionEntity
import com.narvive.app.domain.model.CharacterCard
import com.narvive.app.domain.model.RoleplayMessage
import com.narvive.app.domain.model.RoleplaySession
import com.narvive.app.domain.repository.AiChatMessageInfo
import com.narvive.app.domain.repository.AiChatRepository
import com.narvive.app.domain.repository.AiConversationInfo
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import org.json.JSONObject
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AiChatRepositoryImpl @Inject constructor(
    private val aiDao: AiDao,
    private val roleplayDao: RoleplayDao,
) : AiChatRepository {

    // ---------- AI 对话（围绕本书） ----------

    override suspend fun createConversation(bookId: String, title: String): String {
        val id = UUID.randomUUID().toString()
        aiDao.insertConversation(AiConversationEntity(id = id, bookId = bookId, title = title))
        return id
    }

    override suspend fun touchConversation(id: String) = aiDao.touchConversation(id)

    override suspend fun addAiMessage(conversationId: String, role: String, content: String, contextType: String?, contextSnapshot: String?) {
        aiDao.insertMessage(
            AiMessageEntity(
                id = UUID.randomUUID().toString(),
                conversationId = conversationId,
                role = role,
                content = content,
                contextType = contextType,
                contextSnapshot = contextSnapshot,
                savedAsAnnotationId = null,
            )
        )
    }

    override suspend fun getConversations(bookId: String): List<AiConversationInfo> =
        aiDao.getByBook(bookId).map { AiConversationInfo(it.id, it.title, it.updatedAt) }

    override suspend fun getAiMessages(conversationId: String): List<AiChatMessageInfo> =
        aiDao.getMessages(conversationId).map {
            AiChatMessageInfo(it.role.lowercase(), it.content, it.contextType, it.contextSnapshot)
        }

    override suspend fun deleteConversation(id: String) = aiDao.deleteConversation(id)

    // ---------- 角色扮演 ----------

    override fun observeRoleplaySessions(bookId: String): Flow<List<RoleplaySession>> =
        roleplayDao.observeByBook(bookId).map { list -> list.map { it.toDomain() } }

    override suspend fun getRoleplaySession(id: String): RoleplaySession? =
        roleplayDao.getById(id)?.toDomain()

    override suspend fun saveRoleplaySession(session: RoleplaySession) {
        roleplayDao.insertSession(
            RoleplaySessionEntity(
                id = session.id,
                bookId = session.bookId,
                characterName = session.characterName,
                characterCardJson = session.characterCard.toJson(),
                lastReadChapter = session.lastReadChapter,
                lastReadChapterTitle = session.lastReadChapterTitle,
                createdAt = session.createdAt,
                updatedAt = session.updatedAt,
            )
        )
    }

    override suspend fun touchRoleplaySession(id: String) = roleplayDao.touchSession(id)

    override suspend fun deleteRoleplaySession(id: String) = roleplayDao.deleteSession(id)

    override suspend fun getRoleplayMessages(sessionId: String): List<RoleplayMessage> =
        roleplayDao.observeMessages(sessionId).first().map { it.toDomain() }

    override suspend fun addRoleplayMessage(message: RoleplayMessage) {
        roleplayDao.insertMessage(
            RoleplayMessageEntity(
                id = message.id,
                sessionId = message.sessionId,
                role = message.role,
                content = message.content,
                savedAsAnnotationId = message.savedAsAnnotationId,
                createdAt = message.createdAt,
            )
        )
    }

    // ---------- 映射 ----------

    private fun RoleplaySessionEntity.toDomain() = RoleplaySession(
        id = id,
        bookId = bookId,
        characterName = characterName,
        characterCard = characterCardFromJson(characterCardJson, characterName),
        lastReadChapter = lastReadChapter,
        lastReadChapterTitle = lastReadChapterTitle,
        createdAt = createdAt,
        updatedAt = updatedAt,
    )

    private fun RoleplayMessageEntity.toDomain() = RoleplayMessage(
        id = id,
        sessionId = sessionId,
        role = role,
        content = content,
        savedAsAnnotationId = savedAsAnnotationId,
        createdAt = createdAt,
    )

    companion object {
        fun CharacterCard.toJson(): String = JSONObject().apply {
            put("name", name)
            put("identity", identity)
            put("personality", personality)
            put("tone", tone)
            put("knowledgeBoundary", knowledgeBoundary)
        }.toString()

        fun characterCardFromJson(json: String, fallbackName: String): CharacterCard = runCatching {
            val o = JSONObject(json)
            CharacterCard(
                name = o.optString("name").ifBlank { fallbackName },
                identity = o.optString("identity"),
                personality = o.optString("personality"),
                tone = o.optString("tone"),
                knowledgeBoundary = o.optString("knowledgeBoundary"),
            )
        }.getOrElse {
            CharacterCard(fallbackName, "", "", "", "")
        }
    }
}
