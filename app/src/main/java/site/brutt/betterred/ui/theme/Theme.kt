package site.brutt.betterred.ui.theme

import android.content.Context
import android.content.res.Configuration
import android.os.Build
import androidx.compose.material3.ColorScheme
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import site.brutt.betterred.data.Accent
import site.brutt.betterred.data.ThemeMode

val supportsDynamicColor: Boolean get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

@Composable
fun ThemeMode.isDark(): Boolean = when (this) {
    ThemeMode.SYSTEM -> isSystemInDarkTheme()
    ThemeMode.LIGHT -> false
    ThemeMode.DARK -> true
}

@Composable
fun BetterRedTheme(
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    accent: Accent = Accent.RED,
    content: @Composable () -> Unit,
) {
    val dark = themeMode.isDark()
    val scheme = colorSchemeFor(LocalContext.current, accent, dark)
    MaterialTheme(colorScheme = scheme, typography = Typography, content = content)
}

/** Her chosen colors. Plain function, so home-screen widgets can use it too. */
fun colorSchemeFor(context: Context, accent: Accent, dark: Boolean): ColorScheme = when {
    accent == Accent.DYNAMIC && supportsDynamicColor ->
        if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
    accent == Accent.GREEN -> toneScheme(Tones.GREEN, dark)
    accent == Accent.YELLOW -> toneScheme(Tones.YELLOW, dark)
    dark -> RedDarkScheme
    else -> RedLightScheme
}

/** Dark or light, outside of Compose (for widgets). */
fun ThemeMode.isDark(context: Context): Boolean = when (this) {
    ThemeMode.SYSTEM -> (context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
    ThemeMode.LIGHT -> false
    ThemeMode.DARK -> true
}
