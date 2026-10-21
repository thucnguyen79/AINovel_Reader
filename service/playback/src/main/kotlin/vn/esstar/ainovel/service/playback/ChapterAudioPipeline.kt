package vn.esstar.ainovel.service.playback

import android.net.Uri
import android.os.Bundle
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber
import vn.esstar.ainovel.core.datastore.TtsPrefs
import vn.esstar.ainovel.domain.model.Book
import vn.esstar.ainovel.domain.model.Chapter
import vn.esstar.ainovel.domain.model.ChapterContent
import vn.esstar.ainovel.domain.model.ReadingMode
import vn.esstar.ainovel.domain.model.ReadingPosition
import vn.esstar.ainovel.domain.playback.PlaybackError
import vn.esstar.ainovel.domain.repository.LibraryRepository
import vn.esstar.ainovel.tts.api.AudioCache
import vn.esstar.ainovel.tts.api.Segment
import vn.esstar.ainovel.tts.api.SynthesisResult
import vn.esstar.ainovel.tts.api.TextNormalizer
import vn.esstar.ainovel.tts.api.TextSegmenter
import vn.esstar.ainovel.tts.api.TtsEngine
import vn.esstar.ainovel.tts.api.VoiceParams

/** Side information the service publishes through session extras. */
data class PipelineStatus(
    val isBuffering: Boolean = false,
    val error: PlaybackError? = null,
)

/**
 * Feeds the ExoPlayer playlist with synthesized segments: loads a chapter, normalizes and splits it,
 * synthesizes a few segments ahead of the playhead (one MediaItem per segment) and continues with the
 * next chapter when the current one is exhausted. All player calls happen on the main thread.
 */
