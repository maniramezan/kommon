package io.github.maniramezan.kommon.speech

import org.junit.Test
import java.util.Locale
import kotlin.test.assertEquals

class TranscriptNormalizerTest {
    @Test
    fun `whitespace dashes and slashes separate words`() {
        assertEquals(
            listOf("one", "two", "three", "four", "five"),
            TranscriptNormalizer.words("  one two—three/four\nfive "),
        )
    }

    @Test
    fun `quotes and trailing punctuation are trimmed but inner apostrophes stay`() {
        assertEquals(listOf("It's", "5", "o'clock"), TranscriptNormalizer.words("\"It's 5 o’clock!\""))
    }

    @Test
    fun `text with only punctuation yields no words`() {
        assertEquals(emptyList(), TranscriptNormalizer.words("... !! ?"))
    }

    @Test
    fun `every dash variant separates words`() {
        assertEquals(
            listOf("a", "b", "c", "d", "e", "f"),
            TranscriptNormalizer.words("a-b‐c‑d‒e–f"),
        )
    }

    @Test
    fun `symbols at word edges are trimmed`() {
        assertEquals(listOf("cost", "50"), TranscriptNormalizer.words("£cost 50™"))
    }

    @Test
    fun `non breaking spaces separate words`() {
        assertEquals(listOf("a", "b"), TranscriptNormalizer.words("a b"))
    }

    @Test
    fun `supplementary characters survive`() {
        assertEquals(listOf("𝐀bc"), TranscriptNormalizer.words("𝐀bc"))
    }

    @Test
    fun `key straightens apostrophes and folds case with the locale`() {
        assertEquals("don't", TranscriptNormalizer.key("DON’T", Locale.US))
        assertEquals("ı", TranscriptNormalizer.key("I", Locale.forLanguageTag("tr-TR")))
    }
}
