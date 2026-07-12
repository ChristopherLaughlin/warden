package com.warden.blocker.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

private val Indigo = Color(0xFF4F5BD5)
private val IndigoDark = Color(0xFF3A44A8)
private val Coral = Color(0xFFE86A5C)

private val LightColors = lightColorScheme(
    primary = Indigo, secondary = Coral, tertiary = IndigoDark,
)
private val DarkColors = darkColorScheme(
    primary = Color(0xFF9AA3F5), secondary = Color(0xFFF29B90), tertiary = Indigo,
)

private val WardenTypography = Typography()

@Composable
fun WardenTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val colors = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        darkTheme -> DarkColors
        else -> LightColors
    }
    MaterialTheme(colorScheme = colors, typography = WardenTypography, content = content)
}
