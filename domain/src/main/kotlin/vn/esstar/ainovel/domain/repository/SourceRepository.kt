package vn.esstar.ainovel.domain.repository

import vn.esstar.ainovel.domain.model.FetchedBook
import vn.esstar.ainovel.domain.model.FetchedChapterContent
import vn.esstar.ainovel.domain.model.SearchResult
import vn.esstar.ainovel.domain.model.SourceId
import vn.esstar.ainovel.domain.model.SourceInfo
import vn.esstar.ainovel.domain.model.UrlKind

/** Access to story websites through rule-based sources. */
interface SourceRepository {
    suspend fun sources(): List<SourceInfo>

    /** Returns the source that handles [url], or null when no rule matches its host. */
    suspend fun sourceFor(url: String): SourceInfo?

    suspend fun classifyUrl(url: String): UrlKind

    /** Fetches book info and the full chapter list (all TOC pages). */
    suspend fun fetchBook(url: String): FetchedBook

    suspend fun fetchChapter(url: String): FetchedChapterContent

    /** Searches [query] on the given sources (all searchable sources when empty). */
    suspend fun search(query: String, sourceIds: List<SourceId> = emptyList()): List<SearchResult>
}
