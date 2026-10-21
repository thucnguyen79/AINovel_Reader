package vn.esstar.ainovel.data.source

import org.jsoup.Jsoup
import org.junit.Assert.assertEquals
import org.junit.Test
import vn.esstar.ainovel.data.source.parser.ContentCleaner

class ContentCleanerTest {

    @Test
    fun `br and block tags become paragraph breaks`() {
        val el = Jsoup.parseBodyFragment(
            "<div id=c>Dòng một<br><br>Dòng   hai<p>Dòng ba</p><div>Dòng bốn</div>Dòng năm</div>",
        ).selectFirst("#c")!!
        assertEquals(
            listOf("Dòng một", "Dòng hai", "Dòng ba", "Dòng bốn", "Dòng năm"),
            ContentCleaner.toParagraphs(el),
        )
    }

    @Test
    fun `hidden elements and remove selectors are dropped`() {
        val el = Jsoup.parseBodyFragment(
            """<div id=c>Nội dung<span style="display:none;visibility:hidden">truyenfull</span>
               <div id="ads-top">QC</div><script>x()</script><br>Tiếp</div>""",
        ).selectFirst("#c")!!
        assertEquals(
            listOf("Nội dung", "Tiếp"),
            ContentCleaner.toParagraphs(el, removeSelectors = listOf("script", "div[id^=ads]")),
        )
    }

    @Test
    fun `lines matching patterns are dropped`() {
        val el = Jsoup.parseBodyFragment("<div id=c>Đọc tại truyenfull.vn<br>Chương thật</div>").selectFirst("#c")!!
        assertEquals(
            listOf("Chương thật"),
            ContentCleaner.toParagraphs(el, removeLinePatterns = listOf(Regex("truyenfull", RegexOption.IGNORE_CASE))),
        )
    }

    @Test
    fun `nbsp and exotic spaces are normalized`() {
        val el = Jsoup.parseBodyFragment("<div id=c>A&nbsp;&nbsp;B　C</div>").selectFirst("#c")!!
        assertEquals(listOf("A B C"), ContentCleaner.toParagraphs(el))
    }
}
