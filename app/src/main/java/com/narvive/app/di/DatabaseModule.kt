package com.narvive.app.di

import android.content.Context
import androidx.room.Room
import com.narvive.app.data.local.database.NarviveDatabase
import com.narvive.app.data.local.dao.AiDao
import com.narvive.app.data.local.dao.AnnotationDao
import com.narvive.app.data.local.dao.BookDao
import com.narvive.app.data.local.dao.BookmarkDao
import com.narvive.app.data.local.dao.CollectionDao
import com.narvive.app.data.local.dao.GlobalAiDao
import com.narvive.app.data.local.dao.ReadingDao
import com.narvive.app.data.local.dao.RoleplayDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): NarviveDatabase =
        Room.databaseBuilder(context, NarviveDatabase::class.java, "narvive.db")
            .addMigrations(
                NarviveDatabase.MIGRATION_1_2,
                NarviveDatabase.MIGRATION_2_3,
                NarviveDatabase.MIGRATION_3_4,
                NarviveDatabase.MIGRATION_4_5,
                NarviveDatabase.MIGRATION_5_6,
                NarviveDatabase.MIGRATION_6_7,
            )
            .fallbackToDestructiveMigration()
            .build()

    @Provides
    fun provideBookDao(db: NarviveDatabase): BookDao = db.bookDao()

    @Provides
    fun provideAnnotationDao(db: NarviveDatabase): AnnotationDao = db.annotationDao()

    @Provides
    fun provideBookmarkDao(db: NarviveDatabase): BookmarkDao = db.bookmarkDao()

    @Provides
    fun provideCollectionDao(db: NarviveDatabase): CollectionDao = db.collectionDao()

    @Provides
    fun provideAiDao(db: NarviveDatabase): AiDao = db.aiDao()

    @Provides
    fun provideRoleplayDao(db: NarviveDatabase): RoleplayDao = db.roleplayDao()

    @Provides
    fun provideReadingDao(db: NarviveDatabase): ReadingDao = db.readingDao()

    @Provides
    fun provideGlobalAiDao(db: NarviveDatabase): GlobalAiDao = db.globalAiDao()
}
