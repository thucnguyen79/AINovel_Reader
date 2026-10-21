package vn.esstar.ainovel.data.translate

import java.io.File
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import vn.esstar.ainovel.data.translate.engine.ConvertEngine
import vn.esstar.ainovel.data.translate.engine.ConvertMode
import vn.esstar.ainovel.data.translate.engine.DictionaryTextParser
import vn.esstar.ainovel.data.translate.engine.IndexedDictionary
import vn.esstar.ainovel.data.translate.engine.LuatNhanRule
import org.junit.Assert.assertTrue

class ConvertEngineTest {

    private lateinit var dir: File
    private lateinit var names: IndexedDictionary
    private lateinit var vietPhrase: IndexedDictionary
    private lateinit var hanViet: IndexedDictionary

    @Before
    fun setUp() {
        dir = File(System.getProperty("java.io.tmpdir"), "ainovel-dict-test-${System.nanoTime()}").apply { mkdirs() }
        names = build("names", "林风眠=Lâm Phong Miên\n玉仙宗=Ngọc Tiên Tông")
        vietPhrase = build(
            "vietphrase",
            """
            # comment
            你=ngươi
            这=này
            阴魂不散=âm hồn bất tán/hồn ma không tan
            的=của
            色胚=sắc phôi
            居然=cư nhiên/lại
            又=lại
            来了=đến rồi
            眼前=trước mắt
            白衣=bạch y
            胜雪=thắng tuyết
            女子=nữ tử
            被=bị
            欺负=khi dễ
            在=ở
            """.trimIndent(),
        )
        hanViet = build("hanviet", "你=nhĩ\n这=giá\n阴=âm\n魂=hồn\n不=bất\n散=tán\n林=lâm\n风=phong\n眠=miên\n妖=yêu\n女=nữ\n被=bị\n在=tại\n玉=ngọc\n仙=tiên\n宗=tông\n们=môn\n的=đích")
    }

    @After
    fun tearDown() {
        names.close(); vietPhrase.close(); hanViet.close()
        dir.deleteRecursively()
    }

    private fun build(name: String, text: String): IndexedDictionary {
        val file = File(dir, "$name.dict")
        IndexedDictionary.build(DictionaryTextParser.parse(text.lineSequence()), file)
        return IndexedDictionary.open(file)
    }

    @Test
    fun `indexed dictionary lookups and longest match`() {
        assertEquals("của", vietPhrase.lookup("的"))
        assertNull(vietPhrase.lookup("不存在"))
        assertEquals(4 to "âm hồn bất tán/hồn ma không tan", vietPhrase.longestMatch("阴魂不散的", 0))
        assertEquals(1 to "của", vietPhrase.longestMatch("阴魂不散的", 4))
        assertEquals(4, vietPhrase.maxKeyLength)
        assertEquals(15, vietPhrase.size)
    }

    @Test
    fun `converts with names, phrases, hanviet fallback and punctuation`() {
        val engine = ConvertEngine(names, vietPhrase, hanViet)
        // "{0}的{1}" luật nhân reorders the possessive: X của Y -> Y của X.
        assertEquals(
            "“Ngươi này sắc phôi của âm hồn bất tán, cư nhiên lại đến rồi!”",
            engine.convert("“你这阴魂不散的色胚，居然又来了！”"),
        )
        assertEquals(
            "“Ngươi này âm hồn bất tán của sắc phôi, cư nhiên lại đến rồi!”",
            ConvertEngine(names, vietPhrase, hanViet, rules = emptyList()).convert("“你这阴魂不散的色胚，居然又来了！”"),
        )
        assertEquals("Lâm Phong Miên ở Ngọc Tiên Tông bị yêu nữ khi dễ.", engine.convert("林风眠在玉仙宗被妖女欺负。"))
    }

    @Test
    fun `glossary overrides dictionaries`() {
        val engine = ConvertEngine(names, vietPhrase, hanViet, glossary = mapOf("林风眠" to "Lâm Phong Miên (nam chính)"))
        assertEquals("Lâm Phong Miên (nam chính) ở Ngọc Tiên Tông.", engine.convert("林风眠在玉仙宗。"))
    }

    @Test
    fun `han viet mode reads characters one by one`() {
        val engine = ConvertEngine(names, vietPhrase, hanViet)
        assertEquals("Nhĩ giá âm hồn bất tán.", engine.convert("你这阴魂不散。", ConvertMode.HAN_VIET))
        assertEquals("你这阴魂不散。", engine.convert("你这阴魂不散。", ConvertMode.RAW))
    }

    @Test
    fun `keeps latin text and digits, capitalizes sentences`() {
        val engine = ConvertEngine(names, vietPhrase, hanViet)
        assertEquals("Chương 12: ngươi này! Ngươi đến rồi? ABC 2024.", engine.convert("Chương 12：你这！你来了？ABC 2024。"))
    }

    @Test
    fun `unknown characters are kept`() {
        val engine = ConvertEngine(null, vietPhrase, hanViet)
        assertEquals("Ngươi 龘 này", engine.convert("你龘这"))
    }

    @Test
    fun `luat nhan rules reorder and pluralize`() {
        val engine = ConvertEngine(names, vietPhrase, hanViet)
        assertEquals("Các nữ tử của Ngọc Tiên Tông.", engine.convert("玉仙宗的女子们。"))
        assertEquals("Các nữ tử", engine.convert("女子们"))
        // Rule literal must match whole tokens: "的" inside a dictionary phrase is not a rule hit.
        val custom = ConvertEngine(names, vietPhrase, hanViet, rules = LuatNhanRule.parseLines(sequenceOf("{0}在{1}={0} tại {1}")))
        assertEquals("Lâm Phong Miên tại Ngọc Tiên Tông.", custom.convert("林风眠在玉仙宗。"))
        assertEquals(5, LuatNhanRule.DEFAULT.size)
        assertTrue(LuatNhanRule.parseLines(sequenceOf("# comment", "abc=def", "{0}=x", "{0}们=các {0}")).size == 1)
    }

    @Test
    fun `detailed tokens map output back to source and expose other meanings`() {
        val engine = ConvertEngine(names, vietPhrase, hanViet)
        val tokens = engine.convertDetailed("你这居然来了。")
        assertEquals("Ngươi này cư nhiên đến rồi.", tokens.joinToString("") { it.text })
        assertEquals(listOf("你", "这", "居然", "来了", "。"), tokens.map { it.source })
        assertEquals(listOf("nhĩ"), tokens[0].alternatives)
        assertEquals(listOf("lại"), tokens[2].alternatives)
        assertTrue(tokens[0].isChinese)
        assertTrue(!tokens[4].isChinese)
        val rule = engine.convertDetailed("玉仙宗的女子").single()
        assertEquals("玉仙宗的女子", rule.source)
        assertEquals("Nữ tử của Ngọc Tiên Tông", rule.text)
    }
}
