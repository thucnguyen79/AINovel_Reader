package vn.esstar.ainovel.data.source

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import vn.esstar.ainovel.data.source.parser.RuleParser
import vn.esstar.ainovel.domain.model.UrlKind

class TvtruyenRuleTest {

    private val rule = Fixtures.rule("tvtruyen")
    private val parser = RuleParser(rule)

    @Test
    fun `classifies urls`() {
        assertEquals(UrlKind.BOOK, parser.classify("https://www.tvtruyen.space/thien-tai-tien-dao.html"))
        assertEquals(UrlKind.BOOK, parser.classify("https://www.tvtruyen.space/thien-tai-tien-dao.html?page=2"))
        assertEquals(UrlKind.CHAPTER, parser.classify("https://www.tvtruyen.space/thien-tai-tien-dao/chuong-2"))
        assertEquals(UrlKind.UNKNOWN, parser.classify("https://www.tvtruyen.space/the-loai/tien-hiep.html"))
    }

    @Test
    fun `parses search results and pagination`() {
        val url = "https://www.tvtruyen.space/tim-kiem?tukhoa=tien"
        val results = parser.parseSearch(Fixtures.html("tvtruyen", "search.html"), url)
        assertTrue(results.size >= 10)
        val first = results.first()
        assertEquals("Thiên Tài Tiên Đạo", first.title)
        assertEquals("https://www.tvtruyen.space/thien-tai-tien-dao.html", first.url)
        assertEquals("Bông Lan", first.author)
        assertEquals("https://cdn-2.cscldsck.com/comics/thumb/thien-tai-tien-dao.webp", first.coverUrl)
        assertEquals("Chương 2476", first.latestChapter)

        val empty = parser.parseSearch(Fixtures.html("tvtruyen", "search_empty.html"), url)
        assertTrue(empty.isEmpty())
    }

    @Test
    fun `parses book info`() {
        val info = parser.parseBookInfo(Fixtures.html("tvtruyen", "book.html"), "https://www.tvtruyen.space/thien-tai-tien-dao.html")
        assertEquals("Thiên Tài Tiên Đạo", info.title)
        assertEquals("Bông Lan", info.author)
        assertEquals("https://cdn-2.cscldsck.com/comics/thumb/thien-tai-tien-dao.webp", info.coverUrl)
        assertEquals("Full", info.status)
        assertTrue(info.genres.contains("Tiên Hiệp"))
        assertTrue(info.description!!.startsWith("Một thiếu niên mang trong mình"))
    }

    @Test
    fun `parses paged toc`() {
        val url = "https://www.tvtruyen.space/thien-tai-tien-dao.html"
        val page1 = parser.parseTocPage(Fixtures.html("tvtruyen", "book.html"), url)
        assertEquals(50, page1.totalPages)
        assertEquals(50, page1.chapters.distinctBy { it.url }.size)
        assertEquals("Chương 1", page1.chapters.first().title)
        assertEquals("https://www.tvtruyen.space/thien-tai-tien-dao/chuong-1", page1.chapters.first().url)

        val page2Url = parser.tocPageUrl(url, 2)
        assertEquals("https://www.tvtruyen.space/thien-tai-tien-dao.html?page=2", page2Url)
        val page2 = parser.parseTocPage(Fixtures.html("tvtruyen", "toc_page2.html"), page2Url!!)
        assertEquals("https://www.tvtruyen.space/thien-tai-tien-dao/chuong-51", page2.chapters.first().url)
    }

    @Test
    fun `parses chapter`() {
        val url = "https://www.tvtruyen.space/thien-tai-tien-dao/chuong-2"
        val chapter = parser.parseChapter(Fixtures.html("tvtruyen", "chapter.html"), url)
        assertEquals("Chương 2", chapter.title)
        assertEquals("https://www.tvtruyen.space/thien-tai-tien-dao.html", chapter.bookUrl)
        assertEquals("https://www.tvtruyen.space/thien-tai-tien-dao/chuong-1", chapter.prevUrl)
        assertEquals("https://www.tvtruyen.space/thien-tai-tien-dao/chuong-3", chapter.nextUrl)
        assertTrue(chapter.paragraphs.size > 10)
        assertTrue(chapter.paragraphs.first().startsWith("- Minh ca"))
        assertNull(chapter.paragraphs.firstOrNull { it.contains("tvtruyen", ignoreCase = true) })
    }
}
