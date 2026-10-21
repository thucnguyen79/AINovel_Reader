package vn.esstar.ainovel.domain.usecase

import javax.inject.Inject
import vn.esstar.ainovel.domain.model.UrlKind
import vn.esstar.ainovel.domain.repository.LibraryRepository
import vn.esstar.ainovel.domain.repository.SourceRepository

/** Result of opening a pasted or tapped URL. */
sealed interface OpenUrlResult {
    data class BookOpened(val bookId: Long) : OpenUrlResult
    data class ChapterOpened(val bookId: Long, val chapterId: Long) : OpenUrlResult
    data class Unsupported(val url: String) : OpenUrlResult
}

/**
 * Resolves a URL to a local book (and chapter): detects the source, fetches book + TOC,
 * stores them, and when the URL is a chapter page returns that chapter too.
 */
class OpenUrlUseCase @Inject constructor(
    private val sources: SourceRepository,
    private val library: LibraryRepository,
) {
    suspend operator fun invoke(rawUrl: String): OpenUrlResult {
        val url = normalize(rawUrl)
        sources.sourceFor(url) ?: return OpenUrlResult.Unsupported(url)

        return when (sources.classifyUrl(url)) {
            UrlKind.BOOK -> OpenUrlResult.BookOpened(importBook(url))
            UrlKind.CHAPTER -> openChapter(url)
            UrlKind.UNKNOWN -> {
                // Try the chapter page first (it tells us the book URL), then the book page.
                // A page that is neither (home page, category list) is reported as unsupported.
                runCatching { openChapter(url) }
                    .recoverCatching { OpenUrlResult.BookOpened(importBook(url)) }
                    .getOrElse { OpenUrlResult.Unsupported(url) }
            }
        }
    }

    private suspend fun importBook(bookUrl: String): Long {
        val fetched = sources.fetchBook(bookUrl)
        return library.upsertBook(fetched)
    }

    private suspend fun openChapter(chapterUrl: String): OpenUrlResult {
        val page = sources.fetchChapter(chapterUrl)
        val bookUrl = page.bookUrl ?: return OpenUrlResult.Unsupported(chapterUrl)
        val bookId = library.findBookByUrl(bookUrl)?.id ?: importBook(bookUrl)
        val chapter = library.findChapterByUrl(bookId, chapterUrl)
            ?: run {
                // Chapter list may be stale; refresh once.
                importBook(bookUrl)
                library.findChapterByUrl(bookId, chapterUrl)
            }
        return if (chapter != null) {
            OpenUrlResult.ChapterOpened(bookId, chapter.id)
        } else {
            OpenUrlResult.BookOpened(bookId)
        }
    }

    private fun normalize(raw: String): String {
        var url = raw.trim()
        if (!url.startsWith("http://") && !url.startsWith("https://")) url = "https://$url"
        return url.substringBefore('#')
    }
}
