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
    /** `http` (OkHttp), `webview` (render JS first) or `webview-cf` (wait for Cloudflare). */
    val mode: String = "http",
    val headers: Map<String, String> = emptyMap(),
    val charset: String? = null,
)

/** Regexes (matched with `containsMatchIn`) that tell book URLs and chapter URLs apart. */
@Serializable
data class UrlPatterns(
    val book: String? = null,
    val chapter: String? = null,
)

@Serializable
data class SearchRule(
    /** Template with `{base}` and `{query}` placeholders. */
    val url: String,
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
    val list: String,
    val title: String = "@text",
    val link: String = "@href",
    /** Selector of the next TOC page link; followed until it yields nothing. */
    val next: String? = null,
    /** Selector giving the total number of TOC pages; used with [pageUrl]. */
    val totalPages: String? = null,
    /** Template with `{url}` (book URL, trailing slash) and `{page}` placeholders. */
    val pageUrl: String? = null,
    /** True when the site lists newest chapters first. */
    val reverse: Boolean = false,
    /** Safety cap on the number of TOC pages followed. */
    val maxPages: Int = 500,
)

@Serializable
data class ChapterRule(
    val title: String? = null,
    val content: String,
    /** CSS selectors removed from the content element before extracting text. */
    val remove: List<String> = emptyList(),
    /** Regexes; any paragraph matching one of them is dropped. */
    val removeLines: List<String> = emptyList(),
    val bookUrl: String? = null,
    val prev: String? = null,
    val next: String? = null,
)
