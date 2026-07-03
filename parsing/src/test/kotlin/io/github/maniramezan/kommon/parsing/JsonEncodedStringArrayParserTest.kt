package io.github.maniramezan.kommon.parsing

import kotlin.test.Test
import kotlin.test.assertEquals

class JsonEncodedStringArrayParserTest {
    @Test
    fun `parses a JSON string array and joins it`() {
        val result = JsonEncodedStringArrayParser.parseOrPassthrough("""["a","b","c"]""")
        assertEquals("a\nb\nc", result)
    }

    @Test
    fun `passes through a plain string unchanged`() {
        assertEquals("plain text", JsonEncodedStringArrayParser.parseOrPassthrough("plain text"))
    }

    @Test
    fun `blank input returns empty string`() {
        assertEquals("", JsonEncodedStringArrayParser.parseOrPassthrough(null))
        assertEquals("", JsonEncodedStringArrayParser.parseOrPassthrough(""))
    }

    @Test
    fun `parseOrList flattens JSON arrays and plain strings`() {
        val result = JsonEncodedStringArrayParser.parseOrList(listOf("""["a","b"]""", "c"))
        assertEquals(listOf("a", "b", "c"), result)
    }

    @Test
    fun `parseOrList returns empty list for null or empty input`() {
        assertEquals(emptyList(), JsonEncodedStringArrayParser.parseOrList(null))
        assertEquals(emptyList(), JsonEncodedStringArrayParser.parseOrList(emptyList()))
    }
}
