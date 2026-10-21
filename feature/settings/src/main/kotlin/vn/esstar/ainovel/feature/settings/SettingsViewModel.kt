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
import vn.esstar.ainovel.domain.model.AppInfo
import vn.esstar.ainovel.domain.model.BackupSummary
import vn.esstar.ainovel.domain.repository.BackupRepository
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.receiveAsFlow
import vn.esstar.ainovel.core.common.log.FileLoggingTree
import java.io.File

sealed interface SettingsEvent {
    data class Exported(val summary: BackupSummary) : SettingsEvent
    data class Imported(val summary: BackupSummary) : SettingsEvent
    data class BackupFailed(val reason: String?) : SettingsEvent
    data class ShareLog(val files: List<File>) : SettingsEvent
    data object NoLog : SettingsEvent
}

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val ttsPreferences: TtsPreferences,
    private val engine: TtsEngine,
    private val networkPreferences: NetworkPreferences,
    private val downloads: DownloadRepository,
    private val voiceModels: VoiceModelManager,
    private val backup: BackupRepository,
    private val updateChecker: UpdateChecker,
    val appInfo: AppInfo,
    private val fileLog: FileLoggingTree,
) : ViewModel() {

    private val _events = Channel<SettingsEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    private val _backupBusy = MutableStateFlow(false)
    val backupBusy: StateFlow<Boolean> = _backupBusy

    private val _update = MutableStateFlow<UpdateState>(UpdateState.Idle)
    val update: StateFlow<UpdateState> = _update

    fun exportBackup(uri: String) = runBackup { SettingsEvent.Exported(backup.export(uri)) }
    fun importBackup(uri: String) = runBackup { SettingsEvent.Imported(backup.import(uri)) }

    private fun runBackup(block: suspend () -> SettingsEvent) {
        if (_backupBusy.value) return
        viewModelScope.launch {
            _backupBusy.value = true
            runCatching { block() }
                .onSuccess { _events.send(it) }
                .onFailure { Timber.e(it, "Backup failed"); _events.send(SettingsEvent.BackupFailed(it.message)) }
            _backupBusy.value = false
            refreshStorage()
        }
    }

    fun checkUpdate() = viewModelScope.launch {
        _update.value = UpdateState.Checking
        _update.value = runCatching { updateChecker.check() }
            .fold({ if (it == null) UpdateState.UpToDate else UpdateState.Available(it) }, { Timber.w(it, "Update check failed"); UpdateState.Failed(it.message) })
    }

    fun shareLog() = viewModelScope.launch {
        val files = fileLog.snapshotFiles()
        _events.send(if (files.isEmpty()) SettingsEvent.NoLog else SettingsEvent.ShareLog(files))
    }

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
