package vn.esstar.ainovel.data.source.parser

import java.net.URLEncoder
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import vn.esstar.ainovel.data.source.model.SourceRule
import vn.esstar.ainovel.data.source.selector.all
import vn.esstar.ainovel.data.source.selector.first
import vn.esstar.ainovel.data.source.selector.toSelector
import vn.esstar.ainovel.domain.model.FetchedChapter
import vn.esstar.ainovel.domain.model.FetchedChapterContent
import vn.esstar.ainovel.domain.model.SearchResult
import vn.esstar.ainovel.domain.model.SourceId
import vn.esstar.ainovel.domain.model.UrlKind

/** Book info without chapters, as parsed from the book page. */
data class ParsedBookInfo(
    val title: String,
    val author: String?,
    val coverUrl: String?,
    val description: String?,
    val status: String?,
    val genres: List<String>,
    val tocUrl: String?,
)

/** One TOC page: its chapters plus how to reach the remaining pages. */
data class ParsedTocPage(
    val chapters: List<FetchedChapter>,
    val nextUrl: String?,
    val totalPages: Int?,
)

/** Pure functions that apply a [SourceRule] to HTML. No I/O, so fully unit-testable with fixtures. */
class RuleParser(private val rule: SourceRule) {

    private val removeLinePatterns: List<Regex> by lazy {
        rule.chapter.removeLines.map { Regex(it, RegexOption.IGNORE_CASE) }
    }

    fun parse(html: String, baseUrl: String): Document = Jsoup.parse(html, baseUrl)

    fun classify(url: String): UrlKind {
        val chapterRx = rule.urls.chapter?.let(::Regex)
        val bookRx = rule.urls.book?.let(::Regex)
        return when {
            chapterRx != null && chapterRx.containsMatchIn(url) -> UrlKind.CHAPTER
            bookRx != null && bookRx.containsMatchIn(url) -> UrlKind.BOOK
            else -> UrlKind.UNKNOWN
        }
    }

    fun searchUrl(query: String): String? = rule.search?.url
        ?.replace("{base}", rule.baseUrl.trimEnd('/'))
        ?.replace("{query}", URLEncoder.encode(query, "UTF-8"))

    fun parseSearch(html: String, pageUrl: String): List<SearchResult> {
        val search = rule.search ?: return emptyList()
        val doc = parse(html, pageUrl)
        val titleSel = search.title.toSelector()
        val linkSel = search.link.toSelector()
        return doc.select(search.list).mapNotNull { row ->
            val title = titleSel.first(row) ?: return@mapNotNull null
            val url = linkSel.first(row) ?: return@mapNotNull null
            SearchResult(
                sourceId = SourceId(rule.id),
                title = title,
                url = url,
                author = row.first(search.author),
                coverUrl = row.first(search.cover),
                latestChapter = row.first(search.latestChapter),
            )
        }
    }

    fun parseBookInfo(html: String, pageUrl: String): ParsedBookInfo {
        val doc = parse(html, pageUrl)
        val b = rule.book
        return ParsedBookInfo(
            title = doc.first(b.title)
                ?: throw SourceParseException("Title selector '${b.title}' matched nothing at $pageUrl (not a book page?)"),
            author = doc.first(b.author),
            coverUrl = doc.first(b.cover),
            description = doc.first(b.description)?.let(::htmlToPlainText),
            status = doc.first(b.status),
            genres = doc.all(b.genres),
            tocUrl = doc.first(b.tocUrl),
        )
    }

    /** Parses one TOC page. [startIndex] is the index to assign to the first chapter found. */
    fun parseTocPage(html: String, pageUrl: String, startIndex: Int = 0): ParsedTocPage {
        val doc = parse(html, pageUrl)
        val t = rule.toc
        val titleSel = t.title.toSelector()
        val linkSel = t.link.toSelector()
        val chapters = doc.select(t.list).mapNotNull { el ->
            val url = linkSel.first(el) ?: return@mapNotNull null
            val title = titleSel.first(el) ?: url
            url to title
        }.mapIndexed { i, (url, title) -> FetchedChapter(index = startIndex + i, title = title, url = url) }
        return ParsedTocPage(
            chapters = chapters,
            nextUrl = doc.first(t.next),
            totalPages = doc.first(t.totalPages)?.toIntOrNull(),
        )
    }

    /** Builds the URL of TOC page [page] (1-based) from the template, when the rule has one. */
    fun tocPageUrl(bookUrl: String, page: Int): String? = rule.toc.pageUrl
        ?.replace("{url}", if (bookUrl.endsWith("/")) bookUrl else "$bookUrl/")
        ?.replace("{page}", page.toString())

    fun parseChapter(html: String, pageUrl: String): FetchedChapterContent {
        val doc = parse(html, pageUrl)
        val c = rule.chapter
        val content = doc.selectFirst(c.content)
            ?: throw SourceParseException("Content selector '${c.content}' matched nothing at $pageUrl")
        val paragraphs = ContentCleaner.toParagraphs(content, c.remove, removeLinePatterns)
        if (paragraphs.isEmpty()) throw SourceParseException("Empty chapter content at $pageUrl")
        return FetchedChapterContent(
            url = pageUrl,
            title = doc.first(c.title),
            paragraphs = paragraphs,
            bookUrl = doc.first(c.bookUrl),
            prevUrl = doc.first(c.prev),
            nextUrl = doc.first(c.next),
        )
    }

    private fun htmlToPlainText(html: String): String {
        val body = Jsoup.parseBodyFragment(html).body()
        return ContentCleaner.toParagraphs(body).joinToString("\n")
    }
}

class SourceParseException(message: String, cause: Throwable? = null) : RuntimeException(message, cause)
