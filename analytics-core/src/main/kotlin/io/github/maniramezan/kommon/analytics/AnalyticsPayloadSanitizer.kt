package io.github.maniramezan.kommon.analytics

/**
 * Truncates/limits event and user-property payloads to conservative size limits shared by most
 * analytics providers (event parameter counts, string lengths). Apply before calling
 * [AnalyticsClient.track]/[AnalyticsClient.setUserProperties] to avoid provider-side rejection or
 * truncation surprises.
 */
public object AnalyticsPayloadSanitizer {
    private const val EVENT_PARAMETER_LIMIT = 25
    private const val STRING_VALUE_LIMIT = 100
    private const val USER_PROPERTY_VALUE_LIMIT = 36

    public fun sanitizeEventProperties(properties: Map<String, Any>): LinkedHashMap<String, Any> {
        val sanitized = LinkedHashMap<String, Any>()
        properties.entries.take(EVENT_PARAMETER_LIMIT).forEach { (key, value) ->
            sanitized[key] = sanitizeEventValue(value)
        }
        return sanitized
    }

    public fun sanitizeUserProperties(properties: Map<String, Any>): LinkedHashMap<String, String> {
        val sanitized = LinkedHashMap<String, String>()
        properties.forEach { (key, value) ->
            sanitized[key] = sanitizeUserPropertyValue(value)
        }
        return sanitized
    }

    public fun sanitizeUserPropertyValue(value: Any): String = value.toString().take(USER_PROPERTY_VALUE_LIMIT)

    private fun sanitizeEventValue(value: Any): Any =
        when (value) {
            is String -> value.take(STRING_VALUE_LIMIT)
            is Int, is Long, is Double, is Float, is Boolean -> value
            else -> value.toString().take(STRING_VALUE_LIMIT)
        }
}
