package vn.esstar.ainovel.tts.api

/**
 * Splits chapter paragraphs into speakable segments, keeping each segment's character range in its
 * paragraph (so the text shown and the text spoken stay aligned).
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
            if (paragraph.isBlank()) return@forEachIndexed
            for ((sStart, sEnd) in splitSentences(paragraph)) {
                for ((start, end) in fitToMax(paragraph, sStart, sEnd)) {
                    var a = start
                    var b = end
                    while (a < b && paragraph[a].isWhitespace()) a++
                    while (b > a && paragraph[b - 1].isWhitespace()) b--
                    val text = paragraph.substring(a, b)
                    if (text.isNotEmpty() && hasSpeakableChars(text)) {
                        out += Segment(index = out.size, paragraphIndex = pIndex, text = text, start = a, end = b)
                    }
                }
            }
        }
        return out
    }

    /** Sentence ranges (end exclusive) over [text]. */
    private fun splitSentences(text: String): List<Pair<Int, Int>> {
        val result = ArrayList<Pair<Int, Int>>()
        var segStart = 0
        var i = 0
        while (i < text.length) {
            val c = text[i]
            if (c in SENTENCE_END || (breakAtComma && c == ',')) {
                // Absorb runs like "..." or "?!" and closing quotes/brackets right after.
                var j = i + 1
                while (j < text.length && (text[j] in SENTENCE_END || text[j] in CLOSERS || (breakAtComma && text[j] == ','))) j++
                // Decimal numbers "3.5" and abbreviations like "Ch.12" are not boundaries.
                val next = text.getOrNull(j)
                val prev = text.getOrNull(i - 1)
                val isDecimal = c == '.' && prev?.isDigit() == true && next?.isDigit() == true
                if (!isDecimal && (next == null || next.isWhitespace())) {
                    result += segStart to j
                    segStart = j
                }
                i = j
                continue
            }
            i++
        }
        if (segStart < text.length && text.substring(segStart).isNotBlank()) result += segStart to text.length
        return result
    }

    private fun fitToMax(text: String, start: Int, end: Int): List<Pair<Int, Int>> {
        var a = start
        var b = end
        while (a < b && text[a].isWhitespace()) a++
        while (b > a && text[b - 1].isWhitespace()) b--
        if (b - a <= maxChars) return listOf(a to b)
        // Prefer clause separators, then spaces.
        val pieces = ArrayList<Pair<Int, Int>>()
        var rest = a
        while (b - rest > maxChars) {
            val window = text.substring(rest, rest + maxChars)
            var cut = window.lastIndexOfAny(CLAUSE_SEPARATORS)
            if (cut < maxChars / 3) cut = window.lastIndexOf(' ')
            if (cut <= 0) cut = maxChars - 1
            pieces += rest to rest + cut + 1
            rest += cut + 1
            while (rest < b && text[rest].isWhitespace()) rest++
        }
        if (rest < b) pieces += rest to b
        return pieces
    }

    private fun hasSpeakableChars(text: String): Boolean = text.any { it.isLetterOrDigit() }

    private companion object {
        val SENTENCE_END = setOf('.', '!', '?', '…', ';')
        val CLOSERS = setOf('"', '”', '’', '\'', ')', ']', '」', '』', '》')
        val CLAUSE_SEPARATORS = charArrayOf(',', ':', '–', '—', ';')
    }
}
