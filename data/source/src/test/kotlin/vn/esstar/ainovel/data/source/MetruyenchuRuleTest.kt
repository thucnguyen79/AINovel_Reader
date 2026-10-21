package vn.esstar.ainovel.data.source

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import vn.esstar.ainovel.data.source.parser.RuleParser
import vn.esstar.ainovel.domain.model.UrlKind

/** metruyenchu.com.vn and wikidichvn.com share one engine; both rules are exercised here. */
class MetruyenchuRuleTest {

    private val rule = Fixtures.rule("metruyenchu")
    private val parser = RuleParser(rule)

    @Test
    fun `classifies urls`() {
        assertEquals(UrlKind.BOOK, parser.classify("https://metruyenchu.com.vn/hoa-son-tai-khoi"))
        assertEquals(UrlKind.CHAPTER, parser.classify("https://metruyenchu.com.vn/thien-kim-thien-ho/chuong-2-_3TIAct9v7ux"))
        assertEquals(UrlKind.CHAPTER, parser.classify("https://metruyenchu.com.vn/ban-gai-rong-tai-sinh/chuong-1-!HlWC2MSe7Rm"))
        assertEquals(UrlKind.UNKNOWN, parser.classify("https://metruyenchu.com.vn/danh-sach/truyen-hot/"))
        assertEquals(UrlKind.UNKNOWN, parser.classify("https://metruyenchu.com.vn/"))
    }

    @Test
    fun `parses search results`() {
        val results = parser.parseSearch(Fixtures.html("metruyenchu", "search.html"), "https://metruyenchu.com.vn/search?q=tien+nghich")
        assertEquals(5, results.size)
        val first = results.first()
        assertEquals("Tiên Nghịch: Sát Thần Trở Về, Khai Cục Cưới Vợ Lý Mộ Uyển", first.title)
        assertEquals("https://metruyenchu.com.vn/tien-nghich-sat-than-tro-ve-khai-cuc-cuoi-vo-ly-mo-uyen", first.url)
        assertEquals("Xí Nga Tứ Hào Cơ", first.author)
        assertEquals("https://metruyenchu.com.vn/media/book/tien-nghich-sat-than-tro-ve-khai-cuc-cuo-FVCwaPCe.jpg", first.coverUrl)
    }

    @Test
    fun `parses book info`() {
        val info = parser.parseBookInfo(Fixtures.html("metruyenchu", "book.html"), "https://metruyenchu.com.vn/hoa-son-tai-khoi")
        assertEquals("Hoa Sơn Tái Khởi", info.title)
        assertEquals("LuotNovel", info.author)
        assertEquals("https://metruyenchu.com.vn/media/book/hoa-son-tai-khoi-jERBpEcC.jpg", info.coverUrl)
        assertEquals("Đang cập nhật", info.status)
        assertEquals(listOf("Action"), info.genres)
        assertTrue(info.description!!.contains("Chap 174"))
    }

    @Test
    fun `first toc page is inline and reports ajax page count`() {
        val url = "https://metruyenchu.com.vn/hoa-son-tai-khoi"
        val html = Fixtures.html("metruyenchu", "book.html")
        val page1 = parser.parseTocPage(html, url)
        assertEquals(100, page1.chapters.size)
        assertEquals("Chương 80", page1.chapters.first().title)
        assertEquals("https://metruyenchu.com.vn/hoa-son-tai-khoi/chuong-80-LQ_6GUMpZ0Bv", page1.chapters.first().url)
        assertEquals(19, page1.totalPages)
        assertEquals("118615", parser.tocBookId(html, url))
        assertEquals("https://metruyenchu.com.vn/get/listchap/118615?page=2", parser.tocPageUrl(url, 2, "118615"))
    }

    @Test
    fun `short book has a single inline toc page`() {
        val page = parser.parseTocPage(Fixtures.html("metruyenchu", "book_short.html"), "https://metruyenchu.com.vn/thien-kim-thien-ho")
        assertEquals(9, page.chapters.size)
        assertNull(page.totalPages)
    }

