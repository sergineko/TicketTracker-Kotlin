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
        primary = Color(0xFF006C74),
        onPrimary = Color.White,
        primaryContainer = Color(0xFFC8F4EF),
        onPrimaryContainer = Color(0xFF004C53),
        secondary = Color(0xFF6944A5),
        secondaryContainer = Color(0xFFF0E7FF),
        onSecondaryContainer = Color(0xFF432376),
        tertiary = Color(0xFF965400),
        tertiaryContainer = Color(0xFFFFEDD3),
        onTertiaryContainer = Color(0xFF653600),
        background = Color(0xFFF4F8FA),
        surface = Color(0xFFFFFFFF),
        surfaceContainer = Color(0xFFEAF2F4),
        surfaceContainerLow = Color(0xFFFFFFFF),
        onSurface = Color(0xFF0D1930),
        onSurfaceVariant = Color(0xFF53627B),
        outlineVariant = Color(0xFFDCE6EB),
    )
private val Dark =
    darkColorScheme(
        primary = Color(0xFF6DDCD2),
        onPrimary = Color(0xFF00383D),
        primaryContainer = Color(0xFF00515A),
        onPrimaryContainer = Color(0xFFC8F4EF),
        secondary = Color(0xFFD4BAFF),
        secondaryContainer = Color(0xFF3F2C5F),
        onSecondaryContainer = Color(0xFFF0E7FF),
        tertiary = Color(0xFFFFC77F),
        tertiaryContainer = Color(0xFF593D1E),
        onTertiaryContainer = Color(0xFFFFEDD3),
        background = Color(0xFF0E1822),
        surface = Color(0xFF14222E),
        surfaceContainer = Color(0xFF20313E),
        surfaceContainerLow = Color(0xFF182833),
        onSurface = Color(0xFFE5EDF5),
        onSurfaceVariant = Color(0xFFBAC8D8),
        outlineVariant = Color(0xFF344854),
    )

@Composable
fun TicketTheme(
    mode: String = "SYSTEM",
    dynamicColor: Boolean = false,
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
