package io.github.maniramezan.kommon.speech

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.speech.ModelDownloadListener
import android.speech.RecognitionSupport
import android.speech.RecognitionSupportCallback
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import androidx.annotation.RequiresApi
import androidx.core.content.ContextCompat
import kotlinx.coroutines.suspendCancellableCoroutine
import java.util.Locale
import kotlin.coroutines.resume

/** [ModelBackend] over a real on-device `SpeechRecognizer`. Thin by design: no logic worth testing. */
@RequiresApi(Build.VERSION_CODES.TIRAMISU)
internal class AndroidModelBackend private constructor(
    private val context: Context,
    private val recognizer: SpeechRecognizer,
) : ModelBackend {
    override suspend fun querySupport(locale: Locale): RecognitionSupportLists? =
        suspendCancellableCoroutine { continuation ->
            recognizer.checkRecognitionSupport(
                languageIntent(locale),
                ContextCompat.getMainExecutor(context),
                object : RecognitionSupportCallback {
                    override fun onSupportResult(recognitionSupport: RecognitionSupport) {
                        continuation.resume(
                            RecognitionSupportLists(
                                installedOnDevice = recognitionSupport.installedOnDeviceLanguages,
                                pendingOnDevice = recognitionSupport.pendingOnDeviceLanguages,
                                supportedOnDevice = recognitionSupport.supportedOnDeviceLanguages,
                                online = recognitionSupport.onlineLanguages,
                            ),
                        )
                    }

                    override fun onError(error: Int) {
                        continuation.resume(null)
                    }
                },
            )
        }

    override fun requestDownload(locale: Locale) {
        val intent = languageIntent(locale)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            recognizer.triggerModelDownload(
                intent,
                ContextCompat.getMainExecutor(context),
                object : ModelDownloadListener {
                    override fun onProgress(completedPercent: Int) = Unit

                    override fun onSuccess() = release()

                    override fun onScheduled() = release()

                    override fun onError(error: Int) = release()
                },
            )
        } else {
            recognizer.triggerModelDownload(intent)
            Handler(Looper.getMainLooper()).postDelayed({ release() }, LEGACY_RELEASE_DELAY_MILLIS)
        }
    }

    override fun release() {
        recognizer.destroy()
    }

    private fun languageIntent(locale: Locale): Intent =
        Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
            .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            .putExtra(RecognizerIntent.EXTRA_LANGUAGE, locale.toLanguageTag())

    companion object {
        private const val LEGACY_RELEASE_DELAY_MILLIS = 10_000L

        /** A backend over a new on-device recognizer, or `null` when on-device recognition is unavailable. */
        fun create(context: Context): ModelBackend? =
            runCatching {
                if (SpeechRecognizer.isOnDeviceRecognitionAvailable(context)) {
                    AndroidModelBackend(context, SpeechRecognizer.createOnDeviceSpeechRecognizer(context))
                } else {
                    null
                }
            }.getOrNull()

        /** Runs [block] on the main thread. */
        fun postToMain(block: () -> Unit) {
            Handler(Looper.getMainLooper()).post(block)
        }
    }
}
