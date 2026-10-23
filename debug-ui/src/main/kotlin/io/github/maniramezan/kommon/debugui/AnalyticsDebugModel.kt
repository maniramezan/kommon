package io.github.maniramezan.kommon.debugui

import io.github.maniramezan.kommon.analytics.debug.AnalyticsDebugAction
import io.github.maniramezan.kommon.analytics.debug.AnalyticsDebugEntry
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val TIME_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm:ss.SSS")

/**
 * The entries to show for [action] (`null` for every action) and [query], newest first. The query
 * matches the entry name and its property keys and values, ignoring case.
 */
internal fun List<AnalyticsDebugEntry>.newestFirst(
    action: AnalyticsDebugAction?,
    query: String,
): List<AnalyticsDebugEntry> {
    val needle = query.trim()
    return asReversed().filter { entry ->
        (action == null || entry.action == action) && (needle.isEmpty() || entry.matches(needle))
    }
}

/** The distinct actions present in these entries, in [AnalyticsDebugAction] declaration order. */
internal fun List<AnalyticsDebugEntry>.actions(): List<AnalyticsDebugAction> {
    val present = mapTo(HashSet()) { it.action }
    return AnalyticsDebugAction.entries.filter { it in present }
}

/** A one-line `key=value` summary of the entry's properties, or `null` when it has none. */
internal fun AnalyticsDebugEntry.propertySummary(): String? =
    properties.takeIf { it.isNotEmpty() }?.entries?.joinToString(", ") { (key, value) -> "$key=$value" }

/** Wall-clock time of the entry in [zone], to the millisecond. */
internal fun AnalyticsDebugEntry.formattedTime(zone: ZoneId): String = TIME_FORMAT.format(Instant.ofEpochMilli(timestampMs).atZone(zone))

private fun AnalyticsDebugEntry.matches(needle: String): Boolean =
    name.contains(needle, ignoreCase = true) ||
        properties.any { (key, value) -> key.contains(needle, ignoreCase = true) || value.toString().contains(needle, ignoreCase = true) }
