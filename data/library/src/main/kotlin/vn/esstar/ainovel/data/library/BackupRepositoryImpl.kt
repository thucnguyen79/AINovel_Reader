package vn.esstar.ainovel.data.library

import android.content.Context
import android.net.Uri
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.IOException
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import timber.log.Timber
import vn.esstar.ainovel.core.common.di.AppDispatcher
import vn.esstar.ainovel.core.common.di.Dispatcher
import vn.esstar.ainovel.core.database.dao.BackupDao
import vn.esstar.ainovel.core.database.dao.BookDao
import vn.esstar.ainovel.core.database.dao.ChapterDao
import vn.esstar.ainovel.core.database.dao.EbookDao
import vn.esstar.ainovel.core.database.dao.GlossaryDao
import vn.esstar.ainovel.core.database.dao.HistoryDao
import vn.esstar.ainovel.core.database.dao.ReadingProgressDao
import vn.esstar.ainovel.core.database.entity.BookEntity
import vn.esstar.ainovel.core.database.entity.ChapterEntity
import vn.esstar.ainovel.core.database.entity.EbookEntity
import vn.esstar.ainovel.core.database.entity.GlossaryEntity
import vn.esstar.ainovel.core.database.entity.HistoryEntity
import vn.esstar.ainovel.core.database.entity.ReadingProgressEntity
import vn.esstar.ainovel.core.storage.AppStorage
import vn.esstar.ainovel.core.storage.ChapterStore
import vn.esstar.ainovel.domain.model.AppInfo
import vn.esstar.ainovel.domain.model.BackupSummary
import vn.esstar.ainovel.domain.repository.BackupRepository

/**
 * Backup format (ZIP):
 * - `manifest.json`  – format version, export time, app version
 * - `library.json`   – books with chapter lists, progress, history, ebook rows
 * - `glossary.json`  – glossary entries keyed by book url (null = global)
 * - `text/<n>/<idx>.txt` – chapter text of ebooks (n = book position in library.json)
 * - `covers/<n>.jpg` – ebook covers
 */
