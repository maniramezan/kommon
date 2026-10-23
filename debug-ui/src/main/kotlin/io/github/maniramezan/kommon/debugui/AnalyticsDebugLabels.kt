package io.github.maniramezan.kommon.debugui

import androidx.compose.runtime.Immutable
import io.github.maniramezan.kommon.analytics.debug.AnalyticsDebugAction

/** User-visible strings for [AnalyticsDebugScreen]. English defaults; override to localize. */
@Immutable
public data class AnalyticsDebugLabels(
    public val title: String = "Analytics",
    public val description: String = "Recent calls, newest first, with payloads as providers receive them.",
    public val searchPlaceholder: String = "Filter by name or property",
    public val clearSearch: String = "Clear filter",
    public val allActions: String = "All",
    public val clear: String = "Clear",
    public val empty: String = "No analytics calls yet",
    public val noMatches: String = "No calls match the filter",
    public val entryCount: String = "captured calls",
    public val track: String = "Event",
    public val trackScreen: String = "Screen",
    public val identify: String = "Identify",
    public val setUserProperties: String = "User properties",
    public val increment: String = "Increment",
    public val reset: String = "Reset",
    public val flush: String = "Flush",
) {
    /** The label shown for [action] in filter chips and entry badges. */
    public fun label(action: AnalyticsDebugAction): String =
        when (action) {
            AnalyticsDebugAction.TRACK -> track
            AnalyticsDebugAction.TRACK_SCREEN -> trackScreen
            AnalyticsDebugAction.IDENTIFY -> identify
            AnalyticsDebugAction.SET_USER_PROPERTIES -> setUserProperties
            AnalyticsDebugAction.INCREMENT -> increment
            AnalyticsDebugAction.RESET -> reset
            AnalyticsDebugAction.FLUSH -> flush
        }
}
