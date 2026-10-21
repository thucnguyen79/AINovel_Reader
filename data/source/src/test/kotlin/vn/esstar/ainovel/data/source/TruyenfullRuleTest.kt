package vn.esstar.ainovel.data.source

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import vn.esstar.ainovel.data.source.parser.RuleParser
import vn.esstar.ainovel.domain.model.UrlKind

class TruyenfullRuleTest {

    private val rule = Fixtures.rule("truyenfull")
    private val parser = RuleParser(rule)

    @Test
    fun `classifies book and chapter urls`() {
        assertEquals(UrlKind.BOOK, parser.classify("https://truyenfull.live/tien-nghich-chi-hau/"))
        assertEquals(UrlKind.BOOK, parser.classify("https://truyenfull.live/tien-nghich-chi-hau"))
        assertEquals(UrlKind.CHAPTER, parser.classify("https://truyenfull.live/tien-nghich-chi-hau/chuong-2/"))
        assertEquals(UrlKind.CHAPTER, parser.classify("https://truyenfull.live/x/quyen-1-chuong-3/"))
        assertEquals(UrlKind.UNKNOWN, parser.classify("https://truyenfull.live/the-loai/tien-hiep/trang-2/"))
    }

    @Test
    fun `search url encodes query`() {
        assertEquals("https://truyenfull.live/tim-kiem/?tukhoa=ti%C3%AAn+ngh%E1%BB%8Bch", parser.searchUrl("tiên nghịch"))
    }

    @Test
    fun `parses search results`() {
        val url = "https://truyenfull.live/tim-kiem/?tukhoa=tien+nghich"
        val results = parser.parseSearch(Fixtures.html("truyenfull", "search.html"), url)

        assertTrue("expected results, got ${results.size}", results.size >= 2)
        val first = results.first()
        assertEquals("Tiên Nghịch Chi Hậu", first.title)
        assertEquals("https://truyenfull.live/tien-nghich-chi-hau/", first.url)
        assertEquals("Trần Mộc", first.author)
        assertTrue(first.coverUrl!!.startsWith("https://static.truyenfull.live/cover/"))
        assertEquals("Chương 18", first.latestChapter)
        assertEquals("truyenfull", first.sourceId.value)
    }

    @Test
    fun `search page without pagination has no next, paged one has`() {
        val single = parser.parse(Fixtures.html("truyenfull", "search.html"), rule.baseUrl)
        val paged = parser.parse(Fixtures.html("truyenfull", "search_paged.html"), rule.baseUrl)
        val next = vn.esstar.ainovel.data.source.selector.Selector.parse(rule.search!!.next!!)
        assertNull(next.first(single))
        assertEquals("https://truyenfull.live/tim-kiem/?tukhoa=tien&page=2", next.first(paged))
    }

    @Test
    fun `parses book info`() {
        val url = "https://truyenfull.live/tien-nghich-chi-hau/"
        val info = parser.parseBookInfo(Fixtures.html("truyenfull", "book.html"), url)

        assertEquals("Tiên Nghịch Chi Hậu", info.title)
        assertEquals("Trần Mộc", info.author)
        assertTrue(info.coverUrl!!.endsWith("/tien-nghich-chi-hau.jpg"))
        assertTrue(info.description!!.startsWith("Nếu ai đã xem truyện Tiên Nghịch"))
        assertFalse("hidden SEO spans must be stripped", info.description.contains("truyenfullvn"))
        assertEquals(listOf("Tiên Hiệp", "Huyền Huyễn", "Xuyên Không", "Cổ Đại"), info.genres)
        assertNotNull(info.status)
    }

    @Test
    fun `parses single page toc`() {
        val url = "https://truyenfull.live/tien-nghich-chi-hau/"
        val toc = parser.parseTocPage(Fixtures.html("truyenfull", "book.html"), url)

        assertEquals(1, toc.totalPages)
        assertEquals(18, toc.chapters.size)
        assertEquals(0, toc.chapters.first().index)
        assertEquals("Chương 1: Tiên Cương tái hiện", toc.chapters.first().title)
        assertEquals("https://truyenfull.live/tien-nghich-chi-hau/chuong-1/", toc.chapters.first().url)
    }

    @Test
    fun `parses paged toc and builds page urls`() {
        val url = "https://truyenfull.live/truyen-dau-pha-thuong-khung/"
        val page1 = parser.parseTocPage(Fixtures.html("truyenfull", "book_paged.html"), url)
        assertEquals(33, page1.totalPages)
        assertEquals(50, page1.chapters.size)

        val page2Url = parser.tocPageUrl(url, 2)
        assertEquals("https://truyenfull.live/truyen-dau-pha-thuong-khung/trang-2/", page2Url)

        val page2 = parser.parseTocPage(Fixtures.html("truyenfull", "toc_page2.html"), page2Url!!, startIndex = 50)
        assertEquals(50, page2.chapters.size)
        assertEquals(50, page2.chapters.first().index)
        assertEquals("https://truyenfull.live/truyen-dau-pha-thuong-khung/chuong-51/", page2.chapters.first().url)
    }

    @Test
    fun `parses chapter content and navigation`() {
        val url = "https://truyenfull.live/tien-nghich-chi-hau/chuong-2/"
        val chapter = parser.parseChapter(Fixtures.html("truyenfull", "chapter.html"), url)

        assertEquals("Chương 2: Nghịch Trần Định Giới", chapter.title)
        assertEquals("https://truyenfull.live/tien-nghich-chi-hau/", chapter.bookUrl)
        assertEquals("https://truyenfull.live/tien-nghich-chi-hau/chuong-1/", chapter.prevUrl)
        assertEquals("https://truyenfull.live/tien-nghich-chi-hau/chuong-3/", chapter.nextUrl)
        assertTrue("expected many paragraphs, got ${chapter.paragraphs.size}", chapter.paragraphs.size > 10)
        assertTrue(chapter.paragraphs.first().startsWith("Trần Hiên ăn xong bữa cơm trưa"))
        assertTrue(chapter.paragraphs.none { it.contains("truyenfull", ignoreCase = true) })
        assertTrue(chapter.paragraphs.none { it.contains("quảng cáo", ignoreCase = true) })
    }

    @Test
    fun `every bundled rule deserializes`() {
        val rules = Fixtures.allRules()
        assertTrue(rules.isNotEmpty())
        rules.forEach { assertTrue(it.hosts.isNotEmpty()) }
    }
}