class ChapterAudioPipeline(
    private val scope: CoroutineScope,
    private val player: Player,
    private val library: LibraryRepository,
    /** Chapter text as the user reads it (converted for Chinese books). */
    private val contentProvider: suspend (chapterId: Long) -> ChapterContent,
    private val engine: TtsEngine,
    private val cache: AudioCache,
    private val io: CoroutineDispatcher,
    private val prefsProvider: suspend () -> TtsPrefs,
) {
    private val normalizer = TextNormalizer()

    private val _status = MutableStateFlow(PipelineStatus())
    val status: StateFlow<PipelineStatus> = _status

    private var producer: Job? = null
    private var book: Book? = null
    private var chapters: List<Chapter> = emptyList()

    /** Segments of the chapter currently being produced (kept so seeking ahead can restart quickly). */
    private var currentChapterId: Long? = null
    private var currentSegments: List<Segment> = emptyList()

    val currentBookId: Long? get() = book?.id

    fun start(bookId: Long, chapterId: Long, paragraphIndex: Int = 0, segmentIndex: Int? = null) {
        producer?.cancel()
        producer = scope.launch {
            _status.update { PipelineStatus() }
            withContext(Dispatchers.Main) { player.stop(); player.clearMediaItems() }
            if (!engine.initialize()) {
                _status.update { it.copy(error = PlaybackError.TTS_UNAVAILABLE) }
                return@launch
            }
            val loadedBook = library.getBook(bookId) ?: run {
                _status.update { it.copy(error = PlaybackError.CHAPTER_LOAD_FAILED) }
                return@launch
            }
            book = loadedBook
            chapters = library.getChapters(bookId)
            var chapter = chapters.firstOrNull { it.id == chapterId } ?: run {
                _status.update { it.copy(error = PlaybackError.CHAPTER_LOAD_FAILED) }
                return@launch
            }
            var startParagraph = paragraphIndex
            var startSegment = segmentIndex
            var first = true
            // Produce chapter after chapter until cancelled or the book ends.
            while (isActive) {
                val produced = produceChapter(loadedBook, chapter, startParagraph, startSegment, autoPlay = first)
                if (!produced) return@launch
                first = false
                startParagraph = 0
                startSegment = null
                chapter = chapters.firstOrNull { it.index == chapter.index + 1 } ?: break
            }
        }
    }

    /** Returns false when the chapter could not be produced (error already published). */
    private suspend fun produceChapter(book: Book, chapter: Chapter, paragraphIndex: Int, segmentIndex: Int?, autoPlay: Boolean): Boolean {
        val prefs = prefsProvider()
        val content = runCatching { contentProvider(chapter.id) }
            .onFailure { Timber.e(it, "Cannot load chapter %d", chapter.id) }
            .getOrNull() ?: run {
            _status.update { it.copy(error = PlaybackError.CHAPTER_LOAD_FAILED) }
            return false
        }
        val paragraphs = normalizer.normalizeAll(content.paragraphs)
        val segments = TextSegmenter(breakAtComma = prefs.breakAtComma).segment(paragraphs)
        currentChapterId = chapter.id
        currentSegments = segments
        if (segments.isEmpty()) {
            Timber.w("Chapter %d has no speakable text", chapter.id)
            return true
        }
        val start = segmentIndex?.coerceIn(0, segments.lastIndex)
            ?: segments.indexOfFirst { it.paragraphIndex >= paragraphIndex }.takeIf { it >= 0 } ?: 0

        library.touchHistory(book.id, chapter.id, ReadingMode.LISTEN)
        val params = VoiceParams(voiceId = prefs.voiceId, pitch = prefs.pitch)
        var consecutiveFailures = 0
        var isFirstItem = autoPlay

        for (i in start until segments.size) {
            if (!scope.isActive) return false
            waitForRoom(prefs.prefetchSegments)
            val segment = segments[i]
            val key = cache.key(engine.id, params, segment.text)
            val file = cache.existing(key) ?: withContext(io) {
                when (val r = engine.synthesize(segment.text, params, cache.fileFor(key))) {
                    is SynthesisResult.Success -> r.file.also { cache.evictIfNeeded() }
                    is SynthesisResult.Failure -> {
                        Timber.w("Synthesis failed (%s) for segment %d", r.reason, i)
                        null
                    }
                }
            }
            if (file == null) {
                consecutiveFailures++
                if (consecutiveFailures >= MAX_CONSECUTIVE_FAILURES) {
                    _status.update { it.copy(error = PlaybackError.SYNTHESIS_FAILED, isBuffering = false) }
                    return false
                }
                continue
            }
            consecutiveFailures = 0
            val item = mediaItem(book, chapter, content.title.ifBlank { chapter.title }, segment, segments.size, file.toURI().toString())
            withContext(Dispatchers.Main) {
                player.addMediaItem(item)
                if (isFirstItem) {
                    player.prepare()
                    player.play()
                    isFirstItem = false
                } else if (player.playbackState == Player.STATE_ENDED || player.playbackState == Player.STATE_IDLE) {
                    // Playhead caught up with the producer; resume from the newly added item.
                    player.seekTo(player.mediaItemCount - 1, 0)
                    player.prepare()
                    player.play()
                }
                _status.update { it.copy(isBuffering = false) }
            }
        }
        return true
    }

    /** Backpressure: never synthesize more than [prefetch] segments beyond the one playing. */
    private suspend fun waitForRoom(prefetch: Int) {
        while (scope.isActive) {
            val (ahead, ended) = withContext(Dispatchers.Main) {
                (player.mediaItemCount - 1 - player.currentMediaItemIndex) to (player.playbackState == Player.STATE_ENDED)
            }
            if (ahead < prefetch || ended) {
                if (ahead <= 0) _status.update { it.copy(isBuffering = true) }
                return
            }
            delay(POLL_MS)
        }
    }

    fun nextChapter() {
        val chapter = currentChapter()?.let { c -> chapters.firstOrNull { it.index == c.index + 1 } } ?: return
        book?.let { start(it.id, chapter.id) }
    }

    fun previousChapter() {
        val chapter = currentChapter()?.let { c -> chapters.firstOrNull { it.index == c.index - 1 } } ?: return
        book?.let { start(it.id, chapter.id) }
    }

    /** Jumps to [segmentIndex] of the chapter playing now, restarting production if it is not queued yet. */
    fun seekToSegment(segmentIndex: Int) {
        val chapterId = playingChapterId() ?: currentChapterId ?: return
        val targetId = PlaybackCommands.mediaId(chapterId, segmentIndex)
        for (i in 0 until player.mediaItemCount) {
            if (player.getMediaItemAt(i).mediaId == targetId) {
                player.seekTo(i, 0)
                player.play()
                return
            }
        }
        book?.let { start(it.id, chapterId, segmentIndex = segmentIndex) }
    }

    /** Called by the service on every media item transition to persist progress. */
    fun onItemStarted(item: MediaItem?) {
        val extras = item?.mediaMetadata?.extras ?: return
        val bookId = extras.getLong(PlaybackCommands.EXTRA_BOOK_ID, -1)
        val chapterId = extras.getLong(PlaybackCommands.EXTRA_CHAPTER_ID, -1)
        if (bookId < 0 || chapterId < 0) return
        val paragraph = extras.getInt(PlaybackCommands.EXTRA_PARAGRAPH_INDEX, 0)
        val segment = extras.getInt(PlaybackCommands.EXTRA_SEGMENT_INDEX, 0)
        val count = extras.getInt(PlaybackCommands.EXTRA_SEGMENT_COUNT, 1).coerceAtLeast(1)
        scope.launch {
            library.saveProgress(ReadingPosition(bookId, chapterId, sentenceIndex = paragraph, scrollOffset = 0, percent = segment.toFloat() / count))
            library.touchHistory(bookId, chapterId, ReadingMode.LISTEN)
        }
    }

    fun stop() {
        producer?.cancel()
        producer = null
        _status.update { PipelineStatus() }
    }

    private fun currentChapter(): Chapter? {
        val id = playingChapterId() ?: currentChapterId ?: return null
        return chapters.firstOrNull { it.id == id }
    }

    private fun playingChapterId(): Long? =
        player.currentMediaItem?.mediaMetadata?.extras?.getLong(PlaybackCommands.EXTRA_CHAPTER_ID, -1)?.takeIf { it >= 0 }

    private fun mediaItem(book: Book, chapter: Chapter, title: String, segment: Segment, segmentCount: Int, uri: String): MediaItem {
        val extras = Bundle().apply {
            putLong(PlaybackCommands.EXTRA_BOOK_ID, book.id)
            putLong(PlaybackCommands.EXTRA_CHAPTER_ID, chapter.id)
            putInt(PlaybackCommands.EXTRA_CHAPTER_INDEX, chapter.index)
            putInt(PlaybackCommands.EXTRA_CHAPTER_COUNT, chapters.size)
            putInt(PlaybackCommands.EXTRA_SEGMENT_INDEX, segment.index)
            putInt(PlaybackCommands.EXTRA_SEGMENT_COUNT, segmentCount)
            putInt(PlaybackCommands.EXTRA_PARAGRAPH_INDEX, segment.paragraphIndex)
        }
        val metadata = MediaMetadata.Builder()
            .setTitle(title)
            .setArtist(book.title)
            .setAlbumTitle(book.title)
            .setArtworkUri(book.coverUrl?.let(Uri::parse))
            .setExtras(extras)
            .build()
        return MediaItem.Builder()
            .setMediaId(PlaybackCommands.mediaId(chapter.id, segment.index))
            .setUri(uri)
            .setMediaMetadata(metadata)
            .build()
    }

    private companion object {
        const val POLL_MS = 250L
        const val MAX_CONSECUTIVE_FAILURES = 5
    }
}
