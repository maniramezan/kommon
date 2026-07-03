package io.github.maniramezan.kommon.remoteconfig.debug

import android.content.Context
import android.content.SharedPreferences
import io.github.maniramezan.kommon.foundation.KommonLogger
import io.github.maniramezan.kommon.foundation.NoOpLogger
import io.github.maniramezan.kommon.remoteconfig.ConfigKey
import io.github.maniramezan.kommon.remoteconfig.ConfigValue
import io.github.maniramezan.kommon.remoteconfig.ConfigValueType

private const val LOG_TAG = "RemoteConfigOverride"

/**
 * Debug-only local override store for remote config values.
 *
 * In release builds ([isDebug] = false) the override accessors return null/no-op so they can
 * never accidentally shadow remote values in production.
 */
public class LocalOverrideStore(
    context: Context,
    private val isDebug: Boolean,
    prefsName: String = DEFAULT_PREFS_NAME,
    private val logger: KommonLogger = NoOpLogger,
) {
    private val prefs: SharedPreferences = context.getSharedPreferences(prefsName, Context.MODE_PRIVATE)

    public fun override(key: ConfigKey): ConfigValue? {
        if (!isDebug || !prefs.contains(key.id)) return null
        return when (key.valueType) {
            ConfigValueType.BOOL -> ConfigValue.Bool(prefs.getBoolean(key.id, false))
            ConfigValueType.STRING -> ConfigValue.StringVal(prefs.getString(key.id, "") ?: "")
            ConfigValueType.INT -> ConfigValue.Int(prefs.getInt(key.id, 0))
            ConfigValueType.DOUBLE -> ConfigValue.Double(java.lang.Double.longBitsToDouble(prefs.getLong(key.id, 0L)))
        }
    }

    public fun setOverride(
        key: ConfigKey,
        value: ConfigValue,
    ) {
        if (!isDebug) return
        prefs.edit().apply {
            when (value) {
                is ConfigValue.Bool -> putBoolean(key.id, value.value)
                is ConfigValue.StringVal -> putString(key.id, value.value)
                is ConfigValue.Int -> putInt(key.id, value.value)
                is ConfigValue.Double -> putLong(key.id, java.lang.Double.doubleToRawLongBits(value.value))
            }
            apply()
        }
        logger.debug(LOG_TAG, "Override set for ${key.id}: ${value.stringValue}")
    }

    public fun removeOverride(key: ConfigKey) {
        if (!isDebug) return
        prefs.edit().remove(key.id).apply()
        logger.debug(LOG_TAG, "Override removed for ${key.id}")
    }

    public fun removeAllOverrides() {
        if (!isDebug) return
        prefs.edit().clear().apply()
        logger.debug(LOG_TAG, "All overrides removed")
    }

    public fun hasOverride(key: ConfigKey): Boolean {
        if (!isDebug) return false
        return prefs.contains(key.id)
    }

    public companion object {
        public const val DEFAULT_PREFS_NAME: String = "io.github.maniramezan.kommon.remoteconfig.overrides"
    }
}
