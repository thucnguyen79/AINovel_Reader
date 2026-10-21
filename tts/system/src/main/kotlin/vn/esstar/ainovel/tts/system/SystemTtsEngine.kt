package vn.esstar.ainovel.tts.system

import android.content.Context
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.speech.tts.Voice
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.util.Locale
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import timber.log.Timber
import vn.esstar.ainovel.tts.api.SynthesisResult
import vn.esstar.ainovel.tts.api.TtsEngine
import vn.esstar.ainovel.tts.api.TtsVoice
import vn.esstar.ainovel.tts.api.VoiceParams

/**
 * Android's built-in TextToSpeech (Google Speech Services, Samsung TTS, ...). Free, works offline once a
 * Vietnamese voice is installed. Synthesizes to WAV files one utterance at a time.
 */
@Singleton
class SystemTtsEngine @Inject constructor(
    @ApplicationContext private val context: Context,
) : TtsEngine {

    override val id: String = ID

    private var tts: TextToSpeech? = null
    private var initResult: CompletableDeferred<Boolean>? = null
    private val initMutex = Mutex()
    private val synthMutex = Mutex()
    private val pending = ConcurrentHashMap<String, (Boolean) -> Unit>()

    override suspend fun initialize(): Boolean = initMutex.withLock {
        initResult?.let { return it.await() }
        val deferred = CompletableDeferred<Boolean>()
        initResult = deferred
        tts = TextToSpeech(context) { status ->
            val ok = status == TextToSpeech.SUCCESS
            if (!ok) Timber.e("System TTS init failed: %d", status)
            deferred.complete(ok)
        }
        val ok = withTimeoutOrNull(INIT_TIMEOUT_MS) { deferred.await() } ?: false
        if (ok) {
            tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(utteranceId: String) = Unit
                override fun onDone(utteranceId: String) { pending.remove(utteranceId)?.invoke(true) }

                @Deprecated("Deprecated in Java")
                override fun onError(utteranceId: String) { pending.remove(utteranceId)?.invoke(false) }
                override fun onError(utteranceId: String, errorCode: Int) {
                    Timber.w("TTS error %d for %s", errorCode, utteranceId)
                    pending.remove(utteranceId)?.invoke(false)
                }
            })
            tts?.language = VIETNAMESE
        } else {
            initResult = null
        }
        ok
    }

    override suspend fun voices(): List<TtsVoice> {
        if (!initialize()) return emptyList()
        val engine = tts ?: return emptyList()
        val all = runCatching { engine.voices }.getOrNull().orEmpty()
        return all
            .filter { it.locale.language == "vi" }
            .filterNot { it.features.contains(TextToSpeech.Engine.KEY_FEATURE_NOT_INSTALLED) }
            .sortedWith(compareBy<Voice> { it.isNetworkConnectionRequired }.thenBy { it.name })
            .map { v ->
                TtsVoice(
                    id = v.name,
                    displayName = friendlyName(v),
                    language = v.locale.toLanguageTag(),
                    isOffline = !v.isNetworkConnectionRequired,
                    engineId = ID,
                )
            }
    }

    override suspend fun synthesize(text: String, params: VoiceParams, target: File): SynthesisResult {
        if (!initialize()) return SynthesisResult.Failure("System TTS not available")
        val engine = tts ?: return SynthesisResult.Failure("System TTS not available")
        return synthMutex.withLock {
            applyVoice(engine, params)
            target.parentFile?.mkdirs()
            val tmp = File(target.parentFile, target.name + ".part")
            tmp.delete()
            val utteranceId = UUID.randomUUID().toString()
            val ok = withTimeoutOrNull(SYNTH_TIMEOUT_MS) {
                suspendCancellableCoroutine { cont ->
                    pending[utteranceId] = { success -> if (cont.isActive) cont.resume(success) }
                    val queued = engine.synthesizeToFile(text, Bundle(), tmp, utteranceId)
                    if (queued != TextToSpeech.SUCCESS) {
                        pending.remove(utteranceId)
                        if (cont.isActive) cont.resume(false)
                    }
                    cont.invokeOnCancellation { pending.remove(utteranceId); engine.stop() }
                }
            } ?: run {
                pending.remove(utteranceId)
                engine.stop()
                false
            }
            if (ok && tmp.length() > 44 && (target.delete() || !target.exists()) && tmp.renameTo(target)) {
                SynthesisResult.Success(target)
            } else {
                tmp.delete()
                SynthesisResult.Failure("Synthesis failed for utterance", retryable = true)
            }
        }
    }

    private fun applyVoice(engine: TextToSpeech, params: VoiceParams) {
        engine.setPitch(params.pitch.coerceIn(0.5f, 2f))
        // Speed is applied by the player (time-stretch), so synthesis stays at 1x and cache hits survive speed changes.
        engine.setSpeechRate(1f)
        val wanted = params.voiceId
        val voice = wanted?.let { id -> runCatching { engine.voices }.getOrNull()?.firstOrNull { it.name == id } }
        if (voice != null) {
            engine.voice = voice
        } else {
            engine.language = VIETNAMESE
        }
    }

    private fun friendlyName(v: Voice): String {
        // Google voice names look like "vi-vn-x-vif-local"; show a short, stable label.
        val parts = v.name.split('-')
        val variant = parts.getOrNull(3)?.uppercase() ?: parts.lastOrNull()?.uppercase() ?: v.name
        val kind = if (v.isNetworkConnectionRequired) "online" else "offline"
        return "Giọng $variant ($kind)"
    }

    override fun release() {
        tts?.shutdown()
        tts = null
        initResult = null
    }

    companion object {
        const val ID = "system"
        private val VIETNAMESE: Locale = Locale.forLanguageTag("vi-VN")
        private const val INIT_TIMEOUT_MS = 10_000L
        private const val SYNTH_TIMEOUT_MS = 30_000L
    }
}
