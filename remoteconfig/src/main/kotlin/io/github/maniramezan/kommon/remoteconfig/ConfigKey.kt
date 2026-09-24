package io.github.maniramezan.kommon.remoteconfig

/** The type of a remote config value. */
public enum class ConfigValueType {
    BOOL,
    STRING,
    INT,
    DOUBLE,
}

/** A typed remote config value. */
public sealed class ConfigValue {
    public data class Bool(
        val value: Boolean,
    ) : ConfigValue()

    public data class StringVal(
        val value: String,
    ) : ConfigValue()

    public data class Int(
        val value: kotlin.Int,
    ) : ConfigValue()

    public data class Double(
        val value: kotlin.Double,
    ) : ConfigValue()

    /** The [ConfigValueType] this value carries. */
    public val type: ConfigValueType
        get() =
            when (this) {
                is Bool -> ConfigValueType.BOOL
                is StringVal -> ConfigValueType.STRING
                is Int -> ConfigValueType.INT
                is Double -> ConfigValueType.DOUBLE
            }

    public val boolValue: Boolean
        get() =
            when (this) {
                is Bool -> value
                is StringVal -> value.trim().let { it.equals("true", ignoreCase = true) || it == "1" }
                is Int -> value != 0
                is Double -> value != 0.0
            }

    public val stringValue: String
        get() =
            when (this) {
                is Bool -> value.toString()
                is StringVal -> value
                is Int -> value.toString()
                is Double -> value.toString()
            }

    public val intValue: kotlin.Int
        get() =
            when (this) {
                is Bool -> if (value) 1 else 0
                is StringVal -> value.trim().toIntOrNull() ?: 0
                is Int -> value
                is Double -> value.toInt()
            }

    public val doubleValue: kotlin.Double
        get() =
            when (this) {
                is Bool -> if (value) 1.0 else 0.0
                is StringVal -> value.trim().toDoubleOrNull() ?: 0.0
                is Int -> value.toDouble()
                is Double -> value
            }
}

/**
 * A typed remote config key with a compiled-in default.
 *
 * Unlike Novalingo's original version, this generic [ConfigKey] carries no built-in registry —
 * apps own their own `object AppConfigKeys { val ALL_KEYS = listOf(...) }` and pass it to
 * whatever debug override UI they build on top of [RemoteConfigClient.allValues].
 *
 * Construction validates that [defaultValue] matches [valueType] (and, for constrained string
 * keys, that the default is one of [allowedValues]), so a mistyped key fails at startup instead of
 * silently resolving the wrong type later. Prefer the typed factories:
 *
 * ```kotlin
 * object AppConfigKeys {
 *     val NEW_ONBOARDING = ConfigKey.bool("new_onboarding", "Show the redesigned onboarding", false)
 *     val THEME = ConfigKey.string("theme", "UI theme", "system", allowedValues = listOf("light", "dark", "system"))
 *     val ALL_KEYS = listOf(NEW_ONBOARDING, THEME)
 * }
 * ```
 */
public data class ConfigKey(
    val id: String,
    val description: String,
    val valueType: ConfigValueType,
    val defaultValue: ConfigValue,
    /**
     * When set, the value is constrained to this fixed set of well-known choices. A debug
     * override UI can render a dropdown instead of a free-text field. Only meaningful for
     * [ConfigValueType.STRING] keys.
     */
    val allowedValues: List<String>? = null,
) {
    init {
        require(id.isNotBlank()) { "ConfigKey id must not be blank" }
        require(defaultValue.type == valueType) {
            "ConfigKey '$id' declares $valueType but its default is ${defaultValue.type}"
        }
        require(allowedValues == null || valueType == ConfigValueType.STRING) {
            "ConfigKey '$id': allowedValues is only supported for STRING keys"
        }
        require(accepts(defaultValue)) { "ConfigKey '$id': default '${defaultValue.stringValue}' is not in allowedValues" }
    }

    /** True when [value] has this key's type and, for constrained string keys, is an allowed choice. */
    public fun accepts(value: ConfigValue): Boolean =
        value.type == valueType &&
            (value !is ConfigValue.StringVal || allowedValues == null || value.value in allowedValues)

    public companion object {
        public fun bool(
            id: String,
            description: String,
            default: Boolean,
        ): ConfigKey = ConfigKey(id, description, ConfigValueType.BOOL, ConfigValue.Bool(default))

        public fun string(
            id: String,
            description: String,
            default: String,
            allowedValues: List<String>? = null,
        ): ConfigKey = ConfigKey(id, description, ConfigValueType.STRING, ConfigValue.StringVal(default), allowedValues)

        public fun int(
            id: String,
            description: String,
            default: Int,
        ): ConfigKey = ConfigKey(id, description, ConfigValueType.INT, ConfigValue.Int(default))

        public fun double(
            id: String,
            description: String,
            default: Double,
        ): ConfigKey = ConfigKey(id, description, ConfigValueType.DOUBLE, ConfigValue.Double(default))
    }
}
