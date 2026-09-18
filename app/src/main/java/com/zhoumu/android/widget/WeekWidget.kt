package com.zhoumu.android.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.background
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.updateAll
import androidx.glance.layout.*
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.zhoumu.android.data.*
import java.time.LocalDate
import java.time.LocalDateTime

/**
 * 桌面小组件：不打开 App 也能看到第几周和今天上什么。
 *
 * 对应 iOS 版的 `WeekWidget`。
 */
class WeekWidget : GlanceAppWidget() {

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val settings = WidgetData.load(context)
        provideContent { WidgetBody(settings) }
    }

    companion object {
        /** 刷新所有小组件。设置一变就调一次。 */
        suspend fun refresh(context: Context) {
            WeekWidget().updateAll(context)
        }
    }
}

/** 小组件里显示的数据。 */
private data class WidgetSnapshot(
    val week: Int?,
    val phaseLabel: String,
    val classes: List<ScheduledClass>,
    val hasTimes: Boolean,
    val isDark: Boolean,
)

private object WidgetData {
    fun load(context: Context): WidgetSnapshot {
        val s = SettingsRepository.get(context).current()
        val today = LocalDate.now()
        val phase = s.phase(today)
        val week = (phase as? SemesterPhase.InSession)?.info
        return WidgetSnapshot(
            week = week?.displayWeek,
            phaseLabel = when {
                week == null -> "未开学"
                week.isCycling -> "开学第 ${week.rawWeek} 周 · ${week.cycleWeeks} 周循环"
                else -> "开学第 ${week.rawWeek} 周"
            },
            classes = s.classes(today),
            hasTimes = s.todayHasTimes,
            isDark = s.themeMode == com.zhoumu.android.ui.theme.ThemeMode.Dark,
        )
    }
}

@Composable
private fun WidgetBody(s: WidgetSnapshot) {
    val dark = s.isDark
    val accent = Color(if (dark) 0xFF4C8DFF else 0xFF2563EB)
    val bg = Color(if (dark) 0xFF101827 else 0xFFFFFFFF)
    val primary = Color(if (dark) 0xFFE8F0FF else 0xFF0F1B2D)
    val secondary = Color(if (dark) 0xFF8296B4 else 0xFF5A6B85)

    Column(
        GlanceModifier.fillMaxSize().background(ColorProvider(bg)).padding(14.dp),
        verticalAlignment = Alignment.Vertical.CenterVertically,
    ) {
        if (s.week == null) {
            Text("周目", style = TextStyle(color = ColorProvider(accent), fontSize = 16.sp,
                fontWeight = FontWeight.Bold))
            Text("还没设开学日期", style = TextStyle(color = ColorProvider(secondary), fontSize = 12.sp))
            return@Column
        }

        Row(verticalAlignment = Alignment.Vertical.CenterVertically) {
            Text("第 ", style = TextStyle(color = ColorProvider(secondary), fontSize = 13.sp))
            Text("${s.week}", style = TextStyle(color = ColorProvider(accent), fontSize = 28.sp,
                fontWeight = FontWeight.Bold))
            Text(" 周", style = TextStyle(color = ColorProvider(secondary), fontSize = 13.sp))
        }
        Text(s.phaseLabel, style = TextStyle(color = ColorProvider(secondary), fontSize = 11.sp))

        Spacer(GlanceModifier.height(6.dp))

        if (s.classes.isEmpty()) {
            Text("今天没有课", style = TextStyle(color = ColorProvider(secondary), fontSize = 12.sp))
        } else {
            // 最多显示三节，多了放不下
            s.classes.take(3).forEach { c ->
                Row(GlanceModifier.fillMaxWidth().padding(vertical = 1.dp)) {
                    Text(c.subject, style = TextStyle(color = ColorProvider(primary), fontSize = 13.sp),
                        maxLines = 1)
                    Spacer(GlanceModifier.defaultWeight())
                    Text(
                        DateText.timeOfDay(c.start.hour * 60 + c.start.minute),
                        style = TextStyle(color = ColorProvider(secondary), fontSize = 11.sp),
                    )
                }
            }
            if (s.classes.size > 3) {
                Text("还有 ${s.classes.size - 3} 节",
                    style = TextStyle(color = ColorProvider(secondary), fontSize = 11.sp))
            }
        }
    }
}

/** 小组件的广播接收器。 */
class WeekWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = WeekWidget()
}
