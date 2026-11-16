package io.github.maniramezan.kommon.analytics

/**
 * Truncates, limits, and redacts event and user-property payloads according to an
 * [AnalyticsPayloadPolicy] (by default [AnalyticsPayloadPolicy.DEFAULT], the conservative limits
 * most analytics providers share). Apply before calling [AnalyticsClient.track]/
 * [AnalyticsClient.setUserProperties], or wrap a client in [SanitizingAnalyticsClient] to apply it
 * to every call.
 */
public object AnalyticsPayloadSanitizer {
    public fun sanitizeEventProperties(properties: Map<String, Any>): LinkedHashMap<String, Any> =
        sanitizeEventProperties(properties, AnalyticsPayloadPolicy.DEFAULT)

    /** Keeps the first [AnalyticsPayloadPolicy.eventParameterLimit] properties, redacted and truncated. */
    public fun sanitizeEventProperties(
        properties: Map<String, Any>,
        policy: AnalyticsPayloadPolicy,
    ): LinkedHashMap<String, Any> {
        val sanitized = LinkedHashMap<String, Any>()
        properties.entries.take(policy.eventParameterLimit).forEach { (key, value) ->
            sanitized[key] = if (policy.isRedacted(key)) policy.redactedValue else sanitizeEventValue(value, policy)
        }
        return sanitized
    }

    public fun sanitizeUserProperties(properties: Map<String, Any>): LinkedHashMap<String, String> =
        sanitizeUserProperties(properties, AnalyticsPayloadPolicy.DEFAULT)

    /** Renders every user property as text, redacted and truncated. */
    public fun sanitizeUserProperties(
        properties: Map<String, Any>,
        policy: AnalyticsPayloadPolicy,
    ): LinkedHashMap<String, String> {
        val sanitized = LinkedHashMap<String, String>()
        properties.forEach { (key, value) ->
            sanitized[key] = if (policy.isRedacted(key)) policy.redactedValue else sanitizeUserPropertyValue(value, policy)
        }
        return sanitized
    }

    public fun sanitizeUserPropertyValue(value: Any): String = sanitizeUserPropertyValue(value, AnalyticsPayloadPolicy.DEFAULT)

    public fun sanitizeUserPropertyValue(
        value: Any,
        policy: AnalyticsPayloadPolicy,
    ): String = value.toString().take(policy.userPropertyValueLimit)

    private fun sanitizeEventValue(
        value: Any,
        policy: AnalyticsPayloadPolicy,
    ): Any =
        when (value) {
            is String -> value.take(policy.stringValueLimit)
            is Int, is Long, is Double, is Float, is Boolean -> value
            else -> value.toString().take(policy.stringValueLimit)
        }
}
