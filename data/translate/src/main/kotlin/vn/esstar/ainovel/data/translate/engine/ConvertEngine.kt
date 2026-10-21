package vn.esstar.ainovel.data.translate.engine

/** Which dictionaries to apply. HAN_VIET reads each character's Sino-Vietnamese sound only. */
enum class ConvertMode { CONVERT, HAN_VIET, RAW }

/** One unit of converted output; concatenating [text] of all tokens gives the converted paragraph. */
data class ConvertedToken(
    /** Original Chinese text (or the verbatim run for Latin/punctuation). */
    val source: String,
    /** Output text, including any leading space the layout needs. */
    val text: String,
    /** Other meanings the dictionaries offer for [source] (VietPhrase `a/b/c`, Hán Việt reading). */
    val alternatives: List<String> = emptyList(),
    val kind: Kind = Kind.WORD,
) {
    enum class Kind { WORD, RULE, PUNCT, VERBATIM }

    /** True when the user can meaningfully pick another meaning or add a glossary entry. */
    val isChinese: Boolean get() = kind != Kind.PUNCT && kind != Kind.VERBATIM && source.any { ConvertEngine.isHan(it) }
}

/**
 * Classic Vietnamese "truyện convert" engine: longest-match over glossary, Names and VietPhrase, a
 * per-character Hán Việt fallback, then "luật nhân" templates that reorder words. Pure Kotlin;
 * dictionaries are looked up through [IndexedDictionary] so memory stays small. Thread-safe as long
 * as the dictionaries are not closed while converting.
 */
