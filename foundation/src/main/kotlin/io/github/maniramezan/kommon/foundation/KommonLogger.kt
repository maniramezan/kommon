package io.github.maniramezan.kommon.foundation

/**
 * Minimal logging seam so shared-library modules (sync, auth-session bootstrap, etc.) can log
 * without depending on any specific logging framework (Timber, java.util.logging, etc.).
 *
 * Consumers bridge this to their own logging stack (see [TimberLogger] for a ready-made Timber
 * bridge, or implement it directly against their own facade). [NoOpLogger] is the default so
 * nothing breaks if a consumer doesn't wire one in.
 */
public interface KommonLogger {
    public fun debug(
        tag: String,
        message: String,
    )

    public fun info(
        tag: String,
        message: String,
    )

    public fun warning(
        tag: String,
        message: String,
        error: Throwable? = null,
    )

    public fun error(
        tag: String,
        message: String,
        error: Throwable? = null,
    )
}

/** A [KommonLogger] that discards everything. Safe default for libraries and tests. */
public object NoOpLogger : KommonLogger {
    override fun debug(
        tag: String,
        message: String,
    ): Unit = Unit

    override fun info(
        tag: String,
        message: String,
    ): Unit = Unit

    override fun warning(
        tag: String,
        message: String,
        error: Throwable?,
    ): Unit = Unit

    override fun error(
        tag: String,
        message: String,
        error: Throwable?,
    ): Unit = Unit
}
