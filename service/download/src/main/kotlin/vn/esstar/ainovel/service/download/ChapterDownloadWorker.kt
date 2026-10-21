package vn.esstar.ainovel.service.download

import android.app.NotificationManager
import android.content.Context
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlin.random.Random
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import timber.log.Timber
import vn.esstar.ainovel.core.database.dao.BookDao
import vn.esstar.ainovel.core.database.dao.ChapterDao
import vn.esstar.ainovel.core.database.dao.DownloadDao
import vn.esstar.ainovel.core.network.HttpException
import vn.esstar.ainovel.domain.model.DownloadState
import vn.esstar.ainovel.domain.repository.LibraryRepository

/**
 * Downloads a range of chapters sequentially. Sites block bursts, so the worker waits a randomized
 * interval between chapters and backs off for a long time on 429/403 before continuing.
 */
@HiltWorker
class ChapterDownloadWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val downloadDao: DownloadDao,
    private val chapterDao: ChapterDao,
    private val bookDao: BookDao,
    private val library: LibraryRepository,
) : CoroutineWorker(appContext, params) {

    private val jobId: Long = inputData.getLong(KEY_JOB_ID, -1)
    private val notifications = DownloadNotifications(appContext)

    override suspend fun doWork(): Result {
        val job = downloadDao.getById(jobId) ?: return Result.failure()
        if (job.state == DownloadState.CANCELLED.name) return Result.failure()
        val book = bookDao.getById(job.bookId) ?: return Result.failure()
        val chapters = downloadDao.chaptersInRange(job.bookId, job.fromIndex, job.toIndex)
        if (chapters.isEmpty()) {
            downloadDao.setState(jobId, DownloadState.DONE.name, null, now())
            return Result.success()
        }

        runCatching { setForeground(foregroundInfo(book.title, 0, chapters.size)) }
            .onFailure { Timber.w(it, "Foreground not available, continuing in background") }
        downloadDao.setState(jobId, DownloadState.RUNNING.name, null, now())

        var completed = 0
        var failed = 0
        var consecutiveFailures = 0
        try {
            for (chapter in chapters) {
                if (isStopped) break
                if (chapter.downloadedAt != null) {
                    completed++
                    report(book.title, completed, failed, chapters.size)
                    continue
                }
                var attempt = 0
                var ok = false
                while (!ok && attempt < MAX_ATTEMPTS && !isStopped) {
                    attempt++
                    try {
                        library.getChapterContent(chapter.id, forceRefresh = chapter.contentPath == null)
                        chapterDao.setDownloadedAt(chapter.id, now())
                        ok = true
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: HttpException) {
                        Timber.w("HTTP %d downloading chapter %d (attempt %d)", e.code, chapter.id, attempt)
                        delay(backoffFor(e.code, attempt))
                    } catch (e: Exception) {
                        Timber.w(e, "Download chapter %d failed (attempt %d)", chapter.id, attempt)
                        delay(RETRY_DELAY_MS * attempt)
                    }
                }
                if (ok) {
                    completed++
                    consecutiveFailures = 0
                } else {
                    failed++
                    consecutiveFailures++
                }
                report(book.title, completed, failed, chapters.size)
                if (consecutiveFailures >= MAX_CONSECUTIVE_FAILURES) {
                    downloadDao.setProgress(jobId, completed, failed, DownloadState.FAILED.name, now())
                    downloadDao.setState(jobId, DownloadState.FAILED.name, "Website từ chối liên tục", now())
                    notifications.finished(jobId.toInt(), book.title, completed, failed, chapters.size)
                    return Result.failure()
                }
                // Politeness delay between chapters (randomized so the pattern is not mechanical).
                delay(MIN_INTERVAL_MS + Random.nextLong(0, JITTER_MS))
            }
        } catch (e: CancellationException) {
            downloadDao.setProgress(jobId, completed, failed, DownloadState.CANCELLED.name, now())
            throw e
        }

        val finalState = if (isStopped) DownloadState.CANCELLED else if (failed == 0) DownloadState.DONE else DownloadState.FAILED
        downloadDao.setProgress(jobId, completed, failed, finalState.name, now())
        notifications.finished(jobId.toInt(), book.title, completed, failed, chapters.size)
        return if (finalState == DownloadState.DONE) Result.success() else Result.failure()
    }

    private suspend fun report(title: String, completed: Int, failed: Int, total: Int) {
        downloadDao.setProgress(jobId, completed, failed, DownloadState.RUNNING.name, now())
        setProgress(workDataOf(KEY_COMPLETED to completed, KEY_TOTAL to total))
        runCatching { setForeground(foregroundInfo(title, completed + failed, total)) }
    }

    private fun foregroundInfo(title: String, done: Int, total: Int): ForegroundInfo {
        val notification = notifications.progress(jobId.toInt(), title, done, total, id)
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ForegroundInfo(NOTIFICATION_ID_BASE + jobId.toInt(), notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            ForegroundInfo(NOTIFICATION_ID_BASE + jobId.toInt(), notification)
        }
    }

    private fun backoffFor(code: Int, attempt: Int): Long = when (code) {
        429, 503 -> RATE_LIMIT_BACKOFF_MS * attempt
        403 -> BLOCKED_BACKOFF_MS
        else -> RETRY_DELAY_MS * attempt
    }

    private fun now() = System.currentTimeMillis()

    companion object {
        const val KEY_JOB_ID = "jobId"
        const val KEY_COMPLETED = "completed"
        const val KEY_TOTAL = "total"
        const val NOTIFICATION_ID_BASE = 2000

        private const val MIN_INTERVAL_MS = 900L
        private const val JITTER_MS = 700L
        private const val RETRY_DELAY_MS = 5_000L
        private const val RATE_LIMIT_BACKOFF_MS = 60_000L
        private const val BLOCKED_BACKOFF_MS = 120_000L
        private const val MAX_ATTEMPTS = 3
        private const val MAX_CONSECUTIVE_FAILURES = 5

        fun uniqueName(bookId: Long) = "download-book-$bookId"
    }
}

/** Notification channel + builders for download progress. */
class DownloadNotifications(private val context: Context) {

    private val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    init {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = android.app.NotificationChannel(
                CHANNEL_ID,
                context.getString(R.string.download_channel_name),
                NotificationManager.IMPORTANCE_LOW,
            ).apply { description = context.getString(R.string.download_channel_description) }
            manager.createNotificationChannel(channel)
        }
    }

    fun progress(jobId: Int, title: String, done: Int, total: Int, workId: java.util.UUID): android.app.Notification {
        val cancelIntent = androidx.work.WorkManager.getInstance(context).createCancelPendingIntent(workId)
        return NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle(context.getString(R.string.download_notification_title, title))
            .setContentText(context.getString(R.string.download_notification_progress, done, total))
            .setProgress(total, done, false)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, context.getString(R.string.download_cancel), cancelIntent)
            .build()
    }

    fun finished(jobId: Int, title: String, completed: Int, failed: Int, total: Int) {
        val text = if (failed == 0) {
            context.getString(R.string.download_notification_done, completed)
        } else {
            context.getString(R.string.download_notification_partial, completed, failed)
        }
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_download_done)
            .setContentTitle(context.getString(R.string.download_notification_title, title))
            .setContentText(text)
            .setAutoCancel(true)
            .build()
        runCatching { manager.notify(ChapterDownloadWorker.NOTIFICATION_ID_BASE + 10_000 + jobId, notification) }
    }

    companion object {
        const val CHANNEL_ID = "downloads"
    }
}
