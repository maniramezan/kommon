package io.github.maniramezan.kommon.testing

import io.github.maniramezan.kommon.foundation.KommonLogger

/** In-memory [KommonLogger] that records every call, for asserting on library/app log output. */
public class RecordingLogger : KommonLogger {
    public enum class Level { DEBUG, INFO, WARNING, ERROR }

    public data class Entry(
        val level: Level,
        val tag: String,
        val message: String,
        val error: Throwable? = null,
    )

    public val entries: MutableList<Entry> = mutableListOf()

    /** Entries at [level], in order. */
    public fun entriesAt(level: Level): List<Entry> = entries.filter { it.level == level }

    override fun debug(
        tag: String,
        message: String,
    ) {
        entries += Entry(Level.DEBUG, tag, message)
    }

    override fun info(
        tag: String,
        message: String,
    ) {
        entries += Entry(Level.INFO, tag, message)
    }

    override fun warning(
        tag: String,
        message: String,
        error: Throwable?,
    ) {
        entries += Entry(Level.WARNING, tag, message, error)
    }

    override fun error(
        tag: String,
        message: String,
        error: Throwable?,
    ) {
        entries += Entry(Level.ERROR, tag, message, error)
    }
}
