package vn.esstar.ainovel.data.ebook.parser

import java.nio.charset.Charset

/**
 * Splits plain text into chapters by heading lines ("Chương 12: …", "第十二章 …", "Chapter 3", "Hồi 5"…).
 * When no headings are found the text is cut into fixed-size parts so long files stay readable.
 */
object TextChapterSplitter {

    private val HEADING = Regex(
        """^\s*(?:(?:chương|chuong|hồi|hoi|quyển|quyen|phần|phan|tập|tap|chapter|part|book)\s*\d+[^\n]{0,80}|第\s*[0-9０-９一二三四五六七八九十百千零〇两]+\s*[章节回卷集部篇][^\n]{0,80})\s*$""",
        RegexOption.IGNORE_CASE,
    )

    /** Paragraphs per chapter when the text has no headings. */
    private const val FALLBACK_PARAGRAPHS = 120

    fun split(text: String, fallbackTitle: String): List<ParsedChapter> {
        val lines = text.replace("\r\n", "\n").replace('\r', '\n').split('\n')
        val chapters = ArrayList<ParsedChapter>()
        var title: String? = null
        var buffer = ArrayList<String>()

        fun flush() {
            val paragraphs = buffer.map { it.trim() }.filter { it.isNotEmpty() }
            if (paragraphs.isNotEmpty() || title != null) {
                val t = title ?: fallbackTitle
                if (paragraphs.isNotEmpty()) chapters += ParsedChapter(t, paragraphs)
            }
            buffer = ArrayList()
        }

        var headingCount = 0
        for (line in lines) {
            if (HEADING.matches(line) && line.trim().length <= 100) {
                headingCount++
                flush()
                title = line.trim()
            } else {
                buffer += line
            }
        }
        flush()

        if (headingCount >= 2) return chapters

        // No usable headings: one flat list of paragraphs, cut into parts.
        val all = lines.map { it.trim() }.filter { it.isNotEmpty() }
        if (all.isEmpty()) return emptyList()
        if (all.size <= FALLBACK_PARAGRAPHS) return listOf(ParsedChapter(fallbackTitle, all))
        return all.chunked(FALLBACK_PARAGRAPHS).mapIndexed { i, part -> ParsedChapter("Phần ${i + 1}", part) }
    }

    /** UTF-8 (with or without BOM), UTF-16 with BOM, otherwise GB18030 when the bytes are not valid UTF-8. */
    fun decode(bytes: ByteArray): String {
        if (bytes.size >= 3 && bytes[0] == 0xEF.toByte() && bytes[1] == 0xBB.toByte() && bytes[2] == 0xBF.toByte()) {
            return String(bytes, 3, bytes.size - 3, Charsets.UTF_8)
        }
        if (bytes.size >= 2 && bytes[0] == 0xFF.toByte() && bytes[1] == 0xFE.toByte()) return String(bytes, 2, bytes.size - 2, Charsets.UTF_16LE)
        if (bytes.size >= 2 && bytes[0] == 0xFE.toByte() && bytes[1] == 0xFF.toByte()) return String(bytes, 2, bytes.size - 2, Charsets.UTF_16BE)
        return if (isValidUtf8(bytes)) String(bytes, Charsets.UTF_8) else String(bytes, Charset.forName("GB18030"))
    }

    private fun isValidUtf8(bytes: ByteArray): Boolean {
        val decoder = Charsets.UTF_8.newDecoder()
        return runCatching { decoder.decode(java.nio.ByteBuffer.wrap(bytes)); true }.getOrDefault(false)
    }

    /** True when most letters are CJK, so the book should go through the convert engine. */
    fun looksChinese(text: String): Boolean {
        var han = 0
        var letters = 0
        for (c in text) {
            if (c.isLetter()) {
                letters++
                if (c in '一'..'鿿') han++
            }
            if (letters > 4000) break
        }
        return letters > 0 && han * 2 > letters
    }
}
