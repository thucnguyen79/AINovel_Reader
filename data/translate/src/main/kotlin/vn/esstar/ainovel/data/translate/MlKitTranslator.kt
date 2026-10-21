package vn.esstar.ainovel.data.translate

import com.google.android.gms.tasks.Task
import com.google.mlkit.common.model.DownloadConditions
import com.google.mlkit.common.model.RemoteModelManager
import com.google.mlkit.nl.translate.TranslateLanguage
import com.google.mlkit.nl.translate.TranslateRemoteModel
import com.google.mlkit.nl.translate.Translation
import com.google.mlkit.nl.translate.Translator
import com.google.mlkit.nl.translate.TranslatorOptions
import java.util.LinkedHashMap
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.suspendCancellableCoroutine
import timber.log.Timber
import vn.esstar.ainovel.domain.model.MlKitStatus

/**
 * On-device Chinese → Vietnamese machine translation (Google ML Kit, free, no account). Models are
 * downloaded only when the user asks and can be deleted again; nothing leaves the device afterwards.
 */
@Singleton
class MlKitTranslator @Inject constructor() {

    private val manager = RemoteModelManager.getInstance()
    private val zhModel = TranslateRemoteModel.Builder(TranslateLanguage.CHINESE).build()
    private val viModel = TranslateRemoteModel.Builder(TranslateLanguage.VIETNAMESE).build()

    private val _status = MutableStateFlow(MlKitStatus())
    val status: StateFlow<MlKitStatus> = _status

    private var translator: Translator? = null

    /** Small memory cache so re-opening a chapter does not re-run the model. */
    private val cache = object : LinkedHashMap<String, String>(256, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, String>?): Boolean = size > 2000
    }

    suspend fun refreshStatus(): MlKitStatus {
        val downloaded = runCatching {
            val models = manager.getDownloadedModels(TranslateRemoteModel::class.java).await()
            val langs = models.map { it.language }.toSet()
            TranslateLanguage.CHINESE in langs && TranslateLanguage.VIETNAMESE in langs
        }.getOrElse { Timber.w(it, "ML Kit model check failed"); false }
        _status.update { it.copy(downloaded = downloaded) }
        return _status.value
    }

    suspend fun download(wifiOnly: Boolean) {
        _status.update { it.copy(downloading = true, error = null) }
        val conditions = DownloadConditions.Builder().apply { if (wifiOnly) requireWifi() }.build()
        runCatching {
            manager.download(zhModel, conditions).await()
            manager.download(viModel, conditions).await()
        }.onSuccess {
            _status.update { it.copy(downloading = false, downloaded = true) }
        }.onFailure { e ->
            Timber.e(e, "ML Kit model download failed")
            _status.update { it.copy(downloading = false, downloaded = false, error = e.message ?: e.javaClass.simpleName) }
        }
    }

    suspend fun delete() {
        runCatching {
            translator?.close(); translator = null
            manager.deleteDownloadedModel(zhModel).await()
            manager.deleteDownloadedModel(viModel).await()
        }.onFailure { Timber.w(it, "ML Kit model delete failed") }
        synchronized(cache) { cache.clear() }
        _status.update { it.copy(downloaded = false, downloading = false, error = null) }
    }

    /** Translates each paragraph; throws when the models are missing. */
    suspend fun translate(paragraphs: List<String>): List<String> {
        val client = translator ?: Translation.getClient(
            TranslatorOptions.Builder()
                .setSourceLanguage(TranslateLanguage.CHINESE)
                .setTargetLanguage(TranslateLanguage.VIETNAMESE)
                .build(),
        ).also { translator = it }
        return paragraphs.map { p ->
            if (p.isBlank()) return@map p
            synchronized(cache) { cache[p] }?.let { return@map it }
            val out = client.translate(p).await()
            synchronized(cache) { cache[p] = out }
            out
        }
    }

    private suspend fun <T> Task<T>.await(): T = suspendCancellableCoroutine { cont ->
        addOnSuccessListener { cont.resume(it) }
        addOnFailureListener { cont.resumeWithException(it) }
        addOnCanceledListener { cont.cancel() }
    }
}
