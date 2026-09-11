package com.narvive.app.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.narvive.app.data.local.entity.AnnotationEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface AnnotationDao {
    @Query("SELECT * FROM annotations WHERE bookId = :bookId ORDER BY createdAt DESC")
    fun observeByBook(bookId: String): Flow<List<AnnotationEntity>>

    @Query("SELECT * FROM annotations ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<AnnotationEntity>>

    @Query("SELECT * FROM annotations")
    suspend fun getAll(): List<AnnotationEntity>

    @Query("SELECT * FROM annotations WHERE type = :type ORDER BY createdAt DESC")
    fun observeByType(type: String): Flow<List<AnnotationEntity>>

    @Query("SELECT * FROM annotations WHERE bookId = :bookId AND type = :type ORDER BY createdAt DESC")
    fun observeByBookAndType(bookId: String, type: String): Flow<List<AnnotationEntity>>

    @Query("SELECT * FROM annotations WHERE id = :id")
    suspend fun getById(id: String): AnnotationEntity?

    @Query("SELECT COUNT(*) FROM annotations WHERE bookId = :bookId")
    suspend fun countByBook(bookId: String): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(annotation: AnnotationEntity)

    @Delete
    suspend fun delete(annotation: AnnotationEntity)

    @Query("DELETE FROM annotations WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("SELECT * FROM annotations WHERE selectedText = :text AND bookId = :bookId AND type = 'TRANSLATION' AND targetLang = :targetLang LIMIT 1")
    suspend fun findTranslation(bookId: String, text: String, targetLang: String = "zh"): AnnotationEntity?
}
