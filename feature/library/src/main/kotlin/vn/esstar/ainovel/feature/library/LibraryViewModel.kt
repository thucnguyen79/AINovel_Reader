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

@HiltViewModel
class LibraryViewModel @Inject constructor(
    private val library: LibraryRepository,
    private val downloads: DownloadRepository,
) : ViewModel() {

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
