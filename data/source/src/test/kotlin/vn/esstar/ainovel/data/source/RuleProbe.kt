package vn.esstar.ainovel.data.source

import java.io.File
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import vn.esstar.ainovel.data.source.parser.RuleParser

/**
 * Runs a rule over its fixture directory and flattens everything it extracts into `key=value` pairs.
 * [RuleDumpTest] prints them (to write a new rule by eye) and [SiteFixturesTest] compares them with the
 * recorded `expect.json`, so every rule under `fixtures/` is covered by one data-driven test.
 *
 * `fixtures/_urls.json` maps each rule id to the page URLs of its fixtures:
 * `book` (book.html), `toc` (toc.html or toc.json: the first TOC page when the rule fetches it apart from the
 * book page, e.g. a POSTed Madara `ajax/chapters/` fragment or a JSON API), `toc2` (toc_page2.html or toc_page2.json),
 * `chapter` / `chapter_first` / `chapter_part2` (same-named .html) and `search` (search.html).
 */
object RuleProbe {
    private val json = Json { ignoreUnknownKeys = true }
    private val fixturesDir = File("src/test/resources/fixtures")

    fun urls(): Map<String, Map<String, String>> {
        val file = File(fixturesDir, "_urls.json")
        if (!file.exists()) return emptyMap()
        return json.parseToJsonElement(file.readText()).jsonObject.mapValues { (_, pages) ->
            pages.jsonObject.mapValues { it.value.jsonPrimitive.content }
        }
    }

    fun probe(id: String, pages: Map<String, String>): Map<String, String> {
        val out = linkedMapOf<String, String>()
        val rule = Fixtures.rule(id)
        val parser = RuleParser(rule)
        val dir = File(fixturesDir, id)
        fun fixture(name: String) = File(dir, name).takeIf { it.exists() }?.readText()
        fun put(key: String, value: Any?) { out[key] = value?.toString() ?: "null" }
        fun guard(prefix: String, block: () -> Unit) {
            runCatching(block).onFailure { put("$prefix.error", "${it::class.simpleName}: ${it.message}") }
        }

        for ((kind, url) in pages) {
            put("classify.$kind", parser.classify(url))
            put("canonical.$kind", parser.canonicalUrl(url))
        }

        val bookHtml = fixture("book.html")
        val bookUrl = pages["book"]
        if (bookHtml != null && bookUrl != null) {
            guard("book") {
                val info = parser.parseBookInfo(bookHtml, bookUrl)
                put("book.title", info.title)
                put("book.author", info.author)
                put("book.cover", info.coverUrl)
                put("book.status", info.status)
                put("book.genres", info.genres)
                put("book.desc", info.description?.take(120)?.replace('\n', '|'))
                put("book.tocUrl", info.tocUrl)
            }
            guard("toc") {
                val tocHtml = fixture("toc.html")
                val tocJson = fixture("toc.json")
                val tocUrl = pages["toc"]
                val bookId = parser.tocBookId(bookHtml, bookUrl)
                val meta = parser.parseTocPage(bookHtml, bookUrl)
                val url = tocUrl ?: bookUrl
                val toc = when {
                    tocJson != null && tocUrl != null -> parser.parseTocJson(tocJson, bookUrl).copy(totalPages = meta.totalPages)
                    tocHtml != null && tocUrl != null -> parser.parseTocPage(tocHtml, tocUrl)
                    else -> meta
                }
                put("toc.url", parser.tocUrl(bookUrl, bookId))
                put("toc.count", toc.chapters.size)
                put("toc.totalPages", toc.totalPages)
                put("toc.next", toc.nextUrl)
                put("toc.first", toc.chapters.firstOrNull()?.let { "${it.title} | ${it.url}" })
                put("toc.last", toc.chapters.lastOrNull()?.let { "${it.title} | ${it.url}" })
                put("toc.bookId", bookId)
                put("toc.page2Url", parser.tocPageUrl(url, 2, bookId))
                put("toc.page2Body", parser.tocPageBody(url, 2, bookId))
            }
        }
        val page2Html = fixture("toc_page2.html")
        val page2Json = fixture("toc_page2.json")
        val page2Url = pages["toc2"]
        if (page2Url != null && bookUrl != null && (page2Html != null || page2Json != null)) {
            guard("toc2") {
                val toc = when {
                    rule.toc.jsonItems != null -> parser.parseTocJson(page2Json!!, bookUrl)
                    page2Json != null -> parser.parseTocPage(parser.tocHtmlFromJson(page2Json), page2Url, fragment = true)
                    else -> parser.parseTocPage(page2Html!!, page2Url, fragment = rule.toc.pageList != null)
                }
                put("toc2.count", toc.chapters.size)
                put("toc2.first", toc.chapters.firstOrNull()?.let { "${it.title} | ${it.url}" })
                put("toc2.last", toc.chapters.lastOrNull()?.let { "${it.title} | ${it.url}" })
            }
        }
        for (name in listOf("chapter", "chapter_first", "chapter_part2")) {
            val html = fixture("$name.html") ?: continue
            val url = pages[name] ?: continue
            guard(name) {
                val ch = parser.parseChapter(html, url)
                put("$name.title", ch.title)
                put("$name.paragraphs", ch.paragraphs.size)
                put("$name.first", ch.paragraphs.first().take(90))
                put("$name.second", ch.paragraphs.getOrNull(1)?.take(90))
                put("$name.last", ch.paragraphs.last().take(90))
                put("$name.prev", ch.prevUrl)
                put("$name.next", ch.nextUrl)
                put("$name.bookUrl", ch.bookUrl)
                put("$name.nextPage", parser.chapterNextPage(html, url))
            }
        }
        val searchHtml = fixture("search.html")
        val searchUrl = pages["search"]
        if (searchHtml != null && searchUrl != null) {
            guard("search") {
                val results = parser.parseSearch(searchHtml, searchUrl)
                put("search.count", results.size)
                put("search.url", parser.searchUrl("ba chu"))
                results.take(2).forEachIndexed { i, r ->
                    put("search.item$i", "${r.title} | ${r.url} | ${r.author} | ${r.coverUrl} | ${r.latestChapter}")
                }
            }
        }
        return out
    }
}
