package io.github.maniramezan.kommon.analytics

import io.github.maniramezan.kommon.foundation.KommonLogger
import io.github.maniramezan.kommon.foundation.NoOpLogger

private const val LOG_TAG = "Analytics"

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

/** Forwards every call to all configured [clients], so apps can fan out to multiple providers. */
public class CompositeAnalyticsClient(
    private val clients: List<AnalyticsClient>,
) : AnalyticsClient {
    override fun initialize(
        token: String,
        serverUrl: String?,
    ) {
        clients.forEach { it.initialize(token, serverUrl) }
    }

    override fun identify(userId: String) {
        clients.forEach { it.identify(userId) }
    }

    override fun track(event: AnalyticsEvent) {
        clients.forEach { it.track(event) }
    }

    override fun setUserProperties(properties: Map<String, Any>) {
        clients.forEach { it.setUserProperties(properties) }
    }

    override fun increment(
        property: String,
        value: Double,
    ) {
        clients.forEach { it.increment(property, value) }
    }

    override fun trackScreen(
        name: String,
        properties: Map<String, Any>,
    ) {
        clients.forEach { it.trackScreen(name, properties) }
    }

    override fun reset() {
        clients.forEach { it.reset() }
    }

    override fun flush() {
        clients.forEach { it.flush() }
    }
}
