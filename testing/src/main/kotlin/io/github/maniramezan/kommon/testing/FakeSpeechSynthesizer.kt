package io.github.maniramezan.kommon.testing

import io.github.maniramezan.kommon.speech.SpeechSynthesisEvent
import io.github.maniramezan.kommon.speech.SpeechSynthesisException
import io.github.maniramezan.kommon.speech.SpeechSynthesisRequest
import io.github.maniramezan.kommon.speech.SpeechSynthesizing
import io.github.maniramezan.kommon.speech.SpeechVoice
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/**
 * Scripted [SpeechSynthesizing] for consumer tests.
 *
 * Each collected [speak] records its request, emits [script] in order, then throws [error] if set,
 * otherwise completes.
 *
 * @property script The events every request emits.
 * @property voicesToReturn The voices [voices] returns.
 * @property error An exception thrown after the script, or `null` to complete normally.
 */
public class FakeSpeechSynthesizer(
    public var script: List<SpeechSynthesisEvent> = listOf(SpeechSynthesisEvent.Started, SpeechSynthesisEvent.Finished),
    public var voicesToReturn: List<SpeechVoice> = emptyList(),
    public var error: SpeechSynthesisException? = null,
) : SpeechSynthesizing {
    /** Every request spoken, in order. */
    public val spoken: MutableList<SpeechSynthesisRequest> = mutableListOf()

    /** How many times [stop] was called. */
    public var stopCount: Int = 0
        private set

    override fun speak(request: SpeechSynthesisRequest): Flow<SpeechSynthesisEvent> =
        flow {
            spoken += request
            script.forEach { emit(it) }
            error?.let { throw it }
        }

    override fun stop() {
        stopCount++
    }

    override suspend fun voices(): List<SpeechVoice> = voicesToReturn
}
