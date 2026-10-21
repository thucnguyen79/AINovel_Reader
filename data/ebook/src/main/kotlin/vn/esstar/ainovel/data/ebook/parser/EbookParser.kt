package vn.esstar.ainovel.data.ebook.parser

import java.io.InputStream

/** A book read from a file, ready to be stored chapter by chapter. */
data class ParsedEbook(
    val title: String,
    val author: String? = null,
    val description: String? = null,
    val language: String = "vi",
    val chapters: List<ParsedChapter>,
    /** Raw cover image bytes (JPEG/PNG) when the file has one. */
    val cover: ByteArray? = null,
)

data class ParsedChapter(val title: String, val paragraphs: List<String>)

class EbookParseException(message: String, cause: Throwable? = null) : RuntimeException(message, cause)

/** Turns one file into chapters. Implementations are pure JVM so they can be unit-tested with fixtures. */
interface EbookParser {
    /** Lower-case extensions this parser handles, e.g. `epub`. */
    val extensions: Set<String>

    /** [fileName] is only a hint for the title; the stream is read fully and not closed. */
    fun parse(input: InputStream, fileName: String): ParsedEbook
}

/** Picks a parser by extension (falls back to sniffing the ZIP/PDF magic bytes). */
class EbookParsers(private val parsers: List<EbookParser>) {
    fun forFile(fileName: String, head: ByteArray): EbookParser? {
        val ext = fileName.substringAfterLast('.', "").lowercase()
        parsers.firstOrNull { ext in it.extensions }?.let { return it }
        return when {
            head.startsWith("%PDF".toByteArray()) -> parsers.firstOrNull { "pdf" in it.extensions }
            head.startsWith(byteArrayOf(0x50, 0x4B, 0x03, 0x04)) -> parsers.firstOrNull { "epub" in it.extensions }
            else -> parsers.firstOrNull { "txt" in it.extensions }
        }
    }

    private fun ByteArray.startsWith(prefix: ByteArray): Boolean =
        size >= prefix.size && prefix.indices.all { this[it] == prefix[it] }

    companion object {
        /** Title guessed from a file name: `Tru_Tien-full.txt` -> `Tru Tien-full`. */
        fun titleFromFileName(fileName: String): String =
            fileName.substringAfterLast('/').substringAfterLast('\\').substringBeforeLast('.').replace('_', ' ').trim().ifEmpty { fileName }
    }
}
