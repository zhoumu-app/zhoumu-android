package com.zhoumu.android.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
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
import com.zhoumu.android.ui.theme.ZhoumuColors
import com.zhoumu.android.ui.theme.ZhoumuThemeAccessor
import java.time.LocalDate

/** 单张课表的编辑器。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScheduleEditorScreen(
    repo: SettingsRepository,
    settings: ZhoumuSettings,
    kind: ScheduleKind,
    onBack: () -> Unit,
) {
    val colors = ZhoumuThemeAccessor.colors
    val table = settings.table(kind)

    var selectedRow by remember { mutableIntStateOf(0) }
    var selectedDay by remember { mutableIntStateOf(SemesterCalculator.dayIndexInWeek(LocalDate.now())) }
    var selectedTimeDay by remember { mutableIntStateOf(SemesterCalculator.dayIndexInWeek(LocalDate.now())) }
    var subjectTarget by remember { mutableStateOf<Triple<Int, Int, Int>?>(null) }
    var timeTarget by remember { mutableStateOf<Pair<Int, Int?>?>(null) }

    fun put(next: ScheduleTable) = repo.update { it.withTable(kind, next) }

    Scaffold(
        containerColor = colors.background,
        topBar = {
            TopAppBar(
                title = { Text(kind.label, fontWeight = FontWeight.Bold, color = colors.primaryText) },
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
            // 启用
            SettingsCard("启用", colors) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("启用${kind.label}", fontSize = 16.sp, color = colors.primaryText)
                    Spacer(Modifier.weight(1f))
                    Switch(checked = table.enabled,
                        onCheckedChange = { v -> put(table.copy(enabled = v)) })
                }
                Text(
                    if (table.enabled) "已启用。首页会把这节课算进去。"
                    else "已关闭。这张表不参与首页显示。",
                    fontSize = 12.sp, color = colors.secondaryText,
                )
            }

            // 排布方式
            SettingsCard("排布方式", colors) {
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    listOf(false to "固定", true to "按周目轮换").forEachIndexed { i, (v, label) ->
                        SegmentedButton(
                            selected = table.rotatesByWeek == v,
                            onClick = { put(table.copy(rotatesByWeek = v)) },
                            shape = SegmentedButtonDefaults.itemShape(i, 2),
                        ) { Text(label, fontSize = 14.sp) }
                    }
                }
                Text(
                    if (table.rotatesByWeek)
                        "共 ${maxOf(1, settings.cycleWeeks)} 排，每周换一排。"
                    else "只有一排，每周都上同一份课表。",
                    fontSize = 12.sp, color = colors.secondaryText,
                )
            }

            // 每日节数
            SettingsCard("每日节数", colors) {
                for (day in 0 until ScheduleTable.dayCount) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text(SemesterCalculator.weekdayShortNames[day],
                            fontSize = 15.sp, color = colors.primaryText)
                        Spacer(Modifier.weight(1f))
                        CountStepper(
                            value = table.periodCount(day),
                            range = ScheduleTable.minPeriods..ScheduleTable.maxPeriods,
                            unit = "节",
                            colors = colors,
                        ) { v -> put(table.withPeriodCount(day, v)) }
                    }
                }
                Text("每天可以不一样，最多 ${ScheduleTable.maxPeriods} 节。",
                    fontSize = 12.sp, color = colors.secondaryText)
            }

            // 上下课时间
            SettingsCard("上下课时间（可选）", colors) {
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    TimeMode.entries.forEachIndexed { i, mode ->
                        SegmentedButton(
                            selected = table.timeMode == mode,
                            onClick = {
                                var next = table.copy(timeMode = mode)
                                if (mode == TimeMode.PerDay) next = next.seedDailyTimesFromUnified()
                                put(next.normalized())
                            },
                            shape = SegmentedButtonDefaults.itemShape(i, TimeMode.entries.size),
                        ) { Text(mode.label, fontSize = 13.sp) }
                    }
                }

                if (table.timeMode == TimeMode.PerDay) {
                    DayChips(selected = selectedTimeDay, colors = colors) { selectedTimeDay = it }
                }

                val count = if (table.timeMode == TimeMode.PerDay)
                    table.periodCount(selectedTimeDay)
                else (table.periodsPerDay.maxOrNull() ?: 0)

                for (period in 0 until count) {
                    val day = if (table.timeMode == TimeMode.PerDay) selectedTimeDay else null
                    val t = table.time(period, day)
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp))
                            .clickable {
                                timeTarget = period to day
                            }.padding(vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text("第 ${period + 1} 节", fontSize = 15.sp, color = colors.primaryText)
                        Spacer(Modifier.weight(1f))
                        Text(
                            t?.let { "${DateText.timeOfDay(it.start)} – ${DateText.timeOfDay(it.end)}" }
                                ?: "未设置",
                            fontSize = 15.sp,
                            fontWeight = if (t != null) FontWeight.Bold else FontWeight.Normal,
                            color = if (t != null) colors.accent else colors.faintText,
                        )
                        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null,
                            tint = colors.faintText)
                    }
                }
                Text(
                    if (table.hasAnyTime) "已填时间。首页会按时间排时间轴，提醒也能用了。"
                    else "一节都没填。此时首页只显示当天课程，提醒不可用。",
                    fontSize = 12.sp, color = colors.secondaryText,
                )
            }

            // 科目
            SettingsCard("科目", colors) {
                val rowCount = table.rowCount(settings.cycleWeeks)
                if (rowCount > 1) {
                    val row = selectedRow.coerceIn(0, rowCount - 1)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        for (r in 0 until rowCount) {
                            Chip("第 ${r + 1} 周", r == row, colors) { selectedRow = r }
                        }
                    }
                }
                val row = selectedRow.coerceIn(0, maxOf(0, table.rowCount(settings.cycleWeeks) - 1))
                DayChips(selected = selectedDay, colors = colors) { selectedDay = it }

                for (period in 0 until table.periodCount(selectedDay)) {
                    val subject = table.subject(row, selectedDay, period)
                    val t = table.time(period, selectedDay)
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp))
                            .clickable { subjectTarget = Triple(row, selectedDay, period) }
                            .padding(vertical = 9.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(
                            Modifier.size(26.dp).clip(CircleShape).background(colors.accentSoft),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text("${period + 1}", fontSize = 13.sp, fontWeight = FontWeight.Bold,
                                color = colors.accent)
                        }
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(subject.ifEmpty { "无" }, fontSize = 16.sp, color = colors.primaryText)
                            if (t != null) {
                                Text("${DateText.timeOfDay(t.start)} – ${DateText.timeOfDay(t.end)}",
                                    fontSize = 12.sp, color = colors.secondaryText)
                            }
                        }
                        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = colors.faintText)
                    }
                }
                Text("点任意一节改科目，只有「无」和「自定义」两种。",
                    fontSize = 12.sp, color = colors.secondaryText)
            }

            Spacer(Modifier.height(20.dp))
        }
    }

    subjectTarget?.let { (row, day, period) ->
        SubjectDialog(
            initial = table.subject(row, day, period),
            title = "${SemesterCalculator.weekdayShortNames[day]} 第 ${period + 1} 节",
            colors = colors,
            onDismiss = { subjectTarget = null },
        ) { value ->
            put(table.withSubject(value, row, day, period))
            subjectTarget = null
        }
    }

    timeTarget?.let { (period, day) ->
        TimeDialog(
            initial = table.time(period, day),
            title = buildString {
                if (day != null) append(SemesterCalculator.weekdayShortNames[day]).append(" ")
                append("第 ${period + 1} 节")
            },
            colors = colors,
            onDismiss = { timeTarget = null },
        ) { value ->
            put(table.withTime(value, day ?: 0, period))
            timeTarget = null
        }
    }
}

/** 星期选择 chip。 */
@Composable
private fun DayChips(selected: Int, colors: ZhoumuColors, onSelect: (Int) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        for (d in 0 until ScheduleTable.dayCount) {
            Chip(SemesterCalculator.weekdayColumnNames[d], d == selected, colors) { onSelect(d) }
        }
    }
}

