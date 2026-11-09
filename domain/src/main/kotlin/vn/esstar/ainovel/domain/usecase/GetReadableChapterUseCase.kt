package vn.esstar.ainovel.domain.usecase

import javax.inject.Inject
import vn.esstar.ainovel.domain.model.ChapterContent
import vn.esstar.ainovel.domain.model.TranslatableLanguages
import vn.esstar.ainovel.domain.model.TranslateMode
import vn.esstar.ainovel.domain.repository.LibraryRepository
import vn.esstar.ainovel.domain.repository.LlmRepository
import vn.esstar.ainovel.domain.repository.TranslationRepository

/**
 * Chapter text as the user should see or hear it: raw for Vietnamese sources, converted for Chinese ones,
 * or rewritten by the user's own model when the book is in AI mode. Used by the reader and by the TTS
 * pipeline so both agree on paragraph indices.
 */
class GetReadableChapterUseCase @Inject constructor(
    private val library: LibraryRepository,
    private val translation: TranslationRepository,
    private val llm: LlmRepository,
) {
    /**
     * @param mode explicit mode from the reader; null means "whatever applies to this book" (AI mode when the
     *   book was switched to it, the default convert mode for Chinese books, raw text otherwise).
     */
    suspend operator fun invoke(chapterId: Long, mode: TranslateMode? = null, forceRefresh: Boolean = false): ChapterContent {
        val content = library.getChapterContent(chapterId, forceRefresh)
        val chapter = library.getChapter(chapterId) ?: return content
        val book = library.getBook(chapter.bookId) ?: return content
        val chinese = TranslatableLanguages.needsTranslation(book.language)

        val wantsAi = mode == TranslateMode.LLM || (mode == null && llm.isAiBook(book.id))
        if (wantsAi) {
            // A failed model call (quota, network) must not hide the chapter: fall back to the dictionary text.
            val ai = runCatching { llm.translateChapter(book.id, chapterId, content.title, content.paragraphs, book.language) }.getOrNull()
            if (ai != null && ai.paragraphs.size == content.paragraphs.size) {
                return content.copy(title = ai.title.ifBlank { content.title }, paragraphs = ai.paragraphs, tokens = null)
            }
            // Not configured or failed: fall through to the dictionary pipeline (or raw text for Vietnamese books).
        }
        if (!chinese) return content
        val dictMode = mode?.takeIf { it != TranslateMode.LLM }
        val title = translation.translate(listOf(content.title), book.id, dictMode).firstOrNull() ?: content.title
        val tokens = translation.translateDetailed(content.paragraphs, book.id, dictMode)
        if (tokens != null) {
            return content.copy(title = title, paragraphs = tokens.map { p -> p.joinToString("") { it.text } }, tokens = tokens)
        }
        val translated = translation.translate(content.paragraphs, book.id, dictMode)
        return content.copy(title = title, paragraphs = translated)
    }
}
