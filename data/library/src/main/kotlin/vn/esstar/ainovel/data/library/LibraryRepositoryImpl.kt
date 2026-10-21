package vn.esstar.ainovel.data.library

import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import vn.esstar.ainovel.core.database.dao.BookDao
import vn.esstar.ainovel.core.database.dao.ChapterDao
import vn.esstar.ainovel.core.database.dao.HistoryDao
import vn.esstar.ainovel.core.database.dao.ReadingProgressDao
import vn.esstar.ainovel.core.database.entity.BookEntity
import vn.esstar.ainovel.core.database.entity.ChapterEntity
import vn.esstar.ainovel.core.database.entity.HistoryEntity
import vn.esstar.ainovel.core.database.entity.ReadingProgressEntity
import vn.esstar.ainovel.core.storage.ChapterStore
import vn.esstar.ainovel.domain.model.Book
import vn.esstar.ainovel.domain.model.Chapter
import vn.esstar.ainovel.domain.model.ChapterContent
import vn.esstar.ainovel.domain.model.ChapterText
import vn.esstar.ainovel.domain.model.FetchedBook
import vn.esstar.ainovel.domain.model.HistoryEntry
import vn.esstar.ainovel.domain.model.ReadingMode
import vn.esstar.ainovel.domain.model.ReadingPosition
import vn.esstar.ainovel.domain.model.SourceId
import vn.esstar.ainovel.domain.repository.LibraryRepository
import vn.esstar.ainovel.domain.repository.SourceRepository

