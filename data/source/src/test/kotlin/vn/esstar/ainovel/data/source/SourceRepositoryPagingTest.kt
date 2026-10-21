package vn.esstar.ainovel.data.source

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import vn.esstar.ainovel.core.network.FetchedPage
import vn.esstar.ainovel.core.network.HtmlFetcher
import vn.esstar.ainovel.data.source.model.SourceRule
import vn.esstar.ainovel.data.source.registry.RuleLoader
import vn.esstar.ainovel.data.source.registry.SourceRegistry

/** Repository-level paging: chapters split over several pages, JSON TOC pages, computed page counts, POSTed pages. */
class SourceRepositoryPagingTest {

    /** Serves GET pages by URL and POST pages by `"POST <url> <body>"`; records every request it answered. */
    private class FakeFetcher(private val pages: Map<String, String>) : HtmlFetcher {
        val requested = mutableListOf<String>()
        val posted = mutableListOf<Pair<String, String>>()
        override suspend fun get(url: String, headers: Map<String, String>, charsetHint: String?): FetchedPage {
            requested += url
            val html = pages[url] ?: throw IllegalStateException("Unexpected fetch: $url")
            return FetchedPage(requestUrl = url, finalUrl = url, html = html, statusCode = 200)
        }

        override suspend fun post(url: String, body: String, headers: Map<String, String>, charsetHint: String?): FetchedPage {
            posted += url to body
            val html = pages["POST $url $body"] ?: throw IllegalStateException("Unexpected POST: $url [$body]")
            return FetchedPage(requestUrl = url, finalUrl = url, html = html, statusCode = 200)
        }
    }

    private fun repository(rules: List<SourceRule>, pages: Map<String, String>): Pair<SourceRepositoryImpl, FakeFetcher> {
        val fetcher = FakeFetcher(pages)
        val registry = SourceRegistry(object : RuleLoader {
            override suspend fun loadAll() = rules
        })
        return SourceRepositoryImpl(registry, fetcher, Dispatchers.Unconfined) to fetcher
    }

    @Test
    fun `chapter parts are fetched and appended in order`() = runTest {
        val rule = Fixtures.rule("ranwenapp")
        val base = "https://www.ranwenapp.com/html/0/354/"
        val part = { n: Int, body: String, next: String ->
            """<html><body><h1>第1章 直树-《这次不当训练家了》</h1><article>第($n/3)页<br><br>$body</article>
               <a id="prev" href="/html/0/354/">上一章</a><a href="/html/0/354/">目录</a><a id="next" href="$next">下一章</a></body></html>"""
        }
        val (repo, fetcher) = repository(
            listOf(rule),
            mapOf(
                "${base}1.html" to part(1, "một", "/html/0/354/1_2.html"),
                "${base}1_2.html" to part(2, "hai", "/html/0/354/1_3.html"),
                "${base}1_3.html" to part(3, "ba", "/html/0/354/2.html"),
            ),
        )

        val chapter = repo.fetchChapter("http://www.ranwenapp.com/html/0/354/1.html")

        assertEquals(listOf("một", "hai", "ba"), chapter.paragraphs)
        assertEquals("第1章 直树", chapter.title)
        assertEquals("${base}2.html", chapter.nextUrl)
        assertNull(chapter.prevUrl)
        assertEquals(listOf("${base}1.html", "${base}1_2.html", "${base}1_3.html"), fetcher.requested)
    }

    @Test
    fun `json toc pages are merged after the html first page`() = runTest {
        val rule = Fixtures.rule("vnthuquan")
        val bookUrl = "https://vnthuquan.org/TacPham/test-1/"
        val bookHtml = """<html><body><div class="vntq-author-top"><a href="/x">Tác giả</a></div><h1 class="vntq-reader-title">Sách</h1>
            <select id="vntqTocSelect"><option value="1">1-100</option><option value="2">101-102</option></select>
            <div id="vntqTocList"><a class="vntq-toc-item" href="/TacPham/test-1/chuong-1">Hồi 1</a><a class="vntq-toc-item" href="/TacPham/test-1/chuong-2">Hồi 2</a></div>
            <script>async function fetchTocPage(page) { const tuaId = 77; }</script></body></html>"""
        val apiUrl = "https://vnthuquan.org/api/tacpham/get-mucluc?tuaId=77&page=2&pageSize=100"
        val (repo, _) = repository(
            listOf(rule),
            mapOf(bookUrl to bookHtml, apiUrl to """{"success":true,"chapters":[{"chuongId":3,"title":"Hồi 3"},{"chuongId":4,"title":"Hồi 4"}]}"""),
        )

        val book = repo.fetchBook(bookUrl)

        assertEquals(listOf("Hồi 1", "Hồi 2", "Hồi 3", "Hồi 4"), book.chapters.map { it.title })
        assertEquals("https://vnthuquan.org/TacPham/test-1/chuong-4", book.chapters.last().url)
        assertEquals(listOf(0, 1, 2, 3), book.chapters.map { it.index })
    }

