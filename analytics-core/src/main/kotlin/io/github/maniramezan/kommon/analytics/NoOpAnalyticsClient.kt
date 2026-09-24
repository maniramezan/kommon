package io.github.maniramezan.kommon.analytics

import io.github.maniramezan.kommon.foundation.KommonLogger
import io.github.maniramezan.kommon.foundation.NoOpLogger

internal const val LOG_TAG: String = "Analytics"

/** Analytics client that does nothing but log at debug level. Useful for debug builds. */
public class NoOpAnalyticsClient(
    private val logger: KommonLogger = NoOpLogger,
) : AnalyticsClient {
    override fun initialize(
        token: String,
        serverUrl: String?,
    ) {
        logger.debug(LOG_TAG, "Analytics disabled - skipping initialization")
    }

    override fun identify(userId: String) {
        logger.debug(LOG_TAG, "Analytics disabled - skipping identify: $userId")
    }

    override fun track(event: AnalyticsEvent) {
        logger.debug(LOG_TAG, "Analytics disabled - skipping event: ${event.name}")
    }

    override fun setUserProperties(properties: Map<String, Any>) {
        logger.debug(LOG_TAG, "Analytics disabled - skipping user properties")
    }

    override fun increment(
        property: String,
        value: Double,
    ) {
        logger.debug(LOG_TAG, "Analytics disabled - skipping increment: $property")
    }

    override fun trackScreen(
        name: String,
        properties: Map<String, Any>,
    ) {
        logger.debug(LOG_TAG, "Analytics disabled - skipping screen: $name")
    }

    override fun reset() {
        logger.debug(LOG_TAG, "Analytics disabled - skipping reset")
    }

    override fun flush() {
        logger.debug(LOG_TAG, "Analytics disabled - skipping flush")
    }
}
