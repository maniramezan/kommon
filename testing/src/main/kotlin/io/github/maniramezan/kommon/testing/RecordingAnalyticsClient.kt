package io.github.maniramezan.kommon.testing

import io.github.maniramezan.kommon.analytics.AnalyticsClient
import io.github.maniramezan.kommon.analytics.AnalyticsEvent

/** In-memory [AnalyticsClient] fake that records every call, for consumer unit tests. */
public class RecordingAnalyticsClient : AnalyticsClient {
    /** One recorded [AnalyticsClient.trackScreen] call. */
    public data class ScreenView(
        val name: String,
        val properties: Map<String, Any>,
    )

    /** One recorded [AnalyticsClient.increment] call. */
    public data class Increment(
        val property: String,
        val value: Double,
    )

    public val trackedEvents: MutableList<AnalyticsEvent> = mutableListOf()
    public val identifiedUserIds: MutableList<String> = mutableListOf()
    public val trackedScreens: MutableList<ScreenView> = mutableListOf()
    public val increments: MutableList<Increment> = mutableListOf()

    /** Latest value of every user property set via [setUserProperties]. */
    public val userProperties: LinkedHashMap<String, Any> = linkedMapOf()

    /** The `token` passed to the most recent [initialize] call, or `null` if never initialized. */
    public var initializedToken: String? = null
        private set
    public var wasReset: Boolean = false
        private set
    public var wasFlushed: Boolean = false
        private set

    /** Names of every tracked event, in order — the most common assertion in consumer tests. */
    public val trackedEventNames: List<String>
        get() = trackedEvents.map(AnalyticsEvent::name)

    override fun initialize(
        token: String,
        serverUrl: String?,
    ) {
        initializedToken = token
    }

    override fun identify(userId: String) {
        identifiedUserIds += userId
    }

    override fun track(event: AnalyticsEvent) {
        trackedEvents += event
    }

    override fun setUserProperties(properties: Map<String, Any>) {
        userProperties.putAll(properties)
    }

    override fun increment(
        property: String,
        value: Double,
    ) {
        increments += Increment(property, value)
    }

    override fun trackScreen(
        name: String,
        properties: Map<String, Any>,
    ) {
        trackedScreens += ScreenView(name, properties)
    }

    override fun reset() {
        wasReset = true
    }

    override fun flush() {
        wasFlushed = true
    }
}
