package vn.esstar.ainovel.core.database

import androidx.room.Database
import androidx.room.RoomDatabase
import vn.esstar.ainovel.core.database.dao.BookDao
import vn.esstar.ainovel.core.database.dao.ChapterDao
import vn.esstar.ainovel.core.database.dao.HistoryDao
import vn.esstar.ainovel.core.database.dao.ReadingProgressDao
import vn.esstar.ainovel.core.database.entity.BookEntity
import vn.esstar.ainovel.core.database.entity.ChapterEntity
import vn.esstar.ainovel.core.database.entity.HistoryEntity
import vn.esstar.ainovel.core.database.entity.ReadingProgressEntity

@Database(
    entities = [BookEntity::class, ChapterEntity::class, ReadingProgressEntity::class, HistoryEntity::class],
    version = 1,
    exportSchema = true,
)
abstract class AINovelDatabase : RoomDatabase() {
    abstract fun bookDao(): BookDao
    abstract fun chapterDao(): ChapterDao
    abstract fun readingProgressDao(): ReadingProgressDao
    abstract fun historyDao(): HistoryDao

    companion object {
        const val NAME = "ainovel.db"
    }
}
