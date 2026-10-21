package vn.esstar.ainovel.domain.model

enum class DownloadState { QUEUED, RUNNING, PAUSED, DONE, FAILED, CANCELLED }

/** One download request: a contiguous range of chapters of a book. */
data class DownloadJob(
    val id: Long,
    val bookId: Long,
    val fromIndex: Int,
    val toIndex: Int,
    val state: DownloadState,
    /** Chapters completed in this job (including ones that were already cached). */
    val completed: Int,
    val failed: Int,
    val error: String? = null,
    val createdAt: Long,
    val updatedAt: Long,
) {
    val total: Int get() = toIndex - fromIndex + 1
    val progress: Float get() = if (total == 0) 0f else completed.toFloat() / total
    val isActive: Boolean get() = state == DownloadState.QUEUED || state == DownloadState.RUNNING || state == DownloadState.PAUSED
}

/** Per-book storage summary for the "Tải truyện" tab. */
data class BookDownloadInfo(
    val book: Book,
    val chapterCount: Int,
    val downloadedCount: Int,
    val cachedCount: Int,
    val sizeBytes: Long,
    val activeJob: DownloadJob? = null,
)

data class StorageInfo(
    val chapterBytes: Long,
    val audioCacheBytes: Long,
)
