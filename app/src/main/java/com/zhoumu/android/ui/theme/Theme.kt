package com.zhoumu.android.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

/** 外观模式，对应 iOS 版的三态开关。 */
enum class ThemeMode(val label: String) {
    System("跟随系统"),
    Light("浅色"),
    Dark("深色");

    companion object {
        fun fromKey(key: String?): ThemeMode =
            entries.firstOrNull { it.name.equals(key, ignoreCase = true) } ?: System
    }
}

/** 当前生效的配色，供各屏幕直接取用（对应 iOS 版的 `Palette`）。 */
data class ZhoumuColors(
    val accent: Color,
    val accentDeep: Color,
    val accentSoft: Color,
    val background: Color,
    val card: Color,
    val primaryText: Color,
    val secondaryText: Color,
    val warning: Color,
    val isDark: Boolean,
) {
    val faintText: Color get() = secondaryText.copy(alpha = 0.6f)
    val line: Color get() = accent.copy(alpha = if (isDark) 0.20f else 0.14f)
    val evening: Color get() = ZhoumuPalette.EveningAccent
}

private val LightColors = ZhoumuColors(
    accent = ZhoumuPalette.LightAccent,
    accentDeep = ZhoumuPalette.LightAccentDeep,
    accentSoft = ZhoumuPalette.LightAccentSoft,
    background = ZhoumuPalette.LightBackground,
    card = ZhoumuPalette.LightCard,
    primaryText = ZhoumuPalette.LightPrimaryText,
    secondaryText = ZhoumuPalette.LightSecondaryText,
    warning = ZhoumuPalette.LightWarning,
    isDark = false,
)

private val DarkColors = ZhoumuColors(
    accent = ZhoumuPalette.DarkAccent,
    accentDeep = ZhoumuPalette.DarkAccentDeep,
    accentSoft = ZhoumuPalette.DarkAccentSoft,
    background = ZhoumuPalette.DarkBackground,
    card = ZhoumuPalette.DarkCard,
    primaryText = ZhoumuPalette.DarkPrimaryText,
    secondaryText = ZhoumuPalette.DarkSecondaryText,
    warning = ZhoumuPalette.DarkWarning,
    isDark = true,
)

/** 用 CompositionLocal 把当前配色传下去。 */
val LocalZhoumuColors = androidx.compose.runtime.staticCompositionLocalOf { LightColors }

@Composable
fun ZhoumuTheme(
    mode: ThemeMode = ThemeMode.System,
    content: @Composable () -> Unit,
) {
    val dark = when (mode) {
        ThemeMode.System -> isSystemInDarkTheme()
        ThemeMode.Light -> false
        ThemeMode.Dark -> true
    }
    val colors = if (dark) DarkColors else LightColors

    val scheme = if (dark) {
        darkColorScheme(
            primary = colors.accent,
            onPrimary = Color(0xFF04101F),
            background = colors.background,
            onBackground = colors.primaryText,
            surface = colors.card,
            onSurface = colors.primaryText,
            surfaceVariant = colors.accentSoft,
            onSurfaceVariant = colors.secondaryText,
        )
    } else {
        lightColorScheme(
            primary = colors.accent,
            onPrimary = Color.White,
            background = colors.background,
            onBackground = colors.primaryText,
            surface = colors.card,
            onSurface = colors.primaryText,
            surfaceVariant = colors.accentSoft,
            onSurfaceVariant = colors.secondaryText,
        )
    }

    // 状态栏跟着主题走
    val view = LocalView.current
    if (!view.isInEditMode) {
        val context = LocalContext.current
        SideEffect {
            (context as? Activity)?.window?.let { w ->
                WindowCompat.getInsetsController(w, view).isAppearanceLightStatusBars = !dark
            }
        }
    }

    androidx.compose.runtime.CompositionLocalProvider(LocalZhoumuColors provides colors) {
        MaterialTheme(colorScheme = scheme, content = content)
    }
}

/** 取当前配色的小工具。 */
object ZhoumuThemeAccessor {
    val colors: ZhoumuColors
        @Composable get() = LocalZhoumuColors.current
}
