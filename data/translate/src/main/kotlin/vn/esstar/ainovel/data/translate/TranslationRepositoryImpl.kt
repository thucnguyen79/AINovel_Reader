package vn.esstar.ainovel.data.translate

import android.net.Uri
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import vn.esstar.ainovel.core.common.di.AppDispatcher
import vn.esstar.ainovel.core.common.di.Dispatcher
import vn.esstar.ainovel.core.datastore.TranslatePreferences
import vn.esstar.ainovel.data.translate.engine.ConvertMode
import vn.esstar.ainovel.domain.model.DictionaryInfo
import vn.esstar.ainovel.domain.model.DictionaryType
import vn.esstar.ainovel.domain.model.TranslateMode
import vn.esstar.ainovel.domain.repository.TranslationRepository

@Singleton
class TranslationRepositoryImpl @Inject constructor(
    private val manager: DictionaryManager,
    private val prefs: TranslatePreferences,
    @Dispatcher(AppDispatcher.Default) private val default: CoroutineDispatcher,
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
        if (effective == TranslateMode.RAW) return paragraphs
        val engine = manager.engine()
        return withContext(default) {
            engine.convertParagraphs(paragraphs, if (effective == TranslateMode.HAN_VIET) ConvertMode.HAN_VIET else ConvertMode.CONVERT)
        }
    }
}
