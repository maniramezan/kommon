package io.github.maniramezan.kommon.foundation

import android.util.Log
import timber.log.Timber

/**
 * [KommonLogger] implementation backed by Timber, with a configurable [tagPrefix] (ported from
 * Novalingo's `Logger` singleton) and a built-in release tree that suppresses verbose/debug/info
 * logs, forwarding only warnings and errors to an optional [ReleaseLogSink] (e.g. crash reporting).
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
        @Volatile
        private var plantedTree: Timber.Tree? = null

        /**
         * Plants a [Timber.DebugTree] in debug builds, or a release tree that drops
         * verbose/debug/info logs and forwards warnings and errors to [releaseSink] (e.g. a
         * crash-reporting breadcrumb/non-fatal recorder). With no sink, release builds log nothing.
         *
         * Safe to call more than once: the tree planted by a previous call is uprooted first, so
         * repeated setup never duplicates output. Trees planted directly via [Timber.plant] are
         * left alone.
         */
        @Synchronized
        public fun setup(
            isDebug: Boolean,
            releaseSink: ReleaseLogSink? = null,
        ) {
            // Only uproot if still planted: consumers (and tests) may have called Timber.uprootAll().
            plantedTree?.takeIf { it in Timber.forest() }?.let(Timber::uproot)
            val tree = if (isDebug) Timber.DebugTree() else ReleaseTree(releaseSink)
            Timber.plant(tree)
            plantedTree = tree
        }
    }

    /** Receives warning-and-above logs in release builds; see [setup]. */
    public fun interface ReleaseLogSink {
        public fun log(
            priority: Int,
            tag: String?,
            message: String,
            throwable: Throwable?,
        )
    }

    private class ReleaseTree(
        private val sink: ReleaseLogSink?,
    ) : Timber.Tree() {
        override fun isLoggable(
            tag: String?,
            priority: Int,
        ): Boolean = sink != null && priority >= Log.WARN

        override fun log(
            priority: Int,
            tag: String?,
            message: String,
            t: Throwable?,
        ) {
            sink?.log(priority, tag, message, t)
        }
    }
}
