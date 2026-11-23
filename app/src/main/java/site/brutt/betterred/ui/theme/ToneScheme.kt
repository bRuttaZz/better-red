package site.brutt.betterred.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin

/** Hues (OKLCH degrees) for the extra themes. */
object Tones {
    const val GREEN = 145.0  // leaf
    const val YELLOW = 85.0  // sunflower gold
}

/**
 * Builds a full Material 3 color scheme from one hue, the same way the HTML prototype did.
 * Tertiary (used quietly for the fertile window) is the opposite hue.
 */
fun toneScheme(hue: Double, dark: Boolean): ColorScheme {
    val t = (hue + 170) % 360
    fun c(l: Double, ch: Double, h: Double = hue) = oklch(l, ch, h)
    return if (!dark) lightColorScheme(
        primary = c(0.52, 0.13), onPrimary = Color.White,
        primaryContainer = c(0.92, 0.06), onPrimaryContainer = c(0.30, 0.09),
        secondary = c(0.50, 0.04), onSecondary = Color.White,
        secondaryContainer = c(0.92, 0.035), onSecondaryContainer = c(0.28, 0.04),
        tertiary = c(0.52, 0.08, t), onTertiary = Color.White,
        tertiaryContainer = c(0.91, 0.05, t), onTertiaryContainer = c(0.30, 0.06, t),
        background = c(0.985, 0.006), onBackground = c(0.22, 0.01),
        surface = c(0.985, 0.006), onSurface = c(0.22, 0.01),
        surfaceVariant = c(0.92, 0.02), onSurfaceVariant = c(0.45, 0.02),
        outline = c(0.65, 0.02), outlineVariant = c(0.86, 0.015),
        surfaceContainerLowest = Color.White,
        surfaceContainerLow = c(0.968, 0.009),
        surfaceContainer = c(0.952, 0.012),
        surfaceContainerHigh = c(0.935, 0.014),
        surfaceContainerHighest = c(0.91, 0.016),
        inverseSurface = c(0.30, 0.01), inverseOnSurface = c(0.95, 0.008), inversePrimary = c(0.82, 0.10),
        error = Color(0xFFBA1A1A), onError = Color.White,
    ) else darkColorScheme(
        primary = c(0.82, 0.10), onPrimary = c(0.30, 0.08),
        primaryContainer = c(0.40, 0.09), onPrimaryContainer = c(0.92, 0.04),
        secondary = c(0.80, 0.04), onSecondary = c(0.30, 0.03),
        secondaryContainer = c(0.36, 0.035), onSecondaryContainer = c(0.92, 0.03),
        tertiary = c(0.80, 0.07, t), onTertiary = c(0.30, 0.06, t),
        tertiaryContainer = c(0.38, 0.06, t), onTertiaryContainer = c(0.92, 0.04, t),
        background = c(0.17, 0.006), onBackground = c(0.92, 0.01),
        surface = c(0.17, 0.006), onSurface = c(0.92, 0.01),
        surfaceVariant = c(0.36, 0.015), onSurfaceVariant = c(0.76, 0.015),
        outline = c(0.56, 0.015), outlineVariant = c(0.36, 0.012),
        surfaceContainerLowest = c(0.13, 0.005),
        surfaceContainerLow = c(0.20, 0.008),
        surfaceContainer = c(0.225, 0.01),
        surfaceContainerHigh = c(0.26, 0.012),
        surfaceContainerHighest = c(0.30, 0.014),
        inverseSurface = c(0.92, 0.01), inverseOnSurface = c(0.25, 0.01), inversePrimary = c(0.52, 0.13),
        error = Color(0xFFFFB4AB), onError = Color(0xFF690005),
    )
}

/** OKLCH → sRGB (Björn Ottosson's formulas), clamped to the displayable range. */
internal fun oklch(l: Double, c: Double, hueDeg: Double): Color {
    val h = Math.toRadians(hueDeg)
    val a = c * cos(h)
    val b = c * sin(h)
    val l1 = (l + 0.3963377774 * a + 0.2158037573 * b).pow(3)
    val m1 = (l - 0.1055613458 * a - 0.0638541728 * b).pow(3)
    val s1 = (l - 0.0894841775 * a - 1.2914855480 * b).pow(3)
    fun gamma(x: Double) = (if (x <= 0.0031308) 12.92 * x else 1.055 * x.pow(1 / 2.4) - 0.055).coerceIn(0.0, 1.0).toFloat()
    return Color(
        red = gamma(4.0767416621 * l1 - 3.3077115913 * m1 + 0.2309699292 * s1),
        green = gamma(-1.2684380046 * l1 + 2.6097574011 * m1 - 0.3413193965 * s1),
        blue = gamma(-0.0041960863 * l1 - 0.7034186147 * m1 + 1.7076147010 * s1),
    )
}
