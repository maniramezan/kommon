package io.github.maniramezan.kommon.remoteconfig

/** The source of a resolved config value. */
public enum class ValueSource {
    DEFAULT,
    REMOTE,
    OVERRIDE,
}

/** A resolved config entry with its source. */
public data class ResolvedConfigEntry(
    val key: ConfigKey,
    val value: ConfigValue,
    val source: ValueSource,
)

/**
 * Remote config client — decoupled from any specific provider (Firebase Remote Config,
 * LaunchDarkly, etc.).
 *
 * Suggested resolution order for [value]: local override (debug builds only) -> fetched remote
 * value -> the key's compiled-in default.
 */
public interface RemoteConfigClient {
    /**
     * Fetch remote values and activate them. Call once at app launch.
     *
     * Offline and throttled fetches should be handled gracefully and not throw — the compiled-in
     * defaults (and any previously cached values) remain in effect. Only genuine, unexpected
     * failures should propagate to the caller.
     */
    public suspend fun fetchAndActivate()

    /** Resolve the effective value for a key (sync, from cache). */
    public fun value(key: ConfigKey): ConfigValue

    /** All known keys with their effective values (for a debug screen). */
    public fun allValues(): List<ResolvedConfigEntry>
}
