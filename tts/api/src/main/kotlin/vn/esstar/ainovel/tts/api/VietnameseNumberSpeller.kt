package vn.esstar.ainovel.tts.api

/**
 * Spells digits as Vietnamese words ("12" → "mười hai") for engines whose phonemizer has no digit
 * support (Piper/espeak-ng `vi` skips them). Handles integers up to 10^15, decimals, percentages,
 * simple ranges and dotted/comma thousands separators; everything else is left untouched.
 */
object VietnameseNumberSpeller {
    private val DIGITS = listOf("không", "một", "hai", "ba", "bốn", "năm", "sáu", "bảy", "tám", "chín")
    private val NUMBER = Regex("""(?<![\p{L}\d])(\d{1,3}(?:[.,]\d{3})+|\d+)(?:([.,])(\d+))?(%)?(?![\p{L}\d])""")

    fun spell(text: String): String = NUMBER.replace(text) { m ->
        val intPart = m.groupValues[1]
        val sep = m.groupValues[2]
        val frac = m.groupValues[3]
        val percent = m.groupValues[4]
        val digitsOnly = intPart.replace(".", "").replace(",", "")
        if (digitsOnly.length > 15) return@replace m.value
        val sb = StringBuilder(spellInteger(digitsOnly))
        if (sep.isNotEmpty() && frac.isNotEmpty()) {
            sb.append(" phẩy ")
            // Short fractions read as a number ("1,5" → "một phẩy năm"); long ones digit by digit.
            if (frac.length <= 2 && !frac.startsWith("0")) sb.append(spellInteger(frac)) else sb.append(frac.map { DIGITS[it - '0'] }.joinToString(" "))
        }
        if (percent.isNotEmpty()) sb.append(" phần trăm")
        sb.toString()
    }

    fun spellInteger(digits: String): String {
        val n = digits.trimStart('0')
        if (n.isEmpty()) return DIGITS[0]
        if (n.length > 15) return digits
        val groups = ArrayList<Int>()
        var s = n
        while (s.isNotEmpty()) {
            val cut = (s.length - 3).coerceAtLeast(0)
            groups.add(0, s.substring(cut).toInt())
            s = s.substring(0, cut)
        }
        val units = listOf("", " nghìn", " triệu", " tỷ", " nghìn tỷ")
        val parts = ArrayList<String>()
        for ((i, g) in groups.withIndex()) {
            val scale = groups.size - 1 - i
            if (g == 0) continue
            val full = i > 0 // groups after the first read all three places ("không trăm lẻ năm")
            parts += spellGroup(g, full) + units.getOrElse(scale) { "" }
        }
        return parts.joinToString(" ").replace(Regex(" {2,}"), " ").trim()
    }

    private fun spellGroup(n: Int, full: Boolean): String {
        val hundreds = n / 100
        val tens = (n % 100) / 10
        val ones = n % 10
        val out = ArrayList<String>()
        if (hundreds > 0 || full) out += DIGITS[hundreds] + " trăm"
        when {
            tens == 0 && ones == 0 -> Unit
            tens == 0 -> { if (hundreds > 0 || full) out += "lẻ"; out += DIGITS[ones] }
            tens == 1 -> out += "mười" + onesAfterTens(ones, tens)
            else -> out += DIGITS[tens] + " mươi" + onesAfterTens(ones, tens)
        }
        return out.joinToString(" ")
    }

    private fun onesAfterTens(ones: Int, tens: Int): String = when (ones) {
        0 -> ""
        1 -> if (tens >= 2) " mốt" else " một"
        4 -> if (tens >= 2) " tư" else " bốn"
        5 -> " lăm"
        else -> " " + DIGITS[ones]
    }
}
