package vn.esstar.ainovel.domain.model

/** QuickTranslator-compatible dictionary kinds, in lookup priority order; LUATNHAN holds reorder rules. */
enum class DictionaryType { NAMES, VIETPHRASE, HANVIET, LUATNHAN }

data class DictionaryInfo(
    val type: DictionaryType,
    val displayName: String,
    val entries: Int,
    val importedAt: Long,
    /** True for the dictionary bundled with the app (Hán Việt from Unihan, default luật nhân). */
    val builtIn: Boolean,
)

/** How a Chinese chapter is shown. MLKIT is on-device machine translation (model downloaded on demand). */
enum class TranslateMode { CONVERT, HAN_VIET, MLKIT, RAW }

/** A converted word with its Chinese source, used by the reader for tap-to-choose-meaning. */
data class TextToken(
    val source: String,
    /** Output text including any leading space. */
    val text: String,
    val alternatives: List<String> = emptyList(),
    /** False for punctuation and verbatim Latin runs. */
    val isChinese: Boolean = false,
)

/** User override for a Chinese word; [bookId] null means it applies to every book. */
data class GlossaryEntry(
    val id: Long = 0,
    val bookId: Long?,
    val source: String,
    val target: String,
)

data class MlKitStatus(
    val downloaded: Boolean = false,
    val downloading: Boolean = false,
    val error: String? = null,
)

/** Languages whose chapters go through the convert engine. */
object TranslatableLanguages {
    val codes = setOf("zh")
    fun needsTranslation(language: String): Boolean = language.lowercase() in codes
}
