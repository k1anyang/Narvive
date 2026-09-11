package com.narvive.app.di

import com.narvive.app.data.repository.AiChatRepositoryImpl
import com.narvive.app.data.repository.AnnotationRepositoryImpl
import com.narvive.app.data.repository.BookmarkRepositoryImpl
import com.narvive.app.data.repository.BookshelfRepositoryImpl
import com.narvive.app.data.repository.ReadingRepositoryImpl
import com.narvive.app.domain.repository.AiChatRepository
import com.narvive.app.domain.repository.AnnotationRepository
import com.narvive.app.domain.repository.BookmarkRepository
import com.narvive.app.domain.repository.BookshelfRepository
import com.narvive.app.domain.repository.ReadingRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {

    @Binds
    @Singleton
    abstract fun bindBookshelfRepo(impl: BookshelfRepositoryImpl): BookshelfRepository

    @Binds
    @Singleton
    abstract fun bindAnnotationRepo(impl: AnnotationRepositoryImpl): AnnotationRepository

    @Binds
    @Singleton
    abstract fun bindBookmarkRepo(impl: BookmarkRepositoryImpl): BookmarkRepository

    @Binds
    @Singleton
    abstract fun bindReadingRepo(impl: ReadingRepositoryImpl): ReadingRepository

    @Binds
    @Singleton
    abstract fun bindAiChatRepo(impl: AiChatRepositoryImpl): AiChatRepository
}
