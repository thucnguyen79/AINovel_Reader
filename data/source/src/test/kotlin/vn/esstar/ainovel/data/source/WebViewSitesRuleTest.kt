package vn.esstar.ainovel.data.source

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import vn.esstar.ainovel.data.source.parser.RuleParser
import vn.esstar.ainovel.domain.model.UrlKind

/**
 * zongheng.com, ixdzs8.com and 17k.com need the hidden WebView for some pages; the fixtures are the
 * rendered DOM captured from a real browser, so the parsers run on exactly what the WebView returns.
 */
class WebViewSitesRuleTest {

    private val zh = RuleParser(Fixtures.rule("zongheng"))
    private val ix = RuleParser(Fixtures.rule("ixdzs8"))
    private val k17 = RuleParser(Fixtures.rule("17k"))

    @Test
    fun `zongheng classifies urls and builds the catalogue url`() {
        assertEquals(UrlKind.BOOK, zh.classify("https://www.zongheng.com/detail/1552353"))
        assertEquals(UrlKind.BOOK, zh.classify("https://www.zongheng.com/detail/1552353?tabsName=catalogue"))
        assertEquals(UrlKind.CHAPTER, zh.classify("https://read.zongheng.com/chapter/1552353/94413899.html"))
        assertEquals(UrlKind.UNKNOWN, zh.classify("https://www.zongheng.com/categories?cateFineId=8102"))
        assertEquals("https://www.zongheng.com/detail/1552353?tabsName=catalogue", zh.tocUrl("https://www.zongheng.com/detail/1552353/"))
        assertEquals("https://www.zongheng.com/detail/1552353?tabsName=catalogue", zh.tocUrl("https://www.zongheng.com/detail/1552353?tabsName=catalogue"))
    }

    @Test
    fun `zongheng book, toc and chapter`() {
        val url = "https://www.zongheng.com/detail/1552353?tabsName=catalogue"
        val info = zh.parseBookInfo(Fixtures.html("zongheng", "book.html"), url)
        assertEquals("盗梦千年", info.title)
        assertEquals("咸鱼老白", info.author)
        assertEquals("https://static.zongheng.com/upload/cover/26/fb/26fb5f6a7a55f38b8ac4074287f04f4f.jpeg", info.coverUrl)
        assertEquals("连载中", info.status)
        assertEquals(listOf("武侠仙侠"), info.genres)

        val toc = zh.parseTocPage(Fixtures.html("zongheng", "book.html"), url)
        assertEquals(16, toc.chapters.size)
        assertEquals("第1章 仙门", toc.chapters.first().title)
        assertEquals("https://read.zongheng.com/chapter/1552353/94413899.html", toc.chapters.first().url)
        assertEquals("第172章 猜想", toc.chapters.last().title)

        val chapter = zh.parseChapter(Fixtures.html("zongheng", "chapter.html"), "https://read.zongheng.com/chapter/1552353/94413899.html")
        assertEquals("第1章 仙门", chapter.title)
        assertTrue(chapter.paragraphs.size > 10)
        assertEquals("“你这阴魂不散的色胚，居然又来了！”", chapter.paragraphs.first())
        assertEquals("https://read.zongheng.com/chapter/1552353/94413906.html?", chapter.nextUrl)
        assertNull(chapter.prevUrl)
    }

    @Test
    fun `ixdzs8 classifies urls`() {
        assertEquals(UrlKind.BOOK, ix.classify("https://ixdzs8.com/read/620438/"))
        assertEquals(UrlKind.BOOK, ix.classify("https://ixdzs8.com/read/620438"))
        assertEquals(UrlKind.CHAPTER, ix.classify("https://ixdzs8.com/read/620438/p1.html"))
        assertEquals(UrlKind.UNKNOWN, ix.classify("https://ixdzs8.com/sort/7/"))
    }

