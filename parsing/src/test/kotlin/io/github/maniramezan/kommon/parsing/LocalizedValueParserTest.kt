package io.github.maniramezan.kommon.parsing

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

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

    @Test
    fun `JsonPrimitive element parses like a plain string`() {
        assertEquals("hello", parser.parse(JsonPrimitive("hello")))
    }

    @Test
    fun `JsonObject element prefers en_us and ignores non-primitive entries`() {
        val element =
            buildJsonObject {
                put("en_us", JsonPrimitive("hello"))
                put("fr", JsonPrimitive("bonjour"))
                put("nested", JsonArray(emptyList()))
            }
        assertEquals("hello", parser.parse(element))
    }

    @Test
    fun `unsupported JsonElement kinds return null`() {
        assertNull(parser.parse(JsonArray(emptyList())))
        assertNull(parser.parse(JsonNull))
    }
}
