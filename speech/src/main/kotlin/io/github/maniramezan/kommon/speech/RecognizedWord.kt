package io.github.maniramezan.kommon.speech

/**
 * One word in a recognized utterance, with the recognizer's confidence and timing when reported.
 *
 * @property text The word as the recognizer wrote it.
 * @property confidence The recognizer's confidence from `0` to `1`, or `null` when not reported.
 * @property startMillis When the word starts, in milliseconds from the start of the recording, if
 *   reported.
 * @property durationMillis How long the word lasts, in milliseconds, if reported.
 */
public data class RecognizedWord(
    val text: String,
    val confidence: Float? = null,
    val startMillis: Long? = null,
    val durationMillis: Long? = null,
) {
    /** The word as input for [TranscriptAlignment.align]. */
    public val transcriptToken: TranscriptToken
        get() = TranscriptToken(text, confidence)
}
