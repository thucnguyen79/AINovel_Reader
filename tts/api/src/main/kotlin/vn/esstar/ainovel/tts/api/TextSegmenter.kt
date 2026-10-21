package vn.esstar.ainovel.tts.api

/**
 * Splits chapter paragraphs into speakable segments.
 *
 * - Sentence boundaries: `.`, `!`, `?`, `…`, `;` and Vietnamese-style `...`, including when followed by closing quotes.
 * - Long sentences are further split at `,` / `:` / `–` when they exceed [maxChars], and as a last resort at a word boundary.
 * - With [breakAtComma] every comma becomes a boundary (the reference app's "Ngắt dấu phẩy" option).
 */
class TextSegmenter(
    private val maxChars: Int = 220,
    private val breakAtComma: Boolean = false,
) {
    fun segment(paragraphs: List<String>): List<Segment> {
        val out = ArrayList<Segment>()
        paragraphs.forEachIndexed { pIndex, paragraph ->
            val clean = paragraph.trim()
            if (clean.isEmpty()) return@forEachIndexed
            for (sentence in splitSentences(clean)) {
                for (piece in fitToMax(sentence)) {
                    val text = piece.trim()
                    if (text.isNotEmpty() && hasSpeakableChars(text)) {
                        out += Segment(index = out.size, paragraphIndex = pIndex, text = text)
                    }
                }
            }
        }
        return out
    }

    private fun splitSentences(text: String): List<String> {
        val result = ArrayList<String>()
        val sb = StringBuilder()
        var i = 0
        while (i < text.length) {
            val c = text[i]
            sb.append(c)
            if (c in SENTENCE_END || (breakAtComma && c == ',')) {
                // Absorb runs like "..." or "?!" and closing quotes/brackets right after.
                var j = i + 1
                while (j < text.length && (text[j] in SENTENCE_END || text[j] in CLOSERS || (breakAtComma && text[j] == ','))) {
                    sb.append(text[j]); j++
                }
                // Decimal numbers "3.5" and abbreviations like "Ch.12" are not boundaries.
                val next = text.getOrNull(j)
                val prev = text.getOrNull(i - 1)
                val isDecimal = c == '.' && prev?.isDigit() == true && next?.isDigit() == true
                if (!isDecimal && (next == null || next.isWhitespace())) {
                    result += sb.toString()
                    sb.setLength(0)
                }
                i = j
                continue
            }
            i++
        }
        if (sb.isNotBlank()) result += sb.toString()
        return result
    }

    private fun fitToMax(sentence: String): List<String> {
        val s = sentence.trim()
        if (s.length <= maxChars) return listOf(s)
        // Prefer clause separators, then spaces.
        val pieces = ArrayList<String>()
        var rest = s
        while (rest.length > maxChars) {
            val window = rest.substring(0, maxChars)
            var cut = window.lastIndexOfAny(CLAUSE_SEPARATORS)
            if (cut < maxChars / 3) cut = window.lastIndexOf(' ')
            if (cut <= 0) cut = maxChars - 1
            pieces += rest.substring(0, cut + 1).trim()
            rest = rest.substring(cut + 1).trim()
        }
        if (rest.isNotEmpty()) pieces += rest
        return pieces
    }

    private fun hasSpeakableChars(text: String): Boolean = text.any { it.isLetterOrDigit() }

    private companion object {
        val SENTENCE_END = setOf('.', '!', '?', '…', ';')
        val CLOSERS = setOf('"', '”', '’', '\'', ')', ']', '」', '』', '》')
        val CLAUSE_SEPARATORS = charArrayOf(',', ':', '–', '—', ';')
    }
}
