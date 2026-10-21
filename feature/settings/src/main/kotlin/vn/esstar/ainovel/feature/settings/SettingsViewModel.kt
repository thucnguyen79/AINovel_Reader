package vn.esstar.ainovel.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import timber.log.Timber
import vn.esstar.ainovel.core.datastore.TtsPreferences
import vn.esstar.ainovel.core.datastore.TtsPrefs
import vn.esstar.ainovel.tts.api.TtsEngine
import vn.esstar.ainovel.tts.api.TtsVoice
import vn.esstar.ainovel.tts.api.VoiceModelManager
import vn.esstar.ainovel.tts.api.VoiceModelState
import vn.esstar.ainovel.tts.api.VoiceModelStatus
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import vn.esstar.ainovel.core.datastore.DohMode
import vn.esstar.ainovel.core.datastore.NetworkPreferences
import vn.esstar.ainovel.domain.model.StorageInfo
import vn.esstar.ainovel.domain.repository.DownloadRepository

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val ttsPreferences: TtsPreferences,
    private val engine: TtsEngine,
    private val networkPreferences: NetworkPreferences,
    private val downloads: DownloadRepository,
    private val voiceModels: VoiceModelManager,
) : ViewModel() {

    val neuralModels: StateFlow<List<VoiceModelStatus>> = voiceModels.models

    fun downloadModel(id: String) = viewModelScope.launch { voiceModels.download(id) }
    fun cancelModel(id: String) = viewModelScope.launch { voiceModels.cancelDownload(id) }
    fun deleteModel(id: String) = viewModelScope.launch {
        voiceModels.delete(id)
        if (prefs.value.voiceId == VoiceModelStatus.voiceIdFor(id)) ttsPreferences.setVoice("system", null)
        reloadVoices()
    }

    /** Selects a downloaded neural voice. */
    fun useModel(id: String) = viewModelScope.launch { ttsPreferences.setVoice("neural", VoiceModelStatus.voiceIdFor(id)) }

    private suspend fun reloadVoices() {
        _voices.value = runCatching { engine.voices() }.onFailure { Timber.e(it) }.getOrDefault(emptyList())
    }

    val dohMode: StateFlow<DohMode> = networkPreferences.dohMode
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DohMode.SYSTEM)

    private val _storage = MutableStateFlow<StorageInfo?>(null)
    val storage: StateFlow<StorageInfo?> = _storage

    fun setDohMode(mode: DohMode) = viewModelScope.launch { networkPreferences.setDohMode(mode) }

    fun refreshStorage() = viewModelScope.launch { _storage.value = runCatching { downloads.storageInfo() }.getOrNull() }

    fun clearCaches() = viewModelScope.launch {
        runCatching { downloads.clearCaches() }.onFailure { Timber.e(it) }
        _storage.value = runCatching { downloads.storageInfo() }.getOrNull()
    }


    val prefs: StateFlow<TtsPrefs> = ttsPreferences.prefs
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TtsPrefs())

    private val _voices = MutableStateFlow<List<TtsVoice>>(emptyList())
    val voices: StateFlow<List<TtsVoice>> = _voices

    private val _engineAvailable = MutableStateFlow(true)
    val engineAvailable: StateFlow<Boolean> = _engineAvailable

    init {
        refreshStorage()
        viewModelScope.launch {
            val ok = runCatching { engine.initialize() }.getOrDefault(false)
            _engineAvailable.value = ok
            if (ok) reloadVoices()
        }
        viewModelScope.launch {
            // A model that just finished downloading becomes a selectable voice.
            voiceModels.models.map { list -> list.count { it.state == VoiceModelState.READY } }.distinctUntilChanged().collect { reloadVoices() }
        }
    }

    fun setVoice(voice: TtsVoice?) = viewModelScope.launch { ttsPreferences.setVoice(voice?.engineId ?: engine.id, voice?.id) }
    fun setSpeed(value: Float) = viewModelScope.launch { ttsPreferences.setSpeed(value) }
    fun setPitch(value: Float) = viewModelScope.launch { ttsPreferences.setPitch(value) }
    fun setBreakAtComma(value: Boolean) = viewModelScope.launch { ttsPreferences.setBreakAtComma(value) }
    fun setStableMode(value: Boolean) = viewModelScope.launch { ttsPreferences.setStableMode(value) }
}
