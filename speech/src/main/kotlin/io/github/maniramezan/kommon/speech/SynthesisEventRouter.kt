package io.github.maniramezan.kommon.speech

import java.util.concurrent.ConcurrentHashMap

/** Where a [SynthesisEventRouter] delivers the events for one utterance. */
internal interface SynthesisSink {
    /** Delivers an [event]. */
    fun emit(event: SpeechSynthesisEvent)

    /** Ends the flow normally. */
    fun complete()

    /** Ends the flow with [error]. */
    fun fail(error: SpeechSynthesisException)
}

/**
 * Routes `UtteranceProgressListener` callbacks to the flow of the utterance they belong to.
 *
 * Callbacks for unknown utterance ids (already finished or never registered) are ignored. Terminal
 * callbacks remove the utterance so a late duplicate cannot fire twice.
 */
internal class SynthesisEventRouter {
    private val sinks = ConcurrentHashMap<String, SynthesisSink>()

    /** Starts routing events for [utteranceId] to [sink]. */
    fun register(
        utteranceId: String,
        sink: SynthesisSink,
    ) {
        sinks[utteranceId] = sink
    }

    /** Stops routing [utteranceId]. Returns whether it was still registered. */
    fun unregister(utteranceId: String): Boolean = sinks.remove(utteranceId) != null

    fun onStart(utteranceId: String) {
        sinks[utteranceId]?.emit(SpeechSynthesisEvent.Started)
    }

    fun onRange(
        utteranceId: String,
        start: Int,
        end: Int,
    ) {
        sinks[utteranceId]?.emit(SpeechSynthesisEvent.WillSpeak(start, end))
    }

    fun onDone(utteranceId: String) {
        val sink = sinks.remove(utteranceId) ?: return
        sink.emit(SpeechSynthesisEvent.Finished)
        sink.complete()
    }

    fun onStop(utteranceId: String) {
        val sink = sinks.remove(utteranceId) ?: return
        sink.emit(SpeechSynthesisEvent.Cancelled)
        sink.complete()
    }

    fun onError(
        utteranceId: String,
        errorCode: Int?,
    ) {
        val sink = sinks.remove(utteranceId) ?: return
        sink.fail(SpeechSynthesisException("Speech synthesis failed", errorCode))
    }
}
