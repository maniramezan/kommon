package io.github.maniramezan.kommon.speech

import kotlinx.coroutines.flow.Flow

/** Something that speaks text aloud. */
public interface SpeechSynthesizing {
    /**
     * Speaks [request], interrupting anything already being spoken.
     *
     * The returned flow is cold: collecting it starts speaking and it completes after
     * [SpeechSynthesisEvent.Finished] or [SpeechSynthesisEvent.Cancelled]. It throws
     * [SpeechSynthesisException] if synthesis fails. Cancelling the collection stops speech.
     */
    public fun speak(request: SpeechSynthesisRequest): Flow<SpeechSynthesisEvent>

    /** Stops speaking. No-op when idle. */
    public fun stop()

    /** Every voice the engine offers. */
    public suspend fun voices(): List<SpeechVoice>
}