@Composable
private fun Chip(label: String, selected: Boolean, colors: ZhoumuColors, onClick: () -> Unit) {
    Text(
        label,
        fontSize = 14.sp,
        fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
        color = if (selected) colors.card else colors.secondaryText,
        modifier = Modifier
            .clip(RoundedCornerShape(9.dp))
            .background(if (selected) colors.accent else colors.accentSoft)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 6.dp),
    )
}

/** 科目编辑：只有「无」和「自定义」。 */
@Composable
private fun SubjectDialog(
    initial: String,
    title: String,
    colors: ZhoumuColors,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit,
) {
    var text by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = colors.card,
        title = { Text(title, color = colors.primaryText) },
        text = {
            Column {
                Text("点任意一节改科目，只有「无」和「自定义」两种。",
                    fontSize = 12.sp, color = colors.secondaryText)
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    label = { Text("科目名") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(text) }) { Text("保存", color = colors.accent) }
        },
        dismissButton = {
            TextButton(onClick = { onSave("") }) { Text("设成无", color = colors.secondaryText) }
        },
    )
}

/** 时间编辑：两个小时/分钟选择 + 清除。 */
@Composable
private fun TimeDialog(
    initial: PeriodTime?,
    title: String,
    colors: ZhoumuColors,
    onDismiss: () -> Unit,
    onSave: (PeriodTime?) -> Unit,
) {
    var enabled by remember { mutableStateOf(initial != null) }
    var start by remember { mutableIntStateOf(initial?.start ?: (8 * 60)) }
    var end by remember { mutableIntStateOf(initial?.end ?: (8 * 60 + 45)) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = colors.card,
        title = { Text(title, color = colors.primaryText) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("设置上下课时间", fontSize = 15.sp, color = colors.primaryText)
                    Spacer(Modifier.weight(1f))
                    Switch(checked = enabled, onCheckedChange = { enabled = it })
                }
                if (enabled) {
                    MinuteRow("上课", start, colors) { start = it }
                    MinuteRow("下课", end, colors) { end = it }
                    if (end <= start) {
                        Text("下课时间要晚于上课时间。", fontSize = 12.sp, color = colors.warning)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = !enabled || end > start,
                onClick = { onSave(if (enabled) PeriodTime(start, end) else null) },
            ) { Text("保存", color = colors.accent) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消", color = colors.secondaryText) }
        },
    )
}

@Composable
private fun MinuteRow(label: String, minutes: Int, colors: ZhoumuColors, onChange: (Int) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, fontSize = 15.sp, color = colors.primaryText)
        Spacer(Modifier.weight(1f))
        CountStepper(
            value = minutes / 60,
            range = 0..23,
            unit = "时",
            colors = colors,
        ) { onChange(it * 60 + minutes % 60) }
        Spacer(Modifier.width(8.dp))
        CountStepper(
            value = minutes % 60,
            range = 0..55,
            unit = "分",
            colors = colors,
        ) { onChange((minutes / 60) * 60 + it) }
    }
}
