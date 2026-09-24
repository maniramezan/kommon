package io.github.maniramezan.kommon.remoteconfig

/**
 * A source of local overrides that shadow remote values (typically debug-only; see
 * `remoteconfig-debug`'s `LocalOverrideStore`). Returns `null` when [key] has no override.
 */
public fun interface ConfigOverrideSource {
    public fun override(key: ConfigKey): ConfigValue?
}

/**
 * Implements the documented [RemoteConfigClient] resolution order — local override, then fetched
 * remote value, then the key's compiled-in default — so every provider bridge resolves identically.
 *
 * Values from either layer that don't match the key ([ConfigKey.accepts]) are ignored and resolution
 * falls through to the next layer, so a stale override or a remote value of the wrong type can
 * never surface as the wrong type to callers.
 *
 * ```kotlin
 * class FirebaseRemoteConfigClient(private val firebase: FirebaseRemoteConfig, overrides: LocalOverrideStore) :
 *     RemoteConfigClient {
 *     private val resolver = ConfigResolver(AppConfigKeys.ALL_KEYS, overrides) { key -> firebase.readTyped(key) }
 *     override fun value(key: ConfigKey) = resolver.resolve(key).value
 *     override fun allValues() = resolver.resolveAll()
 *     override suspend fun fetchAndActivate() { firebase.fetchAndActivate().await() }
 * }
 * ```
 *
 * @param keys the app's key registry, used by [resolveAll].
 * @param overrides optional override layer; pass `null` in release builds.
 * @param remote reads the provider's cached value for a key, or `null` when it has none.
 */
public class ConfigResolver(
    private val keys: List<ConfigKey>,
    private val overrides: ConfigOverrideSource? = null,
    private val remote: (ConfigKey) -> ConfigValue?,
) {
    public fun resolve(key: ConfigKey): ResolvedConfigEntry =
        overrides?.override(key)?.takeIf(key::accepts)?.let { ResolvedConfigEntry(key, it, ValueSource.OVERRIDE) }
            ?: remote(key)?.takeIf(key::accepts)?.let { ResolvedConfigEntry(key, it, ValueSource.REMOTE) }
            ?: ResolvedConfigEntry(key, key.defaultValue, ValueSource.DEFAULT)

    public fun resolveAll(): List<ResolvedConfigEntry> = keys.map(::resolve)
}
