package io.github.maniramezan.kommon.speech

import java.util.Locale

/**
 * A text-to-speech voice available on the device.
 *
 * @property id The system voice identifier (the `android.speech.tts.Voice` name).
 * @property name The voice's display name.
 * @property languageTag The BCP 47 language tag, such as `en-US`.
 * @property quality The voice's quality tier.
 * @property requiresNetwork Whether the voice needs a network connection to speak (audio text may
 *   then be sent to a remote server).
 */
public data class SpeechVoice(
    val id: String,
    val name: String,
    val languageTag: String,
    val quality: Quality,
    val requiresNetwork: Boolean,
) {
    /** How natural a voice sounds, lowest to highest. */
    public enum class Quality {
        /** The compact voice every device has. */
        Standard,

        /** An enhanced voice. */
        Enhanced,

        /** A premium (neural) voice. */
        Premium,
    }

    public companion object {
        /**
         * The best voice for [languageTag] among [voices].
         *
         * An exact language-and-region match (`en-US`) beats a same-language match in another
         * region (`en-GB`). Within each group, higher [quality] wins, then voices that work
         * offline, then name order.
         *
         * @param languageTag The wanted BCP 47 language tag.
         * @param voices The voices to choose from.
         * @param allowsNetwork Whether voices that [requiresNetwork] may be chosen. When false (the
         *   default) they are filtered out entirely.
         * @return `null` when no acceptable voice speaks the language.
         */
        public fun best(
            languageTag: String,
            voices: List<SpeechVoice>,
            allowsNetwork: Boolean = false,
        ): SpeechVoice? {
            val wanted = normalize(languageTag)
            val wantedLanguage = baseLanguage(wanted)
            return voices
                .filter { allowsNetwork || !it.requiresNetwork }
                .filter { baseLanguage(normalize(it.languageTag)) == wantedLanguage }
                .sortedWith(
                    compareByDescending<SpeechVoice> { normalize(it.languageTag) == wanted }
                        .thenByDescending { it.quality }
                        .thenBy { it.requiresNetwork }
                        .thenBy { it.name },
                ).firstOrNull()
        }

        private fun normalize(tag: String): String = tag.replace('_', '-').lowercase(Locale.ROOT)

        private fun baseLanguage(normalizedTag: String): String = normalizedTag.substringBefore('-')
    }
}