    @Test
    fun `ajax toc page is json wrapping an html fragment`() {
        val json = Fixtures.html("metruyenchu", "toc_page2.json")
        val fragment = parser.tocHtmlFromJson(json)
        assertTrue(fragment.startsWith("<div class='clearfix'>"))
        val page = parser.parseTocPage(fragment, "https://metruyenchu.com.vn/get/listchap/118615?page=2", startIndex = 100, fragment = true)
        assertEquals(100, page.chapters.size)
        assertEquals(100, page.chapters.first().index)
        assertEquals("Chương 180", page.chapters.first().title)
        assertEquals("https://metruyenchu.com.vn/hoa-son-tai-khoi/chuong-180-kHGUwXPhQPN_", page.chapters.first().url)
    }

    @Test
    fun `parses chapter`() {
        val url = "https://metruyenchu.com.vn/thien-kim-thien-ho/chuong-2-_3TIAct9v7ux"
        val chapter = parser.parseChapter(Fixtures.html("metruyenchu", "chapter.html"), url)
        assertEquals("Chương 2", chapter.title)
        assertEquals("https://metruyenchu.com.vn/thien-kim-thien-ho", chapter.bookUrl)
        assertEquals("https://metruyenchu.com.vn/thien-kim-thien-ho/chuong-1-y8EZEdwk5A38", chapter.prevUrl)
        assertEquals("https://metruyenchu.com.vn/thien-kim-thien-ho/chuong-3-QpNw!DBlSSvY", chapter.nextUrl)
        assertTrue(chapter.paragraphs.size > 10)
        assertTrue(chapter.paragraphs.first().startsWith("“Uất Trì Phong"))
    }

    // ---- wikidich: same engine, different host and search path ----

    private val wdRule = Fixtures.rule("wikidich")
    private val wd = RuleParser(wdRule)

    @Test
    fun `wikidich search and book`() {
        val results = wd.parseSearch(Fixtures.html("wikidich", "search.html"), "https://wikidichvn.com/tim-kiem?s=tien+nghich")
        assertTrue(results.isNotEmpty())
        assertTrue(results.first().url.startsWith("https://wikidichvn.com/"))

        val info = wd.parseBookInfo(Fixtures.html("wikidich", "book.html"), "https://wikidichvn.com/doi-tuong-lien-hon-nay-khong-on-lam")
        assertEquals("Đối Tượng Liên Hôn Này Không Ổn Lắm!", info.title)
        assertNotNull(info.status)
        val toc = wd.parseTocPage(Fixtures.html("wikidich", "book.html"), "https://wikidichvn.com/doi-tuong-lien-hon-nay-khong-on-lam")
        assertTrue(toc.chapters.isNotEmpty())
        assertEquals("94793", wd.tocBookId(Fixtures.html("wikidich", "book.html"), "https://wikidichvn.com/doi-tuong-lien-hon-nay-khong-on-lam"))
    }

    @Test
    fun `wikidich ajax fragment and chapter`() {
        val fragment = wd.tocHtmlFromJson(Fixtures.html("wikidich", "toc_page2.json"))
        val page = wd.parseTocPage(fragment, "https://wikidichvn.com/get/listchap/94785?page=2", fragment = true)
        assertTrue(page.chapters.isNotEmpty())
        assertEquals("https://wikidichvn.com/la-xanh-ban-tiet-bach-thai/chuong-1-hUEi8Qgy0h_a", page.chapters.first().url)

        val url = "https://wikidichvn.com/doi-tuong-lien-hon-nay-khong-on-lam/chuong-1-W1LbpIhnn8Us"
        val chapter = wd.parseChapter(Fixtures.html("wikidich", "chapter.html"), url)
        assertEquals("Chương 1", chapter.title)
        assertNull("first chapter has a disabled prev link", chapter.prevUrl)
        assertEquals("https://wikidichvn.com/doi-tuong-lien-hon-nay-khong-on-lam/chuong-2-S3abp3DSe4O8", chapter.nextUrl)
        assertEquals("https://wikidichvn.com/doi-tuong-lien-hon-nay-khong-on-lam", chapter.bookUrl)
        assertTrue(chapter.paragraphs.size > 5)
    }
}
