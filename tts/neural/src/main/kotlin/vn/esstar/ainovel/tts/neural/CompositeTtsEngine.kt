package vn.esstar.ainovel.tts.neural

import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import vn.esstar.ainovel.tts.api.SynthesisResult
import vn.esstar.ainovel.tts.api.TtsEngine
import vn.esstar.ainovel.tts.api.TtsVoice
import vn.esstar.ainovel.tts.api.VoiceModelStatus
import vn.esstar.ainovel.tts.api.VoiceParams
import vn.esstar.ainovel.tts.system.SystemTtsEngine

/**
 * Routes each request to the system engine or the neural one by the selected voice id
 * (`neural:<model>` → sherpa-onnx). Falls back to the system voice when the neural model is gone.
 */
@Singleton
class CompositeTtsEngine @Inject constructor(
    private val system: SystemTtsEngine,
    private val neural: NeuralTtsEngine,
    private val models: NeuralModelManager,
) : TtsEngine {

    override val id: String = "auto"

    override suspend fun initialize(): Boolean {
        val systemOk = system.initialize()
        return systemOk || models.readyModels().isNotEmpty()
    }

    override suspend fun voices(): List<TtsVoice> = neural.voices() + system.voices()

    override suspend fun synthesize(text: String, params: VoiceParams, target: File): SynthesisResult {
        val modelId = VoiceModelStatus.modelIdFrom(params.voiceId)
        return if (modelId != null && models.isReady(modelId)) {
            neural.synthesize(text, params, target)
        } else {
            system.synthesize(text, if (modelId != null) params.copy(voiceId = null) else params, target)
        }
    }

    override fun release() {
        neural.release()
        system.release()
    }
}
