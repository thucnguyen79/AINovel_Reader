package vn.esstar.ainovel.feature.translate

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import timber.log.Timber
import vn.esstar.ainovel.domain.model.DictionaryInfo
import vn.esstar.ainovel.domain.model.DictionaryType
import vn.esstar.ainovel.domain.model.HistoryEntry
import vn.esstar.ainovel.domain.model.MlKitStatus
import vn.esstar.ainovel.domain.model.SourceInfo
import vn.esstar.ainovel.domain.model.TranslatableLanguages
import vn.esstar.ainovel.domain.model.TranslateMode
import vn.esstar.ainovel.domain.repository.LibraryRepository
import vn.esstar.ainovel.domain.repository.SourceRepository
import vn.esstar.ainovel.domain.repository.TranslationRepository
import vn.esstar.ainovel.domain.usecase.OpenUrlResult
import vn.esstar.ainovel.domain.usecase.OpenUrlUseCase

data class TranslateUiState(
    val input: String = "",
    val isOpening: Boolean = false,
    val sources: List<SourceInfo> = emptyList(),
    val translated: List<HistoryEntry> = emptyList(),
    val dictionaries: List<DictionaryInfo> = emptyList(),
    val importing: DictionaryType? = null,
    val mode: TranslateMode = TranslateMode.CONVERT,
    val mlKit: MlKitStatus = MlKitStatus(),
)

sealed interface TranslateEvent {
    data class OpenBook(val bookId: Long) : TranslateEvent
    data class OpenReader(val bookId: Long, val chapterId: Long) : TranslateEvent
    data class OpenBrowser(val url: String) : TranslateEvent
    data class ShowMessage(val message: TranslateMessage) : TranslateEvent
}

sealed interface TranslateMessage {
    data object EmptyInput : TranslateMessage
    data object ClipboardEmpty : TranslateMessage
    data object Unsupported : TranslateMessage
    data object OpenFailed : TranslateMessage
    data class Imported(val info: DictionaryInfo) : TranslateMessage
    data object ImportFailed : TranslateMessage
    data object Removed : TranslateMessage
    data object ModelDownloaded : TranslateMessage
    data class ModelFailed(val reason: String?) : TranslateMessage
    data object ModelDeleted : TranslateMessage
}

@HiltViewModel
class TranslateViewModel @Inject constructor(
    private val sources: SourceRepository,
    private val library: LibraryRepository,
    private val translation: TranslationRepository,
    private val openUrl: OpenUrlUseCase,
) : ViewModel() {

    private val _state = MutableStateFlow(TranslateUiState())
    val state: StateFlow<TranslateUiState> = _state

    private val _events = Channel<TranslateEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    init {
        viewModelScope.launch {
            runCatching { sources.sources() }
                .onSuccess { list -> _state.update { s -> s.copy(sources = list.filter { TranslatableLanguages.needsTranslation(it.language) }) } }
                .onFailure { Timber.e(it, "Cannot load sources") }
        }
        viewModelScope.launch {
            library.observeHistory().collect { entries ->
                _state.update { s -> s.copy(translated = entries.filter { TranslatableLanguages.needsTranslation(it.book.language) }) }
            }
        }
        viewModelScope.launch { translation.dictionaries.collect { d -> _state.update { it.copy(dictionaries = d) } } }
        viewModelScope.launch { translation.mode.collect { m -> _state.update { it.copy(mode = m) } } }
        viewModelScope.launch { translation.mlKitStatus.collect { st -> _state.update { it.copy(mlKit = st) } } }
        viewModelScope.launch { runCatching { translation.refreshMlKitStatus() }.onFailure { Timber.w(it, "ML Kit status") } }
        viewModelScope.launch {
            // Touch the engine once so the built-in Hán Việt dictionary is indexed and listed.
            runCatching { translation.translate(listOf("。"), null, TranslateMode.HAN_VIET) }
                .onFailure { Timber.e(it, "Dictionary warm-up failed") }
        }
    }

    fun onInputChange(value: String) = _state.update { it.copy(input = value) }

    fun submit(text: String = _state.value.input) {
        val url = text.trim()
        if (url.isEmpty()) {
            send(TranslateEvent.ShowMessage(TranslateMessage.EmptyInput))
            return
        }
        if (_state.value.isOpening) return
        viewModelScope.launch {
            _state.update { it.copy(isOpening = true) }
            runCatching { openUrl(url) }
                .onSuccess { result ->
                    when (result) {
                        is OpenUrlResult.BookOpened -> send(TranslateEvent.OpenBook(result.bookId))
                        is OpenUrlResult.ChapterOpened -> send(TranslateEvent.OpenReader(result.bookId, result.chapterId))
                        is OpenUrlResult.Unsupported -> {
                            send(TranslateEvent.ShowMessage(TranslateMessage.Unsupported))
                            send(TranslateEvent.OpenBrowser(result.url))
                        }
                    }
                }
                .onFailure {
                    Timber.e(it, "Open url failed: %s", url)
                    send(TranslateEvent.ShowMessage(TranslateMessage.OpenFailed))
                }
            _state.update { it.copy(isOpening = false) }
        }
    }

    fun onClipboardEmpty() = send(TranslateEvent.ShowMessage(TranslateMessage.ClipboardEmpty))

    fun openSite(source: SourceInfo) = send(TranslateEvent.OpenBrowser(source.baseUrl))

    fun openEntry(entry: HistoryEntry) {
        val chapter = entry.lastChapter
        if (chapter != null) send(TranslateEvent.OpenReader(entry.book.id, chapter.id)) else send(TranslateEvent.OpenBook(entry.book.id))
    }

    fun setMode(mode: TranslateMode) {
        viewModelScope.launch { translation.setMode(mode) }
    }

    fun importDictionary(uri: String, type: DictionaryType) {
        if (_state.value.importing != null) return
        viewModelScope.launch {
            _state.update { it.copy(importing = type) }
            runCatching { translation.importDictionary(uri, type, null) }
                .onSuccess { send(TranslateEvent.ShowMessage(TranslateMessage.Imported(it))) }
                .onFailure {
                    Timber.e(it, "Import dictionary %s failed", type)
                    send(TranslateEvent.ShowMessage(TranslateMessage.ImportFailed))
                }
            _state.update { it.copy(importing = null) }
        }
    }

    fun downloadModel(wifiOnly: Boolean) {
        if (_state.value.mlKit.downloading) return
        viewModelScope.launch {
            translation.downloadMlKitModel(wifiOnly)
            val st = _state.value.mlKit
            send(TranslateEvent.ShowMessage(if (st.downloaded) TranslateMessage.ModelDownloaded else TranslateMessage.ModelFailed(st.error)))
        }
    }

    fun deleteModel() {
        viewModelScope.launch {
            translation.deleteMlKitModel()
            send(TranslateEvent.ShowMessage(TranslateMessage.ModelDeleted))
        }
    }

    fun removeDictionary(type: DictionaryType) {
        viewModelScope.launch {
            runCatching { translation.removeDictionary(type) }
                .onSuccess { send(TranslateEvent.ShowMessage(TranslateMessage.Removed)) }
                .onFailure { Timber.e(it, "Remove dictionary %s failed", type) }
        }
    }

    private fun send(event: TranslateEvent) {
        viewModelScope.launch { _events.send(event) }
    }
}
