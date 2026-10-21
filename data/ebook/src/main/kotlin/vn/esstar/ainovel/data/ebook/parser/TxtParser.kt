package vn.esstar.ainovel.data.ebook.parser

import java.io.InputStream

class TxtParser : EbookParser {
    override val extensions = setOf("txt", "text", "md")

    override fun parse(input: InputStream, fileName: String): ParsedEbook {
        val text = TextChapterSplitter.decode(input.readBytes())
        val title = EbookParsers.titleFromFileName(fileName)
        val chapters = TextChapterSplitter.split(text, title)
        if (chapters.isEmpty()) throw EbookParseException("Empty text file")
        return ParsedEbook(
            title = title,
            chapters = chapters,
            language = if (TextChapterSplitter.looksChinese(text)) "zh" else "vi",
        )
    }
}
