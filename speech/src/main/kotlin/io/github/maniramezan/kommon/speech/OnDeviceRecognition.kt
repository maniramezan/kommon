package io.github.maniramezan.kommon.speech

import android.content.Context
import android.os.Build
import android.speech.SpeechRecognizer

/**
 * Whether a dedicated on-device speech recognizer is available (Android 12 / API 31 and later, and
 * a recognition service that supports it).
 *
 * [AndroidSpeechRecognitionSession] needs this to be true to run when
 * [SpeechRecognitionConfiguration.requiresOnDevice] is set (the default). Check it before starting
 * so the UI can show the right state.
 *
 * @param context Any context.
 */
public fun isOnDeviceRecognitionAvailable(context: Context): Boolean =
    Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && SpeechRecognizer.isOnDeviceRecognitionAvailable(context)
