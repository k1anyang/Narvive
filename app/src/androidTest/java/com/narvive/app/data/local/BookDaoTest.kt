package com.narvive.app.data.local

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.narvive.app.data.local.dao.BookDao
import com.narvive.app.data.local.database.NarviveDatabase
import com.narvive.app.data.local.entity.BookEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BookDaoTest {

    private lateinit var db: NarviveDatabase
    private lateinit var dao: BookDao

    @Before
    fun setup() {
        db = Room.inMemoryDatabaseBuilder(
            InstrumentationRegistry.getInstrumentation().targetContext,
            NarviveDatabase::class.java,
        ).build()
        dao = db.bookDao()
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun insertAndGet() = runTest {
        val book = BookEntity(
            id = "1", title = "深度工作", author = "Cal Newport",
            filePath = "/test/deep_work.epub", format = "EPUB",
            coverPath = null, fileHash = "abc123",
            totalPages = 300, currentChapter = "第三章",
            currentLocator = null, progress = 0.62f,
            readingTheme = "sepia", fontSize = 17, lineHeight = 1.6f,
            readingMode = "paged",
        )
        dao.insert(book)
        val result = dao.getById("1")
        assertNotNull(result)
        assertEquals("深度工作", result!!.title)
        assertEquals(0.62f, result.progress)
    }

    @Test
    fun observeAllSorted() = runTest {
        val b1 = book("1", "A", System.currentTimeMillis())
        val b2 = book("2", "B", System.currentTimeMillis() + 1000)
        dao.insert(b2)
        dao.insert(b1)
        val list = dao.observeAll().first()
        assertEquals(2, list.size)
        assertEquals("B", list[0].title) // most recent first
    }

    @Test
    fun dedupByHash() = runTest {
        val b1 = book("1", "A", fileHash = "h1")
        dao.insert(b1)
        val found = dao.getByHash("h1")
        assertNotNull(found)
        val notFound = dao.getByHash("h2")
        assertNull(notFound)
    }

    @Test
    fun updateProgress() = runTest {
        val b = book("1", "深度工作")
        dao.insert(b)
        dao.update(b.copy(progress = 0.8f, currentLocator = """{"href":"ch3"}""", currentChapter = "第四章"))
        val result = dao.getById("1")
        assertEquals(0.8f, result!!.progress)
        assertEquals("第四章", result.currentChapter)
    }

    @Test
    fun deleteBook() = runTest {
        dao.insert(book("1", "深度工作"))
        dao.deleteById("1")
        assertNull(dao.getById("1"))
    }

    @Test
    fun observeContinuingReading() = runTest {
        dao.insert(book("1", "未读", progress = 0f))
        dao.insert(book("2", "在读", progress = 0.5f))
        dao.insert(book("3", "读完", progress = 1f, isFinished = true))
        val list = dao.observeContinuingReading().first()
        assertEquals(1, list.size)
        assertEquals("在读", list[0].title)
    }

    private fun book(
        id: String,
        title: String,
        lastReadAt: Long = System.currentTimeMillis(),
        progress: Float = 0.3f,
        isFinished: Boolean = false,
        fileHash: String? = "hash-$id",
    ) = BookEntity(
        id = id, title = title, author = "Test",
        filePath = "/test/$id.epub", format = "EPUB",
        coverPath = null, fileHash = fileHash,
        totalPages = null, currentChapter = null,
        currentLocator = null, progress = progress,
        readingTheme = "sepia", fontSize = 17, lineHeight = 1.6f,
        readingMode = "paged", lastReadAt = lastReadAt,
        isFinished = isFinished,
    )
}
