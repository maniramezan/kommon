package io.github.maniramezan.kommon.speech

import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SynthesisEventRouterTest {
    private class RecordingSink : SynthesisSink {
        val events = mutableListOf<SpeechSynthesisEvent>()
        var completed = 0
        var failure: SpeechSynthesisException? = null

        override fun emit(event: SpeechSynthesisEvent) {
            events += event
        }

        override fun complete() {
            completed++
        }

        override fun fail(error: SpeechSynthesisException) {
            failure = error
        }
    }

    private val router = SynthesisEventRouter()

    @Test
    fun `events reach only the sink registered for their utterance`() {
        val one = RecordingSink()
        val two = RecordingSink()
        router.register("1", one)
        router.register("2", two)

        router.onStart("1")
        router.onRange("1", 0, 5)
        router.onStart("2")

        assertEquals(listOf(SpeechSynthesisEvent.Started, SpeechSynthesisEvent.WillSpeak(0, 5)), one.events)
        assertEquals(listOf<SpeechSynthesisEvent>(SpeechSynthesisEvent.Started), two.events)
    }

    @Test
    fun `done emits finished completes and unregisters`() {
        val sink = RecordingSink()
        router.register("1", sink)
        router.onDone("1")
        router.onDone("1")
        assertEquals(listOf<SpeechSynthesisEvent>(SpeechSynthesisEvent.Finished), sink.events)
        assertEquals(1, sink.completed)
        assertFalse(router.unregister("1"))
    }

    @Test
    fun `stop emits cancelled and completes`() {
        val sink = RecordingSink()
        router.register("1", sink)
        router.onStop("1")
        assertEquals(listOf<SpeechSynthesisEvent>(SpeechSynthesisEvent.Cancelled), sink.events)
        assertEquals(1, sink.completed)
    }

    @Test
    fun `error fails with the engine code`() {
        val sink = RecordingSink()
        router.register("1", sink)
        router.onError("1", -4)
        assertEquals(-4, sink.failure?.errorCode)
        assertEquals(0, sink.completed)
    }

    @Test
    fun `an error without a code fails with a null code`() {
        val sink = RecordingSink()
        router.register("1", sink)
        router.onError("1", null)
        assertNull(sink.failure?.errorCode)
    }

    @Test
    fun `callbacks for unknown utterances are ignored`() {
        router.onStart("x")
        router.onRange("x", 0, 1)
        router.onDone("x")
        router.onStop("x")
        router.onError("x", 1)
    }

    @Test
    fun `unregister reports whether the utterance was still active`() {
        router.register("1", RecordingSink())
        assertTrue(router.unregister("1"))
        assertFalse(router.unregister("1"))
    }
}
