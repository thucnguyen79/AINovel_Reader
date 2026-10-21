package vn.esstar.ainovel.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow
import vn.esstar.ainovel.core.database.entity.GlossaryEntity

@Dao
interface GlossaryDao {
    /** Entries for one book plus the global ones; book-specific rows come last so they win when merged. */
    @Query("SELECT * FROM glossary WHERE book_id IS NULL OR book_id = :bookId ORDER BY book_id IS NOT NULL, source")
    fun observeForBook(bookId: Long?): Flow<List<GlossaryEntity>>

    @Query("SELECT * FROM glossary WHERE book_id IS NULL OR book_id = :bookId ORDER BY book_id IS NOT NULL, source")
    suspend fun listForBook(bookId: Long?): List<GlossaryEntity>

    @Query("SELECT id FROM glossary WHERE source = :source AND ((:bookId IS NULL AND book_id IS NULL) OR book_id = :bookId) LIMIT 1")
    suspend fun findId(bookId: Long?, source: String): Long?

    @Insert
    suspend fun insert(entity: GlossaryEntity): Long

    @Query("UPDATE glossary SET target = :target, updated_at = :now WHERE id = :id")
    suspend fun updateTarget(id: Long, target: String, now: Long)

    @Query("DELETE FROM glossary WHERE id = :id")
    suspend fun delete(id: Long)
}
