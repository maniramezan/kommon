package io.github.maniramezan.kommon.designsystem

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

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

    @Test
    fun `optional roles default to null so renderers derive them`() {
        val light = KommonDesignTokens.default.lightColors

        assertNull(light.secondary)
        assertNull(light.onSurfaceVariant)
        assertNull(light.onError)
    }

    @Test
    fun `nine-role constructor still builds a scheme`() {
        val black = ColorToken(0xFF_00_00_00)
        val white = ColorToken(0xFF_FF_FF_FF)
        val scheme = ColorSchemeTokens(black, white, white, black, white, black, black, black, black)

        assertEquals(black, scheme.primary)
        assertNull(scheme.tertiary)
    }

    @Test
    fun `channel accessors split the packed argb value`() {
        val token = ColorToken.argb(0x80, 0x12, 0x34, 0x56)

        assertEquals(listOf(0x80, 0x12, 0x34, 0x56), listOf(token.alpha, token.red, token.green, token.blue))
    }

    @Test
    fun `contrast ratio spans one to twenty-one and is symmetric`() {
        val black = ColorToken(0xFF_00_00_00)
        val white = ColorToken(0xFF_FF_FF_FF)

        assertEquals(21.0, black.contrastRatio(white), absoluteTolerance = 0.001)
        assertEquals(1.0, white.contrastRatio(white), absoluteTolerance = 0.001)
        assertEquals(black.contrastRatio(white), white.contrastRatio(black))
    }

    @Test
    fun `default themes meet WCAG AA for their core pairs`() {
        val tokens = KommonDesignTokens.default
        listOf(tokens.lightColors, tokens.darkColors).forEach { colors ->
            assertTrue(colors.onPrimary.contrastRatio(colors.primary) >= MIN_TEXT_CONTRAST)
            assertTrue(colors.onSurface.contrastRatio(colors.surface) >= MIN_TEXT_CONTRAST)
            assertTrue(colors.onSurface.contrastRatio(colors.surfaceVariant) >= MIN_TEXT_CONTRAST)
            assertTrue(colors.outline.contrastRatio(colors.surface) >= MIN_NON_TEXT_CONTRAST)
            listOf(colors.success, colors.warning, colors.error).forEach { accent ->
                assertTrue(accent.contrastRatio(colors.surface) >= MIN_NON_TEXT_CONTRAST)
            }
        }
    }
}
