package com.narvive.app.data.local

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.narvive.app.data.local.dao.AnnotationDao
import com.narvive.app.data.local.database.NarviveDatabase
import com.narvive.app.data.local.entity.AnnotationEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class AnnotationDaoTest {

    private lateinit var db: NarviveDatabase
    private lateinit var dao: AnnotationDao

    @Before
    fun setup() {
        db = Room.inMemoryDatabaseBuilder(
            InstrumentationRegistry.getInstrumentation().targetContext,
            NarviveDatabase::class.java,
        ).build()
        dao = db.annotationDao()
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun insertAndQueryByBook() = runTest {
        dao.insert(ann("1", "B1", "亮点", type = "HIGHLIGHT", color = 0xFFFFCC00))
        dao.insert(ann("2", "B1", "笔记", type = "NOTE", note = "我的想法"))
        dao.insert(ann("3", "B2", "其他", type = "HIGHLIGHT", color = 0xFF38BDF8))

        val b1 = dao.observeByBook("B1").first()
        assertEquals(2, b1.size)
    }

    @Test
    fun filterByType() = runTest {
        dao.insert(ann("1", "B1", "译", type = "TRANSLATION", translation = "hello"))
        dao.insert(ann("2", "B1", "改", type = "REWRITE", rewrittenText = "abc"))
        dao.insert(ann("3", "B1", "亮", type = "HIGHLIGHT", color = 0xFFFFCC00))

        val highlights = dao.observeByType("HIGHLIGHT").first()
        assertEquals(1, highlights.size)
        assertEquals("亮", highlights[0].selectedText)
    }

    @Test
    fun countByBook() = runTest {
        dao.insert(ann("1", "B1", "a", type = "HIGHLIGHT", color = 0xFFFFCC00))
        dao.insert(ann("2", "B1", "b", type = "NOTE", note = "X"))
        assertEquals(2, dao.countByBook("B1"))
    }

    @Test
    fun deleteById() = runTest {
        dao.insert(ann("1", "B1", "a", type = "HIGHLIGHT", color = 0xFFFFCC00))
        dao.deleteById("1")
        assertEquals(0, dao.countByBook("B1"))
    }

    @Test
    fun translationDedup() = runTest {
        val t = ann("1", "B1", "attention", type = "TRANSLATION", translation = "注意力")
        dao.insert(t)
        val found = dao.findTranslation("B1", "attention")
        assertEquals("注意力", found?.translation)

        val missing = dao.findTranslation("B1", "unknown_word")
        assertEquals(null, missing)
    }

    @Test
    fun updateAnnotation() = runTest {
        val a = ann("1", "B1", "v1", type = "HIGHLIGHT", color = 0xFFFFCC00)
        dao.insert(a)
        dao.insert(a.copy(color = 0xFF38BDF8, updatedAt = System.currentTimeMillis()))
        val result = dao.getById("1")
        assertEquals(0xFF38BDF8.toLong(), result!!.color)
    }

    private fun ann(
        id: String, bookId: String, text: String,
        type: String, color: Long? = null, note: String? = null,
        translation: String? = null, rewrittenText: String? = null,
    ) = AnnotationEntity(
        id = id, bookId = bookId,
        locatorJson = """{"href":"test"}""",
        selectedText = text, type = type,
        color = color, note = note,
        translation = translation, rewrittenText = rewrittenText,
        rewriteInstruction = null, providerId = null,
    )
}
