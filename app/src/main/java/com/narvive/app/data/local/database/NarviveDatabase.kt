package com.narvive.app.data.local.database

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.narvive.app.data.local.converter.NarviveConverters
import com.narvive.app.data.local.dao.AiDao
import com.narvive.app.data.local.dao.AnnotationDao
import com.narvive.app.data.local.dao.BookDao
import com.narvive.app.data.local.dao.BookmarkDao
import com.narvive.app.data.local.dao.CollectionDao
import com.narvive.app.data.local.dao.GlobalAiDao
import com.narvive.app.data.local.dao.ReadingDao
import com.narvive.app.data.local.dao.RoleplayDao
import com.narvive.app.data.local.entity.AiConversationEntity
import com.narvive.app.data.local.entity.AiMessageEntity
import com.narvive.app.data.local.entity.AnnotationEntity
import com.narvive.app.data.local.entity.BookCollectionCrossRef
import com.narvive.app.data.local.entity.BookEntity
import com.narvive.app.data.local.entity.BookmarkEntity
import com.narvive.app.data.local.entity.ChapterSummaryCacheEntity
import com.narvive.app.data.local.entity.CollectionEntity
import com.narvive.app.data.local.entity.GlobalConversationEntity
import com.narvive.app.data.local.entity.GlobalMessageEntity
import com.narvive.app.data.local.entity.ReadingSessionEntity
import com.narvive.app.data.local.entity.RoleplayMessageEntity
import com.narvive.app.data.local.entity.RoleplaySessionEntity

@Database(
    entities = [
        BookEntity::class,
        CollectionEntity::class,
        BookCollectionCrossRef::class,
        BookmarkEntity::class,
        AnnotationEntity::class,
        AiConversationEntity::class,
        AiMessageEntity::class,
        RoleplaySessionEntity::class,
        RoleplayMessageEntity::class,
        ReadingSessionEntity::class,
        ChapterSummaryCacheEntity::class,
        GlobalConversationEntity::class,
        GlobalMessageEntity::class,
    ],
    version = 7,
    exportSchema = false,
)
@TypeConverters(NarviveConverters::class)
abstract class NarviveDatabase : RoomDatabase() {
    abstract fun bookDao(): BookDao
    abstract fun annotationDao(): AnnotationDao
    abstract fun bookmarkDao(): BookmarkDao
    abstract fun collectionDao(): CollectionDao
    abstract fun aiDao(): AiDao
    abstract fun roleplayDao(): RoleplayDao
    abstract fun readingDao(): ReadingDao
    abstract fun globalAiDao(): GlobalAiDao

    companion object {
        /** v1→v2：annotations 增加 targetLang（翻译查重键补目标语言，历史数据视为 zh） */
        val MIGRATION_1_2 = object : androidx.room.migration.Migration(1, 2) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE annotations ADD COLUMN targetLang TEXT NOT NULL DEFAULT 'zh'")
            }
        }

        /** v2→v3：annotations 复合索引（bookId+type 筛选 / bookId+selectedText+targetLang 翻译查重） */
        val MIGRATION_2_3 = object : androidx.room.migration.Migration(2, 3) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                db.execSQL("CREATE INDEX IF NOT EXISTS index_annotations_bookId_type ON annotations(bookId, type)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_annotations_bookId_selectedText_targetLang ON annotations(bookId, selectedText, targetLang)")
            }
        }
        /** v3→v4：bookmarks 增加 progress 列（书签位置的全局进度百分比） */
        val MIGRATION_3_4 = object : androidx.room.migration.Migration(3, 4) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE bookmarks ADD COLUMN progress REAL DEFAULT NULL")
            }
        }
        /** v4→v5：底部 AI tab 全局会话表 */
        val MIGRATION_4_5 = object : androidx.room.migration.Migration(4, 5) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS global_ai_conversations (" +
                        "id TEXT NOT NULL PRIMARY KEY, title TEXT NOT NULL, " +
                        "createdAt INTEGER NOT NULL, updatedAt INTEGER NOT NULL)"
                )
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS global_ai_messages (" +
                        "id TEXT NOT NULL PRIMARY KEY, conversationId TEXT NOT NULL, " +
                        "role TEXT NOT NULL, content TEXT NOT NULL, createdAt INTEGER NOT NULL, " +
                        "FOREIGN KEY(conversationId) REFERENCES global_ai_conversations(id) " +
                        "ON UPDATE NO ACTION ON DELETE CASCADE)"
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS index_global_ai_messages_conversationId ON global_ai_messages(conversationId)")
            }
        }
        /** v5→v6：roleplay_sessions 增加 lastReadChapterTitle（快照章节名，直接显示章名用） */
        val MIGRATION_5_6 = object : androidx.room.migration.Migration(5, 6) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE roleplay_sessions ADD COLUMN lastReadChapterTitle TEXT NOT NULL DEFAULT ''")
            }
        }
        /** v6→v7：global_ai_conversations 增加 pinned（会话置顶） */
        val MIGRATION_6_7 = object : androidx.room.migration.Migration(6, 7) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE global_ai_conversations ADD COLUMN pinned INTEGER NOT NULL DEFAULT 0")
            }
        }
    }
}
