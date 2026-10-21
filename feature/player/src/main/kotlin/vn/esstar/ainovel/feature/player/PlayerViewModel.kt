package vn.esstar.ainovel.feature.player

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import vn.esstar.ainovel.domain.model.Book
import vn.esstar.ainovel.domain.model.Chapter
import vn.esstar.ainovel.domain.playback.PlaybackController
import vn.esstar.ainovel.domain.playback.PlaybackState
import vn.esstar.ainovel.domain.repository.LibraryRepository

/** Shared by the mini-player and the full player screen. */
@HiltViewModel
class PlayerViewModel @Inject constructor(
    private val playback: PlaybackController,
    library: LibraryRepository,
) : ViewModel() {

    val state: StateFlow<PlaybackState> = playback.state

    private val bookId = playback.state.map { it.bookId }.distinctUntilChanged()

    val book: StateFlow<Book?> = bookId
        .flatMapLatest { id -> if (id == null) flowOf(null) else library.observeBook(id) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val chapters: StateFlow<List<Chapter>> = bookId
        .flatMapLatest { id -> if (id == null) flowOf(emptyList()) else library.observeChapters(id) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun togglePlayPause() = playback.togglePlayPause()
    fun nextSegment() = playback.nextSegment()
    fun previousSegment() = playback.previousSegment()
    fun nextChapter() = playback.nextChapter()
    fun previousChapter() = playback.previousChapter()
    fun seekToSegment(index: Int) = playback.seekToSegment(index)
    fun setSpeed(speed: Float) = playback.setSpeed(speed)
    fun setSleepTimer(minutes: Int) = playback.setSleepTimer(minutes)
    fun stop() = playback.stop()

    fun playChapter(chapter: Chapter) {
        val bookId = state.value.bookId ?: chapter.bookId
        playback.play(bookId, chapter.id, 0)
    }
}
