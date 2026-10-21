package vn.esstar.ainovel.data.source

import java.nio.charset.Charset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import vn.esstar.ainovel.data.source.parser.RuleParser
import vn.esstar.ainovel.domain.model.UrlKind

/** 52shuku.net (UTF-8) and jjwxc.net (GB18030): the first two Chinese sources. */
class ChineseSitesRuleTest {

    private val shuku = RuleParser(Fixtures.rule("52shuku"))
    private val jj = RuleParser(Fixtures.rule("jjwxc"))

    private fun gbk(source: String, name: String): String {
        val stream = checkNotNull(javaClass.getResourceAsStream("/fixtures/$source/$name"))
        return stream.readBytes().toString(Charset.forName("GB18030"))
    }

    @Test
    fun `52shuku classifies urls`() {
        assertEquals(UrlKind.BOOK, shuku.classify("https://www.52shuku.net/chongsheng/21_b/bkf7q.html"))
        assertEquals(UrlKind.CHAPTER, shuku.classify("https://www.52shuku.net/chongsheng/21_b/bkf7q_2.html"))
        assertEquals(UrlKind.UNKNOWN, shuku.classify("https://www.52shuku.net/chongsheng/"))
    }

    @Test
    fun `52shuku search, book, toc and chapter`() {
        val results = shuku.parseSearch(Fixtures.html("52shuku", "search.html"), "https://www.52shuku.net/so/search.php?q=x")
        assertTrue(results.size >= 5)
        assertEquals("重生集合营_穆烟【完结】", results.first().title)
        assertEquals("https://www.52shuku.net/yanqing/hzqM.html", results.first().url)

        val url = "https://www.52shuku.net/chongsheng/21_b/bkf7q.html"
        val info = shuku.parseBookInfo(Fixtures.html("52shuku", "book.html"), url)
        assertEquals("痴迷变态屠夫后反被盯上_Q皮糖【完结+番外】", info.title)
        assertEquals("Q皮糖", info.author)
        assertTrue(info.description!!.contains("痴迷噶人魔"))

        val toc = shuku.parseTocPage(Fixtures.html("52shuku", "book.html"), url)
        assertTrue(toc.chapters.size >= 10)
        assertEquals("第1页", toc.chapters.first().title)
        assertEquals("https://www.52shuku.net/chongsheng/21_b/bkf7q_2.html", toc.chapters.first().url)

        val chapter = shuku.parseChapter(Fixtures.html("52shuku", "chapter.html"), "https://www.52shuku.net/chongsheng/21_b/bkf7q_2.html")
        assertEquals("痴迷变态屠夫后反被盯上_Q皮糖【完结+番外】(2)", chapter.title)
        assertTrue(chapter.paragraphs.size > 5)
        assertTrue(chapter.paragraphs.none { it.contains("52书库") })
    }

    @Test
    fun `jjwxc classifies urls and encodes search query in gbk`() {
        assertEquals(UrlKind.BOOK, jj.classify("https://www.jjwxc.net/onebook.php?novelid=5000"))
        assertEquals(UrlKind.CHAPTER, jj.classify("https://www.jjwxc.net/onebook.php?novelid=5000&chapterid=1"))
        assertEquals("https://www.jjwxc.net/search.php?kw=%D6%D8%C9%FA&t=1", jj.searchUrl("重生"))
    }

    @Test
    fun `jjwxc search results`() {
        val results = jj.parseSearch(gbk("jjwxc", "search.html"), "https://www.jjwxc.net/search.php?kw=x&t=1")
        assertTrue("got ${results.size}", results.size >= 20)
        assertTrue(results.first().url.contains("onebook.php?novelid="))
        assertTrue(results.first().title.contains("重生"))
    }

    @Test
    fun `jjwxc book and toc`() {
        val url = "https://www.jjwxc.net/onebook.php?novelid=5000"
        val info = jj.parseBookInfo(gbk("jjwxc", "book.html"), url)
        assertEquals("西风碧树（完结）", info.title)
        assertEquals("碧树", info.author)
        assertEquals("完结", info.status)
        assertTrue(info.coverUrl!!.contains("novelimage.php?novelid=5000"))
        assertTrue(info.description!!.contains("即使屈辱"))

        val toc = jj.parseTocPage(gbk("jjwxc", "book.html"), url)
        assertEquals(14, toc.chapters.size) // locked chapters have no link and are skipped
        assertEquals("第 1 章", toc.chapters.first().title)
        assertEquals("http://www.jjwxc.net/onebook.php?novelid=5000&chapterid=1", toc.chapters.first().url)
    }

    @Test
    fun `jjwxc chapter content without site chrome`() {
        val url = "https://www.jjwxc.net/onebook.php?novelid=5000&chapterid=1"
        val chapter = jj.parseChapter(gbk("jjwxc", "chapter.html"), url)
        assertEquals("第 1 章", chapter.title)
        assertEquals("http://www.jjwxc.net/onebook.php?novelid=5000", chapter.bookUrl)
        assertEquals("https://www.jjwxc.net/onebook.php?novelid=5000&chapterid=2", chapter.nextUrl)
        assertTrue(chapter.paragraphs.size > 10)
        assertTrue(chapter.paragraphs.any { it.contains("殿下，陛下赐的御酒到了") })
        assertTrue(chapter.paragraphs.none { it.contains("添加书签") || it.contains("投诉") })
    }
}
