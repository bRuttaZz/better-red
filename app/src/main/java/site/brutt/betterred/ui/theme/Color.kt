package site.brutt.betterred.ui.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

// Pastel red Material 3 scheme (matches the HTML prototype).
// Tertiary (soft teal) is only used, quietly, for the fertile window.

val RedLightScheme = lightColorScheme(
    primary = Color(0xFFB3474E),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFFFDADA),
    onPrimaryContainer = Color(0xFF5C1219),
    secondary = Color(0xFF775656),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFFFDAD9),
    onSecondaryContainer = Color(0xFF2C1515),
    tertiary = Color(0xFF3B6A6C),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFBEEBEC),
    onTertiaryContainer = Color(0xFF002022),
    background = Color(0xFFFFF8F7),
    onBackground = Color(0xFF231919),
    surface = Color(0xFFFFF8F7),
    onSurface = Color(0xFF231919),
    surfaceVariant = Color(0xFFF5DDDC),
    onSurfaceVariant = Color(0xFF534342),
    outline = Color(0xFF857372),
    outlineVariant = Color(0xFFD8C2C0),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFFFF0EF),
    surfaceContainer = Color(0xFFFCEAE9),
    surfaceContainerHigh = Color(0xFFF6E4E3),
    surfaceContainerHighest = Color(0xFFF0DEDD),
    error = Color(0xFFBA1A1A),
    onError = Color(0xFFFFFFFF),
)

val RedDarkScheme = darkColorScheme(
    primary = Color(0xFFFFB3B2),
    onPrimary = Color(0xFF680F1A),
    primaryContainer = Color(0xFF8A2A33),
    onPrimaryContainer = Color(0xFFFFDAD9),
    secondary = Color(0xFFE6BDBB),
    onSecondary = Color(0xFF442928),
    secondaryContainer = Color(0xFF5D3F3E),
    onSecondaryContainer = Color(0xFFFFDAD9),
    tertiary = Color(0xFFA2CED0),
    onTertiary = Color(0xFF00373A),
    tertiaryContainer = Color(0xFF214E51),
    onTertiaryContainer = Color(0xFFBEEBEC),
    background = Color(0xFF1A1111),
    onBackground = Color(0xFFF0DEDD),
    surface = Color(0xFF1A1111),
    onSurface = Color(0xFFF0DEDD),
    surfaceVariant = Color(0xFF534342),
    onSurfaceVariant = Color(0xFFD8C2C0),
    outline = Color(0xFFA08C8B),
    outlineVariant = Color(0xFF534342),
    surfaceContainerLowest = Color(0xFF140C0C),
    surfaceContainerLow = Color(0xFF231919),
    surfaceContainer = Color(0xFF271D1D),
    surfaceContainerHigh = Color(0xFF322827),
    surfaceContainerHighest = Color(0xFF3D3232),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
)
