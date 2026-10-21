package vn.esstar.ainovel.domain.model

import kotlinx.serialization.Serializable

/** Stable identifier of a story source (website rule or local import). */
@JvmInline
@Serializable
value class SourceId(val value: String)

/** Public description of a supported source, shown in the "Website hỗ trợ" list. */
data class SourceInfo(
    val id: SourceId,
    val name: String,
    val baseUrl: String,
    val hosts: List<String>,
    val language: String,
    val supportsSearch: Boolean,
)

data class Book(
    val id: Long = 0,
    val sourceId: SourceId,
    val url: String,
    val title: String,
    val author: String? = null,
    val coverUrl: String? = null,
    val description: String? = null,
    val status: String? = null,
    val genres: List<String> = emptyList(),
    val language: String = "vi",
    val isFavorite: Boolean = false,
    val chapterCount: Int = 0,
)

data class Chapter(
    val id: Long = 0,
    val bookId: Long,
    val index: Int,
    val title: String,
    val url: String,
    val isCached: Boolean = false,
    val isDownloaded: Boolean = false,
)

/** Text of one chapter, already cleaned and split into paragraphs. */
data class ChapterContent(
    val chapterId: Long,
    val title: String,
    val paragraphs: List<String>,
) {
    val text: String get() = paragraphs.joinToString("\n\n")
}

/** Where the user left off inside a chapter, shared by the reader and the player. */
data class ReadingPosition(
    val bookId: Long,
    val chapterId: Long,
    val sentenceIndex: Int = 0,
    val scrollOffset: Int = 0,
    val percent: Float = 0f,
)

enum class ReadingMode { READ, LISTEN }

data class HistoryEntry(
    val book: Book,
    val lastChapter: Chapter?,
    val lastAt: Long,
    val mode: ReadingMode,
)

data class SearchResult(
    val sourceId: SourceId,
    val title: String,
    val url: String,
    val author: String? = null,
    val coverUrl: String? = null,
    val latestChapter: String? = null,
)
