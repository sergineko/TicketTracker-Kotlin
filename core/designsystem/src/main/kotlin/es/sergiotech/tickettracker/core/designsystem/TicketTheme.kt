package es.sergiotech.tickettracker.core.designsystem

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

private val Light =
    lightColorScheme(
        primary = Color(0xFF176B58),
        onPrimary = Color.White,
        primaryContainer = Color(0xFFD4EEE2),
        onPrimaryContainer = Color(0xFF123D32),
        secondary = Color(0xFF8B5733),
        secondaryContainer = Color(0xFFFFE8D7),
        background = Color(0xFFF8F9F5),
        surface = Color(0xFFF8F9F5),
        surfaceContainer = Color(0xFFEEF1EB),
        surfaceContainerLow = Color(0xFFF2F4EF),
        onSurface = Color(0xFF1C2924),
        onSurfaceVariant = Color(0xFF53635B),
    )
private val Dark =
    darkColorScheme(
        primary = Color(0xFF9DD6BB),
        onPrimary = Color(0xFF00382A),
        primaryContainer = Color(0xFF16513F),
        onPrimaryContainer = Color(0xFFB8EED1),
        background = Color(0xFF101713),
        surface = Color(0xFF101713),
        surfaceContainer = Color(0xFF1B2620),
    )

@Composable
fun TicketTheme(
    mode: String = "SYSTEM",
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit,
) {
    val dark =
        when (mode) {
            "LIGHT" -> false
            "DARK" -> true
            else -> isSystemInDarkTheme()
        }
    val context = LocalContext.current
    val colors =
        if (dynamicColor && Build.VERSION.SDK_INT >= 31) {
            if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        } else if (dark) Dark else Light
    MaterialTheme(
        colorScheme = colors,
        typography =
            Typography(
                headlineLarge =
                    TextStyle(
                        fontFamily = FontFamily.SansSerif,
                        fontWeight = FontWeight.Bold,
                        fontSize = 32.sp,
                        lineHeight = 38.sp,
                    ),
                headlineMedium =
                    TextStyle(
                        fontFamily = FontFamily.SansSerif,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 26.sp,
                        lineHeight = 32.sp,
                    ),
                titleLarge =
                    TextStyle(
                        fontFamily = FontFamily.SansSerif,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 22.sp,
                        lineHeight = 28.sp,
                    ),
            ),
        content = content,
    )
}
