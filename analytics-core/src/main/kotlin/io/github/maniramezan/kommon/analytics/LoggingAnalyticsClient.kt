package io.github.maniramezan.kommon.analytics

import io.github.maniramezan.kommon.foundation.KommonLogger

/** Analytics client that logs every call, for local and staging verification. */
public class LoggingAnalyticsClient(
    private val logger: KommonLogger,
) : AnalyticsClient {
    override fun initialize(
        token: String,
        serverUrl: String?,
    ) {
        logger.info(LOG_TAG, "Analytics logging initialized")
    }

    override fun identify(userId: String) {
        logger.info(LOG_TAG, "Identify user: $userId")
    }

    override fun track(event: AnalyticsEvent) {
        logger.info(LOG_TAG, "Track event: ${event.name}, properties: ${event.properties}")
    }

    override fun setUserProperties(properties: Map<String, Any>) {
        logger.info(LOG_TAG, "Set user properties: $properties")
    }

    override fun increment(
        property: String,
        value: Double,
    ) {
        logger.info(LOG_TAG, "Increment property: $property, by: $value")
    }

    override fun trackScreen(
        name: String,
        properties: Map<String, Any>,
    ) {
        logger.info(LOG_TAG, "Track screen: $name, properties: $properties")
    }

    override fun reset() {
        logger.info(LOG_TAG, "Reset analytics user")
    }

    override fun flush() {
        logger.info(LOG_TAG, "Flush analytics events")
    }
}
