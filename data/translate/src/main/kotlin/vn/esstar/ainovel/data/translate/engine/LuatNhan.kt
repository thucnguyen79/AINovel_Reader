package vn.esstar.ainovel.data.translate.engine

/**
 * "Luật nhân" (QuickTranslator `LuatNhan.txt`): phrase templates with `{0}`, `{1}`… placeholders, e.g.
 * `{0}的{1}={1} của {0}`. Placeholders stand for dictionary words; literal characters must appear as-is.
 * Rules are applied on the token stream after dictionary matching, so they reorder words that the
 * plain longest-match pass already produced (adjective/possessive order).
 */
data class LuatNhanRule(val pattern: String, val replacement: String) {
    /** Pattern split into placeholder indices and literal strings. */
    val parts: List<Part> = parse(pattern)
    val placeholderCount: Int = parts.count { it is Part.Placeholder }

    sealed interface Part {
        data class Placeholder(val index: Int) : Part
        data class Literal(val text: String) : Part
    }

    companion object {
        // Both braces escaped: Android's ICU regex rejects a bare closing brace.
        private val PLACEHOLDER = Regex("""\{(\d)\}""")

        fun parse(pattern: String): List<Part> {
            val parts = ArrayList<Part>()
            var last = 0
            for (m in PLACEHOLDER.findAll(pattern)) {
                if (m.range.first > last) parts += Part.Literal(pattern.substring(last, m.range.first))
                parts += Part.Placeholder(m.groupValues[1].toInt())
                last = m.range.last + 1
            }
            if (last < pattern.length) parts += Part.Literal(pattern.substring(last))
            return parts
        }

        /** Parses `pattern=replacement` lines; blank lines and `#` comments are skipped. */
        fun parseLines(lines: Sequence<String>): List<LuatNhanRule> = lines
            .map { it.removePrefix("﻿").trimEnd('\r').trim() }
            .filter { it.isNotEmpty() && !it.startsWith("#") }
            .mapNotNull { line ->
                val eq = line.indexOf('=')
                if (eq <= 0) null else LuatNhanRule(line.substring(0, eq), line.substring(eq + 1))
            }
            .filter { it.placeholderCount > 0 && it.parts.any { p -> p is Part.Literal } }
            .toList()

        /** Rules bundled with the app; users can replace them by importing their own LuatNhan.txt. */
        val DEFAULT: List<LuatNhanRule> = parseLines(
            sequenceOf(
                "{0}的{1}={1} của {0}",
                "{0}之{1}={1} của {0}",
                "{0}们的{1}={1} của các {0}",
                "{0}们=các {0}",
                "{0}的=của {0}",
            ),
        )
    }
}
