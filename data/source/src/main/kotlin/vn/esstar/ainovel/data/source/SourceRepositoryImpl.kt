package vn.esstar.ainovel.data.source

import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import timber.log.Timber
import vn.esstar.ainovel.core.common.di.AppDispatcher
import vn.esstar.ainovel.core.common.di.Dispatcher
import vn.esstar.ainovel.core.network.FetchMethod
import vn.esstar.ainovel.core.network.FetchMode
import vn.esstar.ainovel.core.network.FetchOptions
import vn.esstar.ainovel.core.network.FetchedPage
import vn.esstar.ainovel.core.network.HtmlFetcher
import vn.esstar.ainovel.data.source.model.SourceRule
import vn.esstar.ainovel.data.source.parser.RuleParser
import vn.esstar.ainovel.data.source.parser.SourceParseException
import vn.esstar.ainovel.data.source.registry.SourceRegistry
import vn.esstar.ainovel.domain.model.FetchedBook
import vn.esstar.ainovel.domain.model.FetchedChapter
import vn.esstar.ainovel.domain.model.FetchedChapterContent
import vn.esstar.ainovel.domain.model.SearchResult
import vn.esstar.ainovel.domain.model.SourceId
import vn.esstar.ainovel.domain.model.SourceInfo
import vn.esstar.ainovel.domain.model.UrlKind
import vn.esstar.ainovel.domain.repository.SourceRepository

class UnsupportedSourceException(url: String) : IllegalArgumentException("No source rule for $url")

