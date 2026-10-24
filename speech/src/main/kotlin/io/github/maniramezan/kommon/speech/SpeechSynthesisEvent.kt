package io.github.maniramezan.kommon.speech

/** A progress update while text is being spoken. */
public sealed interface SpeechSynthesisEvent {
    /** The engine started speaking. */
    public data object Started : SpeechSynthesisEvent

    /**
     * The engine is about to speak the range [start] until [end] of the request text.
     *
     * Not every engine reports ranges.
     *
     * @property start The inclusive start index into the request text.
     * @property end The exclusive end index into the request text.
     */
    public data class WillSpeak(
        val start: Int,
        val end: Int,
    ) : SpeechSynthesisEvent

    /** The engine finished speaking. */
    public data object Finished : SpeechSynthesisEvent

    /** Speaking was interrupted, by [SpeechSynthesizing.stop] or by a newer request. */
    public data object Cancelled : SpeechSynthesisEvent
}
