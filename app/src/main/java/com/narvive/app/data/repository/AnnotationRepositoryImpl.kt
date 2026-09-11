package com.narvive.app.data.repository

import com.narvive.app.data.local.dao.AnnotationDao
import com.narvive.app.data.local.entity.AnnotationEntity
import com.narvive.app.domain.model.Annotation
import com.narvive.app.domain.model.AnnotationType
import com.narvive.app.domain.repository.AnnotationRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AnnotationRepositoryImpl @Inject constructor(
    private val dao: AnnotationDao,
) : AnnotationRepository {

    override fun observeByBook(bookId: String): Flow<List<Annotation>> =
        dao.observeByBook(bookId).map { list -> list.map { it.toDomain() } }

    override fun observeAll(): Flow<List<Annotation>> =
        dao.observeAll().map { list -> list.map { it.toDomain() } }

    override fun observeByType(type: AnnotationType): Flow<List<Annotation>> =
        dao.observeByType(type.name).map { list -> list.map { it.toDomain() } }

    override suspend fun getById(id: String): Annotation? =
        dao.getById(id)?.toDomain()

    override suspend fun countByBook(bookId: String): Int =
        dao.countByBook(bookId)

    override suspend fun insertOrUpdate(annotation: Annotation) {
        dao.insert(annotation.toEntity())
    }

    override suspend fun delete(id: String) {
        dao.deleteById(id)
    }

    override suspend fun findTranslation(bookId: String, text: String, targetLang: String): String? =
        dao.findTranslation(bookId, text, targetLang)?.translation

    private fun AnnotationEntity.toDomain() = Annotation(
        id = id, bookId = bookId, locatorJson = locatorJson,
        selectedText = selectedText, type = AnnotationType.valueOf(type),
        color = color, note = note, translation = translation,
        rewrittenText = rewrittenText, rewriteInstruction = rewriteInstruction,
        providerId = providerId, createdAt = createdAt, updatedAt = updatedAt,
        targetLang = targetLang, chapterTitle = chapterTitle, progress = progress,
    )

    private fun Annotation.toEntity() = AnnotationEntity(
        id = id, bookId = bookId, locatorJson = locatorJson,
        selectedText = selectedText, type = type.name,
        color = color, note = note, translation = translation,
        rewrittenText = rewrittenText, rewriteInstruction = rewriteInstruction,
        providerId = providerId, createdAt = createdAt, updatedAt = updatedAt,
        targetLang = targetLang, chapterTitle = chapterTitle, progress = progress,
    )
}
