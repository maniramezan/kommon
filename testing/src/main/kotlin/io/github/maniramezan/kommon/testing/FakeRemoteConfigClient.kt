package io.github.maniramezan.kommon.testing

import io.github.maniramezan.kommon.remoteconfig.ConfigKey
import io.github.maniramezan.kommon.remoteconfig.ConfigValue
import io.github.maniramezan.kommon.remoteconfig.RemoteConfigClient
import io.github.maniramezan.kommon.remoteconfig.ResolvedConfigEntry
import io.github.maniramezan.kommon.remoteconfig.ValueSource

/** In-memory [RemoteConfigClient] fake seeded with fixed values, for consumer unit tests. */
public class FakeRemoteConfigClient(
    private val overrides: Map<ConfigKey, ConfigValue> = emptyMap(),
) : RemoteConfigClient {
    public var fetchAndActivateCallCount: Int = 0
        private set

    override suspend fun fetchAndActivate() {
        fetchAndActivateCallCount += 1
    }

    override fun value(key: ConfigKey): ConfigValue = overrides[key] ?: key.defaultValue

    override fun allValues(): List<ResolvedConfigEntry> =
        overrides.map { (key, value) -> ResolvedConfigEntry(key, value, ValueSource.OVERRIDE) }
}
