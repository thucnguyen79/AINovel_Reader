package vn.esstar.ainovel.domain.model

import kotlinx.serialization.Serializable

/** Stable identifier of a story source (website rule or local import). */
@JvmInline
@Serializable
value class SourceId(val value: String)

@Serializable
data class Book(
    val id: Long = 0,
    val sourceId: SourceId,
    val url: String,
    val title: String,
    val author: String? = null,
    val coverUrl: String? = null,
    val description: String? = null,
    val language: String = "vi",
    val isFavorite: Boolean = false,
)

@Serializable
data class Chapter(
    val id: Long = 0,
    val bookId: Long,
    val index: Int,
    val title: String,
    val url: String,
    val isDownloaded: Boolean = false,
)

/** Where the user left off inside a chapter, shared by the reader and the player. */
@Serializable
data class ReadingPosition(
    val bookId: Long,
    val chapterId: Long,
    val sentenceIndex: Int = 0,
    val scrollOffset: Int = 0,
    val percent: Float = 0f,
)
