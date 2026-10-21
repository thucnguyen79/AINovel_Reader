package vn.esstar.ainovel.data.translate

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import timber.log.Timber
import vn.esstar.ainovel.core.common.di.AppDispatcher
import vn.esstar.ainovel.core.common.di.Dispatcher
import vn.esstar.ainovel.core.storage.AppStorage
import vn.esstar.ainovel.data.translate.engine.ConvertEngine
import vn.esstar.ainovel.data.translate.engine.DictionaryTextParser
import vn.esstar.ainovel.data.translate.engine.IndexedDictionary
import vn.esstar.ainovel.data.translate.engine.LuatNhanRule
import vn.esstar.ainovel.domain.model.DictionaryInfo
import vn.esstar.ainovel.domain.model.DictionaryType

/**
 * Owns the dictionary files under `files/dicts/` and the [ConvertEngine] built from them.
 * The Hán Việt dictionary ships in assets (Unihan kVietnamese) and is indexed on first use.
 */
@Singleton
class DictionaryManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val storage: AppStorage,
    private val json: Json,
    @Dispatcher(AppDispatcher.IO) private val io: CoroutineDispatcher,
) {
    @Serializable
    private data class Meta(val type: String, val displayName: String, val entries: Int, val importedAt: Long, val builtIn: Boolean = false, val version: Int = 0)

    private val dir: File get() = storage.dictionaries
    private val metaFile: File get() = File(dir, "meta.json")
    private val mutex = Mutex()

    private val _dictionaries = MutableStateFlow<List<DictionaryInfo>>(emptyList())
    val dictionaries: StateFlow<List<DictionaryInfo>> = _dictionaries

    private class Loaded(val dictionaries: Map<DictionaryType, IndexedDictionary>, val rules: List<LuatNhanRule>)

    private var loaded: Loaded? = null

    /** Returns an engine for the current dictionaries (plus [glossary]), opening or building files as needed. */
    suspend fun engine(glossary: Map<String, String> = emptyMap()): ConvertEngine = mutex.withLock {
        val current = loaded ?: withContext(io) { load().also { loaded = it } }
        ConvertEngine(
            names = current.dictionaries[DictionaryType.NAMES],
            vietPhrase = current.dictionaries[DictionaryType.VIETPHRASE],
            hanViet = current.dictionaries[DictionaryType.HANVIET],
            glossary = glossary,
            rules = current.rules,
        )
    }

    private fun load(): Loaded {
        dir.mkdirs()
        ensureBuiltInHanViet()
        ensureBuiltInLuatNhan()
        val metas = readMeta()
        val opened = HashMap<DictionaryType, IndexedDictionary>()
        for (m in metas) {
            val type = runCatching { DictionaryType.valueOf(m.type) }.getOrNull() ?: continue
            if (type == DictionaryType.LUATNHAN) continue
            val file = fileFor(type)
            if (!file.isFile) continue
            runCatching { IndexedDictionary.open(file) }
                .onSuccess { opened[type] = it }
                .onFailure { Timber.e(it, "Cannot open dictionary %s", type) }
        }
        val rulesFile = fileFor(DictionaryType.LUATNHAN)
        val rules = if (rulesFile.isFile) {
            runCatching { rulesFile.bufferedReader(Charsets.UTF_8).useLines { LuatNhanRule.parseLines(it) } }
                .getOrElse { Timber.e(it, "Cannot read luật nhân"); LuatNhanRule.DEFAULT }
        } else {
            LuatNhanRule.DEFAULT
        }
        _dictionaries.value = metas.mapNotNull { it.toInfo() }
        return Loaded(opened, rules)
    }

    suspend fun refreshList() {
        withContext(io) {
            dir.mkdirs()
            ensureBuiltInHanViet()
            ensureBuiltInLuatNhan()
            _dictionaries.value = readMeta().mapNotNull { it.toInfo() }
        }
    }

    suspend fun import(uri: Uri, type: DictionaryType, displayName: String?): DictionaryInfo = mutex.withLock {
        withContext(io) {
            dir.mkdirs()
            val name = displayName ?: queryDisplayName(uri) ?: type.name.lowercase()
            val target = fileFor(type)
            val entries = context.contentResolver.openInputStream(uri)?.use { input ->
                if (type == DictionaryType.LUATNHAN) {
                    val text = input.bufferedReader(Charsets.UTF_8).readText()
                    val rules = LuatNhanRule.parseLines(text.lineSequence())
                    require(rules.isNotEmpty()) { "No luật nhân rules in file" }
                    target.writeText(text)
                    rules.size
                } else {
                    input.bufferedReader(Charsets.UTF_8).useLines { lines ->
                        IndexedDictionary.build(DictionaryTextParser.parse(lines), target)
                    }
                }
            } ?: throw IllegalArgumentException("Cannot open $uri")
            val meta = Meta(type.name, name, entries, System.currentTimeMillis(), builtIn = false)
            writeMeta(readMeta().filter { it.type != type.name } + meta)
            invalidate()
            _dictionaries.value = readMeta().mapNotNull { it.toInfo() }
            checkNotNull(meta.toInfo())
        }
    }

    suspend fun remove(type: DictionaryType) = mutex.withLock {
        withContext(io) {
            invalidate()
            fileFor(type).delete()
            writeMeta(readMeta().filter { it.type != type.name })
            if (type == DictionaryType.HANVIET) ensureBuiltInHanViet()
            if (type == DictionaryType.LUATNHAN) ensureBuiltInLuatNhan()
            _dictionaries.value = readMeta().mapNotNull { it.toInfo() }
        }
    }

    private fun invalidate() {
        loaded?.dictionaries?.values?.forEach { runCatching { it.close() } }
        loaded = null
    }

    /** The default luật nhân rules show up in the list as a built-in entry until the user imports a file. */
    private fun ensureBuiltInLuatNhan() {
        val metas = readMeta()
        if (metas.any { it.type == DictionaryType.LUATNHAN.name }) return
        writeMeta(metas + Meta(DictionaryType.LUATNHAN.name, "Luật nhân mặc định", LuatNhanRule.DEFAULT.size, System.currentTimeMillis(), builtIn = true))
    }

    private fun ensureBuiltInHanViet() {
        val metas = readMeta()
        val current = metas.firstOrNull { it.type == DictionaryType.HANVIET.name }
        val upToDate = current != null && (!current.builtIn || current.version == BUILTIN_HANVIET_VERSION)
        if (upToDate && fileFor(DictionaryType.HANVIET).isFile) return
        runCatching {
            val entries = context.assets.open("dicts/hanviet.txt").bufferedReader(Charsets.UTF_8).useLines { lines ->
                IndexedDictionary.build(DictionaryTextParser.parse(lines), fileFor(DictionaryType.HANVIET))
            }
            writeMeta(metas.filter { it.type != DictionaryType.HANVIET.name } + Meta(DictionaryType.HANVIET.name, "Hán Việt (Thiều Chửu + Unihan)", entries, System.currentTimeMillis(), builtIn = true, version = BUILTIN_HANVIET_VERSION))
        }.onFailure { Timber.e(it, "Cannot build built-in Hán Việt dictionary") }
    }

    private companion object {
        /** Bump when assets/dicts/hanviet.txt changes so installed devices rebuild the index. */
        const val BUILTIN_HANVIET_VERSION = 2
    }

    private fun fileFor(type: DictionaryType) =
        if (type == DictionaryType.LUATNHAN) File(dir, "luatnhan.txt") else File(dir, "${type.name.lowercase()}.dict")

    private fun readMeta(): List<Meta> = runCatching {
        if (metaFile.isFile) json.decodeFromString<List<Meta>>(metaFile.readText()) else emptyList()
    }.getOrDefault(emptyList())

    private fun writeMeta(list: List<Meta>) {
        metaFile.writeText(json.encodeToString(list))
    }

    private fun Meta.toInfo(): DictionaryInfo? {
        val t = runCatching { DictionaryType.valueOf(type) }.getOrNull() ?: return null
        return DictionaryInfo(t, displayName, entries, importedAt, builtIn)
    }

    private fun queryDisplayName(uri: Uri): String? = runCatching {
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
            if (c.moveToFirst()) c.getString(0) else null
        }
    }.getOrNull()
}
