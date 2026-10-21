package vn.esstar.ainovel.domain.repository

import kotlinx.coroutines.flow.Flow
import vn.esstar.ainovel.domain.model.BookDownloadInfo
import vn.esstar.ainovel.domain.model.DownloadJob
import vn.esstar.ainovel.domain.model.StorageInfo

/** Offline downloads of chapter text, run in the background by WorkManager. */
interface DownloadRepository {
    /**
     * Queues chapters [fromIndex]..[fromIndex]+[count]-1 of [bookId]. A book has at most one active job;
     * a new request while one is running is rejected (returns null).
     */
    suspend fun enqueue(bookId: Long, fromIndex: Int, count: Int): DownloadJob?

    suspend fun cancel(jobId: Long)

    fun observeJobs(): Flow<List<DownloadJob>>
    fun observeActiveJob(bookId: Long): Flow<DownloadJob?>

    /** Books that have at least one downloaded or cached chapter, with sizes. */
    fun observeDownloadedBooks(): Flow<List<BookDownloadInfo>>

    /** Removes downloaded/cached chapter files of a book (keeps the book and its progress). */
    suspend fun deleteBookContent(bookId: Long)

    suspend fun storageInfo(): StorageInfo

    /** Deletes cached (not explicitly downloaded) chapter files and the audio cache. */
    suspend fun clearCaches()
}
