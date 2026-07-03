package io.github.maniramezan.kommon.testing

import io.github.maniramezan.kommon.analytics.AnalyticsClient
import io.github.maniramezan.kommon.analytics.AnalyticsEvent

/** In-memory [AnalyticsClient] fake that records every call, for consumer unit tests. */
public class RecordingAnalyticsClient : AnalyticsClient {
    public val trackedEvents: MutableList<AnalyticsEvent> = mutableListOf()
    public val identifiedUserIds: MutableList<String> = mutableListOf()
    public var wasReset: Boolean = false
        private set
    public var wasFlushed: Boolean = false
        private set

    override fun initialize(
        token: String,
        serverUrl: String?,
    ): Unit = Unit

    override fun identify(userId: String) {
        identifiedUserIds += userId
    }

    override fun track(event: AnalyticsEvent) {
        trackedEvents += event
    }

    override fun setUserProperties(properties: Map<String, Any>): Unit = Unit

    override fun increment(
        property: String,
        value: Double,
    ): Unit = Unit

    override fun trackScreen(
        name: String,
        properties: Map<String, Any>,
    ): Unit = Unit

    override fun reset() {
        wasReset = true
    }

    override fun flush() {
        wasFlushed = true
    }
}
