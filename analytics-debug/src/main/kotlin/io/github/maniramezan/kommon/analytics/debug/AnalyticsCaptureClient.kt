package io.github.maniramezan.kommon.analytics.debug

import io.github.maniramezan.kommon.analytics.AnalyticsClient
import io.github.maniramezan.kommon.analytics.AnalyticsEvent
import io.github.maniramezan.kommon.analytics.AnalyticsPayloadSanitizer

/**
 * Decorates an [AnalyticsClient], logging every call into an [AnalyticsDebugStore] (e.g. for an
 * in-app "recent analytics events" debug screen) before forwarding to [delegate].
 */
public class AnalyticsCaptureClient(
    private val delegate: AnalyticsClient,
    private val debugStore: AnalyticsDebugStore,
    private val clockMs: () -> Long = System::currentTimeMillis,
) : AnalyticsClient {
    override fun initialize(
        token: String,
        serverUrl: String?,
    ) {
        delegate.initialize(token, serverUrl)
    }

    override fun identify(userId: String) {
        debugStore.append(
            AnalyticsDebugEntry(
                timestampMs = clockMs(),
                action = AnalyticsDebugAction.IDENTIFY,
                name = "identify",
                properties = linkedMapOf("user_id" to userId),
            ),
        )
        delegate.identify(userId)
    }

    override fun track(event: AnalyticsEvent) {
        debugStore.append(
            AnalyticsDebugEntry(
                timestampMs = clockMs(),
                action = AnalyticsDebugAction.TRACK,
                name = event.name,
                properties = AnalyticsPayloadSanitizer.sanitizeEventProperties(event.properties),
            ),
        )
        delegate.track(event)
    }

    override fun setUserProperties(properties: Map<String, Any>) {
        val sanitized = AnalyticsPayloadSanitizer.sanitizeUserProperties(properties)
        debugStore.append(
            AnalyticsDebugEntry(
                timestampMs = clockMs(),
                action = AnalyticsDebugAction.SET_USER_PROPERTIES,
                name = "set_user_properties",
                properties = sanitized,
            ),
        )
        delegate.setUserProperties(properties)
    }

    override fun increment(
        property: String,
        value: Double,
    ) {
        debugStore.append(
            AnalyticsDebugEntry(
                timestampMs = clockMs(),
                action = AnalyticsDebugAction.INCREMENT,
                name = "increment",
                properties = linkedMapOf("property_name" to property, "value" to value),
            ),
        )
        delegate.increment(property, value)
    }

    override fun trackScreen(
        name: String,
        properties: Map<String, Any>,
    ) {
        val sanitized = AnalyticsPayloadSanitizer.sanitizeEventProperties(properties)
        sanitized["screen_name"] = name

        debugStore.append(
            AnalyticsDebugEntry(
                timestampMs = clockMs(),
                action = AnalyticsDebugAction.TRACK_SCREEN,
                name = "screen_view",
                properties = sanitized,
            ),
        )
        delegate.trackScreen(name, properties)
    }

    override fun reset() {
        debugStore.append(
            AnalyticsDebugEntry(
                timestampMs = clockMs(),
                action = AnalyticsDebugAction.RESET,
                name = "reset",
                properties = emptyMap(),
            ),
        )
        delegate.reset()
    }

    override fun flush() {
        debugStore.append(
            AnalyticsDebugEntry(
                timestampMs = clockMs(),
                action = AnalyticsDebugAction.FLUSH,
                name = "flush",
                properties = emptyMap(),
            ),
        )
        delegate.flush()
    }
}
