package vn.esstar.ainovel.feature.home

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
import vn.esstar.ainovel.domain.model.SearchResult
import vn.esstar.ainovel.domain.model.SourceInfo
import vn.esstar.ainovel.domain.repository.SourceRepository
import vn.esstar.ainovel.domain.usecase.OpenUrlResult
import vn.esstar.ainovel.domain.usecase.OpenUrlUseCase

data class HomeUiState(
    val input: String = "",
    val sources: List<SourceInfo> = emptyList(),
    val results: List<SearchResult> = emptyList(),
    val isOpening: Boolean = false,
    val isSearching: Boolean = false,
    val searchedQuery: String? = null,
)

sealed interface HomeEvent {
    data class OpenBook(val bookId: Long) : HomeEvent
    data class OpenReader(val bookId: Long, val chapterId: Long) : HomeEvent
    data class OpenBrowser(val url: String) : HomeEvent
    data class ShowMessage(val message: UiMessage) : HomeEvent
}

/** Messages the UI maps to string resources. */
enum class UiMessage { UNSUPPORTED_SITE, OPEN_FAILED, SEARCH_FAILED, EMPTY_INPUT, CLIPBOARD_EMPTY }

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val sources: SourceRepository,
    private val openUrl: OpenUrlUseCase,
) : ViewModel() {

    private val _state = MutableStateFlow(HomeUiState())
    val state: StateFlow<HomeUiState> = _state

    private val _events = Channel<HomeEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    init {
        viewModelScope.launch {
            runCatching { sources.sources() }
                .onSuccess { list -> _state.update { it.copy(sources = list) } }
                .onFailure { Timber.e(it, "Cannot load sources") }
        }
    }

    fun onInputChange(value: String) = _state.update { it.copy(input = value) }

    /** Opens a URL directly, or searches when the input is not a URL. */
    fun submit(text: String = _state.value.input) {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) {
            send(HomeEvent.ShowMessage(UiMessage.EMPTY_INPUT))
            return
        }
        if (looksLikeUrl(trimmed)) open(trimmed) else search(trimmed)
    }

    fun open(url: String) {
        if (_state.value.isOpening) return
        viewModelScope.launch {
            _state.update { it.copy(isOpening = true) }
            runCatching { openUrl(url) }
                .onSuccess { result ->
                    when (result) {
                        is OpenUrlResult.BookOpened -> send(HomeEvent.OpenBook(result.bookId))
                        is OpenUrlResult.ChapterOpened -> send(HomeEvent.OpenReader(result.bookId, result.chapterId))
                        is OpenUrlResult.Unsupported -> {
                            send(HomeEvent.ShowMessage(UiMessage.UNSUPPORTED_SITE))
                            send(HomeEvent.OpenBrowser(result.url))
                        }
                    }
                }
                .onFailure {
                    Timber.e(it, "Open url failed: %s", url)
                    send(HomeEvent.ShowMessage(UiMessage.OPEN_FAILED))
                }
            _state.update { it.copy(isOpening = false) }
        }
    }

    fun search(query: String) {
        viewModelScope.launch {
            _state.update { it.copy(isSearching = true, searchedQuery = query) }
            runCatching { sources.search(query) }
                .onSuccess { results -> _state.update { it.copy(results = results) } }
                .onFailure {
                    Timber.e(it, "Search failed: %s", query)
                    send(HomeEvent.ShowMessage(UiMessage.SEARCH_FAILED))
                }
            _state.update { it.copy(isSearching = false) }
        }
    }

    fun clearResults() = _state.update { it.copy(results = emptyList(), searchedQuery = null) }

    fun openSite(source: SourceInfo) = send(HomeEvent.OpenBrowser(source.baseUrl))

    fun onClipboardEmpty() = send(HomeEvent.ShowMessage(UiMessage.CLIPBOARD_EMPTY))

    private fun send(event: HomeEvent) {
        viewModelScope.launch { _events.send(event) }
    }

    private fun looksLikeUrl(text: String): Boolean =
        text.startsWith("http://") || text.startsWith("https://") ||
            Regex("^[\\w.-]+\\.[a-z]{2,}(/.*)?$", RegexOption.IGNORE_CASE).matches(text)
}
