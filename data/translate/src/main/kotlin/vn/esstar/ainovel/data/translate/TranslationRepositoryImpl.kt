package vn.esstar.ainovel.data.translate

import android.net.Uri
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import timber.log.Timber
import vn.esstar.ainovel.core.common.di.AppDispatcher
import vn.esstar.ainovel.core.common.di.Dispatcher
import vn.esstar.ainovel.core.database.dao.GlossaryDao
import vn.esstar.ainovel.core.database.entity.GlossaryEntity
import vn.esstar.ainovel.core.datastore.TranslatePreferences
import vn.esstar.ainovel.data.translate.engine.ConvertEngine
import vn.esstar.ainovel.data.translate.engine.ConvertMode
import vn.esstar.ainovel.data.translate.engine.ConvertedToken
import vn.esstar.ainovel.domain.model.DictionaryInfo
import vn.esstar.ainovel.domain.model.DictionaryType
import vn.esstar.ainovel.domain.model.GlossaryEntry
import vn.esstar.ainovel.domain.model.MlKitStatus
import vn.esstar.ainovel.domain.model.TextToken
import vn.esstar.ainovel.domain.model.TranslateMode
import vn.esstar.ainovel.domain.repository.TranslationRepository

@Singleton
class TranslationRepositoryImpl @Inject constructor(
    private val manager: DictionaryManager,
    private val prefs: TranslatePreferences,
    private val glossaryDao: GlossaryDao,
    private val mlKit: MlKitTranslator,
    @Dispatcher(AppDispatcher.Default) private val default: CoroutineDispatcher,
    @Dispatcher(AppDispatcher.IO) private val io: CoroutineDispatcher,
) : TranslationRepository {

    override val dictionaries: Flow<List<DictionaryInfo>> get() = manager.dictionaries

    override suspend fun importDictionary(uriString: String, type: DictionaryType, displayName: String?): DictionaryInfo =
        manager.import(Uri.parse(uriString), type, displayName)

    override suspend fun removeDictionary(type: DictionaryType) = manager.remove(type)

    override val mode: Flow<TranslateMode> get() = prefs.mode

    override suspend fun setMode(mode: TranslateMode) {
        prefs.setMode(mode)
    }

    override suspend fun translate(paragraphs: List<String>, bookId: Long?, mode: TranslateMode?): List<String> {
        val effective = mode ?: prefs.mode.first()
        return when (effective) {
            TranslateMode.RAW -> paragraphs
            TranslateMode.MLKIT -> translateMlKit(paragraphs, bookId)
            TranslateMode.CONVERT, TranslateMode.HAN_VIET -> {
                val engine = engineFor(bookId)
                withContext(default) { engine.convertParagraphs(paragraphs, effective.toConvertMode()) }
            }
        }
    }

    override suspend fun translateDetailed(paragraphs: List<String>, bookId: Long?, mode: TranslateMode?): List<List<TextToken>>? {
        val effective = mode ?: prefs.mode.first()
        if (effective == TranslateMode.RAW || effective == TranslateMode.MLKIT) return null
        val engine = engineFor(bookId)
        return withContext(default) {
            engine.convertDetailedParagraphs(paragraphs, effective.toConvertMode()).map { tokens -> tokens.map { it.toDomain() } }
        }
    }

    private suspend fun translateMlKit(paragraphs: List<String>, bookId: Long?): List<String> {
        val status = mlKit.status.value.takeIf { it.downloaded } ?: mlKit.refreshStatus()
        if (!status.downloaded) {
            Timber.w("ML Kit model missing; falling back to convert")
            return translate(paragraphs, bookId, TranslateMode.CONVERT)
        }
        return runCatching { withContext(io) { mlKit.translate(paragraphs) } }
            .onFailure { Timber.e(it, "ML Kit translation failed; falling back to convert") }
            .getOrElse { translate(paragraphs, bookId, TranslateMode.CONVERT) }
    }

    private suspend fun engineFor(bookId: Long?): ConvertEngine {
        val glossary = withContext(io) { glossaryDao.listForBook(bookId) }
            .associate { it.source to it.target } // book rows come last, so they override global ones
        return manager.engine(glossary)
    }

    override fun observeGlossary(bookId: Long?): Flow<List<GlossaryEntry>> =
        glossaryDao.observeForBook(bookId).map { rows -> rows.map { it.toDomain() } }

    override suspend fun upsertGlossary(bookId: Long?, source: String, target: String): Unit = withContext(io) {
        val now = System.currentTimeMillis()
        val cleanSource = source.trim()
        val cleanTarget = target.trim()
        require(cleanSource.isNotEmpty() && cleanTarget.isNotEmpty())
        val existing = glossaryDao.findId(bookId, cleanSource)
        if (existing != null) {
            glossaryDao.updateTarget(existing, cleanTarget, now)
        } else {
            glossaryDao.insert(GlossaryEntity(bookId = bookId, source = cleanSource, target = cleanTarget, updatedAt = now))
        }
    }

    override suspend fun removeGlossary(id: Long): Unit = withContext(io) { glossaryDao.delete(id) }

    override val mlKitStatus: Flow<MlKitStatus> get() = mlKit.status

    override suspend fun refreshMlKitStatus() {
        mlKit.refreshStatus()
    }

    override suspend fun downloadMlKitModel(wifiOnly: Boolean) = mlKit.download(wifiOnly)

    override suspend fun deleteMlKitModel() = mlKit.delete()

    private fun TranslateMode.toConvertMode(): ConvertMode = if (this == TranslateMode.HAN_VIET) ConvertMode.HAN_VIET else ConvertMode.CONVERT

    private fun ConvertedToken.toDomain() = TextToken(source = source, text = text, alternatives = alternatives, isChinese = isChinese)

    private fun GlossaryEntity.toDomain() = GlossaryEntry(id = id, bookId = bookId, source = source, target = target)
}
