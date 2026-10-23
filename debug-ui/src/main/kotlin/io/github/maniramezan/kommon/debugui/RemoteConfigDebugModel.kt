package io.github.maniramezan.kommon.debugui

import io.github.maniramezan.kommon.remoteconfig.ConfigKey
import io.github.maniramezan.kommon.remoteconfig.ConfigValue
import io.github.maniramezan.kommon.remoteconfig.ConfigValueType
import io.github.maniramezan.kommon.remoteconfig.ResolvedConfigEntry

/**
 * Parses [text] typed into a debug override field as a value for this key, or `null` when it is not
 * a valid value of the key's type (or not one of its allowed values). Numbers are trimmed; strings
 * are kept exactly as typed.
 */
internal fun ConfigKey.parseOverride(text: String): ConfigValue? {
    val trimmed = text.trim()
    val value =
        when (valueType) {
            ConfigValueType.BOOL ->
                when (trimmed.lowercase()) {
                    "true" -> ConfigValue.Bool(true)
                    "false" -> ConfigValue.Bool(false)
                    else -> null
                }
            ConfigValueType.STRING -> ConfigValue.StringVal(text)
            ConfigValueType.INT -> trimmed.toIntOrNull()?.let { ConfigValue.Int(it) }
            ConfigValueType.DOUBLE -> trimmed.toDoubleOrNull()?.takeIf { it.isFinite() }?.let { ConfigValue.Double(it) }
        }
    return value?.takeIf(::accepts)
}

/** Entries whose key id or description contains [query], ignoring case; all entries for a blank query. */
internal fun List<ResolvedConfigEntry>.matching(query: String): List<ResolvedConfigEntry> {
    val needle = query.trim()
    if (needle.isEmpty()) return this
    return filter { it.key.id.contains(needle, ignoreCase = true) || it.key.description.contains(needle, ignoreCase = true) }
}