class ConvertEngine(
    private val names: IndexedDictionary? = null,
    private val vietPhrase: IndexedDictionary? = null,
    private val hanViet: IndexedDictionary? = null,
    /** Per-book (and global) glossary; wins over every dictionary. */
    private val glossary: Map<String, String> = emptyMap(),
    private val rules: List<LuatNhanRule> = LuatNhanRule.DEFAULT,
) {
    private val glossaryMaxLen = glossary.keys.maxOfOrNull { it.length } ?: 0

    fun convertParagraphs(paragraphs: List<String>, mode: ConvertMode = ConvertMode.CONVERT): List<String> =
        paragraphs.map { convert(it, mode) }

    fun convert(text: String, mode: ConvertMode = ConvertMode.CONVERT): String =
        if (mode == ConvertMode.RAW) text else convertDetailed(text, mode).joinToString("") { it.text }

    fun convertDetailedParagraphs(paragraphs: List<String>, mode: ConvertMode = ConvertMode.CONVERT): List<List<ConvertedToken>> =
        paragraphs.map { convertDetailed(it, mode) }

    /** Converts [text] and keeps the source ↔ output mapping for each word. */
    fun convertDetailed(text: String, mode: ConvertMode = ConvertMode.CONVERT): List<ConvertedToken> {
        if (mode == ConvertMode.RAW) return listOf(ConvertedToken(text, text, kind = ConvertedToken.Kind.VERBATIM))
        var raw = tokenize(text, mode)
        if (mode == ConvertMode.CONVERT && rules.isNotEmpty()) raw = applyRules(raw)
        return render(raw)
    }

    // ---- phase 1: dictionary matching -------------------------------------------------------------

    private class Raw(
        val source: String,
        /** Lower-case word for WORD/RULE, mapped punctuation char for PUNCT, verbatim run otherwise. */
        val word: String,
        val alternatives: List<String>,
        val kind: ConvertedToken.Kind,
    )

    private fun tokenize(text: String, mode: ConvertMode): List<Raw> {
        val out = ArrayList<Raw>(text.length)
        var i = 0
        while (i < text.length) {
            val c = text[i]
            if (isHan(c)) {
                val match = if (mode == ConvertMode.HAN_VIET) null else matchPhrase(text, i)
                if (match != null) {
                    val (len, value) = match
                    val meanings = meanings(value)
                    val source = text.substring(i, i + len)
                    out += Raw(source, meanings.first(), alternativesFor(source, meanings), ConvertedToken.Kind.WORD)
                    i += len
                    continue
                }
                val ch = c.toString()
                val reading = hanViet?.lookup(ch)
                val alternatives = if (mode == ConvertMode.HAN_VIET) emptyList() else vietPhrase?.lookup(ch)?.let(::meanings).orEmpty()
                out += Raw(ch, reading ?: ch, alternatives, ConvertedToken.Kind.WORD)
                i++
                continue
            }
            val mapped = PUNCTUATION[c]
            if (mapped != null) {
                out += Raw(c.toString(), mapped.toString(), emptyList(), ConvertedToken.Kind.PUNCT)
                i++
                continue
            }
            if (c.isWhitespace()) {
                i++
                continue
            }
            // Latin letters, digits, existing punctuation: copy runs verbatim as one word.
            val start = i
            while (i < text.length && !isHan(text[i]) && !text[i].isWhitespace() && PUNCTUATION[text[i]] == null) i++
            val run = text.substring(start, i)
            val kind = if (run.length == 1 && (NO_SPACE_BEFORE.contains(run[0]) || NO_SPACE_AFTER.contains(run[0]))) {
                ConvertedToken.Kind.PUNCT
            } else {
                ConvertedToken.Kind.VERBATIM
            }
            out += Raw(run, run, emptyList(), kind)
        }
        return out
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

    /** Other meanings for a matched phrase: the remaining VietPhrase meanings, plus Names/VietPhrase cross-hits. */
    private fun alternativesFor(source: String, chosen: List<String>): List<String> {
        val all = LinkedHashSet<String>()
        all += chosen
        glossary[source]?.let { all += meanings(it) }
        names?.lookup(source)?.let { all += meanings(it) }
        vietPhrase?.lookup(source)?.let { all += meanings(it) }
        if (source.length == 1) hanViet?.lookup(source)?.let { all += it }
        all.remove(chosen.first())
        return all.toList()
    }

    private fun meanings(value: String): List<String> {
        val list = value.split('/').map { it.trim() }.filter { it.isNotEmpty() }
        return if (list.isEmpty()) listOf(value.trim()) else list
    }

    // ---- phase 2: luật nhân -----------------------------------------------------------------------

    /**
     * Applies rules in passes until nothing changes: at each position the rule consuming the most tokens
     * wins, and a rule result can itself fill a placeholder in a later pass ("các (nữ tử của X)").
     */
    private fun applyRules(tokens: List<Raw>): List<Raw> {
        var current = tokens
        repeat(MAX_RULE_PASSES) {
            val (next, changed) = applyRulesOnce(current)
            if (!changed) return next
            current = next
        }
        return current
    }

    private fun applyRulesOnce(tokens: List<Raw>): Pair<List<Raw>, Boolean> {
        val out = ArrayList<Raw>(tokens.size)
        var changed = false
        var i = 0
        while (i < tokens.size) {
            var best: LuatNhanRule? = null
            var bestLen = 0
            for (rule in rules) {
                val consumed = matchRule(rule, tokens, i) ?: continue
                if (consumed > bestLen) { best = rule; bestLen = consumed }
            }
            if (best == null) {
                out += tokens[i]
                i++
                continue
            }
            val captured = HashMap<Int, Raw>()
            var k = i
            for (part in best.parts) {
                when (part) {
                    is LuatNhanRule.Part.Placeholder -> captured[part.index] = tokens[k++]
                    is LuatNhanRule.Part.Literal -> k += literalTokenCount(tokens, k, part.text)
                }
            }
            var word = best.replacement
            captured.forEach { (idx, tok) -> word = word.replace("{$idx}", tok.word) }
            val source = tokens.subList(i, i + bestLen).joinToString("") { it.source }
            out += Raw(source, word.replace(Regex(" {2,}"), " ").trim(), emptyList(), ConvertedToken.Kind.RULE)
            i += bestLen
            changed = true
        }
        return out to changed
    }

    /** Number of tokens the rule consumes starting at [start], or null when it does not match there. */
    private fun matchRule(rule: LuatNhanRule, tokens: List<Raw>, start: Int): Int? {
        var k = start
        for (part in rule.parts) {
            when (part) {
                is LuatNhanRule.Part.Placeholder -> {
                    val t = tokens.getOrNull(k) ?: return null
                    if ((t.kind != ConvertedToken.Kind.WORD && t.kind != ConvertedToken.Kind.RULE) || !t.source.any(::isHan)) return null
                    k++
                }
                is LuatNhanRule.Part.Literal -> {
                    val n = literalTokenCount(tokens, k, part.text)
                    if (n == 0) return null
                    k += n
                }
            }
        }
        return k - start
    }

    /** How many consecutive tokens from [start] spell exactly [literal]; 0 when they do not. */
    private fun literalTokenCount(tokens: List<Raw>, start: Int, literal: String): Int {
        var acc = ""
        var k = start
        while (acc.length < literal.length && k < tokens.size) {
            acc += tokens[k].source
            k++
        }
        return if (acc == literal) k - start else 0
    }

    // ---- phase 3: spacing and capitalisation -----------------------------------------------------

    private fun render(tokens: List<Raw>): List<ConvertedToken> {
        val out = ArrayList<ConvertedToken>(tokens.size)
        var capitalizeNext = true
        var pendingSpace = false
        var lastChar: Char? = null

        fun append(raw: Raw, text: String) {
            out += ConvertedToken(raw.source, text, raw.alternatives, raw.kind)
            lastChar = text.lastOrNull() ?: lastChar
        }

        for (raw in tokens) {
            when (raw.kind) {
                ConvertedToken.Kind.PUNCT -> {
                    val p = raw.word[0]
                    when {
                        NO_SPACE_BEFORE.contains(p) -> { append(raw, raw.word); pendingSpace = true }
                        NO_SPACE_AFTER.contains(p) -> {
                            val space = if (out.isNotEmpty() && lastChar?.isWhitespace() == false) " " else ""
                            append(raw, space + raw.word); pendingSpace = false
                        }
                        else -> { append(raw, raw.word); pendingSpace = true }
                    }
                    if (SENTENCE_END.contains(p)) capitalizeNext = true
                }
                else -> {
                    if (raw.word.isEmpty()) continue
                    var w = raw.word
                    if (capitalizeNext) {
                        w = w.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
                        capitalizeNext = false
                    }
                    val space = if (pendingSpace && out.isNotEmpty() && lastChar?.isWhitespace() == false && !NO_SPACE_AFTER.contains(lastChar)) " " else ""
                    append(raw, space + w)
                    pendingSpace = true
                    if (raw.kind == ConvertedToken.Kind.VERBATIM && w.last() in SENTENCE_END) capitalizeNext = true
                }
            }
        }
        return out
    }

    companion object {
        private const val MAX_RULE_PASSES = 3

        fun isHan(c: Char): Boolean = c in '一'..'鿿' || c in '㐀'..'䶿' || c in '豈'..'﫿'

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
