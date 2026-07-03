package io.github.maniramezan.kommon.parsing

import kotlin.test.Test
import kotlin.test.assertEquals

class LocalizedValueParserTest {
    private val parser = LocalizedValueParser()

    @Test
    fun `plain string passes through unchanged`() {
        assertEquals("hello", parser.parse("hello"))
    }

    @Test
    fun `locale map prefers en_us`() {
        val value = """{"en_us":"hello","fr":"bonjour"}"""
        assertEquals("hello", parser.parse(value))
    }

    @Test
    fun `locale map falls back to first non-blank value when preferred locale missing`() {
        val value = """{"fr":"bonjour","de":"hallo"}"""
        assertEquals("bonjour", parser.parse(value))
    }

    @Test
    fun `blank value returns null`() {
        assertEquals(null, parser.parse(""))
        assertEquals(null, parser.parse(null))
    }

    @Test
    fun `custom preferred locale keys are honored`() {
        val custom = LocalizedValueParser(preferredLocaleKeys = listOf("fr"))
        val value = """{"en_us":"hello","fr":"bonjour"}"""
        assertEquals("bonjour", custom.parse(value))
    }
}
