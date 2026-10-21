package vn.esstar.ainovel.tts.api

import org.junit.Assert.assertEquals
import org.junit.Test

class TextNormalizerTest {

    private val n = TextNormalizer()

    @Test
    fun `removes anti-copy dots inside words`() {
        assertEquals("ai cho ngươi lấy sạch thuốc trong trại", n.normalize("ai cho ngươi lấy sạch t.h.u.ố.c trong trại"))
        assertEquals("một cảnh đẹp", n.normalize("một c·ả·n·h đẹp"))
    }

    @Test
    fun `keeps real sentence dots and decimals`() {
        assertEquals("Hết rồi. Giá 3.5 lượng.", n.normalize("Hết rồi. Giá 3.5 lượng."))
        // Two letters joined by a dot (initials like "T.H") are left alone; three or more are treated as anti-copy.
        assertEquals("T.H đến rồi", n.normalize("T.H đến rồi"))
    }

    @Test
    fun `collapses repeated punctuation and normalizes ellipsis`() {
        assertEquals("Cái gì?! Không…", n.normalize("Cái gì???!!! Không......"))
    }

    @Test
    fun `expands chapter abbreviation and strips dialogue dash`() {
        assertEquals("Xem Chương 12 đi", n.normalize("Xem Ch.12 đi"))
        assertEquals("Minh ca, ngươi hôm nay khí phách quá!", n.normalize("- Minh ca, ngươi hôm nay khí phách quá!"))
    }

    @Test
    fun `removes decorative symbols and collapses whitespace`() {
        assertEquals("Chương mới ra rồi", n.normalize("★ Chương   mới ra rồi ✨"))
    }

    @Test
    fun `normalizeAll drops blank results`() {
        assertEquals(listOf("A", "B"), n.normalizeAll(listOf("A", "✨", "B")))
    }
}
