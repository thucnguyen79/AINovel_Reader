package vn.esstar.ainovel.domain.playback

import kotlinx.coroutines.flow.StateFlow

/** What the UI needs to draw the mini-player and the full player. */
data class PlaybackState(
    val bookId: Long? = null,
    val chapterId: Long? = null,
    val bookTitle: String = "",
    val chapterTitle: String = "",
    val chapterIndex: Int = 0,
    val chapterCount: Int = 0,
    val segmentIndex: Int = 0,
    val segmentCount: Int = 0,
    val paragraphIndex: Int = 0,
    /** Character range of the segment being spoken inside its paragraph (end exclusive). */
    val segmentStart: Int = 0,
    val segmentEnd: Int = 0,
    val isPlaying: Boolean = false,
    /** True while the engine is still synthesizing the segment that should play now. */
    val isBuffering: Boolean = false,
    val speed: Float = 1f,
    val sleepTimerEndsAt: Long? = null,
    val error: PlaybackError? = null,
) {
    val isActive: Boolean get() = chapterId != null
    val percent: Float get() = if (segmentCount == 0) 0f else segmentIndex.toFloat() / segmentCount
}

enum class PlaybackError { TTS_UNAVAILABLE, CHAPTER_LOAD_FAILED, SYNTHESIS_FAILED }

/** Commands the UI can send to the background player. Implemented over a Media3 MediaController. */
interface PlaybackController {
    val state: StateFlow<PlaybackState>

    /** Starts reading [chapterId] aloud from [paragraphIndex] (the reader's visible paragraph). */
    fun play(bookId: Long, chapterId: Long, paragraphIndex: Int = 0)

    fun togglePlayPause()
    fun pause()
    fun stop()

    fun nextSegment()
    fun previousSegment()
    fun nextChapter()
    fun previousChapter()
    fun seekToSegment(index: Int)

    fun setSpeed(speed: Float)

    /** Minutes from now; 0 cancels. */
    fun setSleepTimer(minutes: Int)
}
