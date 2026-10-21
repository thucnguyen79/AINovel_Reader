package vn.esstar.ainovel.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow
import vn.esstar.ainovel.core.database.entity.BookEntity
import vn.esstar.ainovel.core.database.entity.ChapterEntity
import vn.esstar.ainovel.core.database.entity.HistoryEntity
import vn.esstar.ainovel.core.database.entity.HistoryWithBook
import vn.esstar.ainovel.core.database.entity.ReadingProgressEntity

@Dao
interface BookDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIgnore(book: BookEntity): Long

    @Update
    suspend fun update(book: BookEntity)

    @Query("SELECT * FROM books WHERE id = :id")
    suspend fun getById(id: Long): BookEntity?

    @Query("SELECT * FROM books WHERE id = :id")
    fun observeById(id: Long): Flow<BookEntity?>

    @Query("SELECT * FROM books WHERE url = :url LIMIT 1")
    suspend fun getByUrl(url: String): BookEntity?

    @Query("UPDATE books SET is_favorite = :favorite, updated_at = :now WHERE id = :id")
    suspend fun setFavorite(id: Long, favorite: Boolean, now: Long)

    @Query("SELECT * FROM books WHERE is_favorite = 1 ORDER BY updated_at DESC")
    fun observeFavorites(): Flow<List<BookEntity>>

    @Query("DELETE FROM books WHERE id = :id")
    suspend fun delete(id: Long)
}

@Dao
interface ChapterDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIgnore(chapters: List<ChapterEntity>): List<Long>

    @Query("SELECT * FROM chapters WHERE book_id = :bookId ORDER BY idx ASC")
    suspend fun getByBook(bookId: Long): List<ChapterEntity>

    @Query("SELECT * FROM chapters WHERE book_id = :bookId ORDER BY idx ASC")
    fun observeByBook(bookId: Long): Flow<List<ChapterEntity>>

    @Query("SELECT * FROM chapters WHERE id = :id")
    suspend fun getById(id: Long): ChapterEntity?

    @Query("SELECT * FROM chapters WHERE book_id = :bookId AND url = :url LIMIT 1")
    suspend fun getByUrl(bookId: Long, url: String): ChapterEntity?

    @Query("SELECT * FROM chapters WHERE book_id = :bookId AND idx = :index LIMIT 1")
    suspend fun getByIndex(bookId: Long, index: Int): ChapterEntity?

    @Query("SELECT COUNT(*) FROM chapters WHERE book_id = :bookId")
    suspend fun countByBook(bookId: Long): Int

    @Query("UPDATE chapters SET idx = :index, title = :title WHERE id = :id")
    suspend fun updateIndexAndTitle(id: Long, index: Int, title: String)

    @Query("UPDATE chapters SET content_path = :path WHERE id = :id")
    suspend fun setContentPath(id: Long, path: String?)

    @Query("UPDATE chapters SET downloaded_at = :at WHERE id = :id")
    suspend fun setDownloadedAt(id: Long, at: Long?)

    /**
     * Syncs the chapter list of a book: inserts new URLs, refreshes index/title of known ones.
     * Cached content of existing chapters is kept.
     */
    @Transaction
    suspend fun sync(bookId: Long, chapters: List<ChapterEntity>) {
        val existing = getByBook(bookId).associateBy { it.url }
        val toInsert = chapters.filter { it.url !in existing }
        if (toInsert.isNotEmpty()) insertIgnore(toInsert)
        chapters.forEach { c ->
            val old = existing[c.url] ?: return@forEach
            if (old.index != c.index || old.title != c.title) updateIndexAndTitle(old.id, c.index, c.title)
        }
    }
}

@Dao
interface ReadingProgressDao {
    @Upsert
    suspend fun upsert(progress: ReadingProgressEntity)

    @Query("SELECT * FROM reading_progress WHERE book_id = :bookId")
    suspend fun get(bookId: Long): ReadingProgressEntity?

    @Query("SELECT * FROM reading_progress WHERE book_id = :bookId")
    fun observe(bookId: Long): Flow<ReadingProgressEntity?>
}

@Dao
interface HistoryDao {
    @Upsert
    suspend fun upsert(history: HistoryEntity)

    @Query("DELETE FROM history WHERE book_id = :bookId")
    suspend fun delete(bookId: Long)

    @Query(
        """
        SELECT h.*,
               b.id AS b_id, b.source_id AS b_source_id, b.url AS b_url, b.title AS b_title,
               b.author AS b_author, b.cover_url AS b_cover_url, b.description AS b_description,
               b.status AS b_status, b.genres AS b_genres, b.language AS b_language,
               b.is_favorite AS b_is_favorite, b.created_at AS b_created_at, b.updated_at AS b_updated_at,
               c.id AS ch_id, c.book_id AS ch_book_id, c.idx AS ch_idx, c.title AS ch_title, c.url AS ch_url,
               c.content_path AS ch_content_path, c.downloaded_at AS ch_downloaded_at
        FROM history h
        INNER JOIN books b ON b.id = h.book_id
        LEFT JOIN chapters c ON c.id = h.last_chapter_id
        ORDER BY h.last_at DESC
        """,
    )
    fun observeAll(): Flow<List<HistoryWithBook>>
}
