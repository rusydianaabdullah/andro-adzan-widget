package com.rusydiana.adzantime.ui

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/** Emerald. The darker shade is the light theme's, so accent text holds up on white. */
private val Emerald = Color(0xFF10B981)
private val EmeraldDeep = Color(0xFF059669)

private val DarkColors = darkColorScheme(
    primary = Emerald,
    onPrimary = Color(0xFF04231A),
    background = Color(0xFF0B1220),
    surface = Color(0xFF141C2B),
)

private val LightColors = lightColorScheme(
    primary = EmeraldDeep,
    onPrimary = Color.White,
)

@Composable
fun AdzanTheme(content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()

    // The status and navigation bars are transparent, so their icons are drawn over our own
    // background. On the light theme that background is white and the icons have to be dark,
    // or the clock, wifi and battery vanish into it.
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = !dark
                isAppearanceLightNavigationBars = !dark
            }
        }
    }

    MaterialTheme(
        colorScheme = if (dark) DarkColors else LightColors,
        content = content,
    )
}
