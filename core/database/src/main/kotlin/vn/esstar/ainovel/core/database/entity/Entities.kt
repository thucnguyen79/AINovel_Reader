package vn.esstar.ainovel.core.database.entity

import androidx.room.ColumnInfo
import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "books",
    indices = [Index(value = ["url"], unique = true), Index("source_id")],
)
data class BookEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "source_id") val sourceId: String,
    val url: String,
    val title: String,
    val author: String?,
    @ColumnInfo(name = "cover_url") val coverUrl: String?,
    val description: String?,
    val status: String?,
    /** Comma-separated genre names. */
    val genres: String?,
    val language: String,
    @ColumnInfo(name = "is_favorite") val isFavorite: Boolean = false,
    @ColumnInfo(name = "created_at") val createdAt: Long,
    @ColumnInfo(name = "updated_at") val updatedAt: Long,
)

@Entity(
    tableName = "chapters",
    foreignKeys = [
        ForeignKey(entity = BookEntity::class, parentColumns = ["id"], childColumns = ["book_id"], onDelete = ForeignKey.CASCADE),
    ],
    indices = [Index(value = ["book_id", "url"], unique = true), Index(value = ["book_id", "idx"])],
)
data class ChapterEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "book_id") val bookId: Long,
    @ColumnInfo(name = "idx") val index: Int,
    val title: String,
    val url: String,
    /** Relative path of the cached text file, null when not cached yet. */
    @ColumnInfo(name = "content_path") val contentPath: String? = null,
    /** Set when the user explicitly downloaded the chapter (phase 3); cached-on-read leaves it null. */
    @ColumnInfo(name = "downloaded_at") val downloadedAt: Long? = null,
)

@Entity(
    tableName = "reading_progress",
    foreignKeys = [
        ForeignKey(entity = BookEntity::class, parentColumns = ["id"], childColumns = ["book_id"], onDelete = ForeignKey.CASCADE),
    ],
)
data class ReadingProgressEntity(
    @PrimaryKey @ColumnInfo(name = "book_id") val bookId: Long,
    @ColumnInfo(name = "chapter_id") val chapterId: Long,
    @ColumnInfo(name = "sentence_index") val sentenceIndex: Int,
    @ColumnInfo(name = "scroll_offset") val scrollOffset: Int,
    val percent: Float,
    @ColumnInfo(name = "updated_at") val updatedAt: Long,
)

@Entity(
    tableName = "history",
    foreignKeys = [
        ForeignKey(entity = BookEntity::class, parentColumns = ["id"], childColumns = ["book_id"], onDelete = ForeignKey.CASCADE),
    ],
    indices = [Index("last_at")],
)
data class HistoryEntity(
    @PrimaryKey @ColumnInfo(name = "book_id") val bookId: Long,
    @ColumnInfo(name = "last_chapter_id") val lastChapterId: Long,
    @ColumnInfo(name = "last_at") val lastAt: Long,
    /** READ or LISTEN. */
    val mode: String,
)

/** History row joined with its book and last chapter. */
data class HistoryWithBook(
    @Embedded val history: HistoryEntity,
    @Embedded(prefix = "b_") val book: BookEntity,
    @Embedded(prefix = "ch_") val chapter: ChapterEntity?,
)

/** User word override for the convert engine; `book_id` null = applies to every book. */
@Entity(
    tableName = "glossary",
    foreignKeys = [
        ForeignKey(entity = BookEntity::class, parentColumns = ["id"], childColumns = ["book_id"], onDelete = ForeignKey.CASCADE),
    ],
    indices = [Index(value = ["book_id", "source"]), Index("source")],
)
data class GlossaryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "book_id") val bookId: Long?,
    val source: String,
    val target: String,
    @ColumnInfo(name = "updated_at") val updatedAt: Long,
)

/** One row per imported ebook; the book itself lives in `books` with source_id = "ebook". */
@Entity(
    tableName = "ebooks",
    foreignKeys = [
        ForeignKey(entity = BookEntity::class, parentColumns = ["id"], childColumns = ["book_id"], onDelete = ForeignKey.CASCADE),
    ],
)
data class EbookEntity(
    @PrimaryKey @ColumnInfo(name = "book_id") val bookId: Long,
    val format: String,
    @ColumnInfo(name = "file_name") val fileName: String,
    @ColumnInfo(name = "size_bytes") val sizeBytes: Long,
    @ColumnInfo(name = "imported_at") val importedAt: Long,
)

data class EbookWithBook(
    @Embedded val ebook: EbookEntity,
    @Embedded(prefix = "b_") val book: BookEntity,
)