    @Test
    fun `whole toc from a json api uses page count and book id from the book page`() = runTest {
        val rule = Fixtures.rule("bientruyen")
        val bookUrl = "https://www.bientruyen.com/truyen/test"
        val api = "https://www.bientruyen.com/api/story-chapters/abc-1"
        val bookHtml = """<html><body><h1>Test</h1>
            <select aria-label="Chọn khoảng chương"><option value="0">Ch.1–50</option><option value="1">Ch.51–70</option></select>
            <script>self.__next_f.push([1,"{\"storyId\":\"abc-1\",\"x\":1}"])</script></body></html>"""
        val (repo, fetcher) = repository(
            listOf(rule),
            mapOf(
                bookUrl to bookHtml,
                "$api?group=0&perGroup=50&pagination=number" to """{"chapters":[{"chapter_number":1,"title":"Một"},{"chapter_number":2,"title":"Hai"}]}""",
                "$api?group=1&perGroup=50&pagination=number" to """{"chapters":[{"chapter_number":3,"title":"Ba"}]}""",
            ),
        )

        val book = repo.fetchBook(bookUrl)

        // jsonTitle is the template "Chương {chapter_number}", not the raw (Chinese) title field.
        assertEquals(listOf("Chương 1", "Chương 2", "Chương 3"), book.chapters.map { it.title })
        assertEquals("https://www.bientruyen.com/truyen/test/chuong/3", book.chapters.last().url)
        assertEquals(listOf(bookUrl, "$api?group=0&perGroup=50&pagination=number", "$api?group=1&perGroup=50&pagination=number"), fetcher.requested)
    }

    @Test
    fun `toc pages 2 to N are posted with the templated form body`() = runTest {
        val rule = Fixtures.rule("nhatruyen")
        val bookUrl = "https://nhatruyen.site/truyen/test/"
        val ajax = "https://nhatruyen.site/wp-admin/admin-ajax.php"
        val item = { n: Int -> """<div class="chap-item"><a href="/truyen/test/chuong-$n-x/">☀️ Chương $n: X</a></div>""" }
        val bookHtml = """<html><body><h1 class="nt-main-title">Test</h1>
            <div id="ajax-chapter-wrapper"><div class="nt-chapter-grid">${item(1)}${item(2)}</div></div>
            <input id="nt-jump-input" type="number" min="1" max="3">
            <script>formData.append('action', 'nt_load_chapters'); formData.append('parent_id', 14); fetch('$ajax')</script>
            </body></html>"""
        val (repo, fetcher) = repository(
            listOf(rule),
            mapOf(
                bookUrl to bookHtml,
                "POST $ajax action=nt_load_chapters&parent_id=14&page=2" to """<div id="nt-chapter-container">${item(3)}${item(4)}</div>""",
                "POST $ajax action=nt_load_chapters&parent_id=14&page=3" to """<div id="nt-chapter-container">${item(5)}</div>""",
            ),
        )

        val book = repo.fetchBook(bookUrl)

        assertEquals(listOf("Chương 1: X", "Chương 2: X", "Chương 3: X", "Chương 4: X", "Chương 5: X"), book.chapters.map { it.title })
        assertEquals("https://nhatruyen.site/truyen/test/chuong-5-x/", book.chapters.last().url)
        assertEquals(listOf(bookUrl), fetcher.requested)
        assertEquals(
            listOf(ajax to "action=nt_load_chapters&parent_id=14&page=2", ajax to "action=nt_load_chapters&parent_id=14&page=3"),
            fetcher.posted.sortedBy { it.second },
        )
    }

    @Test
    fun `first toc page from toc url is posted and reversed`() = runTest {
        val rule = Fixtures.rule("truyenchu")
        val bookUrl = "https://truyenchu.net/truyen/test/"
        val li = { n: Int -> """<li class="wp-manga-chapter"><a href="https://truyenchu.net/truyen/test/chuong-$n/">Chương $n</a></li>""" }
        val bookHtml = """<html><body><div class="post-title"><h1>Test</h1></div>
            <div id="manga-chapters-holder" data-id="7"><ul class="main version-chap">${li(3)}</ul></div></body></html>"""
        val (repo, fetcher) = repository(
            listOf(rule),
            mapOf(
                bookUrl to bookHtml,
                "POST https://truyenchu.net/truyen/test/ajax/chapters/ " to """<ul class="main version-chap">${li(3)}${li(2)}${li(1)}</ul>""",
            ),
        )

        val book = repo.fetchBook(bookUrl)

        assertEquals(listOf("Chương 1", "Chương 2", "Chương 3"), book.chapters.map { it.title })
        assertEquals(listOf(0, 1, 2), book.chapters.map { it.index })
        assertEquals(listOf(bookUrl), fetcher.requested)
        assertEquals(listOf("https://truyenchu.net/truyen/test/ajax/chapters/" to ""), fetcher.posted)
    }
}
