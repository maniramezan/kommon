package io.github.maniramezan.kommon.speech

import android.speech.tts.Voice
import io.github.maniramezan.kommon.speech.SpeechVoice.Quality
import org.junit.Test
import java.util.Locale
import kotlin.test.assertEquals
import kotlin.test.assertNull

class SpeechVoiceTest {
    private fun voice(
        name: String,
        tag: String,
        quality: Quality = Quality.Standard,
        network: Boolean = false,
    ) = SpeechVoice(name, name, tag, quality, network)

    @Test
    fun `an exact language and region beats a higher quality voice in another region`() {
        val us = voice("us", "en-US", Quality.Standard)
        val gb = voice("gb", "en-GB", Quality.Premium)
        assertEquals(us, SpeechVoice.best("en-US", listOf(gb, us)))
    }

    @Test
    fun `without an exact match the same base language in another region is used`() {
        val gb = voice("gb", "en-GB")
        assertEquals(gb, SpeechVoice.best("en-US", listOf(voice("fr", "fr-FR"), gb)))
    }

    @Test
    fun `higher quality wins within a group`() {
        val standard = voice("a", "en-US", Quality.Standard)
        val premium = voice("b", "en-US", Quality.Premium)
        val enhanced = voice("c", "en-US", Quality.Enhanced)
        assertEquals(premium, SpeechVoice.best("en-US", listOf(standard, enhanced, premium)))
    }

    @Test
    fun `offline voices win quality ties and then name order decides`() {
        val network = voice("a", "en-US", Quality.Enhanced, network = true)
        val offlineB = voice("b", "en-US", Quality.Enhanced)
        val offlineC = voice("c", "en-US", Quality.Enhanced)
        assertEquals(offlineB, SpeechVoice.best("en-US", listOf(offlineC, network, offlineB)))
    }

    @Test
    fun `underscores and case in the requested tag are normalized`() {
        val us = voice("us", "en-US")
        assertEquals(us, SpeechVoice.best("EN_us", listOf(voice("gb", "en-GB"), us)))
    }

    @Test
    fun `a bare language matches any region`() {
        val gb = voice("gb", "en-GB")
        assertEquals(gb, SpeechVoice.best("en", listOf(gb)))
    }

    @Test
    fun `no voice for the language returns null`() {
        assertNull(SpeechVoice.best("de-DE", listOf(voice("us", "en-US"))))
        assertNull(SpeechVoice.best("en-US", emptyList()))
    }

    @Test
    fun `android voice quality maps to tiers`() {
        assertEquals(Quality.Premium, VoiceMapper.quality(Voice.QUALITY_VERY_HIGH))
        assertEquals(Quality.Enhanced, VoiceMapper.quality(Voice.QUALITY_HIGH))
        assertEquals(Quality.Standard, VoiceMapper.quality(Voice.QUALITY_NORMAL))
        assertEquals(Quality.Standard, VoiceMapper.quality(Voice.QUALITY_VERY_LOW))
    }

    @Test
    fun `voice mapping keeps the system name as id and uses a language tag`() {
        val mapped = VoiceMapper.voice("en-us-x-abc-network", Locale.US, Voice.QUALITY_HIGH, requiresNetwork = true)
        assertEquals(SpeechVoice("en-us-x-abc-network", "en-us-x-abc-network", "en-US", Quality.Enhanced, true), mapped)
    }

    @Test
    fun `voices sort by language then best quality then name`() {
        val sorted =
            VoiceMapper.sorted(
                listOf(
                    voice("z", "fr-FR", Quality.Standard),
                    voice("b", "en-US", Quality.Standard),
                    voice("a", "en-US", Quality.Standard),
                    voice("c", "en-US", Quality.Premium),
                ),
            )
        assertEquals(listOf("c", "a", "b", "z"), sorted.map { it.id })
    }
}