@Singleton
class LibraryRepositoryImpl @Inject constructor(
    private val bookDao: BookDao,
    private val chapterDao: ChapterDao,
    private val progressDao: ReadingProgressDao,
    private val historyDao: HistoryDao,
    private val chapterStore: ChapterStore,
    private val sources: SourceRepository,
) : LibraryRepository {

    override suspend fun upsertBook(fetched: FetchedBook): Long {
        val now = System.currentTimeMillis()
        val existing = bookDao.getByUrl(fetched.url)
        val bookId = if (existing == null) {
            bookDao.insertIgnore(fetched.toEntity(now)).takeIf { it > 0 }
                ?: checkNotNull(bookDao.getByUrl(fetched.url)).id
        } else {
            bookDao.update(
                existing.copy(
                    title = fetched.title,
                    author = fetched.author ?: existing.author,
                    coverUrl = fetched.coverUrl ?: existing.coverUrl,
                    description = fetched.description ?: existing.description,
                    status = fetched.status ?: existing.status,
                    genres = fetched.genres.takeIf { it.isNotEmpty() }?.joinToString(",") ?: existing.genres,
                    updatedAt = now,
                ),
            )
            existing.id
        }
        chapterDao.sync(bookId, fetched.chapters.map { ChapterEntity(bookId = bookId, index = it.index, title = it.title, url = it.url) })
        return bookId
    }

    override suspend fun importLocalBook(book: FetchedBook, chapters: List<ChapterText>): Long {
        val now = System.currentTimeMillis()
        val bookId = bookDao.insertIgnore(book.toEntity(now)).takeIf { it > 0 }
            ?: checkNotNull(bookDao.getByUrl(book.url)).id
        val entities = chapters.mapIndexed { i, c -> ChapterEntity(bookId = bookId, index = i, title = c.title, url = "${book.url}/$i") }
        chapterDao.insertIgnore(entities)
        val stored = chapterDao.getByBook(bookId).associateBy { it.index }
        chapters.forEachIndexed { i, c ->
            val entity = stored[i] ?: return@forEachIndexed
            val path = chapterStore.write(bookId, entity.id, c.paragraphs)
            chapterDao.setContentPath(entity.id, path)
        }
        return bookId
    }

    override suspend fun deleteBook(bookId: Long) {
        chapterStore.deleteBook(bookId)
        bookDao.delete(bookId)
    }

    override suspend fun setCover(bookId: Long, coverUrl: String?) {
        val existing = bookDao.getById(bookId) ?: return
        bookDao.update(existing.copy(coverUrl = coverUrl, updatedAt = System.currentTimeMillis()))
    }

    override suspend fun getBook(bookId: Long): Book? = bookDao.getById(bookId)?.toDomain(chapterDao.countByBook(bookId))

    override fun observeBook(bookId: Long): Flow<Book?> = bookDao.observeById(bookId).map { it?.toDomain() }

    override suspend fun findBookByUrl(url: String): Book? = bookDao.getByUrl(url)?.toDomain()

    override suspend fun getChapters(bookId: Long): List<Chapter> = chapterDao.getByBook(bookId).map { it.toDomain() }

    override fun observeChapters(bookId: Long): Flow<List<Chapter>> =
        chapterDao.observeByBook(bookId).map { list -> list.map { it.toDomain() } }

    override suspend fun getChapter(chapterId: Long): Chapter? = chapterDao.getById(chapterId)?.toDomain()

    override suspend fun findChapterByUrl(bookId: Long, url: String): Chapter? =
        chapterDao.getByUrl(bookId, url)?.toDomain()

    override suspend fun getChapterContent(chapterId: Long, forceRefresh: Boolean): ChapterContent {
        val chapter = checkNotNull(chapterDao.getById(chapterId)) { "Chapter $chapterId not found" }
        if (!forceRefresh) {
            chapter.contentPath?.let { path ->
                chapterStore.read(path)?.let { return ChapterContent(chapter.id, chapter.title, it) }
            }
        }
        val fetched = sources.fetchChapter(chapter.url)
        val path = chapterStore.write(chapter.bookId, chapter.id, fetched.paragraphs)
        chapterDao.setContentPath(chapter.id, path)
        return ChapterContent(chapter.id, chapter.title, fetched.paragraphs)
    }

    override suspend fun getProgress(bookId: Long): ReadingPosition? = progressDao.get(bookId)?.let {
        ReadingPosition(it.bookId, it.chapterId, it.sentenceIndex, it.scrollOffset, it.percent)
    }

    override suspend fun saveProgress(position: ReadingPosition) {
        progressDao.upsert(
            ReadingProgressEntity(
                bookId = position.bookId,
                chapterId = position.chapterId,
                sentenceIndex = position.sentenceIndex,
                scrollOffset = position.scrollOffset,
                percent = position.percent,
                updatedAt = System.currentTimeMillis(),
            ),
        )
    }

    override fun observeHistory(): Flow<List<HistoryEntry>> = historyDao.observeAll().map { rows ->
        rows.map { row ->
            HistoryEntry(
                book = row.book.toDomain(),
                lastChapter = row.chapter?.toDomain(),
                lastAt = row.history.lastAt,
                mode = runCatching { ReadingMode.valueOf(row.history.mode) }.getOrDefault(ReadingMode.READ),
            )
        }
    }

    override suspend fun touchHistory(bookId: Long, chapterId: Long, mode: ReadingMode) {
        historyDao.upsert(HistoryEntity(bookId = bookId, lastChapterId = chapterId, lastAt = System.currentTimeMillis(), mode = mode.name))
    }

    override suspend fun removeFromHistory(bookId: Long) = historyDao.delete(bookId)

    override suspend fun setFavorite(bookId: Long, favorite: Boolean) =
        bookDao.setFavorite(bookId, favorite, System.currentTimeMillis())

    private fun FetchedBook.toEntity(now: Long) = BookEntity(
        sourceId = sourceId.value,
        url = url,
        title = title,
        author = author,
        coverUrl = coverUrl,
        description = description,
        status = status,
        genres = genres.takeIf { it.isNotEmpty() }?.joinToString(","),
        language = language,
        createdAt = now,
        updatedAt = now,
    )

    private fun BookEntity.toDomain(chapterCount: Int = 0) = Book(
        id = id,
        sourceId = SourceId(sourceId),
        url = url,
        title = title,
        author = author,
        coverUrl = coverUrl,
        description = description,
        status = status,
        genres = genres?.split(',')?.filter { it.isNotBlank() } ?: emptyList(),
        language = language,
        isFavorite = isFavorite,
        chapterCount = chapterCount,
    )

    private fun ChapterEntity.toDomain() = Chapter(
        id = id,
        bookId = bookId,
        index = index,
        title = title,
        url = url,
        isCached = contentPath != null,
        isDownloaded = downloadedAt != null,
    )
}
