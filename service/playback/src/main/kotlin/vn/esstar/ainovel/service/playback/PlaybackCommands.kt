package vn.esstar.ainovel.service.playback

/** Custom session commands and bundle keys shared by the service and the controller. */
object PlaybackCommands {
    const val PLAY_CHAPTER = "vn.esstar.ainovel.PLAY_CHAPTER"
    const val NEXT_CHAPTER = "vn.esstar.ainovel.NEXT_CHAPTER"
    const val PREV_CHAPTER = "vn.esstar.ainovel.PREV_CHAPTER"
    const val SEEK_SEGMENT = "vn.esstar.ainovel.SEEK_SEGMENT"
    const val SET_SLEEP_TIMER = "vn.esstar.ainovel.SET_SLEEP_TIMER"
    const val STOP = "vn.esstar.ainovel.STOP"

    const val ARG_BOOK_ID = "bookId"
    const val ARG_CHAPTER_ID = "chapterId"
    const val ARG_PARAGRAPH_INDEX = "paragraphIndex"
    const val ARG_SEGMENT_INDEX = "segmentIndex"
    const val ARG_MINUTES = "minutes"

    // MediaItem metadata extras
    const val EXTRA_BOOK_ID = "bookId"
    const val EXTRA_CHAPTER_ID = "chapterId"
    const val EXTRA_CHAPTER_INDEX = "chapterIndex"
    const val EXTRA_CHAPTER_COUNT = "chapterCount"
    const val EXTRA_SEGMENT_INDEX = "segmentIndex"
    const val EXTRA_SEGMENT_COUNT = "segmentCount"
    const val EXTRA_PARAGRAPH_INDEX = "paragraphIndex"

    // Session extras
    const val SESSION_ERROR = "error"
    const val SESSION_BUFFERING = "buffering"
    const val SESSION_SLEEP_ENDS_AT = "sleepEndsAt"

    fun mediaId(chapterId: Long, segmentIndex: Int): String = "c$chapterId:s$segmentIndex"
}
