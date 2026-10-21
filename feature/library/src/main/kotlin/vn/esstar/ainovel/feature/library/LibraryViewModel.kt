package vn.esstar.ainovel.feature.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import vn.esstar.ainovel.domain.model.HistoryEntry
import vn.esstar.ainovel.domain.repository.LibraryRepository
import vn.esstar.ainovel.domain.model.BookDownloadInfo
import vn.esstar.ainovel.domain.repository.DownloadRepository
import vn.esstar.ainovel.domain.model.EbookInfo
import vn.esstar.ainovel.domain.repository.EbookRepository
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import timber.log.Timber

sealed interface LibraryEvent {
    data class OpenBook(val bookId: Long) : LibraryEvent
    data class ImportFailed(val reason: String?) : LibraryEvent
}

@HiltViewModel
class LibraryViewModel @Inject constructor(
    private val library: LibraryRepository,
    private val downloads: DownloadRepository,
    private val ebooks: EbookRepository,
) : ViewModel() {

    val ebookList: StateFlow<List<EbookInfo>> = ebooks.observeEbooks()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _importing = MutableStateFlow(false)
    val importing: StateFlow<Boolean> = _importing

    private val _events = Channel<LibraryEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    fun importEbook(uri: String) = runImport { ebooks.importFromUri(uri) }

    fun importEbookFromUrl(url: String) {
        if (url.isBlank()) return
        runImport { ebooks.importFromUrl(url.trim()) }
    }

    fun deleteEbook(bookId: Long) {
        viewModelScope.launch { runCatching { ebooks.delete(bookId) }.onFailure { Timber.e(it, "Delete ebook failed") } }
    }

    private fun runImport(block: suspend () -> Long) {
        if (_importing.value) return
        viewModelScope.launch {
            _importing.value = true
            runCatching { block() }
                .onSuccess { _events.send(LibraryEvent.OpenBook(it)) }
                .onFailure {
                    Timber.e(it, "Import ebook failed")
                    _events.send(LibraryEvent.ImportFailed(it.message))
                }
            _importing.value = false
        }
    }

    val downloadedBooks: StateFlow<List<BookDownloadInfo>> = downloads.observeDownloadedBooks()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun cancelDownload(jobId: Long) {
        viewModelScope.launch { downloads.cancel(jobId) }
    }

    fun deleteDownloaded(bookId: Long) {
        viewModelScope.launch { downloads.deleteBookContent(bookId) }
    }


    val history: StateFlow<List<HistoryEntry>> = library.observeHistory()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun removeFromHistory(bookId: Long) {
        viewModelScope.launch { library.removeFromHistory(bookId) }
    }
}
