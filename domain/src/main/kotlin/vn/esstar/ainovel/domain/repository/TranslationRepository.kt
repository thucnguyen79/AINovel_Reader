package vn.esstar.ainovel.domain.repository

import kotlinx.coroutines.flow.Flow
import vn.esstar.ainovel.domain.model.DictionaryInfo
import vn.esstar.ainovel.domain.model.DictionaryType
import vn.esstar.ainovel.domain.model.TranslateMode

/** Offline Chinese → Vietnamese conversion and the dictionaries behind it. */
interface TranslationRepository {
    val dictionaries: Flow<List<DictionaryInfo>>

    /** Imports a `key=value` text file (content URI as string) as [type], replacing the previous one. */
    suspend fun importDictionary(uriString: String, type: DictionaryType, displayName: String?): DictionaryInfo

    suspend fun removeDictionary(type: DictionaryType)

    val mode: Flow<TranslateMode>
    suspend fun setMode(mode: TranslateMode)

    /** Converts Chinese paragraphs using the current mode; [bookId] selects the per-book glossary. */
    suspend fun translate(paragraphs: List<String>, bookId: Long? = null, mode: TranslateMode? = null): List<String>
}
