package vn.esstar.ainovel.data.source.model

import kotlinx.serialization.Serializable

/**
 * Declarative description of one story website. Loaded from `assets/sources/<id>.json`
 * (and later from a remote index). See `docs/SOURCE_RULES.md` for the selector syntax.
 */
@Serializable
data class SourceRule(
    val id: String,
    val name: String,
    val baseUrl: String,
    val hosts: List<String>,
    val lang: String = "vi",
    val fetch: FetchRule = FetchRule(),
    val urls: UrlPatterns = UrlPatterns(),
    val search: SearchRule? = null,
    val book: BookRule,
    val toc: TocRule,
    val chapter: ChapterRule,
)

@Serializable
data class FetchRule(
    /** `http` (OkHttp) or `webview` (hidden WebView, JS runs first). Parts may override with their own `mode`. */
    val mode: String = "http",
    val headers: Map<String, String> = emptyMap(),
    val charset: String? = null,
    /** CSS selector the WebView waits for before returning the DOM; parts may override. */
    val waitFor: String? = null,
    val timeoutMs: Long? = null,
)

/** Regexes (matched with `containsMatchIn`) that tell book URLs and chapter URLs apart. */
@Serializable
data class UrlPatterns(
    val book: String? = null,
    val chapter: String? = null,
    /** Applied in order before classifying/fetching, e.g. mobile host → desktop host, strip tracking query. */
    val rewrite: List<UrlRewrite> = emptyList(),
)

@Serializable
data class UrlRewrite(
    /** Kotlin regex matched against the whole URL (`containsMatchIn`). */
    val pattern: String,
    /** Replacement; `$1` refers to capture groups. */
    val replacement: String,
)

@Serializable
data class SearchRule(
    /** Overrides `fetch.mode` / `fetch.waitFor` for this part only. */
    val mode: String? = null,
    val waitFor: String? = null,
    /** Template with `{base}` and `{query}` placeholders. */
    val url: String,
    /** Charset used to URL-encode `{query}` (e.g. `GBK` for jjwxc); default UTF-8. */
    val queryCharset: String? = null,
    val list: String,
    val title: String,
    val link: String = "@href",
    val author: String? = null,
    val cover: String? = null,
    val latestChapter: String? = null,
    val next: String? = null,
)

@Serializable
data class BookRule(
    /** Overrides `fetch.mode` / `fetch.waitFor` for this part only. */
    val mode: String? = null,
    val waitFor: String? = null,
    val title: String,
    val author: String? = null,
    val cover: String? = null,
    val description: String? = null,
    val status: String? = null,
    val genres: String? = null,
    /** Optional selector giving the URL of the first TOC page when it differs from the book page. */
    val tocUrl: String? = null,
)

@Serializable
data class TocRule(
    /** Overrides `fetch.mode` / `fetch.waitFor` for this part only. */
    val mode: String? = null,
    val waitFor: String? = null,
    /**
     * Template of the TOC page URL when it differs from the book page and no `book.tocUrl` selector
     * exists: `{url}` = book URL without query/trailing slash, `{base}` = site base URL.
     */
    val url: String? = null,
    /** Selector of each chapter link on an HTML TOC page. Empty only when the whole TOC is JSON ([url] + [jsonItems]). */
    val list: String = "",
    val title: String = "@text",
    val link: String = "@href",
    /** Selector of the next TOC page link; followed until it yields nothing. */
    val next: String? = null,
    /** Selector giving the total number of TOC pages; used with [pageUrl]. */
    val totalPages: String? = null,
    /**
     * Template for TOC pages 2..N. Placeholders: `{url}` (book URL without trailing slash), `{base}` (site base URL),
     * `{bookId}` (value of [bookId]) and `{page}`.
     */
    val pageUrl: String? = null,
    /** Selector giving the site's internal book id, for [pageUrl] templates that need it. */
    val bookId: String? = null,
    /** Selector used instead of [list] on pages 2..N when they are HTML fragments with a different structure. */
    val pageList: String? = null,
    /** When pages 2..N are JSON, the field holding the HTML fragment (dot path, e.g. `data` or `result.html`). */
    val pageJson: String? = null,
    /** Extra headers for pages 2..N (e.g. `X-Requested-With` for AJAX endpoints). */
    val pageHeaders: Map<String, String> = emptyMap(),
    /**
     * HTTP method of the TOC pages fetched apart from the book page (the first page when [url] / `book.tocUrl`
     * points elsewhere, and pages 2..N): `GET` (default) or `POST`. POST always goes through OkHttp, never the WebView.
     */
    val pageMethod: String = "GET",
    /**
     * Form body (`application/x-www-form-urlencoded`) sent with POST TOC pages. Placeholders as in [pageUrl]:
     * `{url}`, `{base}`, `{bookId}`, `{page}` (1 for the first page). Null or empty sends an empty form.
     */
    val pageBody: String? = null,
    /**
     * When the site exposes the chapter count but not the page count: the number of chapters per TOC page.
     * Total pages = ceil([totalChapters] / pageSize). Ignored when [totalPages] matches.
     */
    val pageSize: Int? = null,
    /** Selector giving the total number of chapters; used together with [pageSize]. */
    val totalChapters: String? = null,
    /**
     * When pages 2..N are JSON arrays of chapter objects: dot path to the array (e.g. `chapters`).
     * Each item yields a chapter from [jsonTitle] and [jsonLink]. When [url] is also set, page 1 is JSON too
     * (fetched from [url]; [totalPages] / [totalChapters] and [bookId] are then read from the book page).
     */
    val jsonItems: String? = null,
    /** Field of a JSON item holding the chapter title, or a template over its fields (`Chương {chapter_number}`). */
    val jsonTitle: String = "title",
    /** Template of the chapter URL for a JSON item: `{url}`, `{base}` and `{<field>}` placeholders. */
    val jsonLink: String? = null,
    /** True when the site lists newest chapters first. */
    val reverse: Boolean = false,
    /** Safety cap on the number of TOC pages followed. */
    val maxPages: Int = 500,
)

@Serializable
data class ChapterRule(
    /** Overrides `fetch.mode` / `fetch.waitFor` for this part only. */
    val mode: String? = null,
    val waitFor: String? = null,
    val title: String? = null,
    val content: String,
    /** CSS selectors removed from the content element before extracting text. */
    val remove: List<String> = emptyList(),
    /** Regexes; any paragraph matching one of them is dropped. */
    val removeLines: List<String> = emptyList(),
    val bookUrl: String? = null,
    val prev: String? = null,
    val next: String? = null,
    /**
     * Selector of the link to the next part of the *same* chapter, for sites that split a chapter over
     * several pages (`xxx_2.html`). Parts are fetched and appended until it yields nothing.
     */
    val nextPage: String? = null,
    /** Safety cap on the number of parts followed through [nextPage]. */
    val maxPages: Int = 30,
)
