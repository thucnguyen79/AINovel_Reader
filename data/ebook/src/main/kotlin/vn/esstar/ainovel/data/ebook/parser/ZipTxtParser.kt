package vn.esstar.ainovel.data.ebook.parser

import java.io.InputStream
import java.util.zip.ZipInputStream

/**
 * ZIP archive of text files: each `.txt` becomes one or more chapters, files ordered naturally
 * (`001.txt`, `002.txt`, `chuong-10.txt` after `chuong-9.txt`).
 */
class ZipTxtParser : EbookParser {
    override val extensions = setOf("zip")

    override fun parse(input: InputStream, fileName: String): ParsedEbook {
        val files = ArrayList<Pair<String, ByteArray>>()
        ZipInputStream(input).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                val name = entry.name
                val base = name.substringAfterLast('/')
                if (!entry.isDirectory && base.lowercase().endsWith(".txt") && !base.startsWith(".") && !name.startsWith("__MACOSX")) {
                    files += name to zip.readBytes()
                }
                zip.closeEntry()
            }
        }
        if (files.isEmpty()) throw EbookParseException("No .txt files inside the archive")
        files.sortWith(compareBy(NaturalOrder) { it.first })

        val title = EbookParsers.titleFromFileName(fileName)
        val chapters = ArrayList<ParsedChapter>()
        var chinese = 0
        for ((name, bytes) in files) {
            val text = TextChapterSplitter.decode(bytes)
            if (TextChapterSplitter.looksChinese(text)) chinese++
            val fileTitle = EbookParsers.titleFromFileName(name)
            val parts = TextChapterSplitter.split(text, fileTitle)
            chapters += if (files.size > 1 && parts.size == 1) listOf(ParsedChapter(fileTitle, parts[0].paragraphs)) else parts
        }
        if (chapters.isEmpty()) throw EbookParseException("Archive text files are empty")
        return ParsedEbook(title = title, chapters = chapters, language = if (chinese * 2 > files.size) "zh" else "vi")
    }

    /** "chuong-9" < "chuong-10": digit runs compare numerically. */
    object NaturalOrder : Comparator<String> {
        private val CHUNK = Regex("""\d+|\D+""")
        override fun compare(a: String, b: String): Int {
            val xa = CHUNK.findAll(a.lowercase()).map { it.value }.toList()
            val xb = CHUNK.findAll(b.lowercase()).map { it.value }.toList()
            for (i in 0 until minOf(xa.size, xb.size)) {
                val pa = xa[i]
                val pb = xb[i]
                val c = if (pa[0].isDigit() && pb[0].isDigit()) {
                    pa.trimStart('0').length.compareTo(pb.trimStart('0').length).takeIf { it != 0 } ?: pa.compareTo(pb)
                } else {
                    pa.compareTo(pb)
                }
                if (c != 0) return c
            }
            return xa.size.compareTo(xb.size)
        }
    }
}
