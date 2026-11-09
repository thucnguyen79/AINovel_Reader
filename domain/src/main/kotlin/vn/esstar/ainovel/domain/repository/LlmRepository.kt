package vn.esstar.ainovel.domain.repository

import kotlinx.coroutines.flow.Flow
import vn.esstar.ainovel.domain.model.LlmCacheInfo
import vn.esstar.ainovel.domain.model.LlmChapter
import vn.esstar.ainovel.domain.model.LlmProgress
import vn.esstar.ainovel.domain.model.LlmSettings
import vn.esstar.ainovel.domain.model.LlmSummary
import vn.esstar.ainovel.domain.model.LlmTestResult

/**
 * "Dịch bằng AI": rewrites convert text (or translates Chinese) with a model the user pays for or gets
 * free with their own key. Results are cached per chapter so a chapter is sent to the model only once.
 */
interface LlmRepository {
    val settings: Flow<LlmSettings>
    suspend fun saveSettings(settings: LlmSettings)

    /** Sends a tiny request with [settings] (not necessarily saved) and reports whether it worked. */
    suspend fun testConnection(settings: LlmSettings): LlmTestResult

    /** Model ids the provider lists for this key, or empty when the provider has no listing. */
    suspend fun listModels(settings: LlmSettings): List<String>

    /** Books the user switched to AI mode. */
    fun observeAiBooks(): Flow<Set<Long>>
    suspend fun isAiBook(bookId: Long): Boolean
    suspend fun setAiBook(bookId: Long, enabled: Boolean)

    /** Cached translation of [chapterId], if any. */
    suspend fun cached(chapterId: Long): LlmChapter?

    /**
     * Returns the cached translation or produces one now (blocking until the model has answered every
     * chunk). Concurrent calls for the same chapter share one job. Null when AI mode is not configured.
     * @param sourceLanguage book language code; "zh" sends the Chinese text, anything else is treated as convert.
     * @param background true for prefetch: one request at a time and yields to interactive requests.
     */
    suspend fun translateChapter(bookId: Long, chapterId: Long, title: String, paragraphs: List<String>, sourceLanguage: String, background: Boolean = false): LlmChapter?

    /** Chunk progress of translations running now, keyed by chapter id. */
    val progress: Flow<Map<Long, LlmProgress>>

    suspend fun deleteCached(chapterId: Long)
    suspend fun cacheInfo(): LlmCacheInfo
    suspend fun clearCache()

    /** How many chapters ahead of the reading position to translate on Wi-Fi; 0 = off. */
    val prefetchCount: Flow<Int>
    suspend fun setPrefetchCount(count: Int)

    suspend fun cachedSummary(chapterId: Long): LlmSummary?

    /** Summarizes the chapter text (as displayed) in a few Vietnamese sentences; cached unless [force]. */
    suspend fun summarizeChapter(chapterId: Long, title: String, paragraphs: List<String>, force: Boolean = false): LlmSummary
}
