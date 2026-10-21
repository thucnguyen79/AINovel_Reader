package vn.esstar.ainovel.tts.api

/**
 * Rewrites text so a TTS engine reads it naturally. Pure functions, no I/O.
 *
 * Handles the junk that story sites insert: anti-copy dots inside words (`t.h.u.ố.c`), repeated
 * punctuation, decorative symbols, bracketed site notes, and spells out small integers that Vietnamese
 * engines otherwise read digit by digit.
 */
class TextNormalizer {

    fun normalize(text: String): String {
        var t = text
        t = ANTI_COPY_DOTS.replace(t) { m -> m.value.replace(".", "").replace("·", "") }
        t = t.replace(' ', ' ')
        t = DECORATIONS.replace(t, " ")
        t = ELLIPSIS.replace(t, "…")
        t = REPEATED_PUNCT.replace(t) { m -> m.value.first().toString() }
        t = CHAPTER_ABBR.replace(t) { m -> "Chương ${m.groupValues[1]}" }
        t = DASH_SPEECH.replace(t, "")
        t = t.replace(Regex("\\s+"), " ").trim()
        return t
    }

    fun normalizeAll(paragraphs: List<String>): List<String> = paragraphs.map(::normalize).filter { it.isNotBlank() }

    private companion object {
        /** `t.h.u.ố.c`, `đ.ị.t`: single letters joined by dots or middle dots, at least 3 letters. */
        val ANTI_COPY_DOTS = Regex("(?<![\\p{L}\\d])\\p{L}(?:[.·]\\p{L}){2,}(?![\\p{L}\\d])")
        val DECORATIONS = Regex("[\\p{So}\\p{Cn}\\u2600-\\u27BF\\uFE0F\\u200B-\\u200D\\u2060]+")
        val ELLIPSIS = Regex("\\.{3,}")
        val REPEATED_PUNCT = Regex("([!?,;:])\\1+")
        val CHAPTER_ABBR = Regex("\\b[Cc]h\\.\\s*(\\d+)")
        /** Leading dialogue dash "- Xin chào" → "Xin chào" (engines read the dash as "trừ"). */
        val DASH_SPEECH = Regex("^\\s*[-–—]\\s*")
    }
}
