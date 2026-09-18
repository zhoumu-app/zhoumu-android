package com.zhoumu.android.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zhoumu.android.data.*
import com.zhoumu.android.ui.theme.ThemeMode
import com.zhoumu.android.ui.theme.ZhoumuColors
import com.zhoumu.android.ui.theme.ZhoumuThemeAccessor
import java.time.LocalDate

/** 设置首页：外观 / 开学日期 / 两张课表入口 / 提醒 / 预览。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    repo: SettingsRepository,
    settings: ZhoumuSettings,
    onBack: () -> Unit,
    onOpenTable: (ScheduleKind) -> Unit,
) {
    val colors = ZhoumuThemeAccessor.colors

    Scaffold(
        containerColor = colors.background,
        topBar = {
            TopAppBar(
                title = { Text("设置", fontWeight = FontWeight.Bold, color = colors.primaryText) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回", tint = colors.accent)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = colors.background),
            )
        },
    ) { padding ->
        Column(
            Modifier.padding(padding).verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            // 外观
            SettingsCard("外观", colors) {
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    ThemeMode.entries.forEachIndexed { i, mode ->
                        SegmentedButton(
                            selected = settings.themeMode == mode,
                            onClick = { repo.update { it.copy(themeMode = mode) } },
                            shape = SegmentedButtonDefaults.itemShape(i, ThemeMode.entries.size),
                        ) { Text(mode.label, fontSize = 14.sp) }
                    }
                }
                Text(
                    "浅色是白蓝配色，深色是黑蓝配色。",
                    fontSize = 12.sp, color = colors.secondaryText,
                )
            }

            // 开学日期
            SettingsCard("开学日期", colors) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(DateText.short(settings.startDate), fontSize = 17.sp,
                        fontWeight = FontWeight.Bold, color = colors.primaryText)
                    Spacer(Modifier.weight(1f))
                    TextButton(onClick = { repo.update { it.copy(startDate = LocalDate.now()) } }) {
                        Text("设为今天", color = colors.accent)
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = {
                        repo.update { it.copy(startDate = it.startDate.minusDays(1)) }
                    }) { Text("− 1 天") }
                    OutlinedButton(onClick = {
                        repo.update { it.copy(startDate = it.startDate.plusDays(1)) }
                    }) { Text("+ 1 天") }
                    OutlinedButton(onClick = {
                        repo.update { it.copy(startDate = it.startDate.minusWeeks(1)) }
                    }) { Text("− 1 周") }
                }
            }

            // 周目循环
            SettingsCard("周目循环", colors) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("启用循环", fontSize = 16.sp, color = colors.primaryText)
                    Spacer(Modifier.weight(1f))
                    Switch(
                        checked = settings.cyclingEnabled,
                        onCheckedChange = { v -> repo.update { it.copy(cyclingEnabled = v) } },
                    )
                }
                if (settings.cyclingEnabled) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("循环周数", fontSize = 16.sp, color = colors.primaryText)
                        Spacer(Modifier.weight(1f))
                        CountStepper(
                            value = settings.cycleWeeks,
                            range = SemesterCalculator.cycleWeeksRange,
                            unit = "周",
                            colors = colors,
                        ) { v -> repo.update { it.copy(cycleWeeks = v) } }
                    }
                }
                val p = settings.phase()
                if (p is SemesterPhase.InSession) {
                    Text(
                        if (p.info.isCycling)
                            "每 ${p.info.cycleWeeks} 周循环一次。今天是开学第 ${p.info.rawWeek} 周，按循环显示为第 ${p.info.displayWeek} 周。"
                        else "今天是开学第 ${p.info.rawWeek} 周。",
                        fontSize = 12.sp, color = colors.secondaryText,
                    )
                }
            }

            // 两张课表
            SettingsCard("课表", colors) {
                ScheduleKind.entries.forEach { kind ->
                    val t = settings.table(kind)
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp))
                            .clickable { onOpenTable(kind) }.padding(vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(if (kind == ScheduleKind.Evening) "🌙" else "☀️", fontSize = 18.sp)
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text(kind.label, fontSize = 16.sp, fontWeight = FontWeight.SemiBold,
                                color = colors.primaryText)
                            Text(
                                buildString {
                                    append(if (t.enabled) "" else "已关闭 · ")
                                    append(if (t.rotatesByWeek) "按周目轮换" else "固定")
                                    append(" · ")
                                    append(if (t.hasAnyTime) "已填时间" else "未填时间")
                                },
                                fontSize = 12.sp, color = colors.secondaryText,
                            )
                        }
                        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null,
                            tint = colors.faintText)
                    }
                }
                Text(
                    "两张表互相独立：各自设置每日节数、上下课时间、固定或按周目轮换，也可以单独关闭。",
                    fontSize = 12.sp, color = colors.secondaryText,
                )
            }

            // 提醒
            SettingsCard("上课提醒", colors) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("启用提醒", fontSize = 16.sp, color = colors.primaryText)
                    Spacer(Modifier.weight(1f))
                    Switch(
                        checked = settings.remindersEnabled,
                        onCheckedChange = { v -> repo.update { it.copy(remindersEnabled = v) } },
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("提前几分钟", fontSize = 16.sp, color = colors.primaryText)
                    Spacer(Modifier.weight(1f))
                    CountStepper(
                        value = settings.reminderMinutes,
                        range = 0..30,
                        unit = "分",
                        colors = colors,
                    ) { v -> repo.update { it.copy(reminderMinutes = v) } }
                }
                Text(
                    if (settings.todayHasTimes) "今天的课已填时间，提醒可用。"
                    else "今天还没填上下课时间，提醒不可用。",
                    fontSize = 12.sp,
                    color = if (settings.todayHasTimes) colors.secondaryText else colors.warning,
                )
            }

            // 预览
            SettingsCard("预览", colors) {
                val p = settings.phase()
                if (p is SemesterPhase.InSession) {
                    PreviewRow("今天显示", "第 ${p.info.displayWeek} 周", colors)
                }
                val classes = settings.classes()
                val content = ClassSchedule.ringContent(java.time.LocalDateTime.now(), settings.tables,
                    (p as? SemesterPhase.InSession)?.info?.displayWeek ?: 1)
                PreviewRow("圈内", content.subject, colors)
            }

            Spacer(Modifier.height(20.dp))
        }
    }
}

@Composable
private fun PreviewRow(label: String, value: String, colors: ZhoumuColors) {
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Text(label, fontSize = 14.sp, color = colors.secondaryText)
        Spacer(Modifier.weight(1f))
        Text(value, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = colors.accent)
    }
}

/** 卡片容器，对应 iOS 版的 `SettingsCard`。 */
@Composable
fun SettingsCard(
    title: String,
    colors: ZhoumuColors,
    content: @Composable ColumnScope.() -> Unit,
) {
    Card(
        Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = colors.card),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(title, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = colors.accent)
            content()
        }
    }
}

