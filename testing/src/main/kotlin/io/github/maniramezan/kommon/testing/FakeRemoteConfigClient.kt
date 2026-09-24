package io.github.maniramezan.kommon.testing

import io.github.maniramezan.kommon.remoteconfig.ConfigKey
import io.github.maniramezan.kommon.remoteconfig.ConfigValue
import io.github.maniramezan.kommon.remoteconfig.RemoteConfigClient
import io.github.maniramezan.kommon.remoteconfig.ResolvedConfigEntry
import io.github.maniramezan.kommon.remoteconfig.ValueSource

/**
 * In-memory [RemoteConfigClient] fake seeded with fixed values, for consumer unit tests.
 *
 * @param overrides values returned instead of each key's default; mutate later with [set]/[clear].
 * @param keys the key registry reported by [allValues]. Defaults to the seeded keys only; pass the
 *   app's full `ALL_KEYS` to also see un-overridden keys (reported with [ValueSource.DEFAULT]).
 */
public class FakeRemoteConfigClient(
    overrides: Map<ConfigKey, ConfigValue> = emptyMap(),
    private val keys: List<ConfigKey>? = null,
) : RemoteConfigClient {
    private val values = overrides.toMutableMap()

    public var fetchAndActivateCallCount: Int = 0
        private set

    /** Replaces the value returned for [key]; rejects values of the wrong type. */
    public fun set(
        key: ConfigKey,
        value: ConfigValue,
    ) {
        require(key.accepts(value)) { "Value ${value.stringValue} is not valid for config key '${key.id}'" }
        values[key] = value
    }

    /** Reverts [key] to its compiled-in default. */
    public fun clear(key: ConfigKey) {
        values.remove(key)
    }

    override suspend fun fetchAndActivate() {
        fetchAndActivateCallCount += 1
    }

    override fun value(key: ConfigKey): ConfigValue = values[key] ?: key.defaultValue

    override fun allValues(): List<ResolvedConfigEntry> =
        (keys ?: values.keys.toList()).map { key ->
            values[key]?.let { ResolvedConfigEntry(key, it, ValueSource.OVERRIDE) }
                ?: ResolvedConfigEntry(key, key.defaultValue, ValueSource.DEFAULT)
        }
}
