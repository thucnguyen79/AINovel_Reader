package vn.esstar.ainovel.data.source.parser

import java.net.URLEncoder
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
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

    /** Canonical form of [url] after the rule's rewrites (mobile host → desktop host, tracking query dropped). */
    fun canonicalUrl(url: String): String =
        rule.urls.rewrite.fold(url) { acc, r -> Regex(r.pattern).replace(acc, r.replacement) }

    fun classify(rawUrl: String): UrlKind {
        val url = canonicalUrl(rawUrl)
        val chapterRx = rule.urls.chapter?.let(::Regex)
        val bookRx = rule.urls.book?.let(::Regex)
        return when {
            chapterRx != null && chapterRx.containsMatchIn(url) -> UrlKind.CHAPTER
            bookRx != null && bookRx.containsMatchIn(url) -> UrlKind.BOOK
            else -> UrlKind.UNKNOWN
        }
    }

    fun searchUrl(query: String): String? = rule.search?.let { search ->
        val charset = search.queryCharset ?: "UTF-8"
        search.url
            .replace("{base}", rule.baseUrl.trimEnd('/'))
            .replace("{query}", URLEncoder.encode(query, charset))
    }

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

    /**
     * Parses one TOC page. [startIndex] is the index to assign to the first chapter found.
     * [fragment] marks pages 2..N delivered as HTML fragments (possibly extracted from JSON), which use [TocRule.pageList].
     */
    fun parseTocPage(html: String, pageUrl: String, startIndex: Int = 0, fragment: Boolean = false): ParsedTocPage {
        val doc = if (fragment) Jsoup.parseBodyFragment(html, baseUrlOf(pageUrl)) else parse(html, pageUrl)
        val t = rule.toc
        val titleSel = t.title.toSelector()
        val linkSel = t.link.toSelector()
        val listSel = if (fragment && t.pageList != null) t.pageList else t.list
        val chapters = if (listSel.isBlank()) emptyList() else doc.select(listSel).mapNotNull { el ->
            val url = linkSel.first(el) ?: return@mapNotNull null
            val title = titleSel.first(el) ?: url
            url to title
        }.mapIndexed { i, (url, title) -> FetchedChapter(index = startIndex + i, title = title, url = url) }
        val totalPages = doc.first(t.totalPages)?.toIntOrNull()
            ?: t.pageSize?.takeIf { it > 0 }?.let { size ->
                doc.first(t.totalChapters)?.filter(Char::isDigit)?.toIntOrNull()?.let { total -> (total + size - 1) / size }
            }
        return ParsedTocPage(
            chapters = chapters,
            nextUrl = doc.first(t.next),
            totalPages = totalPages,
        )
    }

    /**
     * Parses a JSON TOC page (pages 2..N of rules with [TocRule.jsonItems]): each item of the array becomes a
     * chapter whose URL is built from [TocRule.jsonLink] with `{url}`, `{base}` and `{<field>}` placeholders.
     */
    fun parseTocJson(body: String, bookUrl: String, startIndex: Int = 0): ParsedTocPage {
        val t = rule.toc
        val path = t.jsonItems ?: throw SourceParseException("Rule ${rule.id} has no toc.jsonItems")
        var node: JsonElement = Json.parseToJsonElement(body)
        for (key in path.split('.')) {
            node = (node as? JsonObject)?.get(key) ?: throw SourceParseException("JSON field '$path' missing in TOC page")
        }
        val items = node as? JsonArray ?: throw SourceParseException("JSON field '$path' is not an array")
        val linkTemplate = t.jsonLink ?: throw SourceParseException("Rule ${rule.id} has no toc.jsonLink")
        val chapters = items.mapIndexedNotNull { i, item ->
            val obj = item as? JsonObject ?: return@mapIndexedNotNull null
            var link = expand(linkTemplate, bookUrl)
            var title = if ('{' in t.jsonTitle) t.jsonTitle else (obj[t.jsonTitle] as? JsonPrimitive)?.contentOrNull.orEmpty()
            for ((key, value) in obj) {
                val text = (value as? JsonPrimitive)?.contentOrNull ?: ""
                link = link.replace("{$key}", text)
                title = title.replace("{$key}", text)
            }
            title = title.trim().ifEmpty { link }
            FetchedChapter(index = startIndex + i, title = title, url = link)
        }
        return ParsedTocPage(chapters = chapters, nextUrl = null, totalPages = null)
    }

    /** URL of the next part of the same chapter, when the rule declares [ChapterRule.nextPage]. */
    fun chapterNextPage(html: String, pageUrl: String): String? =
        rule.chapter.nextPage?.let { parse(html, pageUrl).first(it) }

    /** Site-internal book id read from the book/TOC page, when the rule declares one. */
    fun tocBookId(html: String, pageUrl: String): String? = rule.toc.bookId?.let { parse(html, pageUrl).first(it) }

    /** TOC URL from [TocRule.url] for a book page URL, or null when the rule has no template. */
    fun tocUrl(bookUrl: String, bookId: String? = null): String? = rule.toc.url?.let { expand(it, bookUrl, bookId = bookId) }

    /** Builds the URL of TOC page [page] (1-based) from the template, when the rule has one. */
    fun tocPageUrl(bookUrl: String, page: Int, bookId: String? = null): String? =
        rule.toc.pageUrl?.let { expand(it, bookUrl, page, bookId) }

    /** Form body of TOC page [page] from [TocRule.pageBody] (same placeholders as [tocPageUrl]), or null without one. */
    fun tocPageBody(bookUrl: String, page: Int, bookId: String? = null): String? =
        rule.toc.pageBody?.let { expand(it, bookUrl, page, bookId) }

    /**
     * Fills the placeholders shared by `toc.url`, `toc.pageUrl`, `toc.pageBody` and `toc.jsonLink`:
     * `{url}` (book URL without query/fragment/trailing slash), `{base}`, `{bookSlug}` (last path segment of `{url}`),
     * `{bookId}`, `{page}` (1-based) and `{page0}` (0-based, for APIs that count groups from zero).
     */
    private fun expand(template: String, bookUrl: String, page: Int? = null, bookId: String? = null): String {
        val cleanUrl = bookUrl.substringBefore('?').substringBefore('#').trimEnd('/')
        var out = template
            .replace("{url}", cleanUrl)
            .replace("{base}", rule.baseUrl.trimEnd('/'))
            .replace("{bookSlug}", cleanUrl.substringAfterLast('/'))
            .replace("{bookId}", bookId ?: "")
        if (page != null) out = out.replace("{page}", page.toString()).replace("{page0}", (page - 1).toString())
        return out
    }

    /** Extracts the HTML fragment from a JSON TOC page using [TocRule.pageJson] (dot path). */
    fun tocHtmlFromJson(body: String): String {
        val path = rule.toc.pageJson ?: return body
        var node: JsonElement = Json.parseToJsonElement(body)
        for (key in path.split('.')) {
            node = (node as? JsonObject)?.get(key) ?: throw SourceParseException("JSON field '$path' missing in TOC page")
        }
        return (node as? JsonPrimitive)?.contentOrNull ?: throw SourceParseException("JSON field '$path' is not a string")
    }

    private fun baseUrlOf(url: String): String = runCatching {
        val u = java.net.URI(url)
        "${u.scheme}://${u.host}/"
    }.getOrDefault(rule.baseUrl)

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
