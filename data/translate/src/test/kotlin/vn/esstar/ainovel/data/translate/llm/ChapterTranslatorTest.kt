package vn.esstar.ainovel.data.translate.llm

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import vn.esstar.ainovel.domain.model.GlossaryEntry

class ChapterTranslatorTest {

    @Test
    fun `chunk groups consecutive paragraphs without splitting one`() {
        val paragraphs = listOf("a".repeat(900), "b".repeat(900), "c".repeat(900), "d".repeat(3000), "e".repeat(10))
        val chunks = ChapterTranslator.chunk(paragraphs, maxChars = 2000)
        assertEquals(listOf(0, 2, 3, 4), chunks.map { it.startIndex })
        assertEquals(listOf(2, 1, 1, 1), chunks.map { it.paragraphs.size })
        assertEquals(paragraphs, chunks.flatMap { it.paragraphs })
    }

    @Test
    fun `chunk of empty chapter is empty`() {
        assertTrue(ChapterTranslator.chunk(emptyList()).isEmpty())
    }

    @Test
    fun `user prompt numbers paragraphs and lists the glossary`() {
        val chunk = ChapterTranslator.Chunk(5, listOf("Hắn đích mục quang lãnh lãnh.", "Nàng cười."))
        val prompt = ChapterTranslator.userPrompt(chunk, listOf(GlossaryEntry(1, 7, "Lâm Mộc", "Lâm Mộc"), GlossaryEntry(2, null, " ", "x")), "Lâm Mộc Báo Thù")
        assertTrue(prompt.startsWith("Truyện: Lâm Mộc Báo Thù\n"))
        assertTrue(prompt.contains("- Lâm Mộc = Lâm Mộc\n"))
        assertTrue(prompt.contains("[1] Hắn đích mục quang lãnh lãnh.\n[2] Nàng cười.\n"))
        assertTrue(ChapterTranslator.systemPrompt("vi").contains("CONVERT"))
        assertTrue(ChapterTranslator.systemPrompt("zh").contains("tiếng Trung"))
    }

    @Test
    fun `parseReply maps numbered lines, continuation lines and alternative numbering`() {
        val reply = """
            [1] Ánh mắt hắn lạnh lẽo.
            [2] Nàng mỉm cười,
            rồi quay đi.
            3. Trời đã tối.
        """.trimIndent()
        assertEquals(listOf("Ánh mắt hắn lạnh lẽo.", "Nàng mỉm cười, rồi quay đi.", "Trời đã tối."), ChapterTranslator.parseReply(reply, 3))
    }

    @Test
    fun `parseReply tolerates a missing paragraph but rejects mostly missing output`() {
        val partial = ChapterTranslator.parseReply("[1] Một\n[3] Ba", 3)
        assertEquals(listOf("Một", "", "Ba"), partial)
        assertNull(ChapterTranslator.parseReply("Xin lỗi, tôi không thể giúp.", 4))
        assertNull(ChapterTranslator.parseReply("", 2))
    }

    @Test
    fun `parseReply accepts an unnumbered answer for a single paragraph`() {
        assertEquals(listOf("Chương 1: Về Không"), ChapterTranslator.parseReply("Chương 1: Về Không", 1))
        assertEquals(listOf("Chương 1: Về Không"), ChapterTranslator.parseReply("[1] Chương 1: Về Không", 1))
    }
}
