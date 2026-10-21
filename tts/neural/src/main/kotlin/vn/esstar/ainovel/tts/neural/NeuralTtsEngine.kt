package vn.esstar.ainovel.tts.neural

import com.k2fsa.sherpa.onnx.OfflineTts
import com.k2fsa.sherpa.onnx.OfflineTtsConfig
import com.k2fsa.sherpa.onnx.OfflineTtsModelConfig
import com.k2fsa.sherpa.onnx.OfflineTtsVitsModelConfig
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import timber.log.Timber
import vn.esstar.ainovel.core.common.di.AppDispatcher
import vn.esstar.ainovel.core.common.di.Dispatcher
import vn.esstar.ainovel.tts.api.SynthesisResult
import vn.esstar.ainovel.tts.api.TtsEngine
import vn.esstar.ainovel.tts.api.TtsVoice
import vn.esstar.ainovel.tts.api.VietnameseNumberSpeller
import vn.esstar.ainovel.tts.api.VoiceModelStatus
import vn.esstar.ainovel.tts.api.VoiceParams

/**
 * sherpa-onnx VITS/Piper engine. One native model is kept loaded at a time (they take 50–100 MB of
 * RAM); switching voices reloads. Pitch is not supported by Piper, so it is ignored here.
 */
@Singleton
class NeuralTtsEngine @Inject constructor(
    private val models: NeuralModelManager,
    @Dispatcher(AppDispatcher.Default) private val default: CoroutineDispatcher,
) : TtsEngine {

    override val id: String = ID

    private val mutex = Mutex()
    private var loaded: Pair<String, OfflineTts>? = null

    override suspend fun initialize(): Boolean = true

    override suspend fun voices(): List<TtsVoice> = models.readyModels().map { m ->
        TtsVoice(
            id = VoiceModelStatus.voiceIdFor(m.id),
            displayName = m.displayName,
            language = m.language,
            isOffline = true,
            engineId = ID,
        )
    }

    override suspend fun synthesize(text: String, params: VoiceParams, target: File): SynthesisResult {
        val modelId = VoiceModelStatus.modelIdFrom(params.voiceId)
            ?: models.readyModels().firstOrNull()?.id
            ?: return SynthesisResult.Failure("No neural voice downloaded")
        if (!models.isReady(modelId)) return SynthesisResult.Failure("Voice $modelId not downloaded")
        return mutex.withLock {
            withContext(default) {
                val tts = runCatching { ttsFor(modelId) }.getOrElse { e ->
                    Timber.e(e, "Cannot load neural model %s", modelId)
                    return@withContext SynthesisResult.Failure("Cannot load model: ${e.message}")
                }
                target.parentFile?.mkdirs()
                val tmp = File(target.parentFile, target.name + ".part")
                val ok = runCatching {
                    // Piper's espeak-ng Vietnamese voice skips digits, so spell them out first.
                    val spoken = VietnameseNumberSpeller.spell(text)
                    val audio = tts.generate(text = spoken, sid = 0, speed = 1.0f)
                    audio.samples.isNotEmpty() && audio.save(tmp.absolutePath)
                }.onFailure { Timber.e(it, "Neural synthesis failed") }.getOrDefault(false)
                if (ok && tmp.length() > 44 && (target.delete() || !target.exists()) && tmp.renameTo(target)) {
                    SynthesisResult.Success(target)
                } else {
                    tmp.delete()
                    SynthesisResult.Failure("Neural synthesis produced no audio", retryable = true)
                }
            }
        }
    }

    private fun ttsFor(modelId: String): OfflineTts {
        loaded?.let { (id, tts) -> if (id == modelId) return tts else tts.release() }
        loaded = null
        val dir = models.modelDir(modelId)
        val onnx = dir.walk().firstOrNull { it.isFile && it.extension == "onnx" } ?: error("No .onnx in $dir")
        val tokens = File(onnx.parentFile, "tokens.txt").takeIf { it.isFile } ?: File(dir, "tokens.txt")
        val espeak = dir.walk().firstOrNull { it.isDirectory && it.name == "espeak-ng-data" }
        val config = OfflineTtsConfig(
            model = OfflineTtsModelConfig(
                vits = OfflineTtsVitsModelConfig(
                    model = onnx.absolutePath,
                    tokens = tokens.absolutePath,
                    dataDir = espeak?.absolutePath ?: "",
                ),
                numThreads = NUM_THREADS,
                debug = false,
                provider = "cpu",
            ),
            maxNumSentences = 1,
        )
        val tts = OfflineTts(assetManager = null, config = config)
        loaded = modelId to tts
        Timber.i("Loaded neural voice %s (%d Hz)", modelId, tts.sampleRate())
        return tts
    }

    override fun release() {
        loaded?.second?.release()
        loaded = null
    }

    companion object {
        const val ID = "neural"
        private const val NUM_THREADS = 2
    }
}
