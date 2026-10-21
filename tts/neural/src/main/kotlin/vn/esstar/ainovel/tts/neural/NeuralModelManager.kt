package vn.esstar.ainovel.tts.neural

import java.io.File
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.yield
import okhttp3.OkHttpClient
import okhttp3.Request
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import org.apache.commons.compress.compressors.bzip2.BZip2CompressorInputStream
import timber.log.Timber
import vn.esstar.ainovel.core.common.di.AppDispatcher
import vn.esstar.ainovel.core.common.di.Dispatcher
import vn.esstar.ainovel.core.storage.AppStorage
import vn.esstar.ainovel.tts.api.VoiceModel
import vn.esstar.ainovel.tts.api.VoiceModelManager
import vn.esstar.ainovel.tts.api.VoiceModelState
import vn.esstar.ainovel.tts.api.VoiceModelStatus

/**
 * Keeps Piper models under `files/models/tts/<id>/`. A model is "ready" when `.ready` exists next to
 * the ONNX file; partial downloads live in `<id>.tar.bz2.part` and resume via HTTP Range.
 */
@Singleton
class NeuralModelManager @Inject constructor(
    private val storage: AppStorage,
    private val client: OkHttpClient,
    @Dispatcher(AppDispatcher.IO) private val io: CoroutineDispatcher,
) : VoiceModelManager {

    private val root: File get() = File(storage.models, "tts")
    private val scope = CoroutineScope(SupervisorJob() + io)
    private val jobs = HashMap<String, Job>()

    private val _models = MutableStateFlow(NeuralVoiceCatalog.models.map { VoiceModelStatus(it) })
    override val models: StateFlow<List<VoiceModelStatus>> = _models

    init {
        scope.launch { refresh() }
    }

    fun modelDir(modelId: String): File = File(root, modelId)

    fun isReady(modelId: String): Boolean = File(modelDir(modelId), READY_MARK).isFile

    /** Ready model ids, in catalogue order. */
    fun readyModels(): List<VoiceModel> = NeuralVoiceCatalog.models.filter { isReady(it.id) }

    override suspend fun refresh() = withContext(io) {
        root.mkdirs()
        _models.update { list ->
            list.map { st ->
                if (st.state == VoiceModelState.DOWNLOADING) st
                else st.copy(state = if (isReady(st.model.id)) VoiceModelState.READY else VoiceModelState.NOT_DOWNLOADED, progress = 0f)
            }
        }
    }

    override suspend fun download(modelId: String) {
        val model = NeuralVoiceCatalog.byId(modelId) ?: return
        synchronized(jobs) {
            if (jobs[modelId]?.isActive == true) return
            jobs[modelId] = scope.launch { runDownload(model) }
        }
    }

    override suspend fun cancelDownload(modelId: String) {
        synchronized(jobs) { jobs.remove(modelId) }?.cancel()
        set(modelId) { it.copy(state = if (isReady(modelId)) VoiceModelState.READY else VoiceModelState.NOT_DOWNLOADED, progress = 0f) }
    }

    override suspend fun delete(modelId: String) = withContext(io) {
        cancelDownload(modelId)
        modelDir(modelId).deleteRecursively()
        archiveFor(modelId).delete()
        File(archiveFor(modelId).path + ".part").delete()
        set(modelId) { it.copy(state = VoiceModelState.NOT_DOWNLOADED, progress = 0f, error = null) }
    }

    private suspend fun runDownload(model: VoiceModel) {
        set(model.id) { it.copy(state = VoiceModelState.DOWNLOADING, progress = 0f, error = null) }
        try {
            root.mkdirs()
            val archive = archiveFor(model.id)
            if (!archive.isFile) fetch(model, archive)
            set(model.id) { it.copy(progress = 0.9f) }
            extract(archive, modelDir(model.id))
            archive.delete()
            File(modelDir(model.id), READY_MARK).writeText(model.downloadUrl)
            set(model.id) { it.copy(state = VoiceModelState.READY, progress = 1f) }
            Timber.i("Neural voice %s ready", model.id)
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            Timber.e(e, "Download of %s failed", model.id)
            set(model.id) { it.copy(state = VoiceModelState.FAILED, error = e.message ?: e.javaClass.simpleName) }
        } finally {
            synchronized(jobs) { jobs.remove(model.id) }
        }
    }

    private suspend fun fetch(model: VoiceModel, archive: File) {
        val part = File(archive.path + ".part")
        val have = if (part.isFile) part.length() else 0L
        val request = Request.Builder().url(model.downloadUrl).apply { if (have > 0) header("Range", "bytes=$have-") }.build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IOException("HTTP ${response.code}")
            val resumed = response.code == 206
            val body = response.body
            val total = (if (resumed) have else 0L) + body.contentLength().coerceAtLeast(0)
            var done = if (resumed) have else 0L
            body.byteStream().use { input ->
                java.io.FileOutputStream(part, resumed).use { out ->
                    val buf = ByteArray(256 * 1024)
                    var lastReport = 0L
                    while (true) {
                        yield()
                        val n = input.read(buf)
                        if (n < 0) break
                        out.write(buf, 0, n)
                        done += n
                        if (total > 0 && done - lastReport > 512 * 1024) {
                            lastReport = done
                            val p = (done.toFloat() / total) * 0.9f
                            set(model.id) { it.copy(progress = p) }
                        }
                    }
                }
            }
        }
        if (!part.renameTo(archive)) throw IOException("Cannot finalize download")
    }

    /** Extracts `<id>/...` from the tar.bz2 into [target], flattening the top-level directory. */
    private fun extract(archive: File, target: File) {
        target.deleteRecursively()
        target.mkdirs()
        archive.inputStream().buffered().use { raw ->
            TarArchiveInputStream(BZip2CompressorInputStream(raw)).use { tar ->
                while (true) {
                    val entry = tar.nextEntry ?: break
                    val rel = entry.name.substringAfter('/', "")
                    if (rel.isEmpty()) continue
                    val out = File(target, rel)
                    if (!out.canonicalPath.startsWith(target.canonicalPath)) throw IOException("Bad archive entry ${entry.name}")
                    if (entry.isDirectory) {
                        out.mkdirs()
                    } else {
                        out.parentFile?.mkdirs()
                        out.outputStream().use { tar.copyTo(it) }
                    }
                }
            }
        }
        if (target.walk().none { it.extension == "onnx" }) throw IOException("Archive has no .onnx model")
    }

    private fun archiveFor(modelId: String) = File(root, "$modelId.tar.bz2")

    private fun set(modelId: String, f: (VoiceModelStatus) -> VoiceModelStatus) {
        _models.update { list -> list.map { if (it.model.id == modelId) f(it) else it } }
    }

    private companion object {
        const val READY_MARK = ".ready"
    }
}
