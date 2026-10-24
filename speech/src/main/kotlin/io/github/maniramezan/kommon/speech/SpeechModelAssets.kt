package io.github.maniramezan.kommon.speech

import android.content.Context
import android.os.Build
import io.github.maniramezan.kommon.foundation.KommonLogger
import io.github.maniramezan.kommon.foundation.NoOpLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Locale

/**
 * Inspects and requests the on-device speech recognition models for a language.
 *
 * Requires Android 13 (API 33); on older versions [status] returns [Status.Unknown] and
 * [requestDownload] does nothing.
 */
public object SpeechModelAssets {
    private const val TAG = "KommonSpeech"
    private const val MODEL_API_LEVEL = Build.VERSION_CODES.TIRAMISU

    /** Whether the on-device model for a language is ready. */
    public enum class Status {
        /** The language has no on-device recognition support. */
        Unsupported,

        /** The language is supported on device but its model isn't installed. */
        NotInstalled,

        /** The model is downloading. */
        Downloading,

        /** The model is installed and ready. */
        Installed,

        /** The status couldn't be determined (old Android version, no service, or a failed query). */
        Unknown,
    }

    /**
     * The on-device model status for [locale].
     *
     * @param context Any context.
     * @param locale The language to check.
     * @param logger Receives diagnostic messages.
     */
    public suspend fun status(
        context: Context,
        locale: Locale,
        logger: KommonLogger = NoOpLogger,
    ): Status {
        if (Build.VERSION.SDK_INT < MODEL_API_LEVEL) return Status.Unknown
        return withContext(Dispatchers.Main.immediate) {
            val backend = AndroidModelBackend.create(context.applicationContext ?: context)
            statusFrom(backend, locale, logger)
        }
    }

    /**
     * Asks the system to download the on-device model for [locale].
     *
     * Returns immediately; poll [status] to observe progress. No-op below Android 13.
     *
     * @param context Any context.
     * @param locale The language to download.
     * @param logger Receives diagnostic messages.
     */
    public fun requestDownload(
        context: Context,
        locale: Locale,
        logger: KommonLogger = NoOpLogger,
    ) {
        if (Build.VERSION.SDK_INT < MODEL_API_LEVEL) return
        AndroidModelBackend.postToMain {
            downloadWith(AndroidModelBackend.create(context.applicationContext ?: context), locale, logger)
        }
    }

    internal suspend fun statusFrom(
        backend: ModelBackend?,
        locale: Locale,
        logger: KommonLogger,
    ): Status {
        if (backend == null) {
            logger.warning(TAG, "model status unknown: on-device recognition unavailable")
            return Status.Unknown
        }
        return try {
            val lists = backend.querySupport(locale)
            val result = if (lists == null) Status.Unknown else SpeechModelStatusMapper.status(locale, lists)
            logger.info(TAG, "model status locale=${locale.toLanguageTag()} status=$result")
            result
        } finally {
            backend.release()
        }
    }

    internal fun downloadWith(
        backend: ModelBackend?,
        locale: Locale,
        logger: KommonLogger,
    ) {
        if (backend == null) {
            logger.warning(TAG, "download not requested: on-device recognition unavailable")
            return
        }
        logger.info(TAG, "requesting model download locale=${locale.toLanguageTag()}")
        backend.requestDownload(locale)
    }
}
