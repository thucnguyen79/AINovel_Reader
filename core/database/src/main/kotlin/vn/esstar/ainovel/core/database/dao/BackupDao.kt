package vn.esstar.ainovel.core.database.dao

import androidx.room.Dao
import androidx.room.Query
import vn.esstar.ainovel.core.database.entity.BookEntity
import vn.esstar.ainovel.core.database.entity.ChapterEntity
import vn.esstar.ainovel.core.database.entity.EbookEntity
import vn.esstar.ainovel.core.database.entity.GlossaryEntity
import vn.esstar.ainovel.core.database.entity.HistoryEntity
import vn.esstar.ainovel.core.database.entity.ReadingProgressEntity

/** Whole-table reads for backup; writes go through the regular DAOs. */
@Dao
interface BackupDao {
    @Query("SELECT * FROM books ORDER BY id")
    suspend fun allBooks(): List<BookEntity>

    @Query("SELECT * FROM chapters WHERE book_id = :bookId ORDER BY idx")
    suspend fun chaptersOf(bookId: Long): List<ChapterEntity>

    @Query("SELECT * FROM reading_progress")
    suspend fun allProgress(): List<ReadingProgressEntity>

    @Query("SELECT * FROM history")
    suspend fun allHistory(): List<HistoryEntity>

    @Query("SELECT * FROM glossary ORDER BY id")
    suspend fun allGlossary(): List<GlossaryEntity>

    @Query("SELECT * FROM ebooks")
    suspend fun allEbooks(): List<EbookEntity>
}
