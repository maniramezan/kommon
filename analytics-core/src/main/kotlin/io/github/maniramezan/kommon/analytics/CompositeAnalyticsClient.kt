package io.github.maniramezan.kommon.analytics

import io.github.maniramezan.kommon.foundation.KommonLogger
import io.github.maniramezan.kommon.foundation.NoOpLogger
import java.util.concurrent.CancellationException

/**
 * Forwards every call to all configured [clients], so apps can fan out to multiple providers.
 *
 * Each client is isolated: an exception from one provider SDK is logged to [logger] and does not
 * stop the remaining clients from receiving the call (nor propagate to the caller). Analytics is
 * observational and must never crash a user flow. Logger failures are isolated too.
 * [CancellationException] propagates to preserve coroutine cancellation.
 */
public class CompositeAnalyticsClient(
    private val clients: List<AnalyticsClient>,
    private val logger: KommonLogger = NoOpLogger,
) : AnalyticsClient {
    public constructor(vararg clients: AnalyticsClient) : this(clients.toList())

    override fun initialize(
        token: String,
        serverUrl: String?,
    ): Unit = forEachClient("initialize") { it.initialize(token, serverUrl) }

    override fun identify(userId: String): Unit = forEachClient("identify") { it.identify(userId) }

    override fun track(event: AnalyticsEvent): Unit = forEachClient("track") { it.track(event) }

    override fun setUserProperties(properties: Map<String, Any>): Unit =
        forEachClient("setUserProperties") { it.setUserProperties(properties) }

    override fun increment(
        property: String,
        value: Double,
    ): Unit = forEachClient("increment") { it.increment(property, value) }

    override fun trackScreen(
        name: String,
        properties: Map<String, Any>,
    ): Unit = forEachClient("trackScreen") { it.trackScreen(name, properties) }

    override fun reset(): Unit = forEachClient("reset") { it.reset() }

    override fun flush(): Unit = forEachClient("flush") { it.flush() }

    @Suppress("TooGenericExceptionCaught") // Isolate arbitrary provider-SDK failures; see class KDoc.
    private inline fun forEachClient(
        operation: String,
        call: (AnalyticsClient) -> Unit,
    ) {
        for (client in clients) {
            try {
                call(client)
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (error: Exception) {
                runCatching { logger.error(LOG_TAG, "${client::class.simpleName}.$operation failed", error) }
                    .onFailure { if (it is CancellationException) throw it }
            }
        }
    }
}
