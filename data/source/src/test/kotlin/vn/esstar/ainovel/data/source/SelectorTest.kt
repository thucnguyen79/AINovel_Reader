package vn.esstar.ainovel.data.source

import org.jsoup.Jsoup
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import vn.esstar.ainovel.data.source.selector.Selector
import vn.esstar.ainovel.data.source.selector.all
import vn.esstar.ainovel.data.source.selector.first

class SelectorTest {

    private val doc = Jsoup.parse(
        """
        <div class="row">
          <h3 class="t"><a href="/book/abc/" title="ABC">  Tên  Truyện&nbsp;A </a></h3>
          <span class="author"><i></i> Tác Giả</span>
          <div data-image="/cover/x.jpg"></div>
          <a class="ch" href="/book/abc/chuong-12/">Chương 12: Mở đầu</a>
          <span class="g">Tiên Hiệp</span><span class="g">Huyền Huyễn</span>
          <input id="total-page" value="33">
        </div>
        """.trimIndent(),
        "https://example.com/list/",
    )

    @Test
    fun `parse splits css attr and regex`() {
        val s = Selector.parse("h3.t a@href|regex:/book/([^/]+)/")
        assertEquals("h3.t a", s.css)
        assertEquals("href", s.attr)
        assertEquals("/book/([^/]+)/", s.regex?.pattern)
    }

    @Test
    fun `text is default and whitespace is normalized`() {
        assertEquals("Tên Truyện A", doc.first("h3.t a"))
    }

    @Test
    fun `href is resolved to absolute url`() {
        assertEquals("https://example.com/book/abc/", doc.first("h3.t a@href"))
    }

    @Test
    fun `data attributes are resolved to absolute url`() {
        assertEquals("https://example.com/cover/x.jpg", doc.first("div[data-image]@data-image"))
    }

    @Test
    fun `ownText skips child elements`() {
        assertEquals("Tác Giả", doc.first("span.author@ownText"))
    }

    @Test
    fun `regex keeps first group`() {
        assertEquals("12", doc.first("a.ch@text|regex:Chương (\\d+)"))
        assertEquals("abc", doc.first("a.ch@href|regex:/book/([^/]+)/"))
    }

    @Test
    fun `all returns every match`() {
        assertEquals(listOf("Tiên Hiệp", "Huyền Huyễn"), doc.all("span.g"))
    }

    @Test
    fun `empty css selects the element itself`() {
        val el = doc.selectFirst("a.ch")!!
        assertEquals("https://example.com/book/abc/chuong-12/", Selector.parse("@href").first(el))
    }

    @Test
    fun `value attribute reads input value`() {
        assertEquals("33", doc.first("#total-page@value"))
    }

    @Test
    fun `missing match returns null`() {
        assertNull(doc.first(".nope"))
    }
}
