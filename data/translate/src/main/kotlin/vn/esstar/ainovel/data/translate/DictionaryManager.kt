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
    private data class Meta(val type: String, val displayName: String, val entries: Int, val importedAt: Long, val builtIn: Boolean = false)

    private val dir: File get() = storage.dictionaries
    private val metaFile: File get() = File(dir, "meta.json")
    private val mutex = Mutex()

    private val _dictionaries = MutableStateFlow<List<DictionaryInfo>>(emptyList())
    val dictionaries: StateFlow<List<DictionaryInfo>> = _dictionaries

    private var engine: ConvertEngine? = null
    private var open: Map<DictionaryType, IndexedDictionary> = emptyMap()

    /** Returns an engine for the current dictionaries, opening or building files as needed. */
    suspend fun engine(): ConvertEngine = mutex.withLock {
        engine?.let { return it }
        withContext(io) {
            dir.mkdirs()
            ensureBuiltInHanViet()
            val metas = readMeta()
            val opened = HashMap<DictionaryType, IndexedDictionary>()
            for (m in metas) {
                val type = runCatching { DictionaryType.valueOf(m.type) }.getOrNull() ?: continue
                val file = fileFor(type)
                if (!file.isFile) continue
                runCatching { IndexedDictionary.open(file) }
                    .onSuccess { opened[type] = it }
                    .onFailure { Timber.e(it, "Cannot open dictionary %s", type) }
            }
            open.values.forEach { runCatching { it.close() } }
            open = opened
            _dictionaries.value = metas.mapNotNull { it.toInfo() }
            ConvertEngine(
                names = opened[DictionaryType.NAMES],
                vietPhrase = opened[DictionaryType.VIETPHRASE],
                hanViet = opened[DictionaryType.HANVIET],
            ).also { engine = it }
        }
    }

    suspend fun refreshList() {
        withContext(io) {
            dir.mkdirs()
            ensureBuiltInHanViet()
            _dictionaries.value = readMeta().mapNotNull { it.toInfo() }
        }
    }

    suspend fun import(uri: Uri, type: DictionaryType, displayName: String?): DictionaryInfo = mutex.withLock {
        withContext(io) {
            dir.mkdirs()
            val name = displayName ?: queryDisplayName(uri) ?: type.name.lowercase()
            val target = fileFor(type)
            val entries = context.contentResolver.openInputStream(uri)?.use { input ->
                input.bufferedReader(Charsets.UTF_8).useLines { lines ->
                    IndexedDictionary.build(DictionaryTextParser.parse(lines), target)
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
            _dictionaries.value = readMeta().mapNotNull { it.toInfo() }
        }
    }

    private fun invalidate() {
        engine = null
        open.values.forEach { runCatching { it.close() } }
        open = emptyMap()
    }

    private fun ensureBuiltInHanViet() {
        val metas = readMeta()
        if (metas.any { it.type == DictionaryType.HANVIET.name } && fileFor(DictionaryType.HANVIET).isFile) return
        runCatching {
            val entries = context.assets.open("dicts/hanviet.txt").bufferedReader(Charsets.UTF_8).useLines { lines ->
                IndexedDictionary.build(DictionaryTextParser.parse(lines), fileFor(DictionaryType.HANVIET))
            }
            writeMeta(metas.filter { it.type != DictionaryType.HANVIET.name } + Meta(DictionaryType.HANVIET.name, "Hán Việt (Unihan)", entries, System.currentTimeMillis(), builtIn = true))
        }.onFailure { Timber.e(it, "Cannot build built-in Hán Việt dictionary") }
    }

    private fun fileFor(type: DictionaryType) = File(dir, "${type.name.lowercase()}.dict")

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
