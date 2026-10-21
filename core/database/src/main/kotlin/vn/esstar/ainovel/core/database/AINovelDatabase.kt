package vn.esstar.ainovel.core.database

import androidx.room.AutoMigration
import androidx.room.Database
import androidx.room.RoomDatabase
import vn.esstar.ainovel.core.database.dao.BookDao
import vn.esstar.ainovel.core.database.dao.ChapterDao
import vn.esstar.ainovel.core.database.dao.DownloadDao
import vn.esstar.ainovel.core.database.dao.EbookDao
import vn.esstar.ainovel.core.database.dao.GlossaryDao
import vn.esstar.ainovel.core.database.dao.HistoryDao
import vn.esstar.ainovel.core.database.dao.ReadingProgressDao
import vn.esstar.ainovel.core.database.entity.BookEntity
import vn.esstar.ainovel.core.database.entity.ChapterEntity
import vn.esstar.ainovel.core.database.entity.DownloadJobEntity
import vn.esstar.ainovel.core.database.entity.EbookEntity
import vn.esstar.ainovel.core.database.entity.GlossaryEntity
import vn.esstar.ainovel.core.database.entity.HistoryEntity
import vn.esstar.ainovel.core.database.entity.ReadingProgressEntity

@Database(
    entities = [BookEntity::class, ChapterEntity::class, ReadingProgressEntity::class, HistoryEntity::class, DownloadJobEntity::class, GlossaryEntity::class, EbookEntity::class],
    version = 4,
    exportSchema = true,
    autoMigrations = [AutoMigration(from = 1, to = 2), AutoMigration(from = 2, to = 3), AutoMigration(from = 3, to = 4)],
)
abstract class AINovelDatabase : RoomDatabase() {
    abstract fun bookDao(): BookDao
    abstract fun chapterDao(): ChapterDao
    abstract fun readingProgressDao(): ReadingProgressDao
    abstract fun historyDao(): HistoryDao
    abstract fun downloadDao(): DownloadDao
    abstract fun glossaryDao(): GlossaryDao
    abstract fun ebookDao(): EbookDao

    companion object {
        const val NAME = "ainovel.db"
    }
}
