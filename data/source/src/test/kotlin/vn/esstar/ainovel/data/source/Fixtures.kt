package vn.esstar.ainovel.data.source

import java.io.File
import kotlinx.serialization.json.Json
import vn.esstar.ainovel.data.source.model.SourceRule

/** Test helpers: fixture HTML from `src/test/resources`, rule JSON from `src/main/assets`. */
object Fixtures {
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    fun html(source: String, name: String): String {
        val stream = checkNotNull(javaClass.getResourceAsStream("/fixtures/$source/$name")) {
            "Missing fixture /fixtures/$source/$name"
        }
        return stream.bufferedReader().use { it.readText() }
    }

    fun rule(id: String): SourceRule {
        val file = File("src/main/assets/sources/$id.json")
        check(file.exists()) { "Missing rule ${file.absolutePath}" }
        return json.decodeFromString(file.readText())
    }

    fun allRules(): List<SourceRule> = File("src/main/assets/sources").listFiles { f -> f.extension == "json" }
        .orEmpty()
        .map { json.decodeFromString<SourceRule>(it.readText()) }
}
