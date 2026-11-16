package io.github.maniramezan.kommon.speech

import android.content.Context
import android.os.Build
import android.os.Handler
import android.os.Looper
import io.github.maniramezan.kommon.foundation.KommonLogger
import io.github.maniramezan.kommon.foundation.NoOpLogger
import kotlinx.coroutines.Dispatchers

/**
 * A [SpeechRecognizing] backed by Android's `SpeechRecognizer`.
 *
 * Each [recognize] collection creates a recognizer, drives it on the main thread, and destroys it
 * when the flow ends or is cancelled. Only one run can be active at a time; a second concurrent
 * collection fails with [SpeechRecognitionException.Reason.Busy]. The host app must hold the
 * `RECORD_AUDIO` permission, otherwise the run fails with
 * [SpeechRecognitionException.Reason.PermissionDenied].
 *
 * By default ([SpeechRecognitionConfiguration.requiresOnDevice]) only a dedicated on-device
 * recognizer (Android 12 and later) is used. If none is available the flow fails with
 * [SpeechRecognitionException.Reason.NotAvailable] and the system default recognizer is never
 * created; use [isOnDeviceRecognitionAvailable] to check first. With `requiresOnDevice = false` the
 * default recognition service is used as a fallback, **which may stream audio to remote servers**.
 * [SpeechRecognitionEvent.Ready] reports which recognizer ran. Per-word confidence and timing
 * require Android 14.
 * If the recognizer reports no match after partial text was heard, that text is delivered as the
 * final result instead of failing.
 *
 * The logger receives lengths, locales, and error codes only, never transcript text.
 *
 * @param context Any context; the application context is retained.
 * @param logger Receives diagnostic messages. Defaults to [NoOpLogger].
 */
public class AndroidSpeechRecognitionSession(
    context: Context,
    logger: KommonLogger = NoOpLogger,
) : SpeechRecognizing by SpeechRecognitionRunner(
        backendFactory = AndroidRecognizerBackendFactory(context.applicationContext ?: context),
        sdkInt = Build.VERSION.SDK_INT,
        dispatcher = Dispatchers.Main.immediate,
        postToMain = { block -> Handler(Looper.getMainLooper()).post(block) },
        logger = logger,
    )
