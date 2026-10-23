package io.github.maniramezan.kommon.analytics.debug

import io.github.maniramezan.kommon.analytics.AnalyticsClient
import io.github.maniramezan.kommon.analytics.AnalyticsEvent
import io.github.maniramezan.kommon.analytics.AnalyticsPayloadSanitizer
import io.github.maniramezan.kommon.foundation.KommonLogger
import io.github.maniramezan.kommon.foundation.NoOpLogger
import kotlinx.coroutines.CancellationException

/**
 * Decorates an [AnalyticsClient], logging every call into an [AnalyticsDebugStore] (e.g. for an
 * in-app "recent analytics events" debug screen) before forwarding to [delegate].
 *
 * Capture is observational: a failing [debugStore] is logged to [logger] and never stops the call
 * from reaching [delegate].
 */
public class AnalyticsCaptureClient(
    private val delegate: AnalyticsClient,
    private val debugStore: AnalyticsDebugStore,
    private val clockMs: () -> Long = System::currentTimeMillis,
    private val logger: KommonLogger = NoOpLogger,
) : AnalyticsClient {
    override fun initialize(
        token: String,
        serverUrl: String?,
    ) {
        delegate.initialize(token, serverUrl)
    }

    override fun identify(userId: String) {
        capture {
            AnalyticsDebugEntry(
                timestampMs = clockMs(),
                action = AnalyticsDebugAction.IDENTIFY,
                name = "identify",
                properties = linkedMapOf("user_id" to userId),
            )
        }
        delegate.identify(userId)
    }

    override fun track(event: AnalyticsEvent) {
        capture {
            AnalyticsDebugEntry(
                timestampMs = clockMs(),
                action = AnalyticsDebugAction.TRACK,
                name = event.name,
                properties = AnalyticsPayloadSanitizer.sanitizeEventProperties(event.properties),
            )
        }
        delegate.track(event)
    }

    override fun setUserProperties(properties: Map<String, Any>) {
        capture {
            AnalyticsDebugEntry(
                timestampMs = clockMs(),
                action = AnalyticsDebugAction.SET_USER_PROPERTIES,
                name = "set_user_properties",
                properties = AnalyticsPayloadSanitizer.sanitizeUserProperties(properties),
            )
        }
        delegate.setUserProperties(properties)
    }

    override fun increment(
        property: String,
        value: Double,
    ) {
        capture {
            AnalyticsDebugEntry(
                timestampMs = clockMs(),
                action = AnalyticsDebugAction.INCREMENT,
                name = "increment",
                properties = linkedMapOf("property_name" to property, "value" to value),
            )
        }
        delegate.increment(property, value)
    }

    override fun trackScreen(
        name: String,
        properties: Map<String, Any>,
    ) {
        capture {
            val sanitized = AnalyticsPayloadSanitizer.sanitizeEventProperties(properties)
            sanitized["screen_name"] = name
            AnalyticsDebugEntry(
                timestampMs = clockMs(),
                action = AnalyticsDebugAction.TRACK_SCREEN,
                name = "screen_view",
                properties = sanitized,
            )
        }
        delegate.trackScreen(name, properties)
    }

    override fun reset() {
        capture {
            AnalyticsDebugEntry(
                timestampMs = clockMs(),
                action = AnalyticsDebugAction.RESET,
                name = "reset",
                properties = emptyMap(),
            )
        }
        delegate.reset()
    }

    override fun flush() {
        capture {
            AnalyticsDebugEntry(
                timestampMs = clockMs(),
                action = AnalyticsDebugAction.FLUSH,
                name = "flush",
                properties = emptyMap(),
            )
        }
        delegate.flush()
    }

    @Suppress("TooGenericExceptionCaught") // A broken debug store must never break analytics.
    private inline fun capture(entry: () -> AnalyticsDebugEntry) {
        try {
            debugStore.append(entry())
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (error: Exception) {
            runCatching { logger.error(LOG_TAG, "Analytics debug capture failed", error) }
        }
    }

    private companion object {
        const val LOG_TAG = "AnalyticsCapture"
    }
}
