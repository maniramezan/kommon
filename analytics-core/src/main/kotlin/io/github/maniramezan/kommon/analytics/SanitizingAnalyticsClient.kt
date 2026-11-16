package io.github.maniramezan.kommon.analytics

/**
 * Decorates an [AnalyticsClient] so every event, screen view, and user-property payload passes
 * through [AnalyticsPayloadSanitizer] with [policy] before reaching [delegate].
 *
 * Wrap it outermost so every client behind it, including a debug capture client, only sees
 * sanitized payloads:
 *
 * ```kotlin
 * val policy = AnalyticsPayloadPolicy(redactedKeys = setOf("email", "token"))
 * val analytics: AnalyticsClient =
 *     SanitizingAnalyticsClient(CompositeAnalyticsClient(listOf(firebase, amplitude), logger), policy)
 * ```
 *
 * [identify] and [increment] are forwarded unchanged: user IDs and counter names are not payloads.
 */
public class SanitizingAnalyticsClient(
    private val delegate: AnalyticsClient,
    private val policy: AnalyticsPayloadPolicy = AnalyticsPayloadPolicy.DEFAULT,
) : AnalyticsClient {
    override fun initialize(
        token: String,
        serverUrl: String?,
    ) {
        delegate.initialize(token, serverUrl)
    }

    override fun identify(userId: String) {
        delegate.identify(userId)
    }

    override fun track(event: AnalyticsEvent) {
        delegate.track(event.copy(properties = AnalyticsPayloadSanitizer.sanitizeEventProperties(event.properties, policy)))
    }

    override fun setUserProperties(properties: Map<String, Any>) {
        delegate.setUserProperties(AnalyticsPayloadSanitizer.sanitizeUserProperties(properties, policy))
    }

    override fun increment(
        property: String,
        value: Double,
    ) {
        delegate.increment(property, value)
    }

    override fun trackScreen(
        name: String,
        properties: Map<String, Any>,
    ) {
        delegate.trackScreen(name, AnalyticsPayloadSanitizer.sanitizeEventProperties(properties, policy))
    }

    override fun reset() {
        delegate.reset()
    }

    override fun flush() {
        delegate.flush()
    }
}
