package com.example.musicplayer.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

/**
 * Pulse is a deliberately dark, single-look theme: it does not follow the system
 * light/dark setting, because the violet bloom and the glass surfaces only read
 * correctly against the dark canvas.
 *
 * Dynamic color is offered as an opt-in (off by default) and is deliberately
 * wallpaper-derived *dark* only — [dynamicDarkColorScheme], never the light one.
 * That keeps the canvas dark, so the glass stack still works, while letting the
 * accent colours follow the wallpaper for people who want that.
 */
private val PulseColors = darkColorScheme(
    primary = PulseOrchid,
    onPrimary = Color.White,
    primaryContainer = PulsePlum,
    onPrimaryContainer = PulseText,
    secondary = PulseViolet,
    onSecondary = Color.White,
    secondaryContainer = PulseSurfaceHigh,
    onSecondaryContainer = PulseText,
    tertiary = PulseMagenta,
    onTertiary = Color.White,
    tertiaryContainer = PulsePlum,
    onTertiaryContainer = PulseText,
    background = PulseBlack,
    onBackground = PulseText,
    surface = PulseSurface,
    onSurface = PulseText,
    surfaceVariant = PulseSurfaceHigh,
    onSurfaceVariant = PulseTextMuted,
    surfaceContainer = PulseSurface,
    surfaceContainerHigh = PulseSurfaceHigh,
    outline = GlassStroke,
    outlineVariant = GlassFill,
    error = Color(0xFFFF8A9B),
    onError = Color(0xFF3B0713),
)

@Composable
fun MusicPlayerTheme(
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val colorScheme = if (dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        dynamicDarkColorScheme(context)
    } else {
        PulseColors
    }
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            // The app draws edge to edge behind both bars, so only the icon
            // tint matters here — always light, since the canvas is always dark.
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = false
                isAppearanceLightNavigationBars = false
            }
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = AppTypography,
        content = content,
    )
}
