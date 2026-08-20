package io.github.maniramezan.kommon.designsystem

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class DesignTokensTest {
    @Test
    fun `rgb creates an opaque token`() {
        assertEquals(0xFF_12_34_56, ColorToken.rgb(0x12, 0x34, 0x56).argb)
    }

    @Test
    fun `argb preserves all channels`() {
        assertEquals(0x80_12_34_56, ColorToken.argb(0x80, 0x12, 0x34, 0x56).argb)
    }

    @Test
    fun `invalid channels fail explicitly`() {
        assertFailsWith<IllegalArgumentException> { ColorToken.rgb(-1, 0, 0) }
        assertFailsWith<IllegalArgumentException> { ColorToken.rgb(0, 0, 256) }
    }

    @Test
    fun `default theme provides contrasting modes and a shared scale`() {
        val tokens = KommonDesignTokens.default

        assertEquals(16f, tokens.spacing.medium)
        assertEquals(FontWeightToken.BOLD, tokens.typography.display.weight)
        assertEquals(0xFF_FF_FB_FE, tokens.lightColors.surface.argb)
        assertEquals(0xFF_12_12_16, tokens.darkColors.surface.argb)
    }
}
