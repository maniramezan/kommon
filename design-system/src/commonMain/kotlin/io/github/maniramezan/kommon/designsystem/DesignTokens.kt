package io.github.maniramezan.kommon.designsystem

/** A platform-neutral ARGB color value. UI adapters decide how it is rendered. */
public data class ColorToken(
    public val argb: Long,
) {
    init {
        require(argb in 0..MAX_ARGB) { "ARGB must be an unsigned 32-bit value" }
    }

    public companion object {
        private const val MAX_ARGB: Long = 0xFFFF_FFFFL

        public fun rgb(
            red: Int,
            green: Int,
            blue: Int,
        ): ColorToken = argb(alpha = 255, red = red, green = green, blue = blue)

        public fun argb(
            alpha: Int,
            red: Int,
            green: Int,
            blue: Int,
        ): ColorToken {
            require(alpha in BYTE_RANGE && red in BYTE_RANGE && green in BYTE_RANGE && blue in BYTE_RANGE) {
                "ARGB channels must be between 0 and 255"
            }
            return ColorToken(
                (alpha.toLong() shl 24) or
                    (red.toLong() shl 16) or
                    (green.toLong() shl 8) or
                    blue.toLong(),
            )
        }

        private val BYTE_RANGE: IntRange = 0..255
    }
}

/** Density-independent layout values. Compose adapters normally convert these values to `Dp`. */
public data class SpacingTokens(
    public val none: Float = 0f,
    public val extraSmall: Float = 4f,
    public val small: Float = 8f,
    public val medium: Float = 16f,
    public val large: Float = 24f,
    public val extraLarge: Float = 32f,
)

/** Density-independent corner radii. */
public data class ShapeTokens(
    public val smallRadius: Float = 4f,
    public val mediumRadius: Float = 8f,
    public val largeRadius: Float = 12f,
)

public enum class FontWeightToken {
    NORMAL,
    MEDIUM,
    SEMI_BOLD,
    BOLD,
}

/** Scale-independent typography values, interpreted as `sp` by Compose adapters. */
public data class TypeStyleToken(
    public val fontSize: Float,
    public val lineHeight: Float,
    public val weight: FontWeightToken = FontWeightToken.NORMAL,
)

public data class TypographyTokens(
    public val display: TypeStyleToken = TypeStyleToken(32f, 40f, FontWeightToken.BOLD),
    public val title: TypeStyleToken = TypeStyleToken(22f, 28f, FontWeightToken.SEMI_BOLD),
    public val body: TypeStyleToken = TypeStyleToken(14f, 20f),
    public val label: TypeStyleToken = TypeStyleToken(12f, 16f, FontWeightToken.MEDIUM),
    public val code: TypeStyleToken = TypeStyleToken(13f, 18f),
)

public data class MotionTokens(
    public val fastMilliseconds: Int = 100,
    public val standardMilliseconds: Int = 200,
    public val deliberateMilliseconds: Int = 300,
)

/**
 * Semantic roles shared by light and dark product themes.
 *
 * The nine core roles are required. The optional roles default to `null`, which tells a renderer
 * to derive that role from the core ones (KMPComponents' Material 3 adapter blends them, for
 * example). Set an optional role when a brand needs an exact value instead of the derived one.
 *
 * @property secondary accent for less prominent components; derived from [primary] when `null`.
 * @property onSecondary content on [secondary]; derived from [onPrimary] when `null`.
 * @property tertiary contrasting accent; derived from [primary] when `null`.
 * @property onTertiary content on [tertiary]; derived from [onPrimary] when `null`.
 * @property onSurfaceVariant secondary text and icons on [surfaceVariant].
 * @property outlineVariant decorative dividers and the borders of outlined controls.
 * @property onSuccess content on a [success] fill.
 * @property onWarning content on a [warning] fill.
 * @property onError content on an [error] fill.
 */
public data class ColorSchemeTokens(
    public val primary: ColorToken,
    public val onPrimary: ColorToken,
    public val surface: ColorToken,
    public val onSurface: ColorToken,
    public val surfaceVariant: ColorToken,
    public val outline: ColorToken,
    public val success: ColorToken,
    public val warning: ColorToken,
    public val error: ColorToken,
    public val secondary: ColorToken? = null,
    public val onSecondary: ColorToken? = null,
    public val tertiary: ColorToken? = null,
    public val onTertiary: ColorToken? = null,
    public val onSurfaceVariant: ColorToken? = null,
    public val outlineVariant: ColorToken? = null,
    public val onSuccess: ColorToken? = null,
    public val onWarning: ColorToken? = null,
    public val onError: ColorToken? = null,
) {
    /** The nine-role constructor that predates the optional roles, kept for binary compatibility. */
    public constructor(
        primary: ColorToken,
        onPrimary: ColorToken,
        surface: ColorToken,
        onSurface: ColorToken,
        surfaceVariant: ColorToken,
        outline: ColorToken,
        success: ColorToken,
        warning: ColorToken,
        error: ColorToken,
    ) : this(
        primary = primary,
        onPrimary = onPrimary,
        surface = surface,
        onSurface = onSurface,
        surfaceVariant = surfaceVariant,
        outline = outline,
        success = success,
        warning = warning,
        error = error,
        secondary = null,
    )
}

public data class ThemeTokens(
    public val lightColors: ColorSchemeTokens,
    public val darkColors: ColorSchemeTokens,
    public val spacing: SpacingTokens = SpacingTokens(),
    public val shapes: ShapeTokens = ShapeTokens(),
    public val typography: TypographyTokens = TypographyTokens(),
    public val motion: MotionTokens = MotionTokens(),
)

/** Neutral defaults that products can copy and brand without changing token semantics. */
public object KommonDesignTokens {
    public val default: ThemeTokens =
        ThemeTokens(
            lightColors =
                ColorSchemeTokens(
                    primary = ColorToken(0xFF_3F_51_B5),
                    onPrimary = ColorToken(0xFF_FF_FF_FF),
                    surface = ColorToken(0xFF_FF_FB_FE),
                    onSurface = ColorToken(0xFF_1B_1B_1F),
                    surfaceVariant = ColorToken(0xFF_E4_E1_E9),
                    outline = ColorToken(0xFF_77_74_7E),
                    success = ColorToken(0xFF_2E_7D_32),
                    warning = ColorToken(0xFF_9A_67_00),
                    error = ColorToken(0xFF_B3_26_1E),
                ),
            darkColors =
                ColorSchemeTokens(
                    primary = ColorToken(0xFF_BE_C2_FF),
                    onPrimary = ColorToken(0xFF_08_1A_64),
                    surface = ColorToken(0xFF_12_12_16),
                    onSurface = ColorToken(0xFF_E5_E1_E6),
                    surfaceVariant = ColorToken(0xFF_46_43_4D),
                    outline = ColorToken(0xFF_91_8F_99),
                    success = ColorToken(0xFF_81_C7_84),
                    warning = ColorToken(0xFF_FF_C1_4D),
                    error = ColorToken(0xFF_F2_B8_B5),
                ),
        )
}
