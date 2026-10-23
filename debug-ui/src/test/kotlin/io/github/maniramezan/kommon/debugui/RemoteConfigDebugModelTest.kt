package io.github.maniramezan.kommon.debugui

import io.github.maniramezan.kommon.remoteconfig.ConfigKey
import io.github.maniramezan.kommon.remoteconfig.ConfigValue
import io.github.maniramezan.kommon.remoteconfig.ResolvedConfigEntry
import io.github.maniramezan.kommon.remoteconfig.ValueSource
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class RemoteConfigDebugModelTest {
    private val flag = ConfigKey.bool("new_onboarding", "Show the redesigned onboarding", false)
    private val retries = ConfigKey.int("max_retries", "Retry budget", 3)
    private val ratio = ConfigKey.double("sample_ratio", "Trace sampling", 0.1)
    private val theme = ConfigKey.string("theme", "UI theme", "system", allowedValues = listOf("light", "dark", "system"))
    private val greeting = ConfigKey.string("greeting", "Home greeting", "Hi")

    @Test
    fun `numbers are trimmed and parsed by key type`() {
        assertEquals(ConfigValue.Int(5), retries.parseOverride(" 5 "))
        assertEquals(ConfigValue.Double(0.25), ratio.parseOverride("0.25"))
        assertNull(retries.parseOverride("5.5"))
        assertNull(ratio.parseOverride("NaN"))
    }

    @Test
    fun `booleans accept only true and false ignoring case`() {
        assertEquals(ConfigValue.Bool(true), flag.parseOverride("TRUE"))
        assertEquals(ConfigValue.Bool(false), flag.parseOverride("false"))
        assertNull(flag.parseOverride("yes"))
    }

    @Test
    fun `strings keep their whitespace and respect allowed values`() {
        assertEquals(ConfigValue.StringVal(" Hello "), greeting.parseOverride(" Hello "))
        assertEquals(ConfigValue.StringVal("dark"), theme.parseOverride("dark"))
        assertNull(theme.parseOverride("sepia"))
    }

    @Test
    fun `matching filters by id or description and ignores a blank query`() {
        val entries =
            listOf(flag, retries, theme).map { ResolvedConfigEntry(it, it.defaultValue, ValueSource.DEFAULT) }

        assertEquals(entries, entries.matching("  "))
        assertEquals(listOf("max_retries"), entries.matching("RETRY").map { it.key.id })
        assertEquals(listOf("new_onboarding"), entries.matching("onboard").map { it.key.id })
    }
}
