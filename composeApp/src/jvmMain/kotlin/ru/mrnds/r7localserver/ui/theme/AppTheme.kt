package ru.mrnds.r7localserver.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import ru.mrnds.r7localserver.settings.AppThemeMode

private val LightColors = lightColorScheme(
    primary = Color(0xFF0B74B8),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD5ECFF),
    onPrimaryContainer = Color(0xFF001D32),

    secondary = Color(0xFF4F6475),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFD3E5F5),
    onSecondaryContainer = Color(0xFF0B1D2A),

    background = Color(0xFFEAF3FA),
    onBackground = Color(0xFF17212B),
    surface = Color(0xFFF2F8FD),
    onSurface = Color(0xFF17212B),
    surfaceVariant = Color(0xFFD2E4F1),
    onSurfaceVariant = Color(0xFF374957),

    outline = Color(0xFF717D88),
    outlineVariant = Color(0xFFC6D2DD),

    tertiary = Color(0xFF8A5A00),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFFFDEA3),
    onTertiaryContainer = Color(0xFF2B1700),

    error = Color(0xFFBA1A1A),
    onError = Color.White
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF8DCDFF),
    onPrimary = Color(0xFF00344F),
    primaryContainer = Color(0xFF00527C),
    onPrimaryContainer = Color(0xFFD5ECFF),

    secondary = Color(0xFFB7C9D9),
    onSecondary = Color(0xFF21323F),
    secondaryContainer = Color(0xFF374956),
    onSecondaryContainer = Color(0xFFD3E5F5),

    background = Color(0xFF1A222A),
    onBackground = Color(0xFFE6EDF3),
    surface = Color(0xFF222C35),
    onSurface = Color(0xFFE6EDF3),
    surfaceVariant = Color(0xFF344555),
    onSurfaceVariant = Color(0xFFC8D4DE),

    outline = Color(0xFF8B959F),
    outlineVariant = Color(0xFF414B55),

    tertiary = Color(0xFFFFC870),
    onTertiary = Color(0xFF4A2A00),
    tertiaryContainer = Color(0xFF684000),
    onTertiaryContainer = Color(0xFFFFDEA3),

    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005)
)

@Composable
fun AppTheme(
    themeMode: AppThemeMode,
    content: @Composable () -> Unit
) {
    val darkTheme = when (themeMode) {
        AppThemeMode.SYSTEM -> isSystemInDarkTheme()
        AppThemeMode.LIGHT -> false
        AppThemeMode.DARK -> true
    }

    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        content = content
    )
}