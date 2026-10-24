package io.github.maniramezan.kommon.speech

import android.speech.tts.Voice
import java.util.Locale

/** Converts `android.speech.tts.Voice` values to [SpeechVoice]s. */
internal object VoiceMapper {
    /** Maps a `Voice.QUALITY_*` value: very high is premium, high is enhanced, anything else is standard. */
    fun quality(androidQuality: Int): SpeechVoice.Quality =
        when {
            androidQuality >= Voice.QUALITY_VERY_HIGH -> SpeechVoice.Quality.Premium
            androidQuality >= Voice.QUALITY_HIGH -> SpeechVoice.Quality.Enhanced
            else -> SpeechVoice.Quality.Standard
        }

    fun voice(
        name: String,
        locale: Locale,
        androidQuality: Int,
        requiresNetwork: Boolean,
    ): SpeechVoice =
        SpeechVoice(
            id = name,
            name = name,
            languageTag = locale.toLanguageTag(),
            quality = quality(androidQuality),
            requiresNetwork = requiresNetwork,
        )

    /** Every voice sorted by language, then best quality first, then name. */
    fun sorted(voices: Collection<SpeechVoice>): List<SpeechVoice> =
        voices.sortedWith(
            compareBy<SpeechVoice> { it.languageTag }.thenByDescending { it.quality }.thenBy { it.name },
        )
}
