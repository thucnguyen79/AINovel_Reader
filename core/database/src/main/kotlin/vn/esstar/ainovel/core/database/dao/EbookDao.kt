package vn.esstar.ainovel.core.database.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow
import vn.esstar.ainovel.core.database.entity.EbookEntity
import vn.esstar.ainovel.core.database.entity.EbookWithBook

@Dao
interface EbookDao {
    @Upsert
    suspend fun upsert(ebook: EbookEntity)

    @Query(
        """
        SELECT e.*, b.id AS b_id, b.source_id AS b_source_id, b.url AS b_url, b.title AS b_title, b.author AS b_author,
               b.cover_url AS b_cover_url, b.description AS b_description, b.status AS b_status, b.genres AS b_genres,
               b.language AS b_language, b.is_favorite AS b_is_favorite, b.created_at AS b_created_at, b.updated_at AS b_updated_at
        FROM ebooks e JOIN books b ON b.id = e.book_id
        ORDER BY e.imported_at DESC
        """,
    )
    fun observeAll(): Flow<List<EbookWithBook>>

    @Query("SELECT COUNT(*) FROM chapters WHERE book_id = :bookId")
    suspend fun chapterCount(bookId: Long): Int
}
