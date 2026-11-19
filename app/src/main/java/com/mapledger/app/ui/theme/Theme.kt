package com.mapledger.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

val Honey = Color(0xFFF5B700)
val Good = Color(0xFF4CC38A)
val Warn = Color(0xFFF2A93B)
val Bad = Color(0xFFEF5B5B)
val Info = Color(0xFF5AA9F7)

private val Dark = darkColorScheme(
    primary = Honey, onPrimary = Color(0xFF1B1400),
    primaryContainer = Color(0xFF3A2D00), onPrimaryContainer = Color(0xFFFFE08A),
    secondary = Info, onSecondary = Color(0xFF00213D),
    secondaryContainer = Color(0xFF173350), onSecondaryContainer = Color(0xFFCFE5FF),
    tertiary = Good, onTertiary = Color(0xFF00210F),
    background = Color(0xFF0E1116), onBackground = Color(0xFFE6E8EC),
    surface = Color(0xFF0E1116), onSurface = Color(0xFFE6E8EC),
    surfaceVariant = Color(0xFF1C2129), onSurfaceVariant = Color(0xFFB4BBC6),
    surfaceContainer = Color(0xFF161A21), surfaceContainerHigh = Color(0xFF1C2129), surfaceContainerHighest = Color(0xFF232933),
    surfaceContainerLow = Color(0xFF12161C), surfaceContainerLowest = Color(0xFF0A0D11),
    outline = Color(0xFF3A414D), outlineVariant = Color(0xFF2A303A),
    error = Bad, onError = Color(0xFF2D0000),
)

private val Type = Typography().run {
    copy(
        displayLarge = displayLarge.copy(fontWeight = FontWeight.Bold),
        displayMedium = displayMedium.copy(fontWeight = FontWeight.Bold),
        headlineSmall = headlineSmall.copy(fontWeight = FontWeight.SemiBold),
        titleMedium = titleMedium.copy(fontWeight = FontWeight.SemiBold),
        labelSmall = TextStyle(fontSize = 11.sp, letterSpacing = 0.4.sp, fontWeight = FontWeight.Medium),
    )
}

/** MapLedger is dark-only by design (night driving, OLED battery, glare). */
@Composable
fun MapLedgerTheme(content: @Composable () -> Unit) = MaterialTheme(colorScheme = Dark, typography = Type, content = content)
