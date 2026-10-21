package vn.esstar.ainovel.service.download

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.WorkManager
import androidx.work.workDataOf
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import vn.esstar.ainovel.core.common.di.AppDispatcher
import vn.esstar.ainovel.core.common.di.Dispatcher
import vn.esstar.ainovel.core.database.dao.ChapterDao
import vn.esstar.ainovel.core.database.dao.DownloadDao
import vn.esstar.ainovel.core.database.entity.BookEntity
import vn.esstar.ainovel.core.database.entity.DownloadJobEntity
import vn.esstar.ainovel.core.storage.AppStorage
import vn.esstar.ainovel.core.storage.ChapterStore
import vn.esstar.ainovel.domain.model.Book
import vn.esstar.ainovel.domain.model.BookDownloadInfo
import vn.esstar.ainovel.domain.model.DownloadJob
import vn.esstar.ainovel.domain.model.DownloadState
import vn.esstar.ainovel.domain.model.SourceId
import vn.esstar.ainovel.domain.model.StorageInfo
import vn.esstar.ainovel.domain.repository.DownloadRepository

@Singleton
class DownloadRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val downloadDao: DownloadDao,
    private val chapterDao: ChapterDao,
    private val chapterStore: ChapterStore,
    private val storage: AppStorage,
    @Dispatcher(AppDispatcher.IO) private val io: CoroutineDispatcher,
) : DownloadRepository {

    private val workManager: WorkManager get() = WorkManager.getInstance(context)

    override suspend fun enqueue(bookId: Long, fromIndex: Int, count: Int): DownloadJob? {
        if (downloadDao.getActiveForBook(bookId) != null) return null
        val total = chapterDao.countByBook(bookId)
        if (total == 0) return null
        val from = fromIndex.coerceIn(0, total - 1)
        val to = (from + count - 1).coerceIn(from, total - 1)
        val now = System.currentTimeMillis()
        val id = downloadDao.insert(
            DownloadJobEntity(bookId = bookId, fromIndex = from, toIndex = to, state = DownloadState.QUEUED.name, createdAt = now, updatedAt = now),
        )
        val request = OneTimeWorkRequestBuilder<ChapterDownloadWorker>()
            .setInputData(workDataOf(ChapterDownloadWorker.KEY_JOB_ID to id))
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
            .addTag(TAG)
            .build()
        downloadDao.setWorkId(id, request.id.toString())
        workManager.enqueueUniqueWork(ChapterDownloadWorker.uniqueName(bookId), ExistingWorkPolicy.KEEP, request)
        return checkNotNull(downloadDao.getById(id)).toDomain()
    }

    override suspend fun cancel(jobId: Long) {
        val job = downloadDao.getById(jobId) ?: return
        job.workId?.let { runCatching { workManager.cancelWorkById(UUID.fromString(it)) } }
        downloadDao.setState(jobId, DownloadState.CANCELLED.name, null, System.currentTimeMillis())
    }

    override fun observeJobs(): Flow<List<DownloadJob>> = downloadDao.observeAll().map { list -> list.map { it.toDomain() } }

    override fun observeActiveJob(bookId: Long): Flow<DownloadJob?> = downloadDao.observeActiveForBook(bookId).map { it?.toDomain() }

    override fun observeDownloadedBooks(): Flow<List<BookDownloadInfo>> =
        combine(downloadDao.observeStoredBooks(), downloadDao.observeAll()) { rows, jobs ->
            val activeByBook = jobs.filter { it.state in ACTIVE }.associateBy { it.bookId }
            rows.map { row ->
                BookDownloadInfo(
                    book = row.book.toDomain(),
                    chapterCount = row.chapterCount,
                    downloadedCount = row.downloadedCount,
                    cachedCount = row.cachedCount,
                    sizeBytes = chapterStore.sizeOfBook(row.book.id),
                    activeJob = activeByBook[row.book.id]?.toDomain(),
                )
            }
        }

    override suspend fun deleteBookContent(bookId: Long) {
        downloadDao.getActiveForBook(bookId)?.let { cancel(it.id) }
        chapterStore.deleteBook(bookId)
        downloadDao.clearBookContent(bookId)
    }

    override suspend fun storageInfo(): StorageInfo = withContext(io) {
        StorageInfo(
            chapterBytes = storage.chapters.walkBottomUp().filter { it.isFile }.sumOf { it.length() },
            audioCacheBytes = storage.audioCache.walkBottomUp().filter { it.isFile }.sumOf { it.length() },
        )
    }

    override suspend fun clearCaches() {
        val paths = downloadDao.cachedOnlyPaths()
        withContext(io) {
            paths.forEach { storage.resolve(it).delete() }
            storage.audioCache.listFiles()?.forEach { it.delete() }
        }
        downloadDao.clearCachedOnly()
    }

    private fun DownloadJobEntity.toDomain() = DownloadJob(
        id = id,
        bookId = bookId,
        fromIndex = fromIndex,
        toIndex = toIndex,
        state = runCatching { DownloadState.valueOf(state) }.getOrDefault(DownloadState.FAILED),
        completed = completed,
        failed = failed,
        error = error,
        createdAt = createdAt,
        updatedAt = updatedAt,
    )

    private fun BookEntity.toDomain() = Book(
        id = id,
        sourceId = SourceId(sourceId),
        url = url,
        title = title,
        author = author,
        coverUrl = coverUrl,
        description = description,
        status = status,
        genres = genres?.split(',')?.filter { it.isNotBlank() } ?: emptyList(),
        language = language,
        isFavorite = isFavorite,
    )

    private companion object {
        const val TAG = "chapter-download"
        val ACTIVE = setOf(DownloadState.QUEUED.name, DownloadState.RUNNING.name, DownloadState.PAUSED.name)
    }
}
