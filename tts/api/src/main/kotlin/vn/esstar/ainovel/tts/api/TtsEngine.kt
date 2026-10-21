package vn.esstar.ainovel.tts.api

import java.io.File

/** A voice offered by an engine. [id] is engine-specific and stable across runs. */
data class TtsVoice(
    val id: String,
    val displayName: String,
    val language: String,
    val isOffline: Boolean,
    val engineId: String,
)

/** Parameters that change the audio bytes; they are part of the cache key. */
data class VoiceParams(
    val voiceId: String?,
    /** 0.5 .. 2.0; many engines bake this into the audio, so it is cached too. */
    val pitch: Float = 1f,
) {
    fun cacheTag(): String = "${voiceId ?: "default"}|p=$pitch"
}

sealed interface SynthesisResult {
    data class Success(val file: File) : SynthesisResult
    data class Failure(val reason: String, val retryable: Boolean = false) : SynthesisResult
}

/**
 * Turns one text segment into an audio file. Implementations: Android TextToSpeech (phase 2),
 * sherpa-onnx (phase 5), optional online engines later. Calls are serialized by the pipeline.
 */
interface TtsEngine {
    val id: String

    /** Prepares the engine; safe to call repeatedly. Returns false when the engine is unusable on this device. */
    suspend fun initialize(): Boolean

    suspend fun voices(): List<TtsVoice>

    /** Synthesizes [text] into [target] (created or overwritten). */
    suspend fun synthesize(text: String, params: VoiceParams, target: File): SynthesisResult

    fun release()
}
