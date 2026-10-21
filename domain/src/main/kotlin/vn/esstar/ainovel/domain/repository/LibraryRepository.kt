package vn.esstar.ainovel.domain.repository

import kotlinx.coroutines.flow.Flow
import vn.esstar.ainovel.domain.model.Book
import vn.esstar.ainovel.domain.model.Chapter
import vn.esstar.ainovel.domain.model.ChapterContent
import vn.esstar.ainovel.domain.model.ChapterText
import vn.esstar.ainovel.domain.model.FetchedBook
import vn.esstar.ainovel.domain.model.HistoryEntry
import vn.esstar.ainovel.domain.model.ReadingMode
import vn.esstar.ainovel.domain.model.ReadingPosition

/** Local library: books, chapters, cached content, progress and history. */
interface LibraryRepository {
    /** Inserts or updates a book and its chapter list; returns the local book id. */
    suspend fun upsertBook(fetched: FetchedBook): Long

    /**
     * Stores a book whose chapter text is already known (ebook import). [book].chapters is ignored;
     * chapter URLs are generated. Returns the local book id.
     */
    suspend fun importLocalBook(book: FetchedBook, chapters: List<ChapterText>): Long

    /** Deletes a book with its chapters, progress, history and cached text. */
    suspend fun deleteBook(bookId: Long)

    suspend fun setCover(bookId: Long, coverUrl: String?)

    suspend fun getBook(bookId: Long): Book?
    fun observeBook(bookId: Long): Flow<Book?>
    suspend fun findBookByUrl(url: String): Book?

    suspend fun getChapters(bookId: Long): List<Chapter>
    fun observeChapters(bookId: Long): Flow<List<Chapter>>
    suspend fun getChapter(chapterId: Long): Chapter?
    suspend fun findChapterByUrl(bookId: Long, url: String): Chapter?

    /** Returns cached content, fetching and caching it from the source when missing. */
    suspend fun getChapterContent(chapterId: Long, forceRefresh: Boolean = false): ChapterContent

    suspend fun getProgress(bookId: Long): ReadingPosition?
    suspend fun saveProgress(position: ReadingPosition)

    fun observeHistory(): Flow<List<HistoryEntry>>
    suspend fun touchHistory(bookId: Long, chapterId: Long, mode: ReadingMode)
    suspend fun removeFromHistory(bookId: Long)

    suspend fun setFavorite(bookId: Long, favorite: Boolean)
}
