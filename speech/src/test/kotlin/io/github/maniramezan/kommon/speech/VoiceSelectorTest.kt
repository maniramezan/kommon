package io.github.maniramezan.kommon.speech

import android.speech.tts.TextToSpeech
import io.github.maniramezan.kommon.speech.SpeechVoice.Quality
import io.github.maniramezan.kommon.speech.VoiceSelector.Selection
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class VoiceSelectorTest {
    private val local = SpeechVoice("local", "local", "en-US", Quality.Standard, false)
    private val localGb = SpeechVoice("gb", "gb", "en-GB", Quality.Enhanced, false)
    private val network = SpeechVoice("net", "net", "en-US", Quality.Premium, true)
    private val request = SpeechSynthesisRequest("hi", "en-US")

    private fun chosen(selection: Selection): SpeechVoice = assertIs<Selection.Chosen>(selection).voice

    @Test
    fun `the default voice is the best local voice for the language`() {
        assertEquals(local, chosen(VoiceSelector.select(request, listOf(network, localGb, local))))
    }

    @Test
    fun `allowing network voices lets the best voice win`() {
        assertEquals(network, chosen(VoiceSelector.select(request.copy(allowsNetworkVoices = true), listOf(local, network))))
    }

    @Test
    fun `an explicit local voice id is honored even in another language`() {
        assertEquals(localGb, chosen(VoiceSelector.select(request.copy(voiceId = "gb"), listOf(local, localGb))))
    }

    @Test
    fun `an explicit network voice id falls back to the best local voice`() {
        assertEquals(local, chosen(VoiceSelector.select(request.copy(voiceId = "net"), listOf(network, local))))
    }

    @Test
    fun `an unknown voice id falls back to the best voice`() {
        assertEquals(local, chosen(VoiceSelector.select(request.copy(voiceId = "missing"), listOf(local))))
    }

    @Test
    fun `no local voice for the language is unavailable with a clear message`() {
        val unavailable = assertIs<Selection.Unavailable>(VoiceSelector.select(request, listOf(network)))
        assertEquals("No local voice is installed for en-US", unavailable.error.message)
    }

    @Test
    fun `no voice at all is unavailable even when network voices are allowed`() {
        val unavailable = assertIs<Selection.Unavailable>(VoiceSelector.select(request.copy(allowsNetworkVoices = true), emptyList()))
        assertTrue(
            unavailable.error.message
                .orEmpty()
                .contains("en-US"),
        )
    }

    @Test
    fun `utterance params disable network synthesis unless allowed`() {
        assertEquals(
            mapOf(TextToSpeech.Engine.KEY_FEATURE_NETWORK_SYNTHESIS to "false"),
            VoiceSelector.utteranceParams(request),
        )
        assertEquals(emptyMap(), VoiceSelector.utteranceParams(request.copy(allowsNetworkVoices = true)))
    }
}