@Singleton
class SourceRepositoryImpl @Inject constructor(
    private val registry: SourceRegistry,
    private val fetcher: HtmlFetcher,
    @Dispatcher(AppDispatcher.IO) private val io: CoroutineDispatcher,
) : SourceRepository {

    override suspend fun sources(): List<SourceInfo> = registry.all().map { it.toInfo() }

    override suspend fun sourceFor(url: String): SourceInfo? = registry.forUrl(url)?.toInfo()

    override suspend fun classifyUrl(url: String): UrlKind {
        val rule = registry.forUrl(url) ?: return UrlKind.UNKNOWN
        return registry.parser(rule).classify(url)
    }

    override suspend fun fetchBook(rawUrl: String): FetchedBook = withContext(io) {
        val rule = registry.forUrl(rawUrl) ?: throw UnsupportedSourceException(rawUrl)
        val parser = registry.parser(rule)
        val url = parser.canonicalUrl(rawUrl)

        val bookPage = fetcher.fetch(url, rule.options(rule.book.mode, rule.book.waitFor))
        val info = parser.parseBookInfo(bookPage.html, bookPage.finalUrl)

        // The book id (for POST bodies / page URLs) usually sits on the book page; a separate TOC page may carry it too.
        val bookId = parser.tocBookId(bookPage.html, bookPage.finalUrl)
        val tocUrl = info.tocUrl ?: parser.tocUrl(bookPage.finalUrl, bookId) ?: bookPage.finalUrl
        val firstTocHtml = if (tocUrl == bookPage.finalUrl) {
            bookPage.html
        } else {
            fetcher.fetch(tocUrl, rule.tocPageOptions(parser.tocPageBody(tocUrl, 1, bookId))).html
        }
        val chapters = fetchAllTocPages(rule, parser, bookPage, tocUrl, firstTocHtml, bookId)
        if (chapters.isEmpty()) throw SourceParseException("No chapters found at $tocUrl")

        FetchedBook(
            sourceId = SourceId(rule.id),
            url = bookPage.finalUrl,
            title = info.title,
            author = info.author,
            coverUrl = info.coverUrl,
            description = info.description,
            status = info.status,
            genres = info.genres,
            language = rule.lang,
            chapters = chapters,
        )
    }

    private suspend fun fetchAllTocPages(
        rule: SourceRule,
        parser: RuleParser,
        bookPage: FetchedPage,
        tocUrl: String,
        firstHtml: String,
        knownBookId: String?,
    ): List<FetchedChapter> {
        val bookUrl = bookPage.finalUrl
        val first = if (rule.toc.jsonItems != null && tocUrl != bookUrl) {
            // Whole TOC served as JSON: page 1 comes from toc.url, the page count from the book page.
            val meta = parser.parseTocPage(bookPage.html, bookUrl)
            parser.parseTocJson(firstHtml, bookUrl).copy(totalPages = meta.totalPages)
        } else {
            parser.parseTocPage(firstHtml, tocUrl)
        }
        val pages = mutableListOf(first.chapters)
        val totalPages = first.totalPages

        if (totalPages != null && totalPages > 1 && rule.toc.pageUrl != null) {
            // Known page count: fetch the remaining pages concurrently (bounded).
            val bookId = knownBookId ?: parser.tocBookId(firstHtml, tocUrl)
            val limit = Semaphore(MAX_CONCURRENT_TOC_FETCHES)
            val rest = coroutineScope {
                (2..minOf(totalPages, rule.toc.maxPages)).map { page ->
                    async {
                        limit.withPermit {
                            val pageUrl = parser.tocPageUrl(tocUrl, page, bookId) ?: return@withPermit emptyList()
                            val options = rule.tocPageOptions(parser.tocPageBody(tocUrl, page, bookId))
                            val body = fetcher.fetch(pageUrl, options).html
                            if (rule.toc.jsonItems != null) return@withPermit parser.parseTocJson(body, bookUrl).chapters
                            val isFragment = rule.toc.pageJson != null || rule.toc.pageList != null
                            val html = if (rule.toc.pageJson != null) parser.tocHtmlFromJson(body) else body
                            parser.parseTocPage(html, pageUrl, fragment = isFragment).chapters
                        }
                    }
                }.map { it.await() }
            }
            pages += rest
        } else if (rule.toc.next != null) {
            // Unknown page count: follow "next" links sequentially.
            var next = first.nextUrl
            var count = 1
            val seen = hashSetOf(tocUrl)
            while (next != null && count < rule.toc.maxPages && seen.add(next)) {
                val html = fetcher.fetch(next, rule.tocOptions()).html
                val parsed = parser.parseTocPage(html, next)
                pages += parsed.chapters
                next = parsed.nextUrl
                count++
            }
        }

        val flat = pages.flatten().distinctBy { it.url }
        val ordered = if (rule.toc.reverse) flat.asReversed() else flat
        return ordered.mapIndexed { i, c -> c.copy(index = i) }
    }

    override suspend fun fetchChapter(rawUrl: String): FetchedChapterContent = withContext(io) {
        val rule = registry.forUrl(rawUrl) ?: throw UnsupportedSourceException(rawUrl)
        val parser = registry.parser(rule)
        val url = parser.canonicalUrl(rawUrl)
        val options = rule.options(rule.chapter.mode, rule.chapter.waitFor)
        val page = fetcher.fetch(url, options)
        var content = parser.parseChapter(page.html, page.finalUrl)
        if (rule.chapter.nextPage == null) return@withContext content

        // Chapter split over several pages: follow the "next part" link and append the paragraphs.
        var next = parser.chapterNextPage(page.html, page.finalUrl)
        val seen = hashSetOf(page.finalUrl, url)
        var parts = 1
        while (next != null && parts < rule.chapter.maxPages && seen.add(next)) {
            val part = fetcher.fetch(next, options)
            val parsed = runCatching { parser.parseChapter(part.html, part.finalUrl) }
                .onFailure { Timber.w(it, "Chapter part %s failed", next) }
                .getOrNull() ?: break
            content = content.copy(
                paragraphs = content.paragraphs + parsed.paragraphs,
                nextUrl = parsed.nextUrl ?: content.nextUrl,
            )
            next = parser.chapterNextPage(part.html, part.finalUrl)
            parts++
        }
        content
    }

    override suspend fun search(query: String, sourceIds: List<SourceId>): List<SearchResult> = withContext(io) {
        val wanted = sourceIds.map { it.value }.toSet()
        val rules = registry.all().filter { it.search != null && (wanted.isEmpty() || it.id in wanted) }
        coroutineScope {
            rules.map { rule ->
                async {
                    runCatching {
                        val parser = registry.parser(rule)
                        val url = parser.searchUrl(query) ?: return@async emptyList()
                        val page = fetcher.fetch(url, rule.options(rule.search?.mode, rule.search?.waitFor))
                        parser.parseSearch(page.html, page.finalUrl)
                    }.onFailure { Timber.w(it, "Search failed on %s", rule.id) }.getOrDefault(emptyList())
                }
            }.flatMap { it.await() }
        }
    }

    private fun SourceRule.toInfo() = SourceInfo(
        id = SourceId(id),
        name = name,
        baseUrl = baseUrl,
        hosts = hosts,
        language = lang,
        supportsSearch = search != null,
    )

    private companion object {
        const val MAX_CONCURRENT_TOC_FETCHES = 4
    }
}

private fun SourceRule.options(mode: String?, waitFor: String?, headers: Map<String, String> = fetch.headers): FetchOptions = FetchOptions(
    headers = headers,
    charsetHint = fetch.charset,
    mode = FetchMode.parse(mode ?: fetch.mode),
    waitFor = waitFor ?: fetch.waitFor,
    timeoutMs = fetch.timeoutMs ?: FetchOptions.DEFAULT_WEBVIEW_TIMEOUT_MS,
)

private fun SourceRule.tocOptions(headers: Map<String, String> = fetch.headers): FetchOptions = options(toc.mode, toc.waitFor, headers)

/** Options for TOC pages fetched apart from the book page: extra page headers, and the POST body when the rule POSTs. */
private fun SourceRule.tocPageOptions(body: String?): FetchOptions = tocOptions(fetch.headers + toc.pageHeaders).copy(
    method = FetchMethod.parse(toc.pageMethod),
    body = body,
)
