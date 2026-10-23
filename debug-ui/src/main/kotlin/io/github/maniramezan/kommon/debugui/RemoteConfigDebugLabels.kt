package io.github.maniramezan.kommon.debugui

import androidx.compose.runtime.Immutable
import io.github.maniramezan.kommon.remoteconfig.ValueSource

/** User-visible strings for [RemoteConfigDebugScreen]. English defaults; override to localize. */
@Immutable
public data class RemoteConfigDebugLabels(
    public val title: String = "Remote config",
    public val description: String = "Overrides apply to this device only and are ignored in release builds.",
    public val searchPlaceholder: String = "Search keys",
    public val clearSearch: String = "Clear search",
    public val noMatches: String = "No matching keys",
    public val sourceDefault: String = "Default",
    public val sourceRemote: String = "Remote",
    public val sourceOverride: String = "Override",
    public val overrideValue: String = "Override value",
    public val invalidValue: String = "Not a valid value for this key",
    public val apply: String = "Apply",
    public val reset: String = "Reset",
    public val resetAll: String = "Reset all",
    public val resetAllTitle: String = "Reset all overrides?",
    public val cancel: String = "Cancel",
    public val resetAllMessage: String = "Every key returns to its remote or default value.",
) {
    internal fun sourceLabel(source: ValueSource): String =
        when (source) {
            ValueSource.DEFAULT -> sourceDefault
            ValueSource.REMOTE -> sourceRemote
            ValueSource.OVERRIDE -> sourceOverride
        }
}
