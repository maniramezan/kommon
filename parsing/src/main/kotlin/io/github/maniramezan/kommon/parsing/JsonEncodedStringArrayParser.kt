package io.github.maniramezan.kommon.parsing

import kotlinx.serialization.json.Json

/**
 * Parses a backend field that's either a raw string or a JSON-encoded string array — a common
 * "loose" wire shape for fields like definitions/examples that may be sent as a single string or
 * a list depending on backend version/content source.
 */
public object JsonEncodedStringArrayParser {
    private val json = Json { ignoreUnknownKeys = true }

    /** Parses [value] as a JSON string array and joins it with [joinWith]; passes through as-is otherwise. */
    public fun parseOrPassthrough(
        value: String?,
        joinWith: String = "\n",
    ): String {
        if (value.isNullOrBlank()) return ""
        val parsedValues = parseStringArray(value)
        return if (parsedValues.isNullOrEmpty()) value else parsedValues.joinToString(joinWith)
    }

    /** Parses each value in [values] as a JSON string array (or passes it through as a single-item list), flattened. */
    public fun parseOrList(values: List<String>?): List<String> {
        if (values.isNullOrEmpty()) return emptyList()
        return values.flatMap { value ->
            parseStringArray(value)?.filter(String::isNotBlank) ?: listOf(value).filter(String::isNotBlank)
        }
    }

    private fun parseStringArray(value: String): List<String>? = runCatching { json.decodeFromString<List<String>>(value) }.getOrNull()
}
