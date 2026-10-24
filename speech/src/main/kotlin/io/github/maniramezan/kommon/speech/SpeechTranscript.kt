package io.github.maniramezan.kommon.speech

/**
 * Everything recognized so far in one recognition run.
 *
 * [finalizedUtterances] won't change again. [volatileText] is the recognizer's current guess at
 * speech it hasn't finalized yet, and is replaced by each update, so show it as a preview.
 *
 * @property finalizedUtterances Utterances the recognizer has finalized, in order.
 * @property volatileText The recognizer's current guess at not-yet-final speech; empty when
 *   nothing is pending.
 */
public data class SpeechTranscript(
    val finalizedUtterances: List<RecognizedUtterance> = emptyList(),
    val volatileText: String = "",
) {
    /** The finalized text, without the volatile preview. */
    public val finalizedText: String
        get() = join(finalizedUtterances.map { it.text })

    /** The finalized text followed by the volatile preview, parts trimmed and joined by single spaces. */
    public val text: String
        get() = join(finalizedUtterances.map { it.text } + volatileText)

    /** Every finalized word, in order. */
    public val words: List<RecognizedWord>
        get() = finalizedUtterances.flatMap { it.words }

    /** Whether nothing is pending finalization. */
    public val isFinal: Boolean
        get() = volatileText.isEmpty()

    private fun join(parts: List<String>): String = parts.map { it.trim() }.filter { it.isNotEmpty() }.joinToString(" ")
}
