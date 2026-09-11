package com.narvive.app.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.narvive.app.data.local.entity.BookEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface BookDao {
    @Query("SELECT * FROM books ORDER BY lastReadAt DESC")
    fun observeAll(): Flow<List<BookEntity>>

    @Query("SELECT * FROM books")
    suspend fun getAll(): List<BookEntity>

    @Query("SELECT * FROM books WHERE title = :title")
    suspend fun getByTitle(title: String): List<BookEntity>

    @Query("SELECT * FROM books WHERE id = :id")
    suspend fun getById(id: String): BookEntity?

    @Query("SELECT * FROM books WHERE id = :id LIMIT 1")
    fun observeById(id: String): Flow<BookEntity?>

    @Query("SELECT * FROM books WHERE fileHash = :hash LIMIT 1")
    suspend fun getByHash(hash: String): BookEntity?

    @Query("SELECT * FROM books WHERE id IN (SELECT bookId FROM book_collection_cross_ref WHERE collectionId = :collectionId)")
    fun observeByCollection(collectionId: String): Flow<List<BookEntity>>

    @Query("SELECT * FROM books WHERE isFinished = 0 AND progress > 0 ORDER BY lastReadAt DESC LIMIT :limit")
    fun observeContinuingReading(limit: Int = 5): Flow<List<BookEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(book: BookEntity)

    @Update
    suspend fun update(book: BookEntity)

    @Delete
    suspend fun delete(book: BookEntity)

    @Query("DELETE FROM books WHERE id = :id")
    suspend fun deleteById(id: String)
}
