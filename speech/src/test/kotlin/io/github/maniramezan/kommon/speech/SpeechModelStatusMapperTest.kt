package io.github.maniramezan.kommon.speech

import io.github.maniramezan.kommon.speech.SpeechModelAssets.Status
import org.junit.Test
import java.util.Locale
import kotlin.test.assertEquals

class SpeechModelStatusMapperTest {
    private val locale = Locale.forLanguageTag("en-US")

    private fun support(
        installed: List<String> = emptyList(),
        pending: List<String> = emptyList(),
        supported: List<String> = emptyList(),
        online: List<String> = emptyList(),
    ) = RecognitionSupportLists(installed, pending, supported, online)

    @Test
    fun `an installed language is installed`() {
        assertEquals(Status.Installed, SpeechModelStatusMapper.status(locale, support(installed = listOf("en-US"))))
    }

    @Test
    fun `installed wins over pending and supported`() {
        val lists = support(installed = listOf("en-US"), pending = listOf("en-US"), supported = listOf("en-US"))
        assertEquals(Status.Installed, SpeechModelStatusMapper.status(locale, lists))
    }

    @Test
    fun `a pending language is downloading`() {
        val lists = support(pending = listOf("en-US"), supported = listOf("en-US"))
        assertEquals(Status.Downloading, SpeechModelStatusMapper.status(locale, lists))
    }

    @Test
    fun `a supported but absent language is not installed`() {
        assertEquals(Status.NotInstalled, SpeechModelStatusMapper.status(locale, support(supported = listOf("en-US"))))
    }

    @Test
    fun `an online only language has no on-device support`() {
        assertEquals(Status.Unsupported, SpeechModelStatusMapper.status(locale, support(online = listOf("en-US"))))
    }

    @Test
    fun `a missing language is unsupported`() {
        assertEquals(Status.Unsupported, SpeechModelStatusMapper.status(locale, support(installed = listOf("fr-FR"))))
    }

    @Test
    fun `matching ignores case and underscores`() {
        assertEquals(Status.Installed, SpeechModelStatusMapper.status(locale, support(installed = listOf("EN_us"))))
    }

    @Test
    fun `a different region does not match`() {
        assertEquals(Status.Unsupported, SpeechModelStatusMapper.status(locale, support(installed = listOf("en-GB"))))
    }
}
