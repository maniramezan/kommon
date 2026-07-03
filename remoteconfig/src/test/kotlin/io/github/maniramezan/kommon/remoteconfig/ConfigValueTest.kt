package io.github.maniramezan.kommon.remoteconfig

import kotlin.test.Test
import kotlin.test.assertEquals

class ConfigValueTest {
    @Test
    fun `bool value coercions`() {
        assertEquals(true, ConfigValue.Bool(true).boolValue)
        assertEquals("true", ConfigValue.Bool(true).stringValue)
        assertEquals(1, ConfigValue.Bool(true).intValue)
        assertEquals(1.0, ConfigValue.Bool(true).doubleValue)
    }

    @Test
    fun `string value coercions`() {
        assertEquals(true, ConfigValue.StringVal("true").boolValue)
        assertEquals(true, ConfigValue.StringVal("1").boolValue)
        assertEquals(false, ConfigValue.StringVal("nope").boolValue)
        assertEquals(42, ConfigValue.StringVal("42").intValue)
        assertEquals(0, ConfigValue.StringVal("not-a-number").intValue)
        assertEquals(3.5, ConfigValue.StringVal("3.5").doubleValue)
    }

    @Test
    fun `int and double value coercions`() {
        assertEquals(false, ConfigValue.Int(0).boolValue)
        assertEquals(true, ConfigValue.Int(1).boolValue)
        assertEquals("5", ConfigValue.Int(5).stringValue)
        assertEquals(5.0, ConfigValue.Int(5).doubleValue)
        assertEquals(2, ConfigValue.Double(2.9).intValue)
        assertEquals(false, ConfigValue.Double(0.0).boolValue)
    }
}

class ConfigKeyTest {
    @Test
    fun `config key and resolved entry carry the expected fields`() {
        val key =
            ConfigKey(
                id = "flag",
                description = "a flag",
                valueType = ConfigValueType.STRING,
                defaultValue = ConfigValue.StringVal("a"),
                allowedValues = listOf("a", "b"),
            )
        val entry = ResolvedConfigEntry(key, ConfigValue.StringVal("b"), ValueSource.REMOTE)

        assertEquals("flag", entry.key.id)
        assertEquals(listOf("a", "b"), entry.key.allowedValues)
        assertEquals(ValueSource.REMOTE, entry.source)
        assertEquals(ConfigValue.StringVal("b"), entry.value)
        assertEquals(key, key.copy())
    }

    @Test
    fun `value source has three variants`() {
        assertEquals(listOf(ValueSource.DEFAULT, ValueSource.REMOTE, ValueSource.OVERRIDE), ValueSource.entries)
    }
}
