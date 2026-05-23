package com.example.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

private val DarkColorScheme =
  darkColorScheme(
    primary = VibrantPrimary,
    secondary = VibrantSecondary,
    tertiary = VibrantSurfaceVariant,
    background = VibrantOnBackground, // Dark theme swaps background and surfaces nicely
    surface = Color(0xFF2C2422),
    onPrimary = VibrantOnPrimary,
    onSecondary = VibrantOnSecondary,
    onBackground = VibrantBackground,
    onSurface = VibrantBackground,
    surfaceVariant = Color(0xFF3B0909),
    onSurfaceVariant = VibrantSurfaceVariant,
    outline = Color(0xFF524341)
  )

private val LightColorScheme =
  lightColorScheme(
    primary = VibrantPrimary,
    secondary = VibrantSecondary,
    tertiary = VibrantSurfaceVariant,
    background = VibrantBackground,
    surface = VibrantSurface,
    onPrimary = VibrantOnPrimary,
    onSecondary = VibrantOnSecondary,
    onBackground = VibrantOnBackground,
    onSurface = VibrantOnSurface,
    surfaceVariant = VibrantSurfaceVariant,
    onSurfaceVariant = VibrantOnSurfaceVariant,
    outline = VibrantOutline
  )

@Composable
fun MyApplicationTheme(
  darkTheme: Boolean = isSystemInDarkTheme(),
  // Disabling dynamic colors to guarantee consistent custom Vibrant Palette branding
  dynamicColor: Boolean = false,
  content: @Composable () -> Unit,
) {
  val colorScheme =
    when {
      dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
        val context = LocalContext.current
        if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
      }

      darkTheme -> DarkColorScheme
      else -> LightColorScheme
    }

  MaterialTheme(colorScheme = colorScheme, typography = Typography, content = content)
}
