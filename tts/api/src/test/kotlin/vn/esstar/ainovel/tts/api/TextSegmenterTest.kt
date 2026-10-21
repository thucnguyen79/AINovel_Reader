package vn.esstar.ainovel.tts.api

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TextSegmenterTest {

    private val segmenter = TextSegmenter(maxChars = 60)

    @Test
    fun `splits sentences on terminal punctuation and keeps closing quotes`() {
        val segments = segmenter.segment(listOf("“Hãy cạn chén đi.” Vân Kiếm đưa tay nâng chén trà lên! Rồi sao? Không biết…"))
        assertEquals(
            listOf("“Hãy cạn chén đi.”", "Vân Kiếm đưa tay nâng chén trà lên!", "Rồi sao?", "Không biết…"),
            segments.map { it.text },
        )
        assertEquals(listOf(0, 1, 2, 3), segments.map { it.index })
        assertTrue(segments.all { it.paragraphIndex == 0 })
    }

    @Test
    fun `does not split decimals or abbreviations without following space`() {
        val segments = segmenter.segment(listOf("Giá là 3.5 lượng bạc. Xem Ch.12 nhé."))
        assertEquals(listOf("Giá là 3.5 lượng bạc.", "Xem Ch.12 nhé."), segments.map { it.text })
    }

    @Test
    fun `long sentence is cut at clause separators under the limit`() {
        val long = "Một thiếu niên mang trong mình bao khát khao và mơ mộng, một chàng trai nuôi mộng có thể tiến vào Thánh địa, cũng có thể theo đuổi võ học đến cực hạn."
        val segments = segmenter.segment(listOf(long))
        assertTrue(segments.size >= 3)
        assertTrue(segments.all { it.text.length <= 60 })
        assertEquals(long.replace(" ", ""), segments.joinToString("") { it.text }.replace(" ", ""))
    }

    @Test
    fun `comma mode breaks at every comma`() {
        val segments = TextSegmenter(breakAtComma = true).segment(listOf("Một, hai, ba. Bốn."))
        assertEquals(listOf("Một,", "hai,", "ba.", "Bốn."), segments.map { it.text })
    }

    @Test
    fun `paragraph index is tracked and empty or symbol-only pieces are dropped`() {
        val segments = segmenter.segment(listOf("Đoạn một.", "", "***", "Đoạn ba. Câu hai"))
        assertEquals(listOf(0, 3, 3), segments.map { it.paragraphIndex })
        assertEquals(listOf("Đoạn một.", "Đoạn ba.", "Câu hai"), segments.map { it.text })
    }

    @Test
    fun `segments carry their character range inside the paragraph`() {
        val paragraph = "  Xin chào.   Bạn khoẻ không?  Tốt!"
        val segments = TextSegmenter().segment(listOf("", paragraph))
        assertEquals(listOf("Xin chào.", "Bạn khoẻ không?", "Tốt!"), segments.map { it.text })
        assertEquals(listOf(1, 1, 1), segments.map { it.paragraphIndex })
        segments.forEach { assertEquals(it.text, paragraph.substring(it.start, it.end)) }
        val long = ("Một hai ba bốn năm sáu bảy tám chín mười, ").repeat(8) + "hết."
        val pieces = TextSegmenter(maxChars = 100).segment(listOf(long))
        pieces.forEach { assertEquals(it.text, long.substring(it.start, it.end)) }
    }
}
