package io.github.maniramezan.kommon.parsing

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

/**
 * Parses a backend field that's either a plain string or a JSON-encoded `{locale: value}` map,
 * preferring [preferredLocaleKeys] (falling back to the first non-blank value) when it's a map.
 *
 * A common shape for backends that localize a single field without a full i18n response
 * envelope.
 */
public class LocalizedValueParser(
    private val preferredLocaleKeys: List<String> = DEFAULT_PREFERRED_LOCALE_KEYS,
) {
    private val json = Json { ignoreUnknownKeys = true }

    /**
     * Parses a plain string (returned as-is) or a JSON-encoded `{locale: value}` map. A decoded
     * locale map with no usable entry yields `null` (as the [JsonElement] overload does) rather
     * than leaking the raw JSON into the UI.
     */
    public fun parse(value: String?): String? =
        when {
            value.isNullOrBlank() -> null
            // Cheap shape check first: plain strings (the common case) skip a throwing decode.
            !value.trimStart().startsWith('{') -> value
            else -> {
                val parsed = runCatching { json.decodeFromString<Map<String, String>>(value) }.getOrNull()
                if (parsed == null) value else preferredValue(parsed)
            }
        }

    public fun parse(element: JsonElement): String? =
        when (element) {
            is JsonPrimitive -> parse(element.contentOrNull)
            is JsonObject -> preferredValue(element.toStringMap())
            else -> null
        }

    /**
     * Returns the first non-blank value among [preferredLocaleKeys], else the first non-blank value
     * in [values]. A preferred locale present with a blank value is skipped, not returned.
     */
    public fun preferredValue(values: Map<String, String>?): String? {
        if (values.isNullOrEmpty()) return null
        return preferredLocaleKeys.firstNotNullOfOrNull { key -> values[key]?.takeIf(String::isNotBlank) }
            ?: values.values.firstOrNull(String::isNotBlank)
    }

    private fun JsonObject.toStringMap(): Map<String, String> =
        entries
            .mapNotNull { (key, value) ->
                (value as? JsonPrimitive)?.contentOrNull?.let { key to it }
            }.toMap()

    public companion object {
        public val DEFAULT_PREFERRED_LOCALE_KEYS: List<String> = listOf("en_us", "enUs", "en_gb", "enGb")
    }
}
