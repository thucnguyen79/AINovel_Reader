package vn.esstar.ainovel.data.translate.engine

/** Which dictionaries to apply. HAN_VIET reads each character's Sino-Vietnamese sound only. */
enum class ConvertMode { CONVERT, HAN_VIET, RAW }

/**
 * Classic Vietnamese "truyện convert" engine: longest-match over Names, VietPhrase and a per-character
 * Hán Việt fallback. Pure Kotlin; dictionaries are looked up through [IndexedDictionary] so memory
 * stays small. Thread-safe as long as the dictionaries are not closed while converting.
 */
class ConvertEngine(
    private val names: IndexedDictionary? = null,
    private val vietPhrase: IndexedDictionary? = null,
    private val hanViet: IndexedDictionary? = null,
    /** Per-book glossary; wins over every dictionary. */
    private val glossary: Map<String, String> = emptyMap(),
) {
    private val glossaryMaxLen = glossary.keys.maxOfOrNull { it.length } ?: 0

    fun convertParagraphs(paragraphs: List<String>, mode: ConvertMode = ConvertMode.CONVERT): List<String> =
        paragraphs.map { convert(it, mode) }

    fun convert(text: String, mode: ConvertMode = ConvertMode.CONVERT): String {
        if (mode == ConvertMode.RAW) return text
        val out = StringBuilder(text.length * 3)
        var i = 0
        var capitalizeNext = true
        var pendingSpace = false

        fun emitWord(word: String) {
            if (word.isEmpty()) return
            if (pendingSpace && out.isNotEmpty() && !out.last().isWhitespace() && !NO_SPACE_AFTER.contains(out.last())) out.append(' ')
            var w = word
            if (capitalizeNext) {
                w = w.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
                capitalizeNext = false
            }
            out.append(w)
            pendingSpace = true
        }

        while (i < text.length) {
            val c = text[i]
            if (isHan(c)) {
                val match = if (mode == ConvertMode.HAN_VIET) null else matchPhrase(text, i)
                if (match != null) {
                    val (len, value) = match
                    emitWord(firstMeaning(value))
                    i += len
                    continue
                }
                val reading = hanViet?.lookup(c.toString())
                emitWord(reading ?: c.toString())
                i++
                continue
            }
            val mapped = PUNCTUATION[c]
            if (mapped != null) {
                when {
                    NO_SPACE_BEFORE.contains(mapped) -> { out.append(mapped); pendingSpace = true }
                    NO_SPACE_AFTER.contains(mapped) -> {
                        if (out.isNotEmpty() && !out.last().isWhitespace()) out.append(' ')
                        out.append(mapped); pendingSpace = false
                    }
                    else -> { out.append(mapped); pendingSpace = true }
                }
                if (SENTENCE_END.contains(mapped)) capitalizeNext = true
                i++
                continue
            }
            if (c.isWhitespace()) {
                pendingSpace = true
                i++
                continue
            }
            // Latin letters, digits, existing punctuation: copy runs verbatim as one word.
            val start = i
            while (i < text.length && !isHan(text[i]) && !text[i].isWhitespace() && PUNCTUATION[text[i]] == null) i++
            val run = text.substring(start, i)
            if (run.length == 1 && NO_SPACE_BEFORE.contains(run[0])) {
                out.append(run); pendingSpace = true
                if (SENTENCE_END.contains(run[0])) capitalizeNext = true
            } else {
                emitWord(run)
                if (run.last() in SENTENCE_END) capitalizeNext = true
            }
        }
        return out.toString().replace(Regex(" {2,}"), " ").trim()
    }

    private fun matchPhrase(text: String, start: Int): Pair<Int, String>? {
        if (glossaryMaxLen > 0) {
            val limit = minOf(glossaryMaxLen, text.length - start)
            for (len in limit downTo 1) {
                glossary[text.substring(start, start + len)]?.let { return len to it }
            }
        }
        names?.longestMatch(text, start)?.let { return it }
        return vietPhrase?.longestMatch(text, start)
    }

    private fun firstMeaning(value: String): String {
        val slash = value.indexOf('/')
        val first = if (slash > 0) value.substring(0, slash) else value
        return first.trim()
    }

    private companion object {
        fun isHan(c: Char): Boolean = c in '一'..'鿿' || c in '㐀'..'䶿' || c in '豈'..'﫿'

        val PUNCTUATION: Map<Char, Char> = mapOf(
            '，' to ',', '。' to '.', '！' to '!', '？' to '?', '；' to ';', '：' to ':',
            '“' to '“', '”' to '”', '‘' to '‘', '’' to '’', '「' to '“', '」' to '”', '『' to '“', '』' to '”',
            '（' to '(', '）' to ')', '《' to '“', '》' to '”', '、' to ',', '…' to '…', '—' to '-', '～' to '~',
            '【' to '[', '】' to ']', '〈' to '“', '〉' to '”',
        )
        val NO_SPACE_BEFORE = setOf(',', '.', '!', '?', ';', ':', ')', ']', '”', '’', '…')
        val NO_SPACE_AFTER = setOf('(', '[', '“', '‘')
        val SENTENCE_END = setOf('.', '!', '?', '…')
    }
}
