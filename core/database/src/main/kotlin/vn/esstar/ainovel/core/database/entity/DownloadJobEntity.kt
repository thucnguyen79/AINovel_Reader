package vn.esstar.ainovel.core.database.entity

import androidx.room.ColumnInfo
import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "downloads",
    foreignKeys = [
        ForeignKey(entity = BookEntity::class, parentColumns = ["id"], childColumns = ["book_id"], onDelete = ForeignKey.CASCADE),
    ],
    indices = [Index("book_id"), Index("state")],
)
data class DownloadJobEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "book_id") val bookId: Long,
    @ColumnInfo(name = "from_index") val fromIndex: Int,
    @ColumnInfo(name = "to_index") val toIndex: Int,
    /** QUEUED, RUNNING, PAUSED, DONE, FAILED, CANCELLED. */
    val state: String,
    val completed: Int = 0,
    val failed: Int = 0,
    val error: String? = null,
    @ColumnInfo(name = "work_id") val workId: String? = null,
    @ColumnInfo(name = "created_at") val createdAt: Long,
    @ColumnInfo(name = "updated_at") val updatedAt: Long,
)

/** Aggregates for the downloads tab: one row per book that has any stored chapter. */
data class BookStorageRow(
    @Embedded val book: BookEntity,
    @ColumnInfo(name = "chapter_count") val chapterCount: Int,
    @ColumnInfo(name = "downloaded_count") val downloadedCount: Int,
    @ColumnInfo(name = "cached_count") val cachedCount: Int,
)
