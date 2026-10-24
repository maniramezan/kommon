package io.github.maniramezan.kommon.speech

import io.github.maniramezan.kommon.speech.AlignedTokenStatus.Insertion
import io.github.maniramezan.kommon.speech.AlignedTokenStatus.LowConfidence
import io.github.maniramezan.kommon.speech.AlignedTokenStatus.Match
import io.github.maniramezan.kommon.speech.AlignedTokenStatus.Omission
import io.github.maniramezan.kommon.speech.AlignedTokenStatus.Substitution
import org.junit.Test
import java.util.Locale
import kotlin.test.assertEquals

class TranscriptAlignmentTest {
    private fun statuses(
        reference: String,
        heard: String,
    ): List<AlignedTokenStatus> = TranscriptAlignment.align(reference, heard).tokens.map { it.status }

    @Test
    fun `identical text matches every word ignoring case`() {
        assertEquals(listOf(Match, Match, Match, Match), statuses("The quick brown fox", "the quick brown fox"))
    }

    @Test
    fun `a missing word is an omission and the rest stay aligned`() {
        assertEquals(listOf(Match, Match, Omission, Match), statuses("The quick brown fox", "the quick fox"))
    }

    @Test
    fun `a different word is a substitution`() {
        assertEquals(listOf(Match, Match, Match, Substitution), statuses("The quick brown fox", "the quick brown box"))
    }

    @Test
    fun `an extra word is an insertion`() {
        assertEquals(listOf(Match, Insertion, Match, Match), statuses("The quick fox", "the very quick fox"))
    }

    @Test
    fun `edge punctuation is ignored`() {
        assertEquals(listOf(Match, Match), statuses("Hello, world!", "hello world"))
    }

    @Test
    fun `curly apostrophes match straight ones`() {
        assertEquals(listOf(Match, Match), statuses("Don’t stop", "don't stop"))
    }

    @Test
    fun `hyphenated words split into separate words`() {
        assertEquals(listOf(Match, Match, Match, Match), statuses("A well-known fact", "a well known fact"))
    }

    @Test
    fun `nothing heard omits every reference word`() {
        assertEquals(listOf(Omission, Omission), statuses("Good morning", ""))
    }

    @Test
    fun `an empty reference makes every heard word an insertion`() {
        assertEquals(listOf(Insertion), statuses("", "hi"))
    }

    @Test
    fun `words below the confidence threshold are low confidence and null confidence is never low`() {
        val alignment =
            TranscriptAlignment.align(
                reference = "red green blue",
                heard =
                    listOf(
                        TranscriptToken("red", 0.9f),
                        TranscriptToken("green", 0.2f),
                        TranscriptToken("blue", null),
                    ),
                lowConfidenceThreshold = 0.5f,
            )
        assertEquals(listOf(Match, LowConfidence, Match), alignment.tokens.map { it.status })
        assertEquals(0.2f, alignment.tokens[1].confidence)
    }

    @Test
    fun `a multi word heard token gives each piece the token confidence`() {
        val alignment =
            TranscriptAlignment.align("one two", listOf(TranscriptToken("one two", 0.1f)))
        assertEquals(listOf(LowConfidence, LowConfidence), alignment.tokens.map { it.status })
        assertEquals(listOf(0.1f, 0.1f), alignment.tokens.map { it.confidence })
    }

    @Test
    fun `substitution keeps the heard word and the reference word`() {
        val token = TranscriptAlignment.align("cat", "dog").tokens.single()
        assertEquals(AlignedToken("cat", "dog", null, Substitution), token)
    }

    @Test
    fun `omission and insertion carry only one side`() {
        val omission = TranscriptAlignment.align("cat", "").tokens.single()
        val insertion = TranscriptAlignment.align("", "dog").tokens.single()
        assertEquals(AlignedToken("cat", null, null, Omission), omission)
        assertEquals(AlignedToken(null, "dog", null, Insertion), insertion)
    }

    @Test
    fun `locale controls case folding`() {
        val turkish = Locale.forLanguageTag("tr-TR")
        assertEquals(
            listOf(Substitution),
            TranscriptAlignment.align("I", "i", turkish).tokens.map { it.status },
        )
        assertEquals(listOf(Match), TranscriptAlignment.align("I", "i").tokens.map { it.status })
    }

    @Test
    fun `counts completeness and score follow the status mix`() {
        val alignment =
            TranscriptAlignment(
                listOf(
                    AlignedToken("a", "a", null, Match),
                    AlignedToken("b", "b", 0.1f, LowConfidence),
                    AlignedToken("c", "x", null, Substitution),
                    AlignedToken("d", null, null, Omission),
                    AlignedToken(null, "y", null, Insertion),
                ),
            )
        assertEquals(4, alignment.referenceWordCount)
        assertEquals(1, alignment.count(Omission))
        assertEquals(0.75, alignment.completeness, 1e-9)
        assertEquals(1.5 / 5, alignment.score, 1e-9)
    }

    @Test
    fun `an empty alignment is complete and scores one`() {
        val alignment = TranscriptAlignment.align("", "")
        assertEquals(0, alignment.referenceWordCount)
        assertEquals(1.0, alignment.completeness, 1e-9)
        assertEquals(1.0, alignment.score, 1e-9)
    }
}
