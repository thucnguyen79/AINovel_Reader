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
import vn.esstar.ainovel.core.network.FetchMode
import vn.esstar.ainovel.core.network.FetchOptions
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

        val tocUrl = info.tocUrl ?: parser.tocUrl(bookPage.finalUrl) ?: bookPage.finalUrl
        val firstTocHtml = if (tocUrl == bookPage.finalUrl) bookPage.html else fetcher.fetch(tocUrl, rule.tocOptions()).html
        val chapters = fetchAllTocPages(rule, parser, tocUrl, firstTocHtml)
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
        tocUrl: String,
        firstHtml: String,
    ): List<FetchedChapter> {
        val first = parser.parseTocPage(firstHtml, tocUrl)
        val pages = mutableListOf(first.chapters)
        val totalPages = first.totalPages

        if (totalPages != null && totalPages > 1 && rule.toc.pageUrl != null) {
            // Known page count: fetch the remaining pages concurrently (bounded).
            val bookId = parser.tocBookId(firstHtml, tocUrl)
            val headers = rule.fetch.headers + rule.toc.pageHeaders
            val limit = Semaphore(MAX_CONCURRENT_TOC_FETCHES)
            val rest = coroutineScope {
                (2..minOf(totalPages, rule.toc.maxPages)).map { page ->
                    async {
                        limit.withPermit {
                            val pageUrl = parser.tocPageUrl(tocUrl, page, bookId) ?: return@withPermit emptyList()
                            val body = fetcher.fetch(pageUrl, rule.tocOptions(headers)).html
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
        val page = fetcher.fetch(url, rule.options(rule.chapter.mode, rule.chapter.waitFor))
        parser.parseChapter(page.html, page.finalUrl)
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
