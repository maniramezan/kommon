package io.github.maniramezan.kommon.designsystem

import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

/** WCAG 2.x minimum contrast for body text (level AA). */
public const val MIN_TEXT_CONTRAST: Double = 4.5

/** WCAG 2.x minimum contrast for large text, icons, and control boundaries (level AA). */
public const val MIN_NON_TEXT_CONTRAST: Double = 3.0

/** The alpha channel, `0..255`. */
public val ColorToken.alpha: Int get() = channel(ALPHA_SHIFT)

/** The red channel, `0..255`. */
public val ColorToken.red: Int get() = channel(RED_SHIFT)

/** The green channel, `0..255`. */
public val ColorToken.green: Int get() = channel(GREEN_SHIFT)

/** The blue channel, `0..255`. */
public val ColorToken.blue: Int get() = channel(BLUE_SHIFT)

/**
 * WCAG 2.x relative luminance in `0.0..1.0`, computed from the sRGB channels. Alpha is ignored, so
 * translucent tokens are measured as if they were opaque.
 */
public fun ColorToken.relativeLuminance(): Double =
    LUMINANCE_RED * linearize(red) + LUMINANCE_GREEN * linearize(green) + LUMINANCE_BLUE * linearize(blue)

/**
 * WCAG 2.x contrast ratio between this token and [other], from `1.0` (identical) to `21.0` (black on
 * white). The result does not depend on argument order. Compare it with [MIN_TEXT_CONTRAST] or
 * [MIN_NON_TEXT_CONTRAST] when validating a re-branded theme.
 */
public fun ColorToken.contrastRatio(other: ColorToken): Double {
    val first = relativeLuminance()
    val second = other.relativeLuminance()
    return (max(first, second) + CONTRAST_OFFSET) / (min(first, second) + CONTRAST_OFFSET)
}

private fun ColorToken.channel(shift: Int): Int = ((argb shr shift) and CHANNEL_MASK).toInt()

private fun linearize(channel: Int): Double {
    val srgb = channel / CHANNEL_MAX
    return if (srgb <= SRGB_LINEAR_THRESHOLD) srgb / SRGB_LINEAR_DIVISOR else ((srgb + SRGB_OFFSET) / SRGB_SCALE).pow(SRGB_GAMMA)
}

private const val ALPHA_SHIFT = 24
private const val RED_SHIFT = 16
private const val GREEN_SHIFT = 8
private const val BLUE_SHIFT = 0
private const val CHANNEL_MASK = 0xFFL
private const val CHANNEL_MAX = 255.0
private const val LUMINANCE_RED = 0.2126
private const val LUMINANCE_GREEN = 0.7152
private const val LUMINANCE_BLUE = 0.0722
private const val SRGB_LINEAR_THRESHOLD = 0.04045
private const val SRGB_LINEAR_DIVISOR = 12.92
private const val SRGB_OFFSET = 0.055
private const val SRGB_SCALE = 1.055
private const val SRGB_GAMMA = 2.4
private const val CONTRAST_OFFSET = 0.05
