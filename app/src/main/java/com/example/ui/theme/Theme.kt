package com.example.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import com.example.data.pref.KeyboardPreferences

private val DarkColorScheme = darkColorScheme(
    primary = Purple80,
    secondary = PurpleGrey80,
    tertiary = Pink80,
    background = Color(0xFF121212),
    surface = Color(0xFF1E1E1E),
    onPrimary = Color.Black,
    onSecondary = Color.White,
    onBackground = Color.White,
    onSurface = Color.White
)

private val LightColorScheme = lightColorScheme(
    primary = Purple40,
    secondary = PurpleGrey40,
    tertiary = Pink40,
    background = Color(0xFFFFFBFE),
    surface = Color(0xFFFFFBFE),
    onPrimary = Color.White,
    onSecondary = Color.Black,
    onBackground = Color(0xFF1C1B1F),
    onSurface = Color(0xFF1C1B1F)
)

private val AmoledColorScheme = darkColorScheme(
    primary = Color(0xFF4285F4),
    secondary = Color(0xFF1A1A1A),
    background = Color(0xFF000000),
    surface = Color(0xFF121212),
    onPrimary = Color.White,
    onBackground = Color.White,
    onSurface = Color.White
)

private val CosmicColorScheme = darkColorScheme(
    primary = Color(0xFFFF2A5F),
    secondary = Color(0xFF100E1D),
    background = Color(0xFF100E1D),
    surface = Color(0xFF1E1A34),
    onPrimary = Color.White,
    onBackground = Color(0xFFE2E2F2),
    onSurface = Color(0xFFE2E2F2)
)

@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    keyboardTheme: KeyboardPreferences.KeyboardTheme? = null,
    content: @Composable () -> Unit,
) {
    val colorScheme = when {
        keyboardTheme != null -> {
            when (keyboardTheme) {
                KeyboardPreferences.KeyboardTheme.LIGHT -> LightColorScheme
                KeyboardPreferences.KeyboardTheme.DARK -> DarkColorScheme
                KeyboardPreferences.KeyboardTheme.AMOLED -> AmoledColorScheme
                KeyboardPreferences.KeyboardTheme.COSMIC_INDIGO -> CosmicColorScheme
            }
        }
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
