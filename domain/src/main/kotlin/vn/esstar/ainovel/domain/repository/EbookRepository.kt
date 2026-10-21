package vn.esstar.ainovel.domain.repository

import kotlinx.coroutines.flow.Flow
import vn.esstar.ainovel.domain.model.EbookInfo

/** Imports ebook files into the library and lists them. */
interface EbookRepository {
    fun observeEbooks(): Flow<List<EbookInfo>>

    /** Parses a document the user picked (content URI as string) and stores it; returns the book id. */
    suspend fun importFromUri(uriString: String): Long

    /** Downloads a file from a direct link (Google Drive share links are rewritten) and imports it. */
    suspend fun importFromUrl(url: String): Long

    /** Removes the book, its chapters, cached text and cover. */
    suspend fun delete(bookId: Long)
}
