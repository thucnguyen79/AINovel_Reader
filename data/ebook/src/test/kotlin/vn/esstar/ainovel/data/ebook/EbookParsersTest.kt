package vn.esstar.ainovel.data.ebook

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.nio.charset.Charset
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import vn.esstar.ainovel.data.ebook.parser.EbookParsers
import vn.esstar.ainovel.data.ebook.parser.EpubParser
import vn.esstar.ainovel.data.ebook.parser.TextChapterSplitter
import vn.esstar.ainovel.data.ebook.parser.TxtParser
import vn.esstar.ainovel.data.ebook.parser.ZipTxtParser

class EbookParsersTest {

    private val txt = TxtParser()
    private val zip = ZipTxtParser()
    private val epub = EpubParser()

    @Test
    fun `txt splits by Vietnamese and Chinese headings`() {
        val vi = """
            Chương 1: Khởi đầu
            Dòng một.
            Dòng hai.

            Chương 2 - Gặp gỡ
            Nội dung chương hai.
            CHƯƠNG 3
            Ba.
        """.trimIndent()
        val book = txt.parse(ByteArrayInputStream(vi.toByteArray()), "Tru_Tien.txt")
        assertEquals("Tru Tien", book.title)
        assertEquals("vi", book.language)
        assertEquals(listOf("Chương 1: Khởi đầu", "Chương 2 - Gặp gỡ", "CHƯƠNG 3"), book.chapters.map { it.title })
        assertEquals(listOf("Dòng một.", "Dòng hai."), book.chapters[0].paragraphs)

        val zh = "第一章 仙门\n林风眠在玉仙宗。\n第二章 考核\n他被妖女欺负。\n第３章 偶遇\n再来。"
        val zhBook = txt.parse(ByteArrayInputStream(zh.toByteArray()), "dmqn.txt")
        assertEquals("zh", zhBook.language)
        assertEquals(listOf("第一章 仙门", "第二章 考核", "第３章 偶遇"), zhBook.chapters.map { it.title })
    }

