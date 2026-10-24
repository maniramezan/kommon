package io.github.maniramezan.kommon.speech

import io.github.maniramezan.kommon.foundation.KommonLogger
import io.github.maniramezan.kommon.foundation.NoOpLogger
import io.github.maniramezan.kommon.speech.SpeechModelAssets.Status
import kotlinx.coroutines.test.runTest
import org.junit.Test
import java.util.Locale
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class SpeechModelAssetsTest {
    private class FakeBackend(
        private val lists: RecognitionSupportLists?,
        private val failure: Throwable? = null,
    ) : ModelBackend {
        var released = 0
        val downloads = mutableListOf<Locale>()

        override suspend fun querySupport(locale: Locale): RecognitionSupportLists? {
            failure?.let { throw it }
            return lists
        }

        override fun requestDownload(locale: Locale) {
            downloads += locale
        }

        override fun release() {
            released++
        }
    }

    private val locale = Locale.forLanguageTag("de-DE")

    @Test
    fun `status maps the reported lists and releases the recognizer`() =
        runTest {
            val backend = FakeBackend(RecognitionSupportLists(listOf("de-DE"), emptyList(), listOf("de-DE"), emptyList()))
            assertEquals(Status.Installed, SpeechModelAssets.statusFrom(backend, locale, NoOpLogger))
            assertEquals(1, backend.released)
        }

    @Test
    fun `a failed support check is unknown`() =
        runTest {
            val backend = FakeBackend(null)
            assertEquals(Status.Unknown, SpeechModelAssets.statusFrom(backend, locale, NoOpLogger))
            assertEquals(1, backend.released)
        }

    @Test
    fun `an unavailable on-device recognizer is unknown`() =
        runTest {
            assertEquals(Status.Unknown, SpeechModelAssets.statusFrom(null, locale, NoOpLogger))
        }

    @Test
    fun `the recognizer is released even when the query throws`() =
        runTest {
            val backend = FakeBackend(null, failure = IllegalStateException("died"))
            assertFailsWith<IllegalStateException> { SpeechModelAssets.statusFrom(backend, locale, NoOpLogger) }
            assertEquals(1, backend.released)
        }

    @Test
    fun `download forwards the locale to the backend`() {
        val backend = FakeBackend(null)
        SpeechModelAssets.downloadWith(backend, locale, NoOpLogger)
        assertEquals(listOf(locale), backend.downloads)
    }

    @Test
    fun `download without a backend only logs a warning`() {
        val warnings = mutableListOf<String>()
        val logger =
            object : KommonLogger by NoOpLogger {
                override fun warning(
                    tag: String,
                    message: String,
                    error: Throwable?,
                ) {
                    warnings += message
                }
            }
        SpeechModelAssets.downloadWith(null, locale, logger)
        assertEquals(1, warnings.size)
    }
}
