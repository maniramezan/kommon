package io.github.maniramezan.kommon.speech

/**
 * A finalized stretch of recognized speech.
 *
 * @property text The recognized text.
 * @property words The words in [text], with confidence and timing when the recognizer reported them.
 * @property alternatives Other plausible transcriptions, most likely first, excluding [text].
 */
public data class RecognizedUtterance(
    val text: String,
    val words: List<RecognizedWord>,
    val alternatives: List<String> = emptyList(),
)
