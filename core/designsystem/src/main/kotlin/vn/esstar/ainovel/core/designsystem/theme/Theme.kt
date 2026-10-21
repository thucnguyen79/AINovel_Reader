package vn.esstar.ainovel.core.designsystem.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val DarkColors: ColorScheme = darkColorScheme(
    primary = Coral500,
    onPrimary = Cream,
    primaryContainer = Coral700,
    onPrimaryContainer = Cream,
    secondary = Peach,
    onSecondary = Navy950,
    secondaryContainer = Navy700,
    onSecondaryContainer = Navy100,
    tertiary = Navy300,
    background = Navy950,
    onBackground = Navy100,
    surface = Navy950,
    onSurface = Navy100,
    surfaceVariant = Navy800,
    onSurfaceVariant = Navy300,
    surfaceContainer = Navy900,
    surfaceContainerHigh = Navy800,
    surfaceContainerHighest = Navy700,
    surfaceContainerLow = Navy900,
    surfaceContainerLowest = Navy950,
    outline = Navy500,
    outlineVariant = Navy700,
    error = Coral300,
    onError = Navy950,
)

private val LightColors: ColorScheme = lightColorScheme(
    primary = Coral700,
    onPrimary = Light50,
    primaryContainer = Coral300,
    onPrimaryContainer = Ink900,
    secondary = Ink600,
    onSecondary = Light50,
    secondaryContainer = Light200,
    onSecondaryContainer = Ink900,
    background = Light50,
    onBackground = Ink900,
    surface = Light50,
    onSurface = Ink900,
    surfaceVariant = Light100,
    onSurfaceVariant = Ink600,
    surfaceContainer = Light100,
    surfaceContainerHigh = Light200,
    outline = Light200,
    error = Coral700,
)

@Composable
fun AINovelTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        typography = Typography(),
        content = content,
    )
}
