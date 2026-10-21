package vn.esstar.ainovel.tts.api

/**
 * One unit of speech: a sentence or a comma-separated clause of a chapter.
 * [index] is the position within the chapter; [paragraphIndex] links back to the reader paragraph.
 */
data class Segment(
    val index: Int,
    val paragraphIndex: Int,
    val text: String,
)
