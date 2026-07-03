package io.github.maniramezan.kommon.analytics

/** A trackable analytics event. */
public data class AnalyticsEvent(
    val name: String,
    val properties: Map<String, Any> = emptyMap(),
)

/**
 * Provider-agnostic analytics client interface. Apps implement (or bridge) this against
 * whatever analytics SDK they use (Firebase, Amplitude, PostHog, etc.); the shared library only
 * depends on this interface, never a concrete provider.
 */
public interface AnalyticsClient {
    /** Initialize the analytics SDK. */
    public fun initialize(
        token: String,
        serverUrl: String? = null,
    )

    /** Identify a user with a unique ID. */
    public fun identify(userId: String)

    /** Track an event with optional properties. */
    public fun track(event: AnalyticsEvent)

    /** Set user properties. */
    public fun setUserProperties(properties: Map<String, Any>)

    /** Increment a numeric user property. */
    public fun increment(
        property: String,
        value: Double,
    )

    /** Track a screen view. */
    public fun trackScreen(
        name: String,
        properties: Map<String, Any> = emptyMap(),
    )

    /** Reset user identity (logout). */
    public fun reset()

    /** Flush pending events. */
    public fun flush()
}
