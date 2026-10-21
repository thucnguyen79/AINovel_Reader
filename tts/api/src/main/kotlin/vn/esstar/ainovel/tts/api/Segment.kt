package vn.esstar.ainovel.tts.api

/**
 * One unit of speech: a sentence or a comma-separated clause of a chapter.
 * [index] is the position within the chapter; [paragraphIndex] links back to the reader paragraph and
 * [start]/[end] are the character range of [text] inside that paragraph (end exclusive), so the reader
 * can highlight exactly the sentence being spoken.
 */
data class Segment(
    val index: Int,
    val paragraphIndex: Int,
    val text: String,
    val start: Int = 0,
    val end: Int = start + text.length,
)
