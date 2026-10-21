package vn.esstar.ainovel.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow
import vn.esstar.ainovel.core.database.entity.BookStorageRow
import vn.esstar.ainovel.core.database.entity.ChapterEntity
import vn.esstar.ainovel.core.database.entity.DownloadJobEntity

@Dao
interface DownloadDao {
    @Insert
    suspend fun insert(job: DownloadJobEntity): Long

    @Query("SELECT * FROM downloads WHERE id = :id")
    suspend fun getById(id: Long): DownloadJobEntity?

    @Query("SELECT * FROM downloads WHERE book_id = :bookId AND state IN ('QUEUED','RUNNING','PAUSED') ORDER BY created_at DESC LIMIT 1")
    suspend fun getActiveForBook(bookId: Long): DownloadJobEntity?

    @Query("SELECT * FROM downloads WHERE book_id = :bookId AND state IN ('QUEUED','RUNNING','PAUSED') ORDER BY created_at DESC LIMIT 1")
    fun observeActiveForBook(bookId: Long): Flow<DownloadJobEntity?>

    @Query("SELECT * FROM downloads ORDER BY created_at DESC")
    fun observeAll(): Flow<List<DownloadJobEntity>>

    @Query("UPDATE downloads SET state = :state, error = :error, updated_at = :now WHERE id = :id")
    suspend fun setState(id: Long, state: String, error: String?, now: Long)

    @Query("UPDATE downloads SET completed = :completed, failed = :failed, state = :state, updated_at = :now WHERE id = :id")
    suspend fun setProgress(id: Long, completed: Int, failed: Int, state: String, now: Long)

    @Query("UPDATE downloads SET work_id = :workId WHERE id = :id")
    suspend fun setWorkId(id: Long, workId: String?)

    @Query("DELETE FROM downloads WHERE state IN ('DONE','FAILED','CANCELLED') AND updated_at < :olderThan")
    suspend fun pruneFinished(olderThan: Long)

    @Query("SELECT * FROM chapters WHERE book_id = :bookId AND idx BETWEEN :fromIndex AND :toIndex ORDER BY idx ASC")
    suspend fun chaptersInRange(bookId: Long, fromIndex: Int, toIndex: Int): List<ChapterEntity>

    @Query(
        """
        SELECT b.*,
               (SELECT COUNT(*) FROM chapters c WHERE c.book_id = b.id) AS chapter_count,
               (SELECT COUNT(*) FROM chapters c WHERE c.book_id = b.id AND c.downloaded_at IS NOT NULL) AS downloaded_count,
               (SELECT COUNT(*) FROM chapters c WHERE c.book_id = b.id AND c.content_path IS NOT NULL) AS cached_count
        FROM books b
        WHERE EXISTS (SELECT 1 FROM chapters c WHERE c.book_id = b.id AND c.content_path IS NOT NULL)
           OR EXISTS (SELECT 1 FROM downloads d WHERE d.book_id = b.id AND d.state IN ('QUEUED','RUNNING','PAUSED'))
        ORDER BY b.updated_at DESC
        """,
    )
    fun observeStoredBooks(): Flow<List<BookStorageRow>>

    @Query("SELECT content_path FROM chapters WHERE downloaded_at IS NULL AND content_path IS NOT NULL")
    suspend fun cachedOnlyPaths(): List<String>

    @Query("UPDATE chapters SET content_path = NULL WHERE downloaded_at IS NULL AND content_path IS NOT NULL")
    suspend fun clearCachedOnly()

    @Query("UPDATE chapters SET content_path = NULL, downloaded_at = NULL WHERE book_id = :bookId")
    suspend fun clearBookContent(bookId: Long)
}
