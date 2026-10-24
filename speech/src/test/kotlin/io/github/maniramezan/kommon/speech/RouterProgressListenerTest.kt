package io.github.maniramezan.kommon.speech

import org.junit.Test
import kotlin.test.assertEquals

class RouterProgressListenerTest {
    private class RecordingSink : SynthesisSink {
        val events = mutableListOf<SpeechSynthesisEvent>()
        var failure: SpeechSynthesisException? = null

        override fun emit(event: SpeechSynthesisEvent) {
            events += event
        }

        override fun complete() = Unit

        override fun fail(error: SpeechSynthesisException) {
            failure = error
        }
    }

    private val router = SynthesisEventRouter()
    private val listener = RouterProgressListener(router)

    @Test
    fun `start range and done reach the router`() {
        val sink = RecordingSink()
        router.register("u", sink)
        listener.onStart("u")
        listener.onRangeStart("u", 2, 4, 0)
        listener.onDone("u")
        assertEquals(
            listOf(SpeechSynthesisEvent.Started, SpeechSynthesisEvent.WillSpeak(2, 4), SpeechSynthesisEvent.Finished),
            sink.events,
        )
    }

    @Test
    fun `stop reaches the router as cancelled`() {
        val sink = RecordingSink()
        router.register("u", sink)
        listener.onStop("u", true)
        assertEquals(listOf<SpeechSynthesisEvent>(SpeechSynthesisEvent.Cancelled), sink.events)
    }

    @Test
    fun `both error callbacks reach the router`() {
        val coded = RecordingSink()
        router.register("coded", coded)
        listener.onError("coded", -5)
        assertEquals(-5, coded.failure?.errorCode)

        val legacy = RecordingSink()
        router.register("legacy", legacy)
        @Suppress("DEPRECATION")
        listener.onError("legacy")
        assertEquals(null, legacy.failure?.errorCode)
    }

    @Test
    fun `callbacks without an utterance id are ignored`() {
        listener.onStart(null)
        listener.onDone(null)
        listener.onStop(null, false)
        listener.onRangeStart(null, 0, 1, 0)
        listener.onError(null, 1)
        @Suppress("DEPRECATION")
        listener.onError(null)
    }
}
