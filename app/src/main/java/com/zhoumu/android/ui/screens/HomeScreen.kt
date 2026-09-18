package com.zhoumu.android.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zhoumu.android.data.*
import com.zhoumu.android.ui.theme.ZhoumuThemeAccessor
import kotlinx.coroutines.delay
import java.time.LocalDateTime

/**
 * 首页：两页。
 *
 * 第一页是圆环 + 周目 + 三行倒计时；第二页是当天完整课表。
 * 对应 iOS 版 HomeView + TodayScheduleView。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(settings: ZhoumuSettings, onOpenSettings: () -> Unit) {
    val colors = ZhoumuThemeAccessor.colors
    val pager = rememberPagerState(pageCount = { 2 })

    // 每秒走一下，倒计时和圆环才会动
    var now by remember { mutableStateOf(LocalDateTime.now()) }
    LaunchedEffect(Unit) {
        while (true) {
            now = LocalDateTime.now()
            delay(1000)
        }
    }

    Scaffold(
        containerColor = colors.background,
        topBar = {
            TopAppBar(
                title = { Text("周目", fontWeight = FontWeight.Bold, color = colors.accent) },
                actions = {
                    IconButton(onClick = onOpenSettings) {
                        Icon(Icons.Default.Settings, contentDescription = "设置", tint = colors.accent)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = colors.background),
            )
        },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            HorizontalPager(state = pager, modifier = Modifier.weight(1f)) { page ->
                if (page == 0) {
                    WeekPage(settings = settings, now = now)
                } else {
                    TodayPage(settings = settings, now = now)
                }
            }
            PageDots(count = 2, current = pager.currentPage, modifier = Modifier.padding(bottom = 12.dp))
        }
    }
}

@Composable
private fun PageDots(count: Int, current: Int, modifier: Modifier = Modifier) {
    val colors = ZhoumuThemeAccessor.colors
    Row(
        modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        repeat(count) { i ->
            Box(
                Modifier
                    .padding(horizontal = 4.dp)
                    .size(if (i == current) 8.dp else 7.dp)
                    .clip(CircleShape)
                    .background(if (i == current) colors.accent else colors.accent.copy(alpha = 0.25f))
            )
        }
    }
}

// MARK: - 第一页

@Composable
private fun WeekPage(settings: ZhoumuSettings, now: LocalDateTime) {
    val colors = ZhoumuThemeAccessor.colors
    val phase = settings.phase(now.toLocalDate())
    val classes = settings.classes(now.toLocalDate())
    val content = ClassSchedule.ringContent(now, settings.tables,
        (phase as? SemesterPhase.InSession)?.info?.displayWeek ?: 1)

    Column(
        Modifier.fillMaxSize().padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(16.dp))

        // 圆环
        Box(contentAlignment = Alignment.Center) {
            WeekRing(progress = content.progress, modifier = Modifier.size(230.dp))
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    if (classes.isEmpty()) "周六" else SemesterCalculator.weekdayShortNames[
                        SemesterCalculator.dayIndexInWeek(now.toLocalDate())].removePrefix("周"),
                    fontSize = 13.sp, color = colors.secondaryText,
                )
                Text(
                    content.subject,
                    fontSize = if (content.subject.length > 4) 24.sp else 34.sp,
                    fontWeight = FontWeight.Bold,
                    color = colors.accent,
                )
                if (content.caption.isNotEmpty()) {
                    Text(content.caption, fontSize = 12.sp, color = colors.secondaryText)
                }
            }
        }

        Spacer(Modifier.height(14.dp))

        // 第 N 周
        if (phase is SemesterPhase.InSession) {
            Row(verticalAlignment = Alignment.Bottom) {
                Text("第 ", fontSize = 15.sp, color = colors.secondaryText)
                Text("${phase.info.displayWeek}", fontSize = 40.sp, fontWeight = FontWeight.Bold,
                    color = colors.accent)
                Text(" 周", fontSize = 15.sp, color = colors.secondaryText)
            }
            Text(
                if (phase.info.isCycling) "开学第 ${phase.info.rawWeek} 周 · ${phase.info.cycleWeeks} 周循环"
                else "开学第 ${phase.info.rawWeek} 周",
                fontSize = 12.sp, color = colors.secondaryText,
            )
        } else if (phase is SemesterPhase.NotStarted) {
            Text("还有 ${phase.daysUntilStart} 天开学",
                fontSize = 18.sp, fontWeight = FontWeight.Bold, color = colors.accent)
        }

        Spacer(Modifier.height(20.dp))

        // 三行倒计时
        val lines = ClassSchedule.countdownLines(now, classes)
        CountdownCard(lines, settings, colors, Modifier.fillMaxWidth())

        Spacer(Modifier.height(16.dp))
    }
}

@Composable
private fun WeekRing(progress: Double, modifier: Modifier = Modifier) {
    val colors = ZhoumuThemeAccessor.colors
    Canvas(modifier) {
        val stroke = 26f
        val inset = stroke / 2
        val arcSize = Size(size.width - stroke, size.height - stroke)
        val topLeft = Offset(inset, inset)

        // 轨道
        drawArc(
            color = colors.accentSoft,
            startAngle = -90f, sweepAngle = 360f, useCenter = false,
            topLeft = topLeft, size = arcSize,
            style = Stroke(width = stroke, cap = StrokeCap.Round),
        )
        // 进度
        drawArc(
            color = colors.accent,
            startAngle = -90f, sweepAngle = (360.0 * progress.coerceIn(0.0, 1.0)).toFloat(),
            useCenter = false, topLeft = topLeft, size = arcSize,
            style = Stroke(width = stroke, cap = StrokeCap.Round),
        )
    }
}

@Composable
private fun CountdownCard(
    lines: CountdownLines,
    settings: ZhoumuSettings,
    colors: com.zhoumu.android.ui.theme.ZhoumuColors,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier,
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = colors.card),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            if (!settings.todayHasTimes) {
                Text(
                    "今天还没填上下课时间。填上之后这里会显示倒计时和进度。",
                    fontSize = 13.sp, color = colors.warning,
                )
            }
            CountdownRow("距下节课", lines.untilNextStart?.let { DateText.human(it.toInt()) } ?: "—",
                lines.nextSubject, colors)
            CountdownRow("这节还剩", lines.untilCurrentEnd?.let { DateText.human(it.toInt()) } ?: "—",
                null, colors)
            CountdownRow("下节", lines.nextSubject ?: "—", null, colors)
        }
    }
}

@Composable
private fun CountdownRow(
    label: String,
    value: String,
    sub: String?,
    colors: com.zhoumu.android.ui.theme.ZhoumuColors,
) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, fontSize = 14.sp, color = colors.secondaryText)
        Spacer(Modifier.weight(1f))
        Column(horizontalAlignment = Alignment.End) {
            Text(value, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = colors.accent)
            if (sub != null) Text(sub, fontSize = 12.sp, color = colors.secondaryText)
        }
    }
}

// MARK: - 第二页

@Composable
private fun TodayPage(settings: ZhoumuSettings, now: LocalDateTime) {
    val colors = ZhoumuThemeAccessor.colors
    val date = now.toLocalDate()
    val classes = settings.classes(date)
    val current = classes.firstOrNull { !now.isBefore(it.start) && now.isBefore(it.end) }

    Column(Modifier.fillMaxSize().padding(horizontal = 20.dp)) {
        Spacer(Modifier.height(8.dp))
        Text("今天的课", fontSize = 26.sp, fontWeight = FontWeight.Bold, color = colors.primaryText)
        Text(
            "${DateText.short(date)} ${DateText.weekday(date)}" +
                if (classes.isEmpty()) "" else " · 共 ${classes.size} 节",
            fontSize = 13.sp, color = colors.secondaryText,
        )
        Spacer(Modifier.height(16.dp))

        if (classes.isEmpty()) {
            Text("今天没有课", fontSize = 15.sp, color = colors.secondaryText,
                modifier = Modifier.fillMaxWidth().padding(vertical = 40.dp))
        } else {
            classes.forEach { c ->
                val isCurrent = c == current
                Card(
                    Modifier.fillMaxWidth().padding(bottom = 10.dp),
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = if (isCurrent) colors.accentSoft else colors.card
                    ),
                ) {
                    Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(DateText.timeOfDay(c.start.hour * 60 + c.start.minute),
                                fontSize = 15.sp, fontWeight = FontWeight.Bold, color = colors.accent)
                            Text(DateText.timeOfDay(c.end.hour * 60 + c.end.minute),
                                fontSize = 12.sp, color = colors.secondaryText)
                        }
                        Spacer(Modifier.width(14.dp))
                        Box(Modifier.width(3.dp).height(34.dp).clip(CircleShape)
                            .background(if (c.kind == ScheduleKind.Evening) colors.evening else colors.accent))
                        Spacer(Modifier.width(14.dp))
                        Column(Modifier.weight(1f)) {
                            Text(c.subject, fontSize = 17.sp, fontWeight = FontWeight.SemiBold,
                                color = colors.primaryText)
                            Text("${c.kind.label} · 第 ${c.period} 节", fontSize = 12.sp,
                                color = colors.secondaryText)
                        }
                        if (isCurrent) {
                            Text("进行中", fontSize = 12.sp, fontWeight = FontWeight.Bold,
                                color = colors.accent,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(colors.accent)
                                    .padding(horizontal = 8.dp, vertical = 3.dp))
                        }
                    }
                }
            }
        }
    }
}
