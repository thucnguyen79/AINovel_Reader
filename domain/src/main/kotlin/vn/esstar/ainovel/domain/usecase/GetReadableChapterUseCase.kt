package vn.esstar.ainovel.domain.usecase

import javax.inject.Inject
import vn.esstar.ainovel.domain.model.ChapterContent
import vn.esstar.ainovel.domain.model.TranslatableLanguages
import vn.esstar.ainovel.domain.model.TranslateMode
import vn.esstar.ainovel.domain.repository.LibraryRepository
import vn.esstar.ainovel.domain.repository.TranslationRepository

/**
 * Chapter text as the user should see or hear it: raw for Vietnamese sources, converted for Chinese ones.
 * Used by the reader and by the TTS pipeline so both agree on paragraph indices.
 */
class GetReadableChapterUseCase @Inject constructor(
    private val library: LibraryRepository,
    private val translation: TranslationRepository,
) {
    suspend operator fun invoke(chapterId: Long, mode: TranslateMode? = null, forceRefresh: Boolean = false): ChapterContent {
        val content = library.getChapterContent(chapterId, forceRefresh)
        val chapter = library.getChapter(chapterId) ?: return content
        val book = library.getBook(chapter.bookId) ?: return content
        if (!TranslatableLanguages.needsTranslation(book.language)) return content
        val title = translation.translate(listOf(content.title), book.id, mode).firstOrNull() ?: content.title
        val tokens = translation.translateDetailed(content.paragraphs, book.id, mode)
        if (tokens != null) {
            return content.copy(title = title, paragraphs = tokens.map { p -> p.joinToString("") { it.text } }, tokens = tokens)
        }
        val translated = translation.translate(content.paragraphs, book.id, mode)
        return content.copy(title = title, paragraphs = translated)
    }
}
