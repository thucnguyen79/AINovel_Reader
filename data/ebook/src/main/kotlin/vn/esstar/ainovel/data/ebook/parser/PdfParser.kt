package vn.esstar.ainovel.data.ebook.parser

import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper
import java.io.InputStream

/**
 * Text PDFs via pdfbox-android (Apache-2.0). Scanned PDFs (images only) yield no text and are rejected;
 * OCR is out of scope. Chapters are detected by heading lines like the TXT importer, with each page's
 * text joined first so headings split across lines still match.
 */
class PdfParser : EbookParser {
    override val extensions = setOf("pdf")

    override fun parse(input: InputStream, fileName: String): ParsedEbook {
        val title = EbookParsers.titleFromFileName(fileName)
        val doc = runCatching { PDDocument.load(input) }.getOrElse { throw EbookParseException("Cannot open PDF", it) }
        doc.use { pdf ->
            if (pdf.isEncrypted) throw EbookParseException("PDF is encrypted")
            val stripper = PDFTextStripper().apply {
                sortByPosition = true
                lineSeparator = "\n"
                paragraphEnd = "\n"
            }
            val text = StringBuilder()
            val pages = pdf.numberOfPages
            for (page in 1..pages) {
                stripper.startPage = page
                stripper.endPage = page
                text.append(stripper.getText(pdf)).append('\n')
            }
            val cleaned = dehyphenate(text.toString())
            val docTitle = pdf.documentInformation?.title?.trim()?.ifEmpty { null } ?: title
            val author = pdf.documentInformation?.author?.trim()?.ifEmpty { null }
            val chapters = TextChapterSplitter.split(cleaned, docTitle)
            if (chapters.isEmpty()) throw EbookParseException("PDF has no extractable text (scanned?)")
            return ParsedEbook(
                title = docTitle,
                author = author,
                chapters = chapters,
                language = if (TextChapterSplitter.looksChinese(cleaned)) "zh" else "vi",
            )
        }
    }

    /** Joins lines that PDF layout broke mid-sentence; keeps blank-line paragraph breaks and headings. */
    private fun dehyphenate(text: String): String {
        val out = StringBuilder(text.length)
        val lines = text.split('\n')
        var i = 0
        while (i < lines.size) {
            val line = lines[i].trimEnd()
            val next = lines.getOrNull(i + 1)?.trim().orEmpty()
            out.append(line.trim())
            val joinable = line.isNotBlank() && next.isNotBlank() &&
                !line.trim().endsWith(".") && !line.trim().endsWith("!") && !line.trim().endsWith("?") &&
                !line.trim().endsWith("”") && !line.trim().endsWith(":") && line.trim().length > 40 &&
                next.firstOrNull()?.isLowerCase() == true
            out.append(if (joinable) ' ' else '\n')
            i++
        }
        return out.toString()
    }
}
