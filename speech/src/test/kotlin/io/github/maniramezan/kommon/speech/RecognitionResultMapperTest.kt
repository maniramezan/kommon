package io.github.maniramezan.kommon.speech

import android.speech.RecognitionPart
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class RecognitionResultMapperTest {
    @Test
    fun `the first result is the text and the rest are alternatives`() {
        val utterance =
            RecognitionResultMapper.utterance(listOf("hello world", "hollow world", "hello word"), null, null, false)
        assertEquals("hello world", utterance?.text)
        assertEquals(listOf("hollow world", "hello word"), utterance?.alternatives)
    }

    @Test
    fun `alternatives drop duplicates blanks and the best text`() {
        val utterance =
            RecognitionResultMapper.utterance(listOf("a b", "a b", " ", "a c", "a c"), null, null, false)
        assertEquals(listOf("a c"), utterance?.alternatives)
    }

    @Test
    fun `without parts words carry the first confidence score`() {
        val utterance =
            RecognitionResultMapper.utterance(listOf("good  morning"), floatArrayOf(0.8f, 0.3f), null, false)
        assertEquals(listOf(RecognizedWord("good", 0.8f), RecognizedWord("morning", 0.8f)), utterance?.words)
    }

    @Test
    fun `a missing or out of range score leaves word confidence null`() {
        assertEquals(
            listOf(null),
            RecognitionResultMapper.utterance(listOf("hi"), null, null, false)?.words?.map { it.confidence },
        )
        assertEquals(
            listOf(null),
            RecognitionResultMapper.utterance(listOf("hi"), floatArrayOf(-1f), null, false)?.words?.map { it.confidence },
        )
        assertEquals(
            listOf(null),
            RecognitionResultMapper.utterance(listOf("hi"), floatArrayOf(), null, false)?.words?.map { it.confidence },
        )
    }

    @Test
    fun `parts supply per word confidence and timing`() {
        val parts =
            listOf(
                RecognitionPartData("hello", RecognitionPart.CONFIDENCE_LEVEL_HIGH, 100),
                RecognitionPartData("there", RecognitionPart.CONFIDENCE_LEVEL_LOW, 400),
                RecognitionPartData("friend", RecognitionPart.CONFIDENCE_LEVEL_UNKNOWN, 900),
            )
        val utterance = RecognitionResultMapper.utterance(listOf("hello there friend"), floatArrayOf(0.9f), parts, true)
        assertEquals(
            listOf(
                RecognizedWord("hello", 1f, 100, 300),
                RecognizedWord("there", 0.2f, 400, 500),
                RecognizedWord("friend", null, 900, null),
            ),
            utterance?.words,
        )
    }

    @Test
    fun `parts timing is dropped when not requested`() {
        val parts = listOf(RecognitionPartData("hi", RecognitionPart.CONFIDENCE_LEVEL_MEDIUM, 0))
        val word = RecognitionResultMapper.utterance(listOf("hi"), null, parts, false)?.words?.single()
        assertEquals(RecognizedWord("hi", 0.6f, null, null), word)
    }

    @Test
    fun `negative or decreasing timestamps produce no timing`() {
        val parts =
            listOf(
                RecognitionPartData("a", RecognitionPart.CONFIDENCE_LEVEL_MEDIUM_HIGH, -1),
                RecognitionPartData("b", RecognitionPart.CONFIDENCE_LEVEL_MEDIUM_LOW, 500),
                RecognitionPartData("c", RecognitionPart.CONFIDENCE_LEVEL_HIGH, 300),
            )
        val words = RecognitionResultMapper.utterance(listOf("a b c"), null, parts, true)?.words
        assertEquals(listOf(null, 500L, 300L), words?.map { it.startMillis })
        assertEquals(listOf(null, null, null), words?.map { it.durationMillis })
        assertEquals(listOf(0.8f, 0.4f, 1f), words?.map { it.confidence })
    }

    @Test
    fun `parts alone can supply the text`() {
        val parts =
            listOf(
                RecognitionPartData("one", RecognitionPart.CONFIDENCE_LEVEL_HIGH, 0),
                RecognitionPartData(" ", RecognitionPart.CONFIDENCE_LEVEL_HIGH, 10),
                RecognitionPartData("two", RecognitionPart.CONFIDENCE_LEVEL_HIGH, 20),
            )
        val utterance = RecognitionResultMapper.utterance(null, null, parts, false)
        assertEquals("one two", utterance?.text)
        assertEquals(2, utterance?.words?.size)
    }

    @Test
    fun `no text yields no utterance`() {
        assertNull(RecognitionResultMapper.utterance(null, null, null, false))
        assertNull(RecognitionResultMapper.utterance(listOf("  ", ""), null, emptyList(), true))
    }

    @Test
    fun `confidence levels map to fixed values and unknown is null`() {
        assertEquals(1f, RecognitionResultMapper.confidence(RecognitionPart.CONFIDENCE_LEVEL_HIGH))
        assertEquals(0.8f, RecognitionResultMapper.confidence(RecognitionPart.CONFIDENCE_LEVEL_MEDIUM_HIGH))
        assertEquals(0.6f, RecognitionResultMapper.confidence(RecognitionPart.CONFIDENCE_LEVEL_MEDIUM))
        assertEquals(0.4f, RecognitionResultMapper.confidence(RecognitionPart.CONFIDENCE_LEVEL_MEDIUM_LOW))
        assertEquals(0.2f, RecognitionResultMapper.confidence(RecognitionPart.CONFIDENCE_LEVEL_LOW))
        assertNull(RecognitionResultMapper.confidence(RecognitionPart.CONFIDENCE_LEVEL_UNKNOWN))
        assertNull(RecognitionResultMapper.confidence(42))
    }
}
