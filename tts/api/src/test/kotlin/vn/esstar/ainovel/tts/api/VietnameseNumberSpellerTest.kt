package vn.esstar.ainovel.tts.api

import org.junit.Assert.assertEquals
import org.junit.Test

class VietnameseNumberSpellerTest {
    @Test
    fun `integers`() {
        assertEquals("không", VietnameseNumberSpeller.spellInteger("0"))
        assertEquals("năm", VietnameseNumberSpeller.spellInteger("5"))
        assertEquals("mười", VietnameseNumberSpeller.spellInteger("10"))
        assertEquals("mười lăm", VietnameseNumberSpeller.spellInteger("15"))
        assertEquals("hai mươi mốt", VietnameseNumberSpeller.spellInteger("21"))
        assertEquals("hai mươi tư", VietnameseNumberSpeller.spellInteger("24"))
        assertEquals("một trăm lẻ năm", VietnameseNumberSpeller.spellInteger("105"))
        assertEquals("một nghìn không trăm lẻ năm", VietnameseNumberSpeller.spellInteger("1005"))
        assertEquals("hai nghìn không trăm hai mươi tư", VietnameseNumberSpeller.spellInteger("2024"))
        assertEquals("một triệu hai trăm ba mươi nghìn", VietnameseNumberSpeller.spellInteger("1230000"))
        assertEquals("ba tỷ", VietnameseNumberSpeller.spellInteger("3000000000"))
    }

    @Test
    fun `numbers inside text, decimals, percent and separators`() {
        assertEquals("Chương mười hai: ngày ba", VietnameseNumberSpeller.spell("Chương 12: ngày 3"))
        assertEquals("tăng một phẩy năm phần trăm", VietnameseNumberSpeller.spell("tăng 1,5%"))
        assertEquals("giá hai triệu đồng", VietnameseNumberSpeller.spell("giá 2.000.000 đồng"))
        assertEquals("pi là ba phẩy mười bốn", VietnameseNumberSpeller.spell("pi là 3,14"))
        assertEquals("mã A1 và 2B giữ nguyên", VietnameseNumberSpeller.spell("mã A1 và 2B giữ nguyên"))
    }
}
