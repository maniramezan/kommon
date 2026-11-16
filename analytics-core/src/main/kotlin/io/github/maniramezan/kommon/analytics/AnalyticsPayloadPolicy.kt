package io.github.maniramezan.kommon.analytics

/**
 * Limits and redaction rules applied by [AnalyticsPayloadSanitizer] and [SanitizingAnalyticsClient].
 *
 * The defaults match the conservative limits most providers share. Keys in [redactedKeys] are
 * matched case-insensitively and keep their key with [redactedValue] as the value, so dashboards
 * still show that the property was sent.
 *
 * @property eventParameterLimit maximum number of properties kept per event; extra ones are dropped.
 * @property stringValueLimit maximum length of an event property value rendered as text.
 * @property userPropertyValueLimit maximum length of a user property value.
 * @property redactedKeys property keys whose values are never sent, such as `email` or `token`.
 * @property redactedValue the value sent in place of a redacted one.
 */
public data class AnalyticsPayloadPolicy(
    val eventParameterLimit: Int = DEFAULT_EVENT_PARAMETER_LIMIT,
    val stringValueLimit: Int = DEFAULT_STRING_VALUE_LIMIT,
    val userPropertyValueLimit: Int = DEFAULT_USER_PROPERTY_VALUE_LIMIT,
    val redactedKeys: Set<String> = emptySet(),
    val redactedValue: String = DEFAULT_REDACTED_VALUE,
) {
    init {
        require(eventParameterLimit > 0) { "eventParameterLimit must be greater than zero" }
        require(stringValueLimit > 0) { "stringValueLimit must be greater than zero" }
        require(userPropertyValueLimit > 0) { "userPropertyValueLimit must be greater than zero" }
    }

    private val normalizedRedactedKeys: Set<String> = redactedKeys.mapTo(HashSet()) { it.lowercase() }

    /** `true` when values for [key] must be replaced with [redactedValue]. */
    public fun isRedacted(key: String): Boolean = normalizedRedactedKeys.isNotEmpty() && key.lowercase() in normalizedRedactedKeys

    public companion object {
        public const val DEFAULT_EVENT_PARAMETER_LIMIT: Int = 25
        public const val DEFAULT_STRING_VALUE_LIMIT: Int = 100
        public const val DEFAULT_USER_PROPERTY_VALUE_LIMIT: Int = 36
        public const val DEFAULT_REDACTED_VALUE: String = "[redacted]"

        /** The limits [AnalyticsPayloadSanitizer] has always applied, with no redaction. */
        public val DEFAULT: AnalyticsPayloadPolicy = AnalyticsPayloadPolicy()
    }
}
