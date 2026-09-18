package com.zhoumu.android.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * 和 iOS 版同一套蓝色系。
 *
 * iOS 那边用 dynamic color 跟随系统深浅色；Compose 这里直接把两套值写出来，
 * 由 [ZhoumuTheme] 按主题模式挑一组。
 */
object ZhoumuPalette {
    // 浅色：白底蓝调
    val LightAccent = Color(0xFF2563EB)
    val LightAccentDeep = Color(0xFF1D4ED8)
    val LightAccentSoft = Color(0xFFDBEAFE)
    val LightBackground = Color(0xFFF6F9FF)
    val LightCard = Color(0xFFFFFFFF)
    val LightPrimaryText = Color(0xFF0F1B2D)
    val LightSecondaryText = Color(0xFF5A6B85)

    // 深色：黑底蓝调
    val DarkAccent = Color(0xFF4C8DFF)
    val DarkAccentDeep = Color(0xFF86B4FF)
    val DarkAccentSoft = Color(0xFF17253D)
    val DarkBackground = Color(0xFF05080F)
    val DarkCard = Color(0xFF101827)
    val DarkPrimaryText = Color(0xFFE8F0FF)
    val DarkSecondaryText = Color(0xFF8296B4)

    /** 提醒用的警示色。 */
    val LightWarning = Color(0xFFB45309)
    val DarkWarning = Color(0xFFFBBF24)

    /** 两张课表的区分色（正课 = 蓝，晚课 = 紫）。 */
    val EveningAccent = Color(0xFF8B5CF6)
}
