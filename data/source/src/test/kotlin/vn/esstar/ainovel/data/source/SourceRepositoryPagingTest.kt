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

/** Repository-level paging: chapters split over several pages, JSON TOC pages, computed page counts. */
class SourceRepositoryPagingTest {

    private class FakeFetcher(private val pages: Map<String, String>) : HtmlFetcher {
        val requested = mutableListOf<String>()
        override suspend fun get(url: String, headers: Map<String, String>, charsetHint: String?): FetchedPage {
            requested += url
            val html = pages[url] ?: throw IllegalStateException("Unexpected fetch: $url")
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
}
