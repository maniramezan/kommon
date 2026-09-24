package io.github.maniramezan.kommon.remoteconfig

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ConfigKeyValidationTest {
    @Test
    fun `factories build keys whose default matches the declared type`() {
        assertEquals(ConfigValueType.BOOL, ConfigKey.bool("a", "d", true).valueType)
        assertEquals(ConfigValue.StringVal("x"), ConfigKey.string("b", "d", "x").defaultValue)
        assertEquals(ConfigValue.Int(3), ConfigKey.int("c", "d", 3).defaultValue)
        assertEquals(ConfigValue.Double(1.5), ConfigKey.double("e", "d", 1.5).defaultValue)
    }

    @Test
    fun `a default of the wrong type is rejected at construction`() {
        assertFailsWith<IllegalArgumentException> {
            ConfigKey("flag", "d", ConfigValueType.BOOL, ConfigValue.StringVal("true"))
        }
    }

    @Test
    fun `allowed values must contain the default and only apply to strings`() {
        assertFailsWith<IllegalArgumentException> { ConfigKey.string("tier", "d", "gold", allowedValues = listOf("free", "pro")) }
        assertFailsWith<IllegalArgumentException> {
            ConfigKey("n", "d", ConfigValueType.INT, ConfigValue.Int(1), allowedValues = listOf("1"))
        }
    }

    @Test
    fun `accepts checks type and allowed values`() {
        val tier = ConfigKey.string("tier", "d", "free", allowedValues = listOf("free", "pro"))
        assertTrue(tier.accepts(ConfigValue.StringVal("pro")))
        assertFalse(tier.accepts(ConfigValue.StringVal("gold")))
        assertFalse(tier.accepts(ConfigValue.Int(1)))
    }

    @Test
    fun `string coercions tolerate surrounding whitespace and case`() {
        assertTrue(ConfigValue.StringVal(" TRUE ").boolValue)
        assertEquals(7, ConfigValue.StringVal(" 7 ").intValue)
        assertEquals(2.5, ConfigValue.StringVal(" 2.5").doubleValue)
    }
}

class ConfigResolverTest {
    private val flag = ConfigKey.bool("flag", "d", false)
    private val limit = ConfigKey.int("limit", "d", 10)

    @Test
    fun `override wins over remote which wins over default`() {
        val resolver =
            ConfigResolver(
                keys = listOf(flag, limit),
                overrides = { key -> if (key == flag) ConfigValue.Bool(true) else null },
                remote = { key -> if (key == limit) ConfigValue.Int(25) else ConfigValue.Bool(false) },
            )

        assertEquals(
            listOf(
                ResolvedConfigEntry(flag, ConfigValue.Bool(true), ValueSource.OVERRIDE),
                ResolvedConfigEntry(limit, ConfigValue.Int(25), ValueSource.REMOTE),
            ),
            resolver.resolveAll(),
        )
    }

    @Test
    fun `missing remote value falls back to the default`() {
        val resolver = ConfigResolver(keys = listOf(limit)) { null }
        assertEquals(ResolvedConfigEntry(limit, ConfigValue.Int(10), ValueSource.DEFAULT), resolver.resolve(limit))
    }

    @Test
    fun `values of the wrong type fall through to the next layer`() {
        val resolver =
            ConfigResolver(
                keys = listOf(limit),
                overrides = { ConfigValue.StringVal("oops") },
                remote = { ConfigValue.Bool(true) },
            )
        assertEquals(ValueSource.DEFAULT, resolver.resolve(limit).source)
    }
}
