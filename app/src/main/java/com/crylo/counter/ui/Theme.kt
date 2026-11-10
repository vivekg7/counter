package com.crylo.counter.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalContext

@Composable
fun CounterTheme(amoledBlack: Boolean, content: @Composable () -> Unit) {
    val context = LocalContext.current
    val dark = isSystemInDarkTheme()
    var scheme = if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
    if (dark && amoledBlack) {
        scheme = scheme.copy(
            background = Color.Black,
            surface = Color.Black,
            surfaceDim = Color.Black,
            surfaceContainerLowest = Color.Black,
            surfaceContainerLow = Color(0xFF0B0B0B),
            surfaceContainer = Color(0xFF121212),
            surfaceContainerHigh = Color(0xFF1A1A1A),
            surfaceContainerHighest = Color(0xFF222222),
            surfaceBright = Color(0xFF262626),
        )
    }
    MaterialTheme(colorScheme = scheme, content = content)
}

/** A counter's colour, lifted in dark theme so it keeps contrast against a dark surface. */
@Composable
fun accentOf(argb: Int): Color {
    val base = Color(argb)
    return if (isSystemInDarkTheme()) lerp(base, Color.White, 0.35f) else base
}