/**
 * 显示当前数值的加减控件。
 *
 * 系统 Stepper 在 Material3 里不显示数值；这里自己画一个：
 * `[ − | 3 节 | + ]`。到上下限时对应按钮变灰。
 */
@Composable
fun CountStepper(
    value: Int,
    range: IntRange,
    unit: String,
    colors: ZhoumuColors,
    compact: Boolean = false,
    onChange: (Int) -> Unit,
) {
    Row(
        Modifier.clip(RoundedCornerShape(9.dp)).background(colors.accentSoft),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        StepperButton("−", value > range.first, colors, compact) { onChange(value - 1) }
        Text(
            "$value $unit",
            fontSize = if (compact) 13.sp else 15.sp,
            fontWeight = FontWeight.Bold,
            color = colors.accent,
            modifier = Modifier.widthIn(min = if (compact) 42.dp else 56.dp),
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )
        StepperButton("+", value < range.last, colors, compact) { onChange(value + 1) }
    }
}

@Composable
private fun StepperButton(
    label: String,
    enabled: Boolean,
    colors: ZhoumuColors,
    compact: Boolean = false,
    onClick: () -> Unit,
) {
    Text(
        label,
        fontSize = if (compact) 17.sp else 20.sp,
        fontWeight = FontWeight.Bold,
        color = if (enabled) colors.accent else colors.faintText,
        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        modifier = Modifier
            .size(if (compact) 30.dp else 40.dp, 34.dp)
            .clickable(enabled = enabled, onClick = onClick)
            .wrapContentHeight(Alignment.CenterVertically),
    )
}
