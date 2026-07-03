package io.github.maniramezan.kommon.crashreporting

/** Abstraction over a crash-reporting SDK (Crashlytics, Sentry, Bugsnag, etc.). */
public interface CrashlyticsClient {
    public fun setUserId(userId: String)

    public fun clearUserId()

    public fun recordError(
        throwable: Throwable,
        context: Map<String, String> = emptyMap(),
    )

    public fun setCustomValue(
        key: String,
        value: String,
    )

    public fun log(message: String)
}

/** No-op crash reporter for builds where crash reporting is unavailable or disabled. */
public class NoOpCrashlyticsClient : CrashlyticsClient {
    override fun setUserId(userId: String): Unit = Unit

    override fun clearUserId(): Unit = Unit

    override fun recordError(
        throwable: Throwable,
        context: Map<String, String>,
    ): Unit = Unit

    override fun setCustomValue(
        key: String,
        value: String,
    ): Unit = Unit

    override fun log(message: String): Unit = Unit
}

/** In-memory [CrashlyticsClient] that records every call, for use in consumer unit tests. */
public class RecordingCrashlyticsClient : CrashlyticsClient {
    public data class RecordedError(
        val throwable: Throwable,
        val context: Map<String, String>,
    )

    public var userId: String? = null
        private set
    public val recordedErrors: MutableList<RecordedError> = mutableListOf()
    public val customValues: LinkedHashMap<String, String> = linkedMapOf()
    public val logMessages: MutableList<String> = mutableListOf()

    override fun setUserId(userId: String) {
        this.userId = userId
    }

    override fun clearUserId() {
        userId = null
    }

    override fun recordError(
        throwable: Throwable,
        context: Map<String, String>,
    ) {
        recordedErrors += RecordedError(throwable = throwable, context = context)
    }

    override fun setCustomValue(
        key: String,
        value: String,
    ) {
        customValues[key] = value
    }

    override fun log(message: String) {
        logMessages += message
    }
}
