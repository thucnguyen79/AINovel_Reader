package vn.esstar.ainovel.domain.model

/** A book as parsed from a source page, before it is stored. */
data class FetchedBook(
    val sourceId: SourceId,
    val url: String,
    val title: String,
    val author: String? = null,
    val coverUrl: String? = null,
    val description: String? = null,
    val status: String? = null,
    val genres: List<String> = emptyList(),
    val language: String = "vi",
    val chapters: List<FetchedChapter>,
)

data class FetchedChapter(
    val index: Int,
    val title: String,
    val url: String,
)

/** A chapter page as parsed from a source. */
data class FetchedChapterContent(
    val url: String,
    val title: String?,
    val paragraphs: List<String>,
    val bookUrl: String?,
    val prevUrl: String?,
    val nextUrl: String?,
)

/** What kind of page a URL points to, according to the source rule. */
enum class UrlKind { BOOK, CHAPTER, UNKNOWN }
