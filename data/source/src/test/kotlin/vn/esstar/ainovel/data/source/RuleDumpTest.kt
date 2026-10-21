package vn.esstar.ainovel.data.source

import java.io.File
import org.junit.Test

/**
 * Development aid: writes what every rule listed in `fixtures/_urls.json` extracts from its fixtures to
 * `build/rule-dump.txt` (`===== id` then `key=value` lines). Review it, then record it as `expect.json` with
 * `tools/sources/record_expect.py`. Never fails on its own.
 */
class RuleDumpTest {

    @Test
    fun `dump all probed rules`() {
        val out = StringBuilder()
        for ((id, pages) in RuleProbe.urls()) {
            out.appendLine("===== $id")
            val values = runCatching { RuleProbe.probe(id, pages) }
                .getOrElse { mapOf("error" to "${it::class.simpleName}: ${it.message}") }
            for ((k, v) in values) out.appendLine("$k=$v")
        }
        File("build/rule-dump.txt").apply { parentFile.mkdirs() }.writeText(out.toString())
    }
}
