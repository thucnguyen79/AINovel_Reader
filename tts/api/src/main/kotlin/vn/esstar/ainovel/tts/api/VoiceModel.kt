package vn.esstar.ainovel.tts.api

import kotlinx.coroutines.flow.StateFlow

/** A downloadable on-device neural voice (sherpa-onnx + Piper model). */
data class VoiceModel(
    /** Stable id, also the directory name under `files/models/tts/`. */
    val id: String,
    val displayName: String,
    val description: String,
    val language: String,
    val downloadUrl: String,
    val sizeBytes: Long,
    /** Relative quality hint for the UI: 1 = fast/low, 3 = best. */
    val quality: Int,
)

enum class VoiceModelState { NOT_DOWNLOADED, DOWNLOADING, READY, FAILED }

data class VoiceModelStatus(
    val model: VoiceModel,
    val state: VoiceModelState = VoiceModelState.NOT_DOWNLOADED,
    /** 0..1 while downloading / extracting. */
    val progress: Float = 0f,
    val error: String? = null,
) {
    /** Voice id used in [VoiceParams.voiceId] and [TtsVoice.id] for this model. */
    val voiceId: String get() = voiceIdFor(model.id)

    companion object {
        const val VOICE_PREFIX = "neural:"
        fun voiceIdFor(modelId: String) = VOICE_PREFIX + modelId
        fun modelIdFrom(voiceId: String?): String? = voiceId?.takeIf { it.startsWith(VOICE_PREFIX) }?.removePrefix(VOICE_PREFIX)
    }
}

/** Downloads, lists and deletes neural voice models. Implemented in `tts/neural`. */
interface VoiceModelManager {
    val models: StateFlow<List<VoiceModelStatus>>

    suspend fun refresh()

    /** Starts (or resumes) downloading [modelId]; progress is reported through [models]. */
    suspend fun download(modelId: String)

    suspend fun cancelDownload(modelId: String)

    suspend fun delete(modelId: String)
}
