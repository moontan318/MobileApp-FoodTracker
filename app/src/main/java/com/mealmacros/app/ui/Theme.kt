package com.mealmacros.app.ui

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

private val LightColors = lightColorScheme(
    primary = Color(0xFF2E7D32),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFB9F0B8),
    onPrimaryContainer = Color(0xFF002106),
    secondary = Color(0xFF52634F),
    tertiary = Color(0xFF39656B),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF9DD49D),
    onPrimary = Color(0xFF00390F),
    primaryContainer = Color(0xFF12521B),
    onPrimaryContainer = Color(0xFFB9F0B8),
    secondary = Color(0xFFB9CCB4),
    tertiary = Color(0xFFA1CED5),
)

/** Colours used consistently for the three macros. */
object MacroColors {
    val protein = Color(0xFFE53935)
    val carbs = Color(0xFFF9A825)
    val fat = Color(0xFF1E88E5)
}

@Composable
fun MealMacrosTheme(content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    val colors = when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        dark -> DarkColors
        else -> LightColors
    }
    MaterialTheme(colorScheme = colors, content = content)
}
