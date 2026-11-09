package vn.esstar.ainovel.data.translate.llm

import vn.esstar.ainovel.domain.model.GlossaryEntry

/**
 * Pure text logic around the model call: splitting a chapter into chunks the model can handle, writing the
 * prompt, and reading the numbered paragraphs back. Kept free of I/O so it is unit-tested with fixtures.
 */
object ChapterTranslator {

    /**
     * Target size of one request in characters. Larger chunks mean fewer requests, which matters more than
     * latency on free tiers that cap requests per minute (Gemini Flash: about 10/min).
     */
    const val DEFAULT_CHUNK_CHARS = 3_500

    data class Chunk(val startIndex: Int, val paragraphs: List<String>)

    /** Groups consecutive paragraphs up to [maxChars]; a paragraph longer than that becomes its own chunk. */
    fun chunk(paragraphs: List<String>, maxChars: Int = DEFAULT_CHUNK_CHARS): List<Chunk> {
        val chunks = mutableListOf<Chunk>()
        var current = mutableListOf<String>()
        var currentStart = 0
        var size = 0
        paragraphs.forEachIndexed { i, p ->
            if (current.isNotEmpty() && size + p.length > maxChars) {
                chunks += Chunk(currentStart, current)
                current = mutableListOf()
                size = 0
            }
            if (current.isEmpty()) currentStart = i
            current += p
            size += p.length + 1
        }
        if (current.isNotEmpty()) chunks += Chunk(currentStart, current)
        return chunks
    }

    fun systemPrompt(sourceLanguage: String): String {
        val task = if (sourceLanguage.lowercase() == "zh") {
            "Bạn là dịch giả tiểu thuyết mạng Trung Quốc sang tiếng Việt. Dịch văn bản tiếng Trung sau sang tiếng Việt tự nhiên, " +
                "văn phong truyện dịch, giữ nguyên ý, giọng điệu và xưng hô (ta, ngươi, hắn, nàng, huynh, đệ...)."
        } else {
            "Bạn là biên tập viên truyện dịch. Văn bản sau là bản CONVERT (dịch máy từng từ Hán Việt theo trật tự câu tiếng Trung). " +
                "Hãy viết lại thành tiếng Việt tự nhiên, trôi chảy như truyện dịch, giữ nguyên ý, chi tiết, giọng điệu và xưng hô " +
                "(ta, ngươi, hắn, nàng, huynh, đệ...). Không tóm tắt, không thêm bớt tình tiết."
        }
        return task + "\n" +
            "Quy tắc bắt buộc:\n" +
            "- Giữ nguyên tên riêng theo bảng thuật ngữ nếu có; tên không có trong bảng thì giữ dạng Hán Việt.\n" +
            "- Mỗi đoạn đầu vào bắt đầu bằng số thứ tự trong ngoặc vuông, ví dụ [3]. Trả về đúng số đoạn, đúng thứ tự, " +
            "mỗi đoạn trên một dòng bắt đầu bằng cùng số thứ tự đó. Không gộp, không tách đoạn.\n" +
            "- Chỉ trả về các đoạn đã dịch, không giải thích, không ghi chú, không dùng Markdown."
    }

    fun userPrompt(chunk: Chunk, glossary: List<GlossaryEntry>, bookTitle: String?): String = buildString {
        if (!bookTitle.isNullOrBlank()) append("Truyện: ").append(bookTitle.trim()).append('\n')
        val terms = glossary.filter { it.source.isNotBlank() && it.target.isNotBlank() }.take(MAX_GLOSSARY)
        if (terms.isNotEmpty()) {
            append("Bảng thuật ngữ (nguồn = đích):\n")
            terms.forEach { append("- ").append(it.source.trim()).append(" = ").append(it.target.trim()).append('\n') }
        }
        append("Văn bản:\n")
        chunk.paragraphs.forEachIndexed { i, p -> append('[').append(i + 1).append("] ").append(p.trim()).append('\n') }
    }

    /**
     * Maps the reply back onto [expected] paragraphs. Lines starting with `[n]` (or `n.` / `n)`) are assigned by
     * number; continuation lines are appended to the previous paragraph. Returns null when fewer than half the
     * paragraphs came back, so the caller can retry or keep the source text.
     */
    fun parseReply(reply: String, expected: Int): List<String>? {
        val out = arrayOfNulls<String>(expected)
        var last = -1
        val marker = Regex("""^\s*(?:\[\s*(\d{1,4})\s*]|(\d{1,4})[.)])\s*(.*)$""")
        for (rawLine in reply.lines()) {
            val line = rawLine.trimEnd()
            if (line.isBlank()) continue
            val m = marker.find(line)
            val n = m?.let { (it.groupValues[1].ifEmpty { it.groupValues[2] }).toIntOrNull() }
            if (m != null && n != null && n in 1..expected) {
                val text = m.groupValues[3].trim()
                last = n - 1
                out[last] = if (out[last].isNullOrEmpty()) text else out[last] + "\n" + text
            } else if (last >= 0) {
                out[last] = (out[last].orEmpty() + "\n" + line.trim()).trim()
            } else if (expected == 1) {
                out[0] = (out[0].orEmpty() + "\n" + line.trim()).trim()
                last = 0
            }
        }
        val filled = out.count { !it.isNullOrBlank() }
        if (filled == 0 || filled * 2 < expected) return null
        return out.map { it?.replace(Regex("\\s*\\n\\s*"), " ")?.trim().orEmpty() }
    }

    private const val MAX_GLOSSARY = 120
}
