package io.github.maniramezan.kommon.speech

import android.content.Context
import android.os.Bundle
import android.speech.tts.TextToSpeech
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** [TtsBackend] over a real `TextToSpeech`. Thin by design: no logic worth testing. */
internal class AndroidTtsBackend(
    private val tts: TextToSpeech,
) : TtsBackend {
    override fun voices(): List<SpeechVoice> =
        tts.voices.orEmpty().map { VoiceMapper.voice(it.name, it.locale, it.quality, it.isNetworkConnectionRequired) }

    override fun prepare(
        request: SpeechSynthesisRequest,
        voice: SpeechVoice,
    ): SpeechSynthesisException? {
        tts.setSpeechRate(request.rate)
        tts.setPitch(request.pitch)
        val engineVoice = tts.voices.orEmpty().firstOrNull { it.name == voice.id }
        val result = engineVoice?.let { tts.setVoice(it) }
        return if (result == TextToSpeech.SUCCESS) null else SpeechSynthesisException("Voice could not be selected", result)
    }

    override fun speak(
        text: String,
        utteranceId: String,
        params: Map<String, String>,
    ): Int {
        val bundle = Bundle()
        params.forEach { (key, value) -> bundle.putString(key, value) }
        return tts.speak(text, TextToSpeech.QUEUE_FLUSH, bundle, utteranceId)
    }

    override fun isSuccess(result: Int): Boolean = result == TextToSpeech.SUCCESS

    override fun stop() {
        tts.stop()
    }

    override fun shutdown() {
        tts.shutdown()
    }
}

/** Creates [AndroidTtsBackend]s on the main thread, awaiting the engine's init callback. */
internal class AndroidTtsBackendFactory(
    private val context: Context,
) : TtsBackendFactory {
    override suspend fun create(router: SynthesisEventRouter): TtsBackend =
        withContext(Dispatchers.Main.immediate) {
            suspendCancellableCoroutine { continuation ->
                var created: TextToSpeech? = null
                created =
                    TextToSpeech(context) { status ->
                        val tts = created
                        if (status == TextToSpeech.SUCCESS && tts != null) {
                            tts.setOnUtteranceProgressListener(RouterProgressListener(router))
                            continuation.resume(AndroidTtsBackend(tts))
                        } else {
                            tts?.shutdown()
                            continuation.resumeWithException(SpeechSynthesisException("TextToSpeech initialization failed", status))
                        }
                    }
                continuation.invokeOnCancellation { created?.shutdown() }
            }
        }
}
