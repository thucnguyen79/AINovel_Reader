package vn.esstar.ainovel.data.ebook.parser

import java.io.InputStream
import java.util.zip.ZipInputStream
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import org.jsoup.parser.Parser

/**
 * EPUB 2/3 reader built on java.util.zip + Jsoup (no extra dependency): container.xml → OPF →
 * spine order → one chapter per XHTML document (merged when a document is only a heading).
 * Titles come from the NCX/nav TOC when available, else from the document's first heading.
 */
class EpubParser : EbookParser {
    override val extensions = setOf("epub")

    override fun parse(input: InputStream, fileName: String): ParsedEbook {
        val entries = HashMap<String, ByteArray>()
        ZipInputStream(input).use { zip ->
            while (true) {
                val e = zip.nextEntry ?: break
                if (!e.isDirectory) entries[e.name.removePrefix("/")] = zip.readBytes()
                zip.closeEntry()
            }
        }
        val container = entries["META-INF/container.xml"]?.let { xml(it) }
            ?: throw EbookParseException("Not an EPUB: META-INF/container.xml missing")
        val opfPath = container.selectFirst("rootfile")?.attr("full-path")?.removePrefix("/")
            ?: throw EbookParseException("EPUB has no rootfile")
        val opf = entries[opfPath]?.let { xml(it) } ?: throw EbookParseException("EPUB OPF missing: $opfPath")
        val base = opfPath.substringBeforeLast('/', "").let { if (it.isEmpty()) "" else "$it/" }

        val title = opf.selectFirst("metadata > title, metadata > dc|title, title")?.text()?.trim()?.ifEmpty { null }
            ?: EbookParsers.titleFromFileName(fileName)
        val author = opf.select("metadata > creator, metadata > dc|creator, creator").firstOrNull()?.text()?.trim()?.ifEmpty { null }
        val description = opf.selectFirst("metadata > description, metadata > dc|description, description")?.text()?.trim()?.ifEmpty { null }
        val language = opf.selectFirst("metadata > language, metadata > dc|language, language")?.text()?.trim()?.lowercase()
            ?.let { if (it.startsWith("zh")) "zh" else "vi" } ?: "vi"

        val manifest = opf.select("manifest > item").associate { it.attr("id") to it }
        val spineIds = opf.select("spine > itemref").map { it.attr("idref") }.filter { it in manifest }

        val tocTitles = readTocTitles(opf, manifest, entries, base)
        val chapters = ArrayList<ParsedChapter>()
        var pendingTitle: String? = null
        for (id in spineIds) {
            val item = manifest[id] ?: continue
            val href = resolve(base, item.attr("href"))
            val bytes = entries[href] ?: continue
            val doc = Jsoup.parse(String(bytes, Charsets.UTF_8), "")
            val body = doc.body() ?: continue
            body.select("script, style, nav, header > nav").remove()
            val heading = body.selectFirst("h1, h2, h3")?.text()?.trim()?.ifEmpty { null }
            val tocTitle = tocTitles[href] ?: tocTitles[href.substringBefore('#')]
            val chapterTitle = tocTitle ?: heading ?: pendingTitle ?: "Phần ${chapters.size + 1}"
            val paragraphs = paragraphsOf(body, heading)
            if (paragraphs.isEmpty()) {
                // Cover / title pages: keep the heading for the next document with text.
                if (heading != null) pendingTitle = heading
                continue
            }
            chapters += ParsedChapter(chapterTitle, paragraphs)
            pendingTitle = null
        }
        if (chapters.isEmpty()) throw EbookParseException("EPUB has no readable chapters")

        val cover = findCover(opf, manifest, entries, base)
        val text = chapters.asSequence().flatMap { it.paragraphs.asSequence() }.take(200).joinToString("\n")
        val lang = if (language == "zh" || TextChapterSplitter.looksChinese(text)) "zh" else "vi"
        return ParsedEbook(title, author, description, lang, chapters, cover)
    }

