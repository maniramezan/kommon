package io.github.maniramezan.kommon.speech

import kotlinx.coroutines.flow.Flow

/** Something that turns microphone speech into a live transcript. */
public interface SpeechRecognizing {
    /**
     * Starts one recognition run.
     *
     * The returned flow is cold: collecting it starts listening, and it completes after the final
     * result. It throws [SpeechRecognitionException] on error. Cancelling the collection cancels
     * recognition.
     *
     * @param configuration The settings for this run.
     */
    public fun recognize(configuration: SpeechRecognitionConfiguration): Flow<SpeechRecognitionEvent>

    /** Asks the active run to stop listening and deliver its final result. No-op when idle. */
    public fun stop()
}
