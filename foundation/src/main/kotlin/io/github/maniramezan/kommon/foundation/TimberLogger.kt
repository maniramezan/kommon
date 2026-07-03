package io.github.maniramezan.kommon.foundation

import android.util.Log
import timber.log.Timber

/**
 * [KommonLogger] implementation backed by Timber, with a configurable [tagPrefix] (ported from
 * Novalingo's `Logger` singleton) and a built-in release tree that suppresses verbose/debug/info
 * logs, forwarding only warnings and errors (e.g. to crash reporting).
 *
 * Call [setup] once at app startup to plant the appropriate Timber tree; then inject a
 * `TimberLogger` instance wherever [KommonLogger] is required.
 */
public class TimberLogger(
    private val tagPrefix: String,
) : KommonLogger {
    override fun debug(
        tag: String,
        message: String,
    ) {
        Timber.tag(prefixed(tag)).d(message)
    }

    override fun info(
        tag: String,
        message: String,
    ) {
        Timber.tag(prefixed(tag)).i(message)
    }

    override fun warning(
        tag: String,
        message: String,
        error: Throwable?,
    ) {
        Timber.tag(prefixed(tag)).w(error, message)
    }

    override fun error(
        tag: String,
        message: String,
        error: Throwable?,
    ) {
        Timber.tag(prefixed(tag)).e(error, message)
    }

    private fun prefixed(tag: String): String = "$tagPrefix:$tag"

    public companion object {
        /** Plants a [Timber.DebugTree] in debug builds, or a release tree that drops low-severity logs. */
        public fun setup(isDebug: Boolean) {
            if (isDebug) {
                Timber.plant(Timber.DebugTree())
            } else {
                Timber.plant(ReleaseTree())
            }
        }
    }

    private class ReleaseTree : Timber.Tree() {
        override fun log(
            priority: Int,
            tag: String?,
            message: String,
            t: Throwable?,
        ) {
            if (priority == Log.VERBOSE || priority == Log.DEBUG || priority == Log.INFO) {
                return
            }
            super.log(priority, tag, message, t)
        }
    }
}
