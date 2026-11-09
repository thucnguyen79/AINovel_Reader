package vn.esstar.ainovel.service.download

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import timber.log.Timber
import vn.esstar.ainovel.domain.model.LlmException
import vn.esstar.ainovel.domain.repository.AiPrefetchScheduler
import vn.esstar.ainovel.domain.repository.LibraryRepository
import vn.esstar.ainovel.domain.repository.LlmRepository
import vn.esstar.ainovel.domain.usecase.GetReadableChapterUseCase

/**
 * Translates the next chapters of a book in AI mode. Runs only on unmetered networks with battery not low;
 * chapters already cached are skipped, so re-running after a retry is cheap. A provider error (quota, network)
 * stops this run and asks WorkManager to retry later with backoff.
 */
@HiltWorker
class AiPrefetchWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val library: LibraryRepository,
    private val llm: LlmRepository,
    private val readable: GetReadableChapterUseCase,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val bookId = inputData.getLong(KEY_BOOK_ID, -1)
        val from = inputData.getInt(KEY_FROM_INDEX, 0)
        val count = inputData.getInt(KEY_COUNT, 0)
        if (bookId < 0 || count <= 0) return Result.failure()
        if (!llm.isAiBook(bookId) || !llm.settings.first().isConfigured) return Result.success()

        val chapters = library.getChapters(bookId).filter { it.index > from && it.index <= from + count }.sortedBy { it.index }
        var translated = 0
        for (chapter in chapters) {
            if (isStopped) break
            if (llm.cached(chapter.id) != null) continue
            try {
                readable(chapter.id, background = true)
                translated++
                delay(CHAPTER_GAP_MS)
                Timber.i("AI prefetch: chapter %d (index %d) of book %d ready", chapter.id, chapter.index, bookId)
            } catch (e: CancellationException) {
                throw e
            } catch (e: LlmException) {
                Timber.w("AI prefetch stopped at chapter %d: %s", chapter.id, e.userMessage)
                return if (runAttemptCount < MAX_RETRIES) Result.retry() else Result.failure()
            } catch (e: Exception) {
                Timber.w(e, "AI prefetch: chapter %d failed, skipping", chapter.id)
            }
        }
        return Result.success(workDataOf(KEY_TRANSLATED to translated))
    }

    companion object {
        const val KEY_BOOK_ID = "bookId"
        const val KEY_FROM_INDEX = "fromIndex"
        const val KEY_COUNT = "count"
        const val KEY_TRANSLATED = "translated"
        const val MAX_RETRIES = 3
        /** Breathing room between chapters so the user's own requests get the per-minute quota. */
        const val CHAPTER_GAP_MS = 10_000L
        const val TAG = "ai-prefetch"
        fun uniqueName(bookId: Long) = "ai-prefetch-$bookId"
    }
}

@Singleton
class AiPrefetchSchedulerImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val llm: LlmRepository,
) : AiPrefetchScheduler {

    private val workManager: WorkManager get() = WorkManager.getInstance(context)

    override suspend fun scheduleAhead(bookId: Long, fromChapterIndex: Int) {
        val count = llm.prefetchCount.first()
        if (count <= 0 || !llm.settings.first().isConfigured || !llm.isAiBook(bookId)) {
            cancel(bookId)
            return
        }
        val request = OneTimeWorkRequestBuilder<AiPrefetchWorker>()
            .setInputData(workDataOf(AiPrefetchWorker.KEY_BOOK_ID to bookId, AiPrefetchWorker.KEY_FROM_INDEX to fromChapterIndex, AiPrefetchWorker.KEY_COUNT to count))
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.UNMETERED)
                    .setRequiresBatteryNotLow(true)
                    .build(),
            )
            .addTag(AiPrefetchWorker.TAG)
            .build()
        // REPLACE: the reading position moved, so the previous window is stale (cached chapters are skipped anyway).
        workManager.enqueueUniqueWork(AiPrefetchWorker.uniqueName(bookId), ExistingWorkPolicy.REPLACE, request)
    }

    override fun cancel(bookId: Long) {
        workManager.cancelUniqueWork(AiPrefetchWorker.uniqueName(bookId))
    }
}
