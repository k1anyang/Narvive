package com.narvive.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.narvive.app.data.local.entity.CollectionEntity
import com.narvive.app.data.local.entity.BookCollectionCrossRef
import kotlinx.coroutines.flow.Flow

@Dao
interface CollectionDao {
    @Query("SELECT * FROM collections ORDER BY name ASC")
    fun observeAll(): Flow<List<CollectionEntity>>

    @Query("SELECT * FROM collections")
    suspend fun getAll(): List<CollectionEntity>

    @Query("SELECT * FROM book_collection_cross_ref")
    suspend fun getAllCrossRefs(): List<BookCollectionCrossRef>

    @Query("SELECT * FROM book_collection_cross_ref")
    fun observeCrossRefs(): Flow<List<BookCollectionCrossRef>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(collection: CollectionEntity)

    @Query("UPDATE collections SET name = :name WHERE id = :id")
    suspend fun rename(id: String, name: String)

    @Query("DELETE FROM collections WHERE id = :id")
    suspend fun deleteById(id: String)

    // cross ref
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun addBookToCollection(crossRef: BookCollectionCrossRef)

    @Query("DELETE FROM book_collection_cross_ref WHERE bookId = :bookId AND collectionId = :collectionId")
    suspend fun removeBookFromCollection(bookId: String, collectionId: String)

    @Query("SELECT collectionId AS id, COUNT(*) AS cnt FROM book_collection_cross_ref GROUP BY collectionId")
    suspend fun collectionCounts(): List<CollectionCount>

    @Query("SELECT bookId FROM book_collection_cross_ref WHERE collectionId = :collectionId")
    suspend fun bookIdsOf(collectionId: String): List<String>
}

data class CollectionCount(val id: String, val cnt: Int)
