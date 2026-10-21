package vn.esstar.ainovel.core.database.di

import android.content.Context
import androidx.room.Room
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton
import vn.esstar.ainovel.core.database.AINovelDatabase

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): AINovelDatabase =
        Room.databaseBuilder(context, AINovelDatabase::class.java, AINovelDatabase.NAME)
            .fallbackToDestructiveMigrationOnDowngrade(dropAllTables = true)
            .build()

    @Provides fun provideBookDao(db: AINovelDatabase) = db.bookDao()

    @Provides fun provideChapterDao(db: AINovelDatabase) = db.chapterDao()

    @Provides fun provideReadingProgressDao(db: AINovelDatabase) = db.readingProgressDao()

    @Provides fun provideHistoryDao(db: AINovelDatabase) = db.historyDao()

    @Provides fun provideDownloadDao(db: AINovelDatabase) = db.downloadDao()
}
