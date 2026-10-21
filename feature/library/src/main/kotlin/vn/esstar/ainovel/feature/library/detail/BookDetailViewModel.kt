package vn.esstar.ainovel.feature.library.detail

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import timber.log.Timber
import vn.esstar.ainovel.domain.model.Book
import vn.esstar.ainovel.domain.model.Chapter
import vn.esstar.ainovel.domain.model.ReadingPosition
import vn.esstar.ainovel.domain.repository.LibraryRepository
import vn.esstar.ainovel.domain.repository.SourceRepository
import vn.esstar.ainovel.feature.library.BookDetailRoute

data class BookDetailUiState(
    val book: Book? = null,
    val chapters: List<Chapter> = emptyList(),
    val progress: ReadingPosition? = null,
    val isRefreshing: Boolean = false,
    val refreshFailed: Boolean = false,
) {
    val continueChapter: Chapter? get() = progress?.let { p -> chapters.firstOrNull { it.id == p.chapterId } }
}

@HiltViewModel
class BookDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val library: LibraryRepository,
    private val sources: SourceRepository,
) : ViewModel() {

    val bookId: Long = savedStateHandle.toRoute<BookDetailRoute>().bookId

    private val transient = MutableStateFlow(BookDetailUiState())
    private val progress = MutableStateFlow<ReadingPosition?>(null)

    val state: StateFlow<BookDetailUiState> = combine(
        library.observeBook(bookId),
        library.observeChapters(bookId),
        progress,
        transient,
    ) { book, chapters, progress, t ->
        t.copy(book = book, chapters = chapters, progress = progress)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), BookDetailUiState())

    init {
        reloadProgress()
    }

    fun reloadProgress() {
        viewModelScope.launch { progress.value = library.getProgress(bookId) }
    }

    /** Re-fetches the book page to pick up new chapters. */
    fun refresh() {
        val url = state.value.book?.url ?: return
        viewModelScope.launch {
            transient.update { it.copy(isRefreshing = true, refreshFailed = false) }
            runCatching { library.upsertBook(sources.fetchBook(url)) }
                .onFailure { Timber.e(it, "Refresh failed for %s", url) }
                .also { r -> transient.update { it.copy(isRefreshing = false, refreshFailed = r.isFailure) } }
        }
    }

    fun toggleFavorite() {
        val book = state.value.book ?: return
        viewModelScope.launch { library.setFavorite(book.id, !book.isFavorite) }
    }
}
