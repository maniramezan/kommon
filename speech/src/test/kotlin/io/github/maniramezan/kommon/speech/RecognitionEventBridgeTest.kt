package io.github.maniramezan.kommon.speech

import android.os.Bundle
import android.speech.RecognitionPart
import android.speech.SpeechRecognizer
import io.github.maniramezan.kommon.foundation.KommonLogger
import io.mockk.every
import io.mockk.mockk
import org.junit.Test
import java.util.Locale
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class RecognitionEventBridgeTest {
    private class RecordingSink : RecognitionSink {
        val events = mutableListOf<SpeechRecognitionEvent>()
        var completed = 0
        var failure: SpeechRecognitionException? = null

        override fun emit(event: SpeechRecognitionEvent) {
            events += event
        }

        override fun complete() {
            completed++
        }

        override fun fail(error: SpeechRecognitionException) {
            failure = error
        }
    }

    private class MessageLogger : KommonLogger {
        val messages = mutableListOf<String>()

        override fun debug(
            tag: String,
            message: String,
        ) {
            messages += message
        }

        override fun info(
            tag: String,
            message: String,
        ) {
            messages += message
        }

        override fun warning(
            tag: String,
            message: String,
            error: Throwable?,
        ) {
            messages += message
        }

        override fun error(
            tag: String,
            message: String,
            error: Throwable?,
        ) {
            messages += message
        }
    }

    private val sink = RecordingSink()
    private val logger = MessageLogger()
    private val configuration = SpeechRecognitionConfiguration(Locale.US)

    private fun bridge(
        sdkInt: Int = 34,
        config: SpeechRecognitionConfiguration = configuration,
    ) = RecognitionEventBridge(config, RecognizerKind.OnDevice, sdkInt, sink, logger)

    private fun resultBundle(
        results: List<String>?,
        scores: FloatArray? = null,
        parts: List<RecognitionPart>? = null,
    ): Bundle {
        val bundle = mockk<Bundle>()
        every { bundle.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION) } returns results?.let { ArrayList(it) }
        every { bundle.getFloatArray(SpeechRecognizer.CONFIDENCE_SCORES) } returns scores
        every {
            bundle.getParcelableArrayList(SpeechRecognizer.RECOGNITION_PARTS, RecognitionPart::class.java)
        } returns parts?.let { ArrayList(it) }
        return bundle
    }

    private fun partial(text: String?): Bundle {
        val bundle = mockk<Bundle>()
        every { bundle.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION) } returns text?.let { arrayListOf(it) }
        return bundle
    }

    private fun part(
        text: String,
        level: Int,
        timestamp: Long,
    ): RecognitionPart {
        val part = mockk<RecognitionPart>()
        every { part.rawText } returns text
        every { part.confidenceLevel } returns level
        every { part.timestampMillis } returns timestamp
        return part
    }

    @Test
    fun `lifecycle callbacks become ready level and silence events`() {
        val bridge = bridge()
        bridge.onReadyForSpeech(null)
        bridge.onBeginningOfSpeech()
        bridge.onRmsChanged(4f)
        bridge.onBufferReceived(null)
        bridge.onEvent(0, null)
        bridge.onEndOfSpeech()
        assertEquals(
            listOf(
                SpeechRecognitionEvent.Ready(RecognizerKind.OnDevice),
                SpeechRecognitionEvent.AudioLevel(0.5f),
                SpeechRecognitionEvent.SilenceDetected,
            ),
            sink.events,
        )
    }

    @Test
    fun `ready carries the kind of recognizer in use`() {
        RecognitionEventBridge(configuration, RecognizerKind.DefaultService, 34, sink, logger).onReadyForSpeech(null)
        assertEquals(listOf<SpeechRecognitionEvent>(SpeechRecognitionEvent.Ready(RecognizerKind.DefaultService)), sink.events)
    }

    @Test
    fun `partial results replace the volatile text`() {
        val bridge = bridge()
        bridge.onPartialResults(partial("hel"))
        bridge.onPartialResults(partial(" hello "))
        val transcripts = sink.events.map { (it as SpeechRecognitionEvent.Transcript).transcript }
        assertEquals(listOf("hel", "hello"), transcripts.map { it.volatileText })
        assertTrue(transcripts.all { it.finalizedUtterances.isEmpty() })
    }

    @Test
    fun `empty or missing partial results are ignored`() {
        val bridge = bridge()
        bridge.onPartialResults(null)
        bridge.onPartialResults(partial(null))
        bridge.onPartialResults(partial("  "))
        assertEquals(emptyList(), sink.events)
    }

    @Test
    fun `final results emit one finalized utterance and complete`() {
        val bridge = bridge()
        bridge.onPartialResults(partial("hello wor"))
        bridge.onResults(resultBundle(listOf("hello world", "hello word"), floatArrayOf(0.9f, 0.4f)))
        val transcript = (sink.events.last() as SpeechRecognitionEvent.Transcript).transcript
        assertTrue(transcript.isFinal)
        assertEquals("hello world", transcript.text)
        assertEquals(listOf("hello word"), transcript.finalizedUtterances.single().alternatives)
        assertEquals(listOf(0.9f, 0.9f), transcript.words.map { it.confidence })
        assertEquals(1, sink.completed)
        assertNull(sink.failure)
    }

    @Test
    fun `word parts on Android 14 supply confidence and timing`() {
        val bridge = bridge(sdkInt = 34)
        val parts =
            listOf(
                part("hello", RecognitionPart.CONFIDENCE_LEVEL_HIGH, 100),
                part("world", RecognitionPart.CONFIDENCE_LEVEL_LOW, 500),
            )
        bridge.onResults(resultBundle(listOf("hello world"), parts = parts))
        val words = (sink.events.last() as SpeechRecognitionEvent.Transcript).transcript.words
        assertEquals(listOf(RecognizedWord("hello", 1f, 100, 400), RecognizedWord("world", 0.2f, 500, null)), words)
    }

    @Test
    fun `word parts are ignored below Android 14 or when details are not requested`() {
        val parts = listOf(part("hello", RecognitionPart.CONFIDENCE_LEVEL_LOW, 100))
        bridge(sdkInt = 33).onResults(resultBundle(listOf("hello"), floatArrayOf(0.7f), parts))
        val old = (sink.events.last() as SpeechRecognitionEvent.Transcript).transcript.words
        assertEquals(listOf(RecognizedWord("hello", 0.7f)), old)

        val noDetails = RecordingSink()
        RecognitionEventBridge(configuration.copy(requestsWordDetails = false), RecognizerKind.OnDevice, 34, noDetails, logger)
            .onResults(resultBundle(listOf("hello"), floatArrayOf(0.7f), parts))
        val words = (noDetails.events.last() as SpeechRecognitionEvent.Transcript).transcript.words
        assertEquals(listOf(RecognizedWord("hello", 0.7f)), words)
    }

    @Test
    fun `final results missing the confidence array still map`() {
        bridge().onResults(resultBundle(listOf("hi"), scores = null))
        val words = (sink.events.last() as SpeechRecognitionEvent.Transcript).transcript.words
        assertEquals(listOf(RecognizedWord("hi", null)), words)
    }

    @Test
    fun `empty final results with an earlier partial fall back to the partial`() {
        val bridge = bridge()
        bridge.onPartialResults(partial("almost"))
        bridge.onResults(resultBundle(emptyList()))
        assertEquals("almost", (sink.events.last() as SpeechRecognitionEvent.Transcript).transcript.finalizedText)
        assertEquals(1, sink.completed)
    }

    @Test
    fun `empty final results with nothing heard fail with no match`() {
        bridge().onResults(resultBundle(emptyList()))
        assertEquals(SpeechRecognitionException.Reason.NoMatch, sink.failure?.reason)
        bridge().onResults(null)
        assertEquals(0, sink.completed)
    }

    @Test
    fun `no match after nothing was heard fails with no match`() {
        bridge().onError(SpeechRecognizer.ERROR_NO_MATCH)
        assertEquals(SpeechRecognitionException.Reason.NoMatch, sink.failure?.reason)
    }

    @Test
    fun `no match after a partial finalizes the partial instead of failing`() {
        val bridge = bridge()
        bridge.onPartialResults(partial("some words"))
        bridge.onError(SpeechRecognizer.ERROR_NO_MATCH)
        val transcript = (sink.events.last() as SpeechRecognitionEvent.Transcript).transcript
        assertEquals("some words", transcript.finalizedText)
        assertEquals(listOf("some", "words"), transcript.words.map { it.text })
        assertEquals(1, sink.completed)
        assertNull(sink.failure)
    }

    @Test
    fun `other errors fail even after a partial`() {
        val bridge = bridge()
        bridge.onPartialResults(partial("some words"))
        bridge.onError(SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS)
        assertEquals(SpeechRecognitionException.Reason.PermissionDenied, sink.failure?.reason)
        assertEquals(0, sink.completed)
    }

    @Test
    fun `nothing is delivered after the run finished`() {
        val bridge = bridge()
        bridge.onResults(resultBundle(listOf("done")))
        val count = sink.events.size
        bridge.onRmsChanged(5f)
        bridge.onEndOfSpeech()
        bridge.onPartialResults(partial("late"))
        bridge.onResults(resultBundle(listOf("again")))
        bridge.onError(SpeechRecognizer.ERROR_NETWORK)
        assertEquals(count, sink.events.size)
        assertEquals(1, sink.completed)
        assertNull(sink.failure)
    }

    @Test
    fun `logging never contains transcript text`() {
        val bridge = bridge()
        bridge.onPartialResults(partial("secret phrase"))
        bridge.onResults(resultBundle(listOf("secret phrase", "secret phrases")))
        assertTrue(logger.messages.isNotEmpty())
        assertTrue(logger.messages.none { it.contains("secret") })
        assertIs<SpeechRecognitionEvent.Transcript>(sink.events.last())
    }
}
