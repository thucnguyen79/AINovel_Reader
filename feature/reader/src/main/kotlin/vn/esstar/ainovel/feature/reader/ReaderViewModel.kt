package vn.esstar.ainovel.feature.reader

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import timber.log.Timber
import vn.esstar.ainovel.core.datastore.ReaderPreferences
import vn.esstar.ainovel.core.datastore.ReaderPrefs
import vn.esstar.ainovel.domain.model.Book
import vn.esstar.ainovel.domain.model.Chapter
import vn.esstar.ainovel.domain.model.ChapterContent
import vn.esstar.ainovel.domain.model.ReadingMode
import vn.esstar.ainovel.domain.model.ReadingPosition
import vn.esstar.ainovel.domain.model.TranslatableLanguages
import vn.esstar.ainovel.domain.model.TranslateMode
import vn.esstar.ainovel.domain.repository.TranslationRepository
import vn.esstar.ainovel.domain.usecase.GetReadableChapterUseCase
import vn.esstar.ainovel.domain.repository.LibraryRepository
import vn.esstar.ainovel.domain.playback.PlaybackController

data class ReaderUiState(
    val book: Book? = null,
    val chapters: List<Chapter> = emptyList(),
    val currentChapter: Chapter? = null,
    val content: ChapterContent? = null,
    val isLoading: Boolean = true,
    val error: Boolean = false,
    val prefs: ReaderPrefs = ReaderPrefs(),
    /** Null when the book is not in a translatable language. */
    val translateMode: TranslateMode? = null,
    /** Paragraph index + pixel offset to restore when [content] changes. */
    val restoreIndex: Int = 0,
    val restoreOffset: Int = 0,
) {
    val hasPrev: Boolean get() = currentChapter?.let { it.index > 0 } == true
    val hasNext: Boolean get() = currentChapter?.let { c -> chapters.any { it.index == c.index + 1 } } == true
}

@HiltViewModel
class ReaderViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val library: LibraryRepository,
    private val readerPreferences: ReaderPreferences,
    private val playback: PlaybackController,
    private val translation: TranslationRepository,
    private val readableChapter: GetReadableChapterUseCase,
) : ViewModel() {

    private val route = savedStateHandle.toRoute<ReaderRoute>()
    val bookId: Long = route.bookId

    private val _state = MutableStateFlow(ReaderUiState())
    val state: StateFlow<ReaderUiState> = _state

    private var loadJob: Job? = null
    private var lastSavedIndex = -1
    private var lastSavedOffset = -1

    init {
        viewModelScope.launch {
            readerPreferences.prefs.collect { p -> _state.update { it.copy(prefs = p) } }
        }
        viewModelScope.launch {
            val book = library.getBook(bookId)
            val chapters = library.getChapters(bookId)
            val mode = if (book != null && TranslatableLanguages.needsTranslation(book.language)) translation.mode.first() else null
            _state.update { it.copy(book = book, chapters = chapters, translateMode = mode) }
            val progress = library.getProgress(bookId)
            val restore = progress?.takeIf { it.chapterId == route.chapterId }
            open(route.chapterId, restore?.sentenceIndex ?: 0, restore?.scrollOffset ?: 0)
        }
    }

    fun open(chapterId: Long, restoreIndex: Int = 0, restoreOffset: Int = 0) {
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            val chapter = _state.value.chapters.firstOrNull { it.id == chapterId } ?: library.getChapter(chapterId)
            _state.update {
                it.copy(currentChapter = chapter, content = null, isLoading = true, error = false, restoreIndex = restoreIndex, restoreOffset = restoreOffset)
            }
            if (chapter == null) {
                _state.update { it.copy(isLoading = false, error = true) }
                return@launch
            }
            runCatching { readableChapter(chapterId, _state.value.translateMode) }
                .onSuccess { content ->
                    _state.update { it.copy(content = content, isLoading = false) }
                    lastSavedIndex = -1
                    library.touchHistory(bookId, chapterId, ReadingMode.READ)
                    library.saveProgress(ReadingPosition(bookId, chapterId, restoreIndex, restoreOffset, 0f))
                    refreshChapterFlags()
                }
                .onFailure {
                    Timber.e(it, "Load chapter %d failed", chapterId)
                    _state.update { s -> s.copy(isLoading = false, error = true) }
                }
        }
    }

    fun retry() {
        _state.value.currentChapter?.let { open(it.id, _state.value.restoreIndex, _state.value.restoreOffset) }
    }

    fun next() {
        val current = _state.value.currentChapter ?: return
        _state.value.chapters.firstOrNull { it.index == current.index + 1 }?.let { open(it.id) }
    }

    fun prev() {
        val current = _state.value.currentChapter ?: return
        _state.value.chapters.firstOrNull { it.index == current.index - 1 }?.let { open(it.id) }
    }

    /** Called (debounced) by the UI while the user scrolls. */
    fun onScroll(firstVisibleIndex: Int, offset: Int) {
        val s = _state.value
        val chapter = s.currentChapter ?: return
        val content = s.content ?: return
        if (firstVisibleIndex == lastSavedIndex && offset == lastSavedOffset) return
        lastSavedIndex = firstVisibleIndex
        lastSavedOffset = offset
        val percent = if (content.paragraphs.isEmpty()) 0f else firstVisibleIndex.toFloat() / content.paragraphs.size
        viewModelScope.launch {
            library.saveProgress(ReadingPosition(bookId, chapter.id, firstVisibleIndex, offset, percent))
        }
    }

    /** Starts reading aloud from the paragraph currently at the top of the screen. */
    fun listen(firstVisibleIndex: Int) {
        val chapter = _state.value.currentChapter ?: return
        // Item 0 of the list is the title row, so paragraph index = list index - 1.
        playback.play(bookId, chapter.id, (firstVisibleIndex - 1).coerceAtLeast(0))
    }

    /** Switches Convert / Hán Việt / Nguyên văn for Chinese books and reloads the current chapter. */
    fun setTranslateMode(mode: TranslateMode) {
        if (_state.value.translateMode == null || _state.value.translateMode == mode) return
        _state.update { it.copy(translateMode = mode) }
        viewModelScope.launch { translation.setMode(mode) }
        _state.value.currentChapter?.let { open(it.id, lastSavedIndex.coerceAtLeast(0), lastSavedOffset.coerceAtLeast(0)) }
    }

    fun setFontSize(sp: Int) {
        viewModelScope.launch { readerPreferences.setFontSize(sp) }
    }

    fun setLineHeight(value: Float) {
        viewModelScope.launch { readerPreferences.setLineHeight(value) }
    }

    private suspend fun refreshChapterFlags() {
        val chapters = library.getChapters(bookId)
        _state.update { it.copy(chapters = chapters) }
    }
}
