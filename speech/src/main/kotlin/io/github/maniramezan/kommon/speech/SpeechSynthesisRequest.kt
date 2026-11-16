package io.github.maniramezan.kommon.speech

/**
 * One piece of text to speak.
 *
 * @property text The text to speak.
 * @property languageTag The BCP 47 language of [text], such as `en-US`. Used to pick a voice when
 *   [voiceId] is `null`.
 * @property voiceId The [SpeechVoice.id] to speak with, or `null` to use the best voice for
 *   [languageTag].
 * @property allowsNetworkVoices Whether voices that need a network connection may be used. When
 *   false (the default) only voices that work offline are chosen: an explicit [voiceId] that needs
 *   the network is ignored in favor of the best local voice for [languageTag], the engine is asked
 *   not to use network synthesis, and speaking fails with [SpeechSynthesisException] if no local
 *   voice exists for the language.
 * @property rate The speaking rate, where `1` is normal speed.
 * @property pitch The voice pitch, where `1` is normal.
 */
public data class SpeechSynthesisRequest(
    val text: String,
    val languageTag: String,
    val voiceId: String? = null,
    val allowsNetworkVoices: Boolean = false,
    val rate: Float = 1f,
    val pitch: Float = 1f,
)
