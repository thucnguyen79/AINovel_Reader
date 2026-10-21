package vn.esstar.ainovel.data.source

import java.io.File
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Data-driven parser test: every `fixtures/<id>/expect.json` (recorded from a reviewed [RuleDumpTest] output)
 * must still be produced by the current rule and parser. A failing key names the rule, the field and both values,
 * so a site redesign or a parser regression is spotted without reading HTML.
 */
class SiteFixturesTest {

    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun `every recorded fixture still parses the same`() {
        val urls = RuleProbe.urls()
        val failures = mutableListOf<String>()
        var checked = 0
        for ((id, pages) in urls) {
            val expectFile = File("src/test/resources/fixtures/$id/expect.json")
            if (!expectFile.exists()) continue
            val expected = json.parseToJsonElement(expectFile.readText()).jsonObject.mapValues { it.value.jsonPrimitive.content }
            val actual = runCatching { RuleProbe.probe(id, pages) }
                .getOrElse { failures += "$id: probe threw ${it::class.simpleName}: ${it.message}"; continue }
            for ((key, want) in expected) {
                val got = actual[key] ?: "<missing>"
                if (got != want) failures += "$id $key\n    expected: $want\n    actual:   $got"
            }
            checked++
        }
        assertTrue("No fixture with expect.json found", checked > 0)
        assertTrue("${failures.size} mismatch(es):\n" + failures.joinToString("\n"), failures.isEmpty())
    }

    @Test
    fun `every rule asset has fixtures`() {
        val ruleIds = Fixtures.allRules().map { it.id }.toSet()
        val missing = ruleIds.filter { !File("src/test/resources/fixtures/$it").isDirectory }
        assertTrue("Rules without fixtures: $missing", missing.isEmpty())
    }
}
