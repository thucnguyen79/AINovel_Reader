package vn.esstar.ainovel.domain.repository

/**
 * Background "dịch trước": translates the next chapters of a book in AI mode while the device is on Wi-Fi,
 * so reading and listening never wait for the model. Implemented with WorkManager in `service/download`.
 */
interface AiPrefetchScheduler {
    /** (Re)schedules translation of the chapters after [fromChapterIndex]; a no-op when the setting is 0. */
    suspend fun scheduleAhead(bookId: Long, fromChapterIndex: Int)

    fun cancel(bookId: Long)
}
