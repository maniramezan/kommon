package io.github.maniramezan.kommon.speech

/**
 * One piece of text to speak.
 *
 * @property text The text to speak.
 * @property languageTag The BCP 47 language of [text], such as `en-US`. Used to pick a voice when
 *   [voiceId] is `null`.
 * @property voiceId The [SpeechVoice.id] to speak with, or `null` to let the engine choose one for
 *   [languageTag].
 * @property rate The speaking rate, where `1` is normal speed.
 * @property pitch The voice pitch, where `1` is normal.
 */
public data class SpeechSynthesisRequest(
    val text: String,
    val languageTag: String,
    val voiceId: String? = null,
    val rate: Float = 1f,
    val pitch: Float = 1f,
)
