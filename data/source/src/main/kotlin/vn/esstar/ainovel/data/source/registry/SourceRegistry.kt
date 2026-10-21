package vn.esstar.ainovel.data.source.registry

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import java.net.URI
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json
import timber.log.Timber
import vn.esstar.ainovel.data.source.model.SourceRule
import vn.esstar.ainovel.data.source.parser.RuleParser

/** Where rules come from. Production reads `assets/sources/<id>.json`; tests supply rules directly. */
interface RuleLoader {
    suspend fun loadAll(): List<SourceRule>
}

class AssetRuleLoader @Inject constructor(
    @ApplicationContext private val context: Context,
    private val json: Json,
) : RuleLoader {
    override suspend fun loadAll(): List<SourceRule> {
        val assets = context.assets
        val files = assets.list(ASSET_DIR).orEmpty().filter { it.endsWith(".json") }.sorted()
        return files.mapNotNull { file ->
            runCatching {
                assets.open("$ASSET_DIR/$file").bufferedReader().use { json.decodeFromString<SourceRule>(it.readText()) }
            }.onFailure { Timber.e(it, "Invalid source rule %s", file) }.getOrNull()
        }
    }

    private companion object {
        const val ASSET_DIR = "sources"
    }
}

/** Holds all known rules and resolves which one handles a URL. */
@Singleton
class SourceRegistry @Inject constructor(private val loader: RuleLoader) {

    private val mutex = Mutex()
    private var rules: List<SourceRule>? = null
    private val parsers = HashMap<String, RuleParser>()

    suspend fun all(): List<SourceRule> = mutex.withLock {
        rules ?: loader.loadAll().also { rules = it }
    }

    suspend fun byId(id: String): SourceRule? = all().firstOrNull { it.id == id }

    suspend fun forUrl(url: String): SourceRule? {
        val host = hostOf(url) ?: return null
        return all().firstOrNull { rule -> rule.hosts.any { h -> host == h || host.endsWith(".$h") } }
    }

    fun parser(rule: SourceRule): RuleParser = parsers.getOrPut(rule.id) { RuleParser(rule) }

    companion object {
        fun hostOf(url: String): String? = runCatching { URI(url).host }.getOrNull()
            ?.lowercase()
            ?.removePrefix("www.")
            ?.removePrefix("m.")
    }
}
