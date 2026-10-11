package com.face.attendance.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

// ── 主色：靛蓝渐变 ──
val IndigoPrimary = Color(0xFF5B6CFF)
val IndigoLight = Color(0xFF8B98FF)
val IndigoDeep = Color(0xFF3D4FCC)
val Lavender = Color(0xFFA78BFA)
val Cyan = Color(0xFF22D3EE)
val TealAccent = Color(0xFF2DD4BF)

// ── 功能色 ──
val Success = Color(0xFF34D399)
val Error = Color(0xFFF87171)
val Warning = Color(0xFFFBBF24)
val Info = Color(0xFF60A5FA)

// ── 背景/表面 ──
val BgBase = Color(0xFFF7F8FC)
val BgSurface = Color(0xFFFFFFFF)
val BgSurfaceElevated = Color(0xFFF1F3F9)
val BgDark = Color(0xFF15161E)
val BgDarkSurface = Color(0xFF1F2030)
val BgDarkElevated = Color(0xFF2A2B3D)

// ── 文字 ──
val TextPrimary = Color(0xFF1A1B2E)
val TextSecondary = Color(0xFF6B7290)
val TextTertiary = Color(0xFF9CA3C4)
val TextDarkPrimary = Color(0xFFF8F9FC)
val TextDarkSecondary = Color(0xFFA0A6C0)

// ── 描边/分隔 ──
val Border = Color(0xFFE5E8F0)
val Divider = Color(0xFFEFF1F7)
val BorderDark = Color(0xFF36374B)

// ── 渐变 ──
val PrimaryGradient = listOf(IndigoPrimary, Lavender)
val CoolGradient = listOf(IndigoPrimary, Cyan)
val SuccessGradient = listOf(Success, TealAccent)
val WarmGradient = listOf(Warning, Error)

@Composable
fun AttendanceTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val scheme = if (darkTheme) {
        darkColorScheme(
            primary = IndigoLight,
            onPrimary = Color.White,
            primaryContainer = IndigoDeep,
            onPrimaryContainer = Color.White,
            secondary = Lavender,
            onSecondary = Color.White,
            tertiary = TealAccent,
            onTertiary = Color.White,
            error = Error,
            onError = Color.White,
            background = BgDark,
            onBackground = TextDarkPrimary,
            surface = BgDarkSurface,
            onSurface = TextDarkPrimary,
            surfaceVariant = BgDarkElevated,
            onSurfaceVariant = TextDarkSecondary,
            outline = BorderDark
        )
    } else {
        lightColorScheme(
            primary = IndigoPrimary,
            onPrimary = Color.White,
            primaryContainer = IndigoLight,
            onPrimaryContainer = Color.White,
            secondary = Lavender,
            onSecondary = Color.White,
            tertiary = TealAccent,
            onTertiary = Color.White,
            error = Error,
            onError = Color.White,
            background = BgBase,
            onBackground = TextPrimary,
            surface = BgSurface,
            onSurface = TextPrimary,
            surfaceVariant = BgSurfaceElevated,
            onSurfaceVariant = TextSecondary,
            outline = Border
        )
    }

    val typography = Typography(
        headlineLarge = TextStyle(
            fontWeight = FontWeight.Bold, fontSize = 30.sp, lineHeight = 38.sp
        ),
        headlineMedium = TextStyle(
            fontWeight = FontWeight.Bold, fontSize = 24.sp, lineHeight = 32.sp
        ),
        titleLarge = TextStyle(
            fontWeight = FontWeight.SemiBold, fontSize = 20.sp, lineHeight = 28.sp
        ),
        titleMedium = TextStyle(
            fontWeight = FontWeight.SemiBold, fontSize = 17.sp, lineHeight = 24.sp
        ),
        bodyLarge = TextStyle(
            fontWeight = FontWeight.Normal, fontSize = 16.sp, lineHeight = 24.sp
        ),
        bodyMedium = TextStyle(
            fontWeight = FontWeight.Normal, fontSize = 14.sp, lineHeight = 22.sp
        ),
        labelLarge = TextStyle(
            fontWeight = FontWeight.SemiBold, fontSize = 15.sp, lineHeight = 20.sp
        ),
        labelMedium = TextStyle(
            fontWeight = FontWeight.Medium, fontSize = 13.sp, lineHeight = 18.sp
        ),
        labelSmall = TextStyle(
            fontWeight = FontWeight.Normal, fontSize = 12.sp, lineHeight = 16.sp
        )
    )

    MaterialTheme(colorScheme = scheme, typography = typography, content = content)
}
