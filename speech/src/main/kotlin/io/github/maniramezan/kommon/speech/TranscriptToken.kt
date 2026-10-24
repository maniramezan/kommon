package io.github.maniramezan.kommon.speech

/**
 * A word heard by a speech recognizer, with the recognizer's confidence in it when known.
 *
 * This is the input side of [TranscriptAlignment]. Build it from any recognizer's output;
 * [RecognizedWord] converts to it with [RecognizedWord.transcriptToken].
 *
 * @property text The word as the recognizer wrote it. A token holding several words is split
 *   during alignment, and every piece keeps this token's [confidence].
 * @property confidence The recognizer's confidence from `0` to `1`, or `null` when unknown.
 */
public data class TranscriptToken(
    val text: String,
    val confidence: Float? = null,
)
