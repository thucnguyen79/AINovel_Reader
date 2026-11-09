package vn.esstar.ainovel.data.translate.llm

import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import timber.log.Timber
import vn.esstar.ainovel.core.common.di.AppDispatcher
import vn.esstar.ainovel.core.common.di.Dispatcher
import vn.esstar.ainovel.core.database.dao.GlossaryDao
import vn.esstar.ainovel.core.database.dao.LlmChapterDao
import vn.esstar.ainovel.core.database.entity.LlmChapterEntity
import vn.esstar.ainovel.core.datastore.LlmPreferences
import vn.esstar.ainovel.domain.model.GlossaryEntry
import vn.esstar.ainovel.domain.model.LlmCacheInfo
import vn.esstar.ainovel.domain.model.LlmChapter
import vn.esstar.ainovel.domain.model.LlmException
import vn.esstar.ainovel.domain.model.LlmProgress
import vn.esstar.ainovel.domain.model.LlmProviderKind
import vn.esstar.ainovel.domain.model.LlmSettings
import vn.esstar.ainovel.domain.model.LlmTestResult
import vn.esstar.ainovel.domain.repository.LlmRepository

@Singleton
class LlmRepositoryImpl @Inject constructor(
    private val prefs: LlmPreferences,
    private val dao: LlmChapterDao,
    private val glossaryDao: GlossaryDao,
    http: OkHttpClient,
    json: Json,
    @Dispatcher(AppDispatcher.IO) private val io: CoroutineDispatcher,
) : LlmRepository {

    private val factory = LlmClientFactory(http, json)

    /** Translations outlive the caller (reader navigates away, TTS prefetch); they run in this scope. */
    private val scope = CoroutineScope(SupervisorJob() + io)
    private val inFlight = HashMap<Long, Deferred<LlmChapter?>>()
    private val inFlightLock = Mutex()

    private val _progress = MutableStateFlow<Map<Long, LlmProgress>>(emptyMap())
    override val progress: Flow<Map<Long, LlmProgress>> = _progress

    override val settings: Flow<LlmSettings> get() = prefs.settings

    override suspend fun saveSettings(settings: LlmSettings) {
        prefs.save(settings)
    }

    override suspend fun testConnection(settings: LlmSettings): LlmTestResult {
        if (!settings.isConfigured) return LlmTestResult.Failure("Thiếu API key, model hoặc URL máy chủ.")
        return runCatching {
            val reply = withContext(io) {
                factory.create(settings).complete(
                    system = "Bạn là trợ lý kiểm tra kết nối. Trả lời đúng một từ.",
                    user = "Trả lời: OK",
                    temperature = 0.0,
                )
            }
            LlmTestResult.Success("${settings.effectiveModel} trả lời: ${reply.trim().take(60)}")
        }.getOrElse { LlmTestResult.Failure(it.userMessage()) }
    }

    override suspend fun listModels(settings: LlmSettings): List<String> {
        if (settings.apiKey.isBlank()) return emptyList()
        return withContext(io) { factory.create(settings).listModels() }
    }

    override fun observeAiBooks(): Flow<Set<Long>> = prefs.aiBooks
    override suspend fun isAiBook(bookId: Long): Boolean = prefs.isAiBook(bookId)
    override suspend fun setAiBook(bookId: Long, enabled: Boolean) {
        prefs.setAiBook(bookId, enabled)
    }

    override suspend fun cached(chapterId: Long): LlmChapter? = dao.get(chapterId)?.toDomain()

    override suspend fun translateChapter(bookId: Long, chapterId: Long, title: String, paragraphs: List<String>, sourceLanguage: String): LlmChapter? {
        dao.get(chapterId)?.let { return it.toDomain() }
        val settings = prefs.settings.first()
        if (!settings.isConfigured) return null
        val job = inFlightLock.withLock {
            inFlight.getOrPut(chapterId) {
                scope.async {
                    try {
                        produce(settings, bookId, chapterId, title, paragraphs, sourceLanguage)
                    } finally {
                        inFlightLock.withLock { inFlight.remove(chapterId) }
                        _progress.update { it - chapterId }
                    }
                }
            }
        }
        return job.await()
    }

    private suspend fun produce(
        settings: LlmSettings,
        bookId: Long,
        chapterId: Long,
        title: String,
        paragraphs: List<String>,
        sourceLanguage: String,
    ): LlmChapter {
        val client = factory.create(settings)
        val glossary = glossaryDao.listForBook(bookId).map { GlossaryEntry(it.id, it.bookId, it.source, it.target) }
        val system = ChapterTranslator.systemPrompt(sourceLanguage)
        val chunks = ChapterTranslator.chunk(paragraphs)
        val total = chunks.size + 1
        _progress.update { it + (chapterId to LlmProgress(chapterId, 0, total)) }

        val out = paragraphs.toMutableList()
        chunks.forEachIndexed { ci, chunk ->
            val reply = client.complete(system, ChapterTranslator.userPrompt(chunk, glossary, null))
            val parsed = ChapterTranslator.parseReply(reply, chunk.paragraphs.size)
                ?: retryChunk(client, system, chunk, glossary)
            if (parsed == null) {
                Timber.w("LLM chunk %d of chapter %d unparseable; keeping source paragraphs", ci, chapterId)
            } else {
                parsed.forEachIndexed { i, text -> out[chunk.startIndex + i] = text.ifBlank { chunk.paragraphs[i] } }
            }
            _progress.update { it + (chapterId to LlmProgress(chapterId, ci + 1, total)) }
        }
        val newTitle = translateTitle(client, system, title, glossary)
        val entity = LlmChapterEntity(
            chapterId = chapterId,
            bookId = bookId,
            provider = settings.provider.name,
            model = settings.effectiveModel,
            title = newTitle,
            text = out.joinToString("\n"),
            createdAt = System.currentTimeMillis(),
        )
        dao.upsert(entity)
        return entity.toDomain()
    }

    /** One more attempt with the same chunk; some models ignore the numbering the first time. */
    private suspend fun retryChunk(client: LlmClient, system: String, chunk: ChapterTranslator.Chunk, glossary: List<GlossaryEntry>): List<String>? =
        runCatching {
            val reply = client.complete(system, ChapterTranslator.userPrompt(chunk, glossary, null) + "\nNhắc lại: mỗi đoạn một dòng, giữ nguyên số thứ tự [n].", temperature = 0.1)
            ChapterTranslator.parseReply(reply, chunk.paragraphs.size)
        }.onFailure { Timber.w(it, "LLM retry failed") }.getOrNull()

    private suspend fun translateTitle(client: LlmClient, system: String, title: String, glossary: List<GlossaryEntry>): String {
        if (title.isBlank()) return title
        return runCatching {
            val chunk = ChapterTranslator.Chunk(0, listOf(title))
            ChapterTranslator.parseReply(client.complete(system, ChapterTranslator.userPrompt(chunk, glossary, null), temperature = 0.1), 1)
                ?.firstOrNull()?.takeIf { it.isNotBlank() }
        }.onFailure { Timber.w(it, "LLM title translation failed") }.getOrNull() ?: title
    }

    override suspend fun deleteCached(chapterId: Long) = dao.delete(chapterId)

    override suspend fun cacheInfo(): LlmCacheInfo = LlmCacheInfo(chapters = dao.count(), bytes = dao.totalChars() * 2)

    override suspend fun clearCache() = dao.deleteAll()

    private fun LlmChapterEntity.toDomain() = LlmChapter(
        title = title,
        paragraphs = text.split('\n'),
        provider = runCatching { LlmProviderKind.valueOf(provider) }.getOrDefault(LlmProviderKind.OPENAI_COMPATIBLE),
        model = model,
    )

    private fun Throwable.userMessage(): String = (this as? LlmException)?.userMessage ?: (message ?: this::class.simpleName ?: "Lỗi không rõ")
}
