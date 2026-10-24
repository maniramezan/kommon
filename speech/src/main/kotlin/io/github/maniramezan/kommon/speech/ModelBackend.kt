package io.github.maniramezan.kommon.speech

import java.util.Locale

/**
 * The on-device model operations [SpeechModelAssets] needs, so its flow can be tested without the
 * Android framework. The production implementation is [AndroidModelBackend].
 */
internal interface ModelBackend {
    /** The language lists for [locale], or `null` if the support check failed. */
    suspend fun querySupport(locale: Locale): RecognitionSupportLists?

    /** Asks the system to download the model for [locale]. Releases itself when done. */
    fun requestDownload(locale: Locale)

    /** Releases the underlying recognizer. */
    fun release()
}
