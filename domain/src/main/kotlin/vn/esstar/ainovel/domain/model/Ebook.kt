package vn.esstar.ainovel.domain.model

/** Chapter text supplied at import time (ebooks), as opposed to fetched from a website later. */
data class ChapterText(val title: String, val paragraphs: List<String>)

/** Metadata of a book the user imported from a file. */
data class EbookInfo(
    val book: Book,
    /** `epub`, `txt`, `zip`, `pdf`. */
    val format: String,
    val fileName: String,
    val sizeBytes: Long,
    val importedAt: Long,
)

/** Source id used for every imported ebook. */
object EbookSource {
    val id = SourceId("ebook")
    const val URL_SCHEME = "ebook"
}
