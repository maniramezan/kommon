package io.github.maniramezan.kommon.speech

import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SpeechTranscriptTest {
    private val first = RecognizedUtterance("  hello there ", listOf(RecognizedWord("hello", 0.9f), RecognizedWord("there")))
    private val second = RecognizedUtterance("friend", listOf(RecognizedWord("friend")))

    @Test
    fun `an empty transcript is final with empty text`() {
        val transcript = SpeechTranscript()
        assertTrue(transcript.isFinal)
        assertEquals("", transcript.text)
        assertEquals("", transcript.finalizedText)
        assertEquals(emptyList(), transcript.words)
    }

    @Test
    fun `text joins trimmed finalized utterances and the volatile preview with single spaces`() {
        val transcript = SpeechTranscript(listOf(first, second), volatileText = " more ")
        assertEquals("hello there friend", transcript.finalizedText)
        assertEquals("hello there friend more", transcript.text)
        assertFalse(transcript.isFinal)
    }

    @Test
    fun `words flatten finalized utterances only`() {
        val transcript = SpeechTranscript(listOf(first, second), volatileText = "pending")
        assertEquals(listOf("hello", "there", "friend"), transcript.words.map { it.text })
    }

    @Test
    fun `recognized word converts to a transcript token`() {
        assertEquals(TranscriptToken("hi", 0.4f), RecognizedWord("hi", 0.4f, 10, 20).transcriptToken)
    }

    @Test
    fun `accumulator replaces the preview and appends finals`() {
        val accumulator = TranscriptAccumulator()
        assertEquals("hel", accumulator.applyVolatile("hel").volatileText)
        assertEquals("hello", accumulator.applyVolatile("hello").volatileText)
        val afterFinal = accumulator.applyFinal(first)
        assertEquals("", afterFinal.volatileText)
        assertEquals(listOf(first), afterFinal.finalizedUtterances)
        assertEquals(listOf(first, second), accumulator.applyFinal(second).finalizedUtterances)
    }

    @Test
    fun `accumulator drops blank finals but still clears the preview`() {
        val accumulator = TranscriptAccumulator()
        accumulator.applyVolatile("noise")
        val transcript = accumulator.applyFinal(RecognizedUtterance("   ", emptyList()))
        assertEquals(emptyList(), transcript.finalizedUtterances)
        assertEquals("", transcript.volatileText)
        assertEquals(transcript, accumulator.transcript)
    }

    @Test
    fun `configuration rejects invalid values`() {
        val locale = java.util.Locale.US
        assertFailsWith<IllegalArgumentException> { SpeechRecognitionConfiguration(locale, maxAlternatives = 0) }
        assertFailsWith<IllegalArgumentException> { SpeechRecognitionConfiguration(locale, completeSilenceMillis = -1) }
    }

    @Test
    fun `exception carries reason and a default message`() {
        val error = SpeechRecognitionException(SpeechRecognitionException.Reason.Busy)
        assertEquals(SpeechRecognitionException.Reason.Busy, error.reason)
        assertTrue(error.message.orEmpty().contains("Busy"))
    }
}
