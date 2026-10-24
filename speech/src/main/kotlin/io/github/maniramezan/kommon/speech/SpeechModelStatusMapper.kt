package io.github.maniramezan.kommon.speech

import java.util.Locale

/** The language lists reported by `RecognitionSupport`, as plain values. */
internal data class RecognitionSupportLists(
    val installedOnDevice: List<String>,
    val pendingOnDevice: List<String>,
    val supportedOnDevice: List<String>,
    val online: List<String>,
)

/** Maps `RecognitionSupport` language lists to a [SpeechModelAssets.Status]. */
internal object SpeechModelStatusMapper {
    /**
     * The on-device model status for [locale].
     *
     * Installed wins over pending, pending over merely supported. A language that only appears in
     * the online list has no on-device model, so it is [SpeechModelAssets.Status.Unsupported] for
     * on-device purposes.
     */
    fun status(
        locale: Locale,
        support: RecognitionSupportLists,
    ): SpeechModelAssets.Status {
        val wanted = normalize(locale.toLanguageTag())
        return when {
            support.installedOnDevice.containsTag(wanted) -> SpeechModelAssets.Status.Installed
            support.pendingOnDevice.containsTag(wanted) -> SpeechModelAssets.Status.Downloading
            support.supportedOnDevice.containsTag(wanted) -> SpeechModelAssets.Status.NotInstalled
            else -> SpeechModelAssets.Status.Unsupported
        }
    }

    private fun List<String>.containsTag(wanted: String): Boolean = any { normalize(it) == wanted }

    private fun normalize(tag: String): String = tag.replace('_', '-').lowercase(Locale.ROOT)
}