@Singleton
class BackupRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val backupDao: BackupDao,
    private val bookDao: BookDao,
    private val chapterDao: ChapterDao,
    private val progressDao: ReadingProgressDao,
    private val historyDao: HistoryDao,
    private val glossaryDao: GlossaryDao,
    private val ebookDao: EbookDao,
    private val chapterStore: ChapterStore,
    private val storage: AppStorage,
    private val appInfo: AppInfo,
    @Dispatcher(AppDispatcher.IO) private val io: CoroutineDispatcher,
) : BackupRepository {

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    @Serializable
    private data class Manifest(val format: Int, val exportedAt: Long, val appVersion: String)

    @Serializable
    private data class ChapterRow(val index: Int, val title: String, val url: String, val downloaded: Boolean = false)

    @Serializable
    private data class ProgressRow(val chapterIndex: Int, val sentenceIndex: Int, val scrollOffset: Int, val percent: Float, val updatedAt: Long)

    @Serializable
    private data class HistoryRow(val chapterIndex: Int, val lastAt: Long, val mode: String)

    @Serializable
    private data class EbookRow(val format: String, val fileName: String, val sizeBytes: Long, val importedAt: Long)

    @Serializable
    private data class BookRow(
        val sourceId: String,
        val url: String,
        val title: String,
        val author: String? = null,
        val coverUrl: String? = null,
        val description: String? = null,
        val status: String? = null,
        val genres: String? = null,
        val language: String = "vi",
        val isFavorite: Boolean = false,
        val createdAt: Long,
        val updatedAt: Long,
        val chapters: List<ChapterRow>,
        val progress: ProgressRow? = null,
        val history: HistoryRow? = null,
        val ebook: EbookRow? = null,
        /** True when `text/<n>/` holds every chapter of this book. */
        val hasText: Boolean = false,
        val hasCover: Boolean = false,
    )

    @Serializable
    private data class Library(val books: List<BookRow>)

    @Serializable
    private data class GlossaryRow(val bookUrl: String? = null, val source: String, val target: String, val updatedAt: Long)

    @Serializable
    private data class GlossaryFile(val entries: List<GlossaryRow>)

    override suspend fun export(uriString: String): BackupSummary = withContext(io) {
        val books = backupDao.allBooks()
        val progress = backupDao.allProgress().associateBy { it.bookId }
        val history = backupDao.allHistory().associateBy { it.bookId }
        val ebooks = backupDao.allEbooks().associateBy { it.bookId }
        val glossary = backupDao.allGlossary()
        val booksById = books.associateBy { it.id }

        val rows = ArrayList<BookRow>()
        val chapterLists = ArrayList<List<ChapterEntity>>()
        for (book in books) {
            val chapters = backupDao.chaptersOf(book.id)
            chapterLists += chapters
            val indexById = chapters.associate { it.id to it.index }
            val ebook = ebooks[book.id]
            val isEbook = ebook != null || book.sourceId == EBOOK_SOURCE
            rows += BookRow(
                sourceId = book.sourceId, url = book.url, title = book.title, author = book.author, coverUrl = book.coverUrl,
                description = book.description, status = book.status, genres = book.genres, language = book.language,
                isFavorite = book.isFavorite, createdAt = book.createdAt, updatedAt = book.updatedAt,
                chapters = chapters.map { ChapterRow(it.index, it.title, it.url, it.downloadedAt != null) },
                progress = progress[book.id]?.let { p -> indexById[p.chapterId]?.let { ProgressRow(it, p.sentenceIndex, p.scrollOffset, p.percent, p.updatedAt) } },
                history = history[book.id]?.let { h -> indexById[h.lastChapterId]?.let { HistoryRow(it, h.lastAt, h.mode) } },
                ebook = ebook?.let { EbookRow(it.format, it.fileName, it.sizeBytes, it.importedAt) },
                hasText = isEbook && chapters.isNotEmpty() && chapters.all { it.contentPath != null },
                hasCover = isEbook && File(storage.covers, "${book.id}.jpg").isFile,
            )
        }
        val glossaryRows = glossary.map { g -> GlossaryRow(g.bookId?.let { booksById[it]?.url }, g.source, g.target, g.updatedAt).also { r -> if (g.bookId != null && r.bookUrl == null) Timber.w("Glossary %d has unknown book", g.id) } }
            .filter { it.bookUrl != null || glossary.any { g -> g.bookId == null && g.source == it.source } }

        var textChapters = 0
        val now = System.currentTimeMillis()
        val out = context.contentResolver.openOutputStream(Uri.parse(uriString), "wt") ?: throw IOException("Cannot open $uriString")
        ZipOutputStream(out.buffered()).use { zip ->
            fun put(name: String, bytes: ByteArray) {
                zip.putNextEntry(ZipEntry(name)); zip.write(bytes); zip.closeEntry()
            }
            put("manifest.json", json.encodeToString(Manifest(FORMAT, now, appInfo.versionName)).toByteArray())
            put("library.json", json.encodeToString(Library(rows)).toByteArray())
            put("glossary.json", json.encodeToString(GlossaryFile(glossaryRows)).toByteArray())
            rows.forEachIndexed { n, row ->
                if (row.hasText) {
                    for (chapter in chapterLists[n]) {
                        val paragraphs = chapter.contentPath?.let { chapterStore.read(it) } ?: continue
                        put("text/$n/${chapter.index}.txt", paragraphs.joinToString("\n").toByteArray())
                        textChapters++
                    }
                }
                if (row.hasCover) {
                    val cover = File(storage.covers, "${books[n].id}.jpg")
                    put("covers/$n.jpg", cover.readBytes())
                }
            }
        }
        BackupSummary(rows.size, rows.sumOf { it.chapters.size }, glossaryRows.size, textChapters, now, appInfo.versionName)
    }

    override suspend fun import(uriString: String): BackupSummary = withContext(io) {
        val entries = HashMap<String, ByteArray>()
        val input = context.contentResolver.openInputStream(Uri.parse(uriString)) ?: throw IOException("Cannot open $uriString")
        ZipInputStream(input.buffered()).use { zip ->
            while (true) {
                val e = zip.nextEntry ?: break
                if (!e.isDirectory) entries[e.name] = zip.readBytes()
                zip.closeEntry()
            }
        }
        val manifest = entries["manifest.json"]?.let { json.decodeFromString<Manifest>(String(it)) } ?: throw IOException("Not an AINovel backup")
        if (manifest.format > FORMAT) throw IOException("Backup from a newer app version")
        val library = entries["library.json"]?.let { json.decodeFromString<Library>(String(it)) } ?: Library(emptyList())
        val glossary = entries["glossary.json"]?.let { json.decodeFromString<GlossaryFile>(String(it)) } ?: GlossaryFile(emptyList())

        var textChapters = 0
        val idByUrl = HashMap<String, Long>()
        library.books.forEachIndexed { n, row ->
            val existing = bookDao.getByUrl(row.url)
            val bookId = existing?.id ?: bookDao.insertIgnore(
                BookEntity(
                    sourceId = row.sourceId, url = row.url, title = row.title, author = row.author, coverUrl = row.coverUrl,
                    description = row.description, status = row.status, genres = row.genres, language = row.language,
                    isFavorite = row.isFavorite, createdAt = row.createdAt, updatedAt = row.updatedAt,
                ),
            ).takeIf { it > 0 } ?: bookDao.getByUrl(row.url)!!.id
            idByUrl[row.url] = bookId
            if (existing != null && row.isFavorite && !existing.isFavorite) bookDao.setFavorite(bookId, true, System.currentTimeMillis())

            chapterDao.sync(bookId, row.chapters.map { ChapterEntity(bookId = bookId, index = it.index, title = it.title, url = it.url) })
            val chapters = chapterDao.getByBook(bookId).associateBy { it.index }

            if (row.hasText) {
                for ((index, chapter) in chapters) {
                    if (chapter.contentPath != null) continue
                    val bytes = entries["text/$n/$index.txt"] ?: continue
                    val path = chapterStore.write(bookId, chapter.id, String(bytes).split('\n'))
                    chapterDao.setContentPath(chapter.id, path)
                    textChapters++
                }
            }
            if (row.hasCover) {
                entries["covers/$n.jpg"]?.let { bytes ->
                    runCatching {
                        storage.covers.mkdirs()
                        val file = File(storage.covers, "$bookId.jpg")
                        file.writeBytes(bytes)
                        bookDao.getById(bookId)?.let { bookDao.update(it.copy(coverUrl = file.toURI().toString())) }
                    }.onFailure { Timber.w(it, "Cover restore failed") }
                }
            }
            row.ebook?.let { e -> ebookDao.upsert(EbookEntity(bookId, e.format, e.fileName, e.sizeBytes, e.importedAt)) }
            row.progress?.let { p ->
                val chapter = chapters[p.chapterIndex] ?: return@let
                val current = progressDao.get(bookId)
                if (current == null || current.updatedAt < p.updatedAt) {
                    progressDao.upsert(ReadingProgressEntity(bookId, chapter.id, p.sentenceIndex, p.scrollOffset, p.percent, p.updatedAt))
                }
            }
            row.history?.let { h ->
                val chapter = chapters[h.chapterIndex] ?: return@let
                historyDao.upsert(HistoryEntity(bookId, chapter.id, h.lastAt, h.mode))
            }
        }
        var glossaryCount = 0
        for (g in glossary.entries) {
            val bookId = g.bookUrl?.let { idByUrl[it] ?: bookDao.getByUrl(it)?.id }
            if (g.bookUrl != null && bookId == null) continue
            val existing = glossaryDao.findId(bookId, g.source)
            if (existing == null) {
                glossaryDao.insert(GlossaryEntity(bookId = bookId, source = g.source, target = g.target, updatedAt = g.updatedAt))
            } else {
                glossaryDao.updateTarget(existing, g.target, g.updatedAt)
            }
            glossaryCount++
        }
        BackupSummary(library.books.size, library.books.sumOf { it.chapters.size }, glossaryCount, textChapters, manifest.exportedAt, manifest.appVersion)
    }

    private companion object {
        const val FORMAT = 1
        const val EBOOK_SOURCE = "ebook"
    }
}
