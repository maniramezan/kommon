package io.github.maniramezan.kommon.speech

/** An update from a speech recognition run. */
public sealed interface SpeechRecognitionEvent {
    /**
     * The recognizer is listening and the speaker can start talking.
     *
     * @property recognizer Which recognizer this run actually uses.
     */
    public data class Ready(
        val recognizer: RecognizerKind,
    ) : SpeechRecognitionEvent

    /**
     * The current input loudness.
     *
     * @property level The loudness from `0` (silence) to `1` (loud).
     */
    public data class AudioLevel(
        val level: Float,
    ) : SpeechRecognitionEvent

    /**
     * The transcript so far.
     *
     * @property transcript Finalized utterances plus the current volatile preview.
     */
    public data class Transcript(
        val transcript: SpeechTranscript,
    ) : SpeechRecognitionEvent

    /** The speaker stopped talking. A final [Transcript] may still follow. */
    public data object SilenceDetected : SpeechRecognitionEvent
}
