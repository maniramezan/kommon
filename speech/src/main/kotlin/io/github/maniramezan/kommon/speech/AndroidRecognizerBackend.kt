package io.github.maniramezan.kommon.speech

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer

/** [RecognizerBackend] over a real `SpeechRecognizer`. Thin by design: no logic worth testing. */
internal class AndroidRecognizerBackend(
    private val recognizer: SpeechRecognizer,
    override val kind: RecognizerKind,
) : RecognizerBackend {
    override fun start(
        listener: RecognitionListener,
        extras: Map<String, Any>,
    ) {
        recognizer.setRecognitionListener(listener)
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
        extras.forEach { (key, value) ->
            when (value) {
                is Boolean -> intent.putExtra(key, value)
                is Int -> intent.putExtra(key, value)
                is Long -> intent.putExtra(key, value)
                is String -> intent.putExtra(key, value)
            }
        }
        recognizer.startListening(intent)
    }

    override fun stopListening() {
        recognizer.stopListening()
    }

    override fun destroy() {
        recognizer.destroy()
    }
}

/**
 * Creates [AndroidRecognizerBackend]s. With [SpeechRecognitionConfiguration.requiresOnDevice] it
 * only ever creates an on-device recognizer and never touches `createSpeechRecognizer`.
 */
internal class AndroidRecognizerBackendFactory(
    private val context: Context,
) : RecognizerBackendFactory {
    // The on-device branch is only reachable when isOnDeviceRecognitionAvailable (API 31+) is true.
    @SuppressLint("NewApi")
    override fun create(configuration: SpeechRecognitionConfiguration): RecognizerBackend? {
        val onDeviceAvailable = isOnDeviceRecognitionAvailable(context)
        val defaultAvailable = !configuration.requiresOnDevice && SpeechRecognizer.isRecognitionAvailable(context)
        return when (RecognizerSelection.choose(configuration.requiresOnDevice, onDeviceAvailable, defaultAvailable)) {
            RecognizerKind.OnDevice ->
                AndroidRecognizerBackend(SpeechRecognizer.createOnDeviceSpeechRecognizer(context), RecognizerKind.OnDevice)
            RecognizerKind.DefaultService ->
                AndroidRecognizerBackend(SpeechRecognizer.createSpeechRecognizer(context), RecognizerKind.DefaultService)
            null -> null
        }
    }
}
