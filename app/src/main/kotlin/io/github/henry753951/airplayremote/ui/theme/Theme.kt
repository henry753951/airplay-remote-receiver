package io.github.henry753951.airplayremote.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.graphics.Color
import io.github.henry753951.airplayremote.ui.isTv

@Composable
fun AirPlayTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        isTv() -> darkColorScheme(
            primary = Color(0xFFF5F5F7),
            onPrimary = Color(0xFF101012),
            background = Color(0xFF101012),
            onBackground = Color(0xFFF5F5F7),
            surface = Color(0xFF101012),
            onSurface = Color(0xFFF5F5F7),
            surfaceContainer = Color(0xFF1C1C1F),
            surfaceContainerHighest = Color(0xFF27272B),
            secondaryContainer = Color(0xFF323236),
            onSecondaryContainer = Color(0xFFF5F5F7)
        )
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val ctx = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(ctx) else dynamicLightColorScheme(ctx)
        }
        darkTheme -> darkColorScheme()
        else -> lightColorScheme()
    }

    MaterialTheme(colorScheme = colorScheme, content = content)
}
