package io.github.maniramezan.kommon.speech

import android.content.Context
import android.content.Intent
import android.os.Build
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer

/** [RecognizerBackend] over a real `SpeechRecognizer`. Thin by design: no logic worth testing. */
internal class AndroidRecognizerBackend(
    private val recognizer: SpeechRecognizer,
    override val usesOnDeviceRecognizer: Boolean,
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

/** Creates [AndroidRecognizerBackend]s, preferring a dedicated on-device recognizer when asked. */
internal class AndroidRecognizerBackendFactory(
    private val context: Context,
) : RecognizerBackendFactory {
    override fun create(configuration: SpeechRecognitionConfiguration): RecognizerBackend? =
        if (configuration.preferOnDevice && isOnDeviceAvailable()) {
            AndroidRecognizerBackend(SpeechRecognizer.createOnDeviceSpeechRecognizer(context), usesOnDeviceRecognizer = true)
        } else if (SpeechRecognizer.isRecognitionAvailable(context)) {
            AndroidRecognizerBackend(SpeechRecognizer.createSpeechRecognizer(context), usesOnDeviceRecognizer = false)
        } else {
            null
        }

    private fun isOnDeviceAvailable(): Boolean =
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && SpeechRecognizer.isOnDeviceRecognitionAvailable(context)
}
