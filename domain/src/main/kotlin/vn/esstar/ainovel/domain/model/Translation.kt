package vn.esstar.ainovel.domain.model

/** QuickTranslator-compatible dictionary kinds, in lookup priority order. */
enum class DictionaryType { NAMES, VIETPHRASE, HANVIET }

data class DictionaryInfo(
    val type: DictionaryType,
    val displayName: String,
    val entries: Int,
    val importedAt: Long,
    /** True for the dictionary bundled with the app (Hán Việt from Unihan). */
    val builtIn: Boolean,
)

/** How a Chinese chapter is shown. */
enum class TranslateMode { CONVERT, HAN_VIET, RAW }

/** Languages whose chapters go through the convert engine. */
object TranslatableLanguages {
    val codes = setOf("zh")
    fun needsTranslation(language: String): Boolean = language.lowercase() in codes
}