    private fun paragraphsOf(body: Element, heading: String?): List<String> {
        val out = ArrayList<String>()
        val blocks = body.select("p, div, li, blockquote, h1, h2, h3, h4, h5, h6, pre, td")
        val leaves = blocks.filter { b -> b.select("p, div, li, blockquote, h1, h2, h3, h4, h5, h6, pre, td").none { it !== b } }
        val source = if (leaves.isEmpty()) listOf(body) else leaves
        for (el in source) {
            el.select("br").forEach { it.after("\n") }
            val text = el.wholeText().replace(' ', ' ')
            for (line in text.split('\n')) {
                val t = line.trim()
                if (t.isNotEmpty() && t != heading) out += t
            }
        }
        if (out.isEmpty()) {
            val plain = body.wholeText().split('\n').map { it.trim() }.filter { it.isNotEmpty() && it != heading }
            out += plain
        }
        return out
    }

    private fun readTocTitles(opf: Document, manifest: Map<String, Element>, entries: Map<String, ByteArray>, base: String): Map<String, String> {
        val titles = LinkedHashMap<String, String>()
        // EPUB 3 nav
        manifest.values.firstOrNull { it.attr("properties").split(' ').contains("nav") }?.let { nav ->
            val href = resolve(base, nav.attr("href"))
            entries[href]?.let { bytes ->
                val doc = Jsoup.parse(String(bytes, Charsets.UTF_8), "")
                val navBase = href.substringBeforeLast('/', "").let { if (it.isEmpty()) "" else "$it/" }
                doc.select("nav a[href]").forEach { a ->
                    val target = resolve(navBase, a.attr("href")).substringBefore('#')
                    titles.putIfAbsent(target, a.text().trim())
                }
            }
        }
        // EPUB 2 NCX
        val ncxId = opf.selectFirst("spine")?.attr("toc")
        val ncx = manifest[ncxId] ?: manifest.values.firstOrNull { it.attr("media-type") == "application/x-dtbncx+xml" }
        ncx?.let { item ->
            val href = resolve(base, item.attr("href"))
            entries[href]?.let { bytes ->
                val doc = xml(bytes)
                val ncxBase = href.substringBeforeLast('/', "").let { if (it.isEmpty()) "" else "$it/" }
                doc.select("navPoint").forEach { np ->
                    val label = np.selectFirst("navLabel > text")?.text()?.trim() ?: return@forEach
                    val src = np.selectFirst("content")?.attr("src") ?: return@forEach
                    titles.putIfAbsent(resolve(ncxBase, src).substringBefore('#'), label)
                }
            }
        }
        return titles
    }

    private fun findCover(opf: Document, manifest: Map<String, Element>, entries: Map<String, ByteArray>, base: String): ByteArray? {
        val byProperty = manifest.values.firstOrNull { it.attr("properties").split(' ').contains("cover-image") }
        val byMeta = opf.selectFirst("metadata > meta[name=cover]")?.attr("content")?.let { manifest[it] }
        val byName = manifest.values.firstOrNull { it.attr("media-type").startsWith("image/") && it.attr("id").contains("cover", ignoreCase = true) }
        val item = byProperty ?: byMeta ?: byName ?: return null
        return entries[resolve(base, item.attr("href"))]
    }

    private fun xml(bytes: ByteArray): Document = Jsoup.parse(String(bytes, Charsets.UTF_8), "", Parser.xmlParser())

    private fun resolve(base: String, href: String): String {
        val decoded = runCatching { java.net.URLDecoder.decode(href.replace("+", "%2B"), "UTF-8") }.getOrDefault(href)
        val parts = ArrayList(base.split('/').filter { it.isNotEmpty() })
        for (seg in decoded.split('/')) {
            when (seg) {
                "", "." -> Unit
                ".." -> if (parts.isNotEmpty()) parts.removeAt(parts.size - 1)
                else -> parts += seg
            }
        }
        return parts.joinToString("/")
    }
}