    @Test
    fun `txt without headings is cut into parts and GB18030 is decoded`() {
        val lines = (1..300).joinToString("\n") { "Đoạn số $it." }
        val book = txt.parse(ByteArrayInputStream(lines.toByteArray()), "notes.txt")
        assertEquals(3, book.chapters.size)
        assertEquals("Phần 1", book.chapters[0].title)
        assertEquals(120, book.chapters[0].paragraphs.size)
        assertEquals(60, book.chapters[2].paragraphs.size)

        val gbk = "第一章 开始\n这是中文。\n第二章 继续\n还是中文。".toByteArray(Charset.forName("GB18030"))
        val decoded = TextChapterSplitter.decode(gbk)
        assertTrue(decoded.startsWith("第一章 开始"))
        val bom = byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte()) + "Chương 1\nA".toByteArray()
        assertEquals("Chương 1\nA", TextChapterSplitter.decode(bom))
    }

    @Test
    fun `zip of txt files is ordered naturally`() {
        val bytes = ByteArrayOutputStream().also { out ->
            ZipOutputStream(out).use { z ->
                fun add(name: String, text: String) { z.putNextEntry(ZipEntry(name)); z.write(text.toByteArray()); z.closeEntry() }
                add("truyen/chuong-10.txt", "Mười.")
                add("truyen/chuong-2.txt", "Hai.")
                add("truyen/chuong-1.txt", "Một.")
                add("__MACOSX/._chuong-1.txt", "junk")
                add("truyen/cover.jpg", "notatext")
            }
        }.toByteArray()
        val book = zip.parse(ByteArrayInputStream(bytes), "Truyen.zip")
        assertEquals("Truyen", book.title)
        assertEquals(listOf("chuong-1", "chuong-2", "chuong-10"), book.chapters.map { it.title })
        assertEquals(listOf("Một."), book.chapters[0].paragraphs)
    }

    @Test
    fun `epub reads opf metadata, spine order, ncx titles and cover`() {
        val bytes = ByteArrayOutputStream().also { out ->
            ZipOutputStream(out).use { z ->
                fun add(name: String, text: String) { z.putNextEntry(ZipEntry(name)); z.write(text.toByteArray()); z.closeEntry() }
                add("mimetype", "application/epub+zip")
                add("META-INF/container.xml", """<?xml version="1.0"?><container version="1.0" xmlns="urn:oasis:names:tc:opendocument:xmlns:container"><rootfiles><rootfile full-path="OEBPS/content.opf" media-type="application/oebps-package+xml"/></rootfiles></container>""")
                add("OEBPS/content.opf", """<?xml version="1.0"?>
                    <package xmlns="http://www.idpf.org/2007/opf" xmlns:dc="http://purl.org/dc/elements/1.1/" version="2.0">
                      <metadata><dc:title>Đấu Phá</dc:title><dc:creator>Thiên Tằm</dc:creator><dc:language>vi</dc:language><meta name="cover" content="cov"/></metadata>
                      <manifest>
                        <item id="ncx" href="toc.ncx" media-type="application/x-dtbncx+xml"/>
                        <item id="cov" href="images/cover.jpg" media-type="image/jpeg"/>
                        <item id="t" href="text/title.xhtml" media-type="application/xhtml+xml"/>
                        <item id="c1" href="text/c1.xhtml" media-type="application/xhtml+xml"/>
                        <item id="c2" href="text/c2.xhtml" media-type="application/xhtml+xml"/>
                      </manifest>
                      <spine toc="ncx"><itemref idref="t"/><itemref idref="c2"/><itemref idref="c1"/></spine>
                    </package>""")
                add("OEBPS/toc.ncx", """<?xml version="1.0"?><ncx xmlns="http://www.daisy.org/z3986/2005/ncx/"><navMap>
                    <navPoint id="a"><navLabel><text>Chương 2 (từ NCX)</text></navLabel><content src="text/c2.xhtml"/></navPoint>
                    </navMap></ncx>""")
                add("OEBPS/images/cover.jpg", "JPEGDATA")
                add("OEBPS/text/title.xhtml", "<html><body><h1>Đấu Phá</h1></body></html>")
                add("OEBPS/text/c2.xhtml", "<html><body><h2>Ignored heading</h2><p>Đoạn A.</p><p>Đoạn&nbsp;B.<br/>Dòng mới.</p><div><p>Lồng nhau.</p></div></body></html>")
                add("OEBPS/text/c1.xhtml", "<html><body><h2>Chương 1</h2><p>Đoạn C.</p></body></html>")
            }
        }.toByteArray()
        val book = epub.parse(ByteArrayInputStream(bytes), "dau-pha.epub")
        assertEquals("Đấu Phá", book.title)
        assertEquals("Thiên Tằm", book.author)
        assertEquals("vi", book.language)
        assertNotNull(book.cover)
        assertEquals("JPEGDATA", String(book.cover!!))
        assertEquals(listOf("Chương 2 (từ NCX)", "Chương 1"), book.chapters.map { it.title })
        assertEquals(listOf("Đoạn A.", "Đoạn B.", "Dòng mới.", "Lồng nhau."), book.chapters[0].paragraphs)
        assertEquals(listOf("Đoạn C."), book.chapters[1].paragraphs)
    }

    @Test
    fun `parser is picked by extension or magic bytes`() {
        val parsers = EbookParsers(listOf(epub, txt, zip))
        assertEquals(epub, parsers.forFile("a.EPUB", ByteArray(0)))
        assertEquals(zip, parsers.forFile("a.zip", ByteArray(0)))
        assertEquals(epub, parsers.forFile("noext", byteArrayOf(0x50, 0x4B, 0x03, 0x04, 0, 0, 0, 0)))
        assertEquals(txt, parsers.forFile("noext", "Hello".toByteArray()))
        assertEquals("Tru Tien-full", EbookParsers.titleFromFileName("/x/Tru_Tien-full.txt"))
    }

    @Test
    fun `google drive links become direct downloads and file names are recovered`() {
        assertEquals(
            "https://drive.google.com/uc?export=download&confirm=t&id=1AbC_x-9",
            EbookRepositoryImpl.directDownloadUrl("https://drive.google.com/file/d/1AbC_x-9/view?usp=sharing"),
        )
        assertEquals("https://example.com/a.epub", EbookRepositoryImpl.directDownloadUrl("https://example.com/a.epub"))
        assertEquals("Tru Tien.epub", EbookRepositoryImpl.fileNameFrom("attachment; filename=\"Tru Tien.epub\"", "https://x/y", null))
        assertEquals("Đấu.epub", EbookRepositoryImpl.fileNameFrom("attachment; filename*=UTF-8''%C4%90%E1%BA%A5u.epub", "https://x/y", null))
        assertEquals("book.txt", EbookRepositoryImpl.fileNameFrom(null, "https://x/dir/book.txt?dl=1", null))
        assertEquals("download.epub", EbookRepositoryImpl.fileNameFrom(null, "https://x/download", "epub+zip"))
    }
}
