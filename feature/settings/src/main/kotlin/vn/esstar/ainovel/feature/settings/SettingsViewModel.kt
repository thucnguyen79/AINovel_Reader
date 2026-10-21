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

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val ttsPreferences: TtsPreferences,
    private val engine: TtsEngine,
) : ViewModel() {

    val prefs: StateFlow<TtsPrefs> = ttsPreferences.prefs
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TtsPrefs())

    private val _voices = MutableStateFlow<List<TtsVoice>>(emptyList())
    val voices: StateFlow<List<TtsVoice>> = _voices

    private val _engineAvailable = MutableStateFlow(true)
    val engineAvailable: StateFlow<Boolean> = _engineAvailable

    init {
        viewModelScope.launch {
            val ok = runCatching { engine.initialize() }.getOrDefault(false)
            _engineAvailable.value = ok
            if (ok) _voices.value = runCatching { engine.voices() }.onFailure { Timber.e(it) }.getOrDefault(emptyList())
        }
    }

    fun setVoice(voice: TtsVoice?) = viewModelScope.launch { ttsPreferences.setVoice(voice?.engineId ?: engine.id, voice?.id) }
    fun setSpeed(value: Float) = viewModelScope.launch { ttsPreferences.setSpeed(value) }
    fun setPitch(value: Float) = viewModelScope.launch { ttsPreferences.setPitch(value) }
    fun setBreakAtComma(value: Boolean) = viewModelScope.launch { ttsPreferences.setBreakAtComma(value) }
    fun setStableMode(value: Boolean) = viewModelScope.launch { ttsPreferences.setStableMode(value) }
}
