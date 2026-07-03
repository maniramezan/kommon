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

    public val boolValue: Boolean
        get() =
            when (this) {
                is Bool -> value
                is StringVal -> value.lowercase() == "true" || value == "1"
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
                is StringVal -> value.toIntOrNull() ?: 0
                is Int -> value
                is Double -> value.toInt()
            }

    public val doubleValue: kotlin.Double
        get() =
            when (this) {
                is Bool -> if (value) 1.0 else 0.0
                is StringVal -> value.toDoubleOrNull() ?: 0.0
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
)
