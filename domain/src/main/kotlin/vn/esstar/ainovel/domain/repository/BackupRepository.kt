package vn.esstar.ainovel.domain.repository

import vn.esstar.ainovel.domain.model.BackupSummary

/**
 * ZIP backup of the library: books, chapter lists, reading progress, history, glossary and the text of
 * imported ebooks. Web chapter text and audio are not included (they can be fetched again).
 */
interface BackupRepository {
    /** Writes a backup to a document the user picked (content URI as string). */
    suspend fun export(uriString: String): BackupSummary

    /** Merges a backup into the current library; existing books are kept and their progress updated. */
    suspend fun import(uriString: String): BackupSummary
}
