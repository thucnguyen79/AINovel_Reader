package vn.esstar.ainovel.core.database

import androidx.room.AutoMigration
import androidx.room.Database
import androidx.room.RoomDatabase
import vn.esstar.ainovel.core.database.dao.BackupDao
import vn.esstar.ainovel.core.database.dao.BookDao
import vn.esstar.ainovel.core.database.dao.ChapterDao
import vn.esstar.ainovel.core.database.dao.DownloadDao
import vn.esstar.ainovel.core.database.dao.EbookDao
import vn.esstar.ainovel.core.database.dao.GlossaryDao
import vn.esstar.ainovel.core.database.dao.HistoryDao
import vn.esstar.ainovel.core.database.dao.LlmChapterDao
import vn.esstar.ainovel.core.database.dao.LlmSummaryDao
import vn.esstar.ainovel.core.database.dao.ReadingProgressDao
import vn.esstar.ainovel.core.database.entity.BookEntity
import vn.esstar.ainovel.core.database.entity.ChapterEntity
import vn.esstar.ainovel.core.database.entity.DownloadJobEntity
import vn.esstar.ainovel.core.database.entity.EbookEntity
import vn.esstar.ainovel.core.database.entity.GlossaryEntity
import vn.esstar.ainovel.core.database.entity.HistoryEntity
import vn.esstar.ainovel.core.database.entity.LlmChapterEntity
import vn.esstar.ainovel.core.database.entity.LlmSummaryEntity
import vn.esstar.ainovel.core.database.entity.ReadingProgressEntity

@Database(
    entities = [BookEntity::class, ChapterEntity::class, ReadingProgressEntity::class, HistoryEntity::class, DownloadJobEntity::class, GlossaryEntity::class, EbookEntity::class, LlmChapterEntity::class, LlmSummaryEntity::class],
    version = 6,
    exportSchema = true,
    autoMigrations = [AutoMigration(from = 1, to = 2), AutoMigration(from = 2, to = 3), AutoMigration(from = 3, to = 4), AutoMigration(from = 4, to = 5), AutoMigration(from = 5, to = 6)],
)
abstract class AINovelDatabase : RoomDatabase() {
    abstract fun bookDao(): BookDao
    abstract fun chapterDao(): ChapterDao
    abstract fun readingProgressDao(): ReadingProgressDao
    abstract fun historyDao(): HistoryDao
    abstract fun downloadDao(): DownloadDao
    abstract fun glossaryDao(): GlossaryDao
    abstract fun ebookDao(): EbookDao
    abstract fun backupDao(): BackupDao
    abstract fun llmChapterDao(): LlmChapterDao
    abstract fun llmSummaryDao(): LlmSummaryDao

    companion object {
        const val NAME = "ainovel.db"
    }
}
