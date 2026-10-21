package vn.esstar.ainovel.feature.browser

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import timber.log.Timber
import vn.esstar.ainovel.domain.model.UrlKind
import vn.esstar.ainovel.domain.repository.SourceRepository
import vn.esstar.ainovel.domain.usecase.OpenUrlResult
import vn.esstar.ainovel.domain.usecase.OpenUrlUseCase
import vn.esstar.ainovel.domain.playback.PlaybackController

data class BrowserUiState(
    val initialUrl: String,
    val currentUrl: String,
    val sourceName: String? = null,
    val urlKind: UrlKind = UrlKind.UNKNOWN,
    val isOpening: Boolean = false,
)

sealed interface BrowserEvent {
    data class OpenBook(val bookId: Long) : BrowserEvent
    data class OpenReader(val bookId: Long, val chapterId: Long) : BrowserEvent
    data object OpenPlayer : BrowserEvent
    data object Unsupported : BrowserEvent
    data object Failed : BrowserEvent
}

@HiltViewModel
class BrowserViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val sources: SourceRepository,
    private val openUrl: OpenUrlUseCase,
    private val playback: PlaybackController,
) : ViewModel() {

    private val route = savedStateHandle.toRoute<BrowserRoute>()

    private val _state = MutableStateFlow(BrowserUiState(initialUrl = route.url, currentUrl = route.url))
    val state: StateFlow<BrowserUiState> = _state

    private val _events = Channel<BrowserEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    init {
        onUrlChanged(route.url)
    }

    fun onUrlChanged(url: String) {
        _state.update { it.copy(currentUrl = url) }
        viewModelScope.launch {
            val source = runCatching { sources.sourceFor(url) }.getOrNull()
            val kind = if (source != null) runCatching { sources.classifyUrl(url) }.getOrDefault(UrlKind.UNKNOWN) else UrlKind.UNKNOWN
            _state.update { if (it.currentUrl == url) it.copy(sourceName = source?.name, urlKind = kind) else it }
        }
    }

    /** "Đọc chương này" / "Mở truyện này": import the current page through the rule engine. */
    fun openCurrent(listen: Boolean = false) {
        val url = _state.value.currentUrl
        if (_state.value.isOpening) return
        viewModelScope.launch {
            _state.update { it.copy(isOpening = true) }
            runCatching { openUrl(url) }
                .onSuccess { result ->
                    _events.send(
                        when (result) {
                            is OpenUrlResult.BookOpened -> BrowserEvent.OpenBook(result.bookId)
                            is OpenUrlResult.ChapterOpened -> if (listen) {
                                playback.play(result.bookId, result.chapterId, 0)
                                BrowserEvent.OpenPlayer
                            } else {
                                BrowserEvent.OpenReader(result.bookId, result.chapterId)
                            }
                            is OpenUrlResult.Unsupported -> BrowserEvent.Unsupported
                        },
                    )
                }
                .onFailure {
                    Timber.e(it, "Open from browser failed: %s", url)
                    _events.send(BrowserEvent.Failed)
                }
            _state.update { it.copy(isOpening = false) }
        }
    }
}
