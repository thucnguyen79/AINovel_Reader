package vn.esstar.ainovel.data.ebook

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.IOException
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import timber.log.Timber
import vn.esstar.ainovel.core.common.di.AppDispatcher
import vn.esstar.ainovel.core.common.di.Dispatcher
import vn.esstar.ainovel.core.database.dao.EbookDao
import vn.esstar.ainovel.core.database.entity.EbookEntity
import vn.esstar.ainovel.core.storage.AppStorage
import vn.esstar.ainovel.data.ebook.parser.EbookParseException
import vn.esstar.ainovel.data.ebook.parser.EbookParsers
import vn.esstar.ainovel.data.ebook.parser.EpubParser
import vn.esstar.ainovel.data.ebook.parser.ParsedEbook
import vn.esstar.ainovel.data.ebook.parser.PdfParser
import vn.esstar.ainovel.data.ebook.parser.TxtParser
import vn.esstar.ainovel.data.ebook.parser.ZipTxtParser
import vn.esstar.ainovel.domain.model.Book
import vn.esstar.ainovel.domain.model.ChapterText
import vn.esstar.ainovel.domain.model.EbookInfo
import vn.esstar.ainovel.domain.model.EbookSource
import vn.esstar.ainovel.domain.model.FetchedBook
import vn.esstar.ainovel.domain.model.SourceId
import vn.esstar.ainovel.domain.repository.EbookRepository
import vn.esstar.ainovel.domain.repository.LibraryRepository

@Singleton
class EbookRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val library: LibraryRepository,
    private val ebookDao: EbookDao,
    private val storage: AppStorage,
    private val client: OkHttpClient,
    @Dispatcher(AppDispatcher.IO) private val io: CoroutineDispatcher,
) : EbookRepository {

    private val parsers by lazy {
        PDFBoxResourceLoader.init(context.applicationContext)
        EbookParsers(listOf(EpubParser(), TxtParser(), ZipTxtParser(), PdfParser()))
    }

    override fun observeEbooks(): Flow<List<EbookInfo>> = ebookDao.observeAll().map { rows ->
        rows.map { row ->
            EbookInfo(
                book = Book(
                    id = row.book.id,
                    sourceId = SourceId(row.book.sourceId),
                    url = row.book.url,
                    title = row.book.title,
                    author = row.book.author,
                    coverUrl = row.book.coverUrl,
                    description = row.book.description,
                    status = row.book.status,
                    genres = row.book.genres?.split(',')?.filter { it.isNotBlank() } ?: emptyList(),
                    language = row.book.language,
                    isFavorite = row.book.isFavorite,
                ),
                format = row.ebook.format,
                fileName = row.ebook.fileName,
                sizeBytes = row.ebook.sizeBytes,
                importedAt = row.ebook.importedAt,
            )
        }
    }

    override suspend fun importFromUri(uriString: String): Long = withContext(io) {
        val uri = Uri.parse(uriString)
        val name = displayName(uri) ?: uri.lastPathSegment?.substringAfterLast('/') ?: "ebook"
        val temp = copyToTemp { context.contentResolver.openInputStream(uri) ?: throw IOException("Cannot open $uri") }
        try {
            importFile(temp, name)
        } finally {
            temp.delete()
        }
    }

    override suspend fun importFromUrl(url: String): Long = withContext(io) {
        val direct = directDownloadUrl(url.trim())
        val request = Request.Builder().url(direct).build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IOException("HTTP ${response.code}")
            val body = response.body
            val fileName = fileNameFrom(response.header("Content-Disposition"), response.request.url.toString(), body.contentType()?.subtype)
            val temp = copyToTemp { body.byteStream() }
            try {
                importFile(temp, fileName)
            } finally {
                temp.delete()
            }
        }
    }

    override suspend fun delete(bookId: Long) = withContext(io) {
        File(storage.covers, "$bookId.jpg").delete()
        library.deleteBook(bookId)
    }

    private suspend fun importFile(file: File, fileName: String): Long {
        val head = file.inputStream().use { input -> ByteArray(8).also { input.read(it) } }
        val parser = parsers.forFile(fileName, head) ?: throw EbookParseException("Unsupported file: $fileName")
        val parsed: ParsedEbook = file.inputStream().buffered().use { parser.parse(it, fileName) }
        Timber.i("Parsed ebook %s: %d chapters, lang=%s", fileName, parsed.chapters.size, parsed.language)

        val url = "${EbookSource.URL_SCHEME}://${UUID.randomUUID()}"
        val book = FetchedBook(
            sourceId = EbookSource.id,
            url = url,
            title = parsed.title,
            author = parsed.author,
            description = parsed.description,
            language = parsed.language,
            chapters = emptyList(),
        )
        val bookId = library.importLocalBook(book, parsed.chapters.map { ChapterText(it.title, it.paragraphs) })
        parsed.cover?.let { bytes ->
            runCatching {
                storage.covers.mkdirs()
                val coverFile = File(storage.covers, "$bookId.jpg")
                coverFile.writeBytes(bytes)
                library.setCover(bookId, coverFile.toURI().toString())
            }.onFailure { Timber.w(it, "Cannot save cover") }
        }
        val format = fileName.substringAfterLast('.', "").lowercase().ifEmpty { parser.extensions.first() }
        ebookDao.upsert(EbookEntity(bookId = bookId, format = format, fileName = fileName, sizeBytes = file.length(), importedAt = System.currentTimeMillis()))
        return bookId
    }

    private fun copyToTemp(open: () -> java.io.InputStream): File {
        val temp = File.createTempFile("import", ".bin", context.cacheDir)
        open().use { input -> temp.outputStream().use { out -> input.copyTo(out) } }
        return temp
    }

    private fun displayName(uri: Uri): String? = runCatching {
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
            if (c.moveToFirst()) c.getString(0) else null
        }
    }.getOrNull()

    companion object {
        private val DRIVE_FILE = Regex("""drive\.google\.com/file/d/([A-Za-z0-9_-]+)""")
        private val DRIVE_OPEN = Regex("""drive\.google\.com/(?:open|uc)\?(?:.*&)?id=([A-Za-z0-9_-]+)""")
        private val DISPOSITION_NAME = Regex("""filename\*?=(?:UTF-8'')?"?([^";]+)"?""", RegexOption.IGNORE_CASE)

        /** Google Drive share links → direct download; other URLs unchanged. */
        fun directDownloadUrl(url: String): String {
            val id = DRIVE_FILE.find(url)?.groupValues?.get(1) ?: DRIVE_OPEN.find(url)?.groupValues?.get(1)
            return if (id != null) "https://drive.google.com/uc?export=download&confirm=t&id=$id" else url
        }

        fun fileNameFrom(contentDisposition: String?, url: String, subtype: String?): String {
            contentDisposition?.let { cd ->
                DISPOSITION_NAME.find(cd)?.groupValues?.get(1)?.let { raw ->
                    val decoded = runCatching { java.net.URLDecoder.decode(raw, "UTF-8") }.getOrDefault(raw)
                    if (decoded.isNotBlank()) return decoded.trim()
                }
            }
            val last = url.substringBefore('?').substringBefore('#').substringAfterLast('/')
            if (last.contains('.')) return last
            val ext = when (subtype?.lowercase()) {
                "epub+zip" -> "epub"
                "pdf" -> "pdf"
                "zip", "x-zip-compressed" -> "zip"
                else -> "txt"
            }
            return "${last.ifBlank { "ebook" }}.$ext"
        }
    }
}