    @Test
    fun `ixdzs8 book, toc and chapter`() {
        val url = "https://ixdzs8.com/read/620438/"
        val info = ix.parseBookInfo(Fixtures.html("ixdzs8", "book.html"), url)
        assertEquals("丑雌一胎七崽？兽夫们跪求复合", info.title)
        assertEquals("金顶顶", info.author)
        assertEquals("连载中", info.status)
        assertTrue(info.description!!.contains("沈月穿成了"))
        assertEquals(listOf("言情穿越"), info.genres)

        val toc = ix.parseTocPage(Fixtures.html("ixdzs8", "book.html"), url)
        assertEquals(448, toc.chapters.size)
        assertEquals("第1章 穿进兽世即将分娩，却掉进雄性洞中", toc.chapters.first().title)
        assertEquals("https://ixdzs8.com/read/620438/p1.html", toc.chapters.first().url)

        val chapter = ix.parseChapter(Fixtures.html("ixdzs8", "chapter.html"), "https://ixdzs8.com/read/620438/p1.html")
        assertEquals("第1章 穿进兽世即将分娩，却掉进雄性洞中", chapter.title)
        assertEquals("沈月刚穿进兽世大陆，就被跟她抢食物的人一拐肘撞晕了过去。", chapter.paragraphs.first())
        assertEquals("https://ixdzs8.com/read/620438/p2.html", chapter.nextUrl)
        assertEquals("https://ixdzs8.com/read/620438/", chapter.bookUrl)
    }

    @Test
    fun `17k classifies urls and rewrites desktop links to the mobile site`() {
        assertEquals(UrlKind.BOOK, k17.classify("https://h5.17k.com/book/3631088.html"))
        assertEquals(UrlKind.BOOK, k17.classify("https://www.17k.com/list/3631088.html"))
        assertEquals(UrlKind.CHAPTER, k17.classify("https://www.17k.com/chapter/3631088/49406153.html"))
        assertEquals(UrlKind.UNKNOWN, k17.classify("https://www.17k.com/all/book/2_24_9______1.html"))
        assertEquals("https://h5.17k.com/list/3631088.html", k17.canonicalUrl("https://www.17k.com/list/3631088.html?alichl=abc#top"))
        assertEquals("https://h5.17k.com/list/3631088.html", k17.canonicalUrl("https://h5.17k.com/list/3631088.html?alichlgref=x"))
        assertEquals("https://www.zongheng.com/detail/1552353", zh.canonicalUrl("https://m.zongheng.com/book/1552353?bookId=1552353"))
    }

    @Test
    fun `17k book page, list page and chapter`() {
        val bookUrl = "https://h5.17k.com/book/3631088.html"
        val info = k17.parseBookInfo(Fixtures.html("17k", "book.html"), bookUrl)
        assertEquals("师弟，你的丹药太不正经了", info.title)
        assertEquals("迷你奥利奥", info.author)
        assertEquals("https://cdn.static.17k.com/book/189x272/88/10/3631088.jpg-189x272?v=1750129584000", info.coverUrl)
        assertTrue(info.description!!.contains("李玄穿越仙侠世界"))
        assertEquals("https://h5.17k.com/list/3631088.html", info.tocUrl)

        val listUrl = "https://h5.17k.com/list/3631088.html"
        val listInfo = k17.parseBookInfo(Fixtures.html("17k", "list.html"), listUrl)
        assertEquals("师弟，你的丹药太不正经了", listInfo.title)
        assertEquals("迷你奥利奥", listInfo.author)

        val toc = k17.parseTocPage(Fixtures.html("17k", "list.html"), listUrl)
        assertEquals(10, toc.chapters.size)
        assertEquals("第1章 说好的完美开局，网瘾大师姐什么鬼？", toc.chapters.first().title)
        assertEquals("https://h5.17k.com/chapter/3631088/49406153.html", toc.chapters.first().url)

        val chapter = k17.parseChapter(Fixtures.html("17k", "chapter.html"), "https://h5.17k.com/chapter/3631088/49406153.html")
        assertEquals("第1章 说好的完美开局，网瘾大师姐什么鬼？", chapter.title)
        assertEquals("青云宗，外事堂。", chapter.paragraphs.first())
        assertTrue(chapter.paragraphs.none { it.contains("第1章") })
        assertEquals("https://h5.17k.com/book/3631088.html", chapter.bookUrl)
    }
}
