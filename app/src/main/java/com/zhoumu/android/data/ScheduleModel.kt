package com.zhoumu.android.data

import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.Json
import java.time.LocalDate

// MARK: - 课表种类

/** 两张独立的课表。 */
enum class ScheduleKind(val storageKey: String, val label: String) {
    Regular("schedule.regular", "正课表"),
    Evening("schedule.evening", "晚课表");
}

// MARK: - 节次时间

/**
 * 一节课的上下课时间，用「从 0:00 起的分钟数」表示。
 *
 * 用分钟数而不是时刻，是因为它和具体日期无关——
 * 同一个「第 3 节」在周一到周日都是同一个时间段。
 */
@Serializable
data class PeriodTime(val start: Int, val end: Int) {
    /** 下课必须晚于上课。 */
    val isValid: Boolean get() = start in 0..(24 * 60) && end in 0..(24 * 60) && end > start
}

// MARK: - 时间安排方式

/** 上下课时间怎么排：所有天共用一套，还是每天各一套。 */
enum class TimeMode(val label: String) {
    @SerialName("unified")
    Unified("统一"),

    @SerialName("perDay")
    PerDay("每天单独");

    companion object {
        fun fromKey(key: String?): TimeMode =
            entries.firstOrNull { it.name.equals(key, ignoreCase = true) } ?: Unified
    }
}

// MARK: - 一张课表

/**
 * 一张课表。
 *
 * 对应 iOS 版的 `ScheduleTable`，字段和语义完全一致。
 */
@Serializable
data class ScheduleTable(
    /** 这张表是否启用（可单独关闭）。 */
    val enabled: Boolean = true,

    /** 排布方式：true = 按周目轮换，false = 固定。 */
    val rotatesByWeek: Boolean = true,

    /** 每天几节。索引 0 = 周一 … 6 = 周日。 */
    val periodsPerDay: List<Int> = List(dayCount) { defaultPeriods },

    /** 时间安排方式。 */
    val timeMode: TimeMode = TimeMode.Unified,

    /** 每节的上下课时间，统一模式用这份。索引 0 = 第 1 节。 */
    val periodTimes: List<PeriodTime?> = emptyList(),

    /** 每天各自的时间，每天单独模式用这份。索引 0 = 周一。 */
    val dailyPeriodTimes: List<List<PeriodTime?>> = emptyList(),

    /** 科目表。key 是 "排-日-节"（都是 0 基），没有 key = 该格为「无」。 */
    val subjects: Map<String, String> = emptyMap(),
) {

    /** 某天有几节。 */
    fun periodCount(day: Int): Int =
        periodsPerDay.getOrElse(day) { defaultPeriods }.coerceIn(minPeriods, maxPeriods)

    /** 需要几排：轮换时 = 循环周数，固定时 = 1 排。 */
    fun rowCount(cycleWeeks: Int): Int = if (rotatesByWeek) maxOf(1, cycleWeeks) else 1

    /** 某天某节的时间；没填或非法都返回 null。 */
    fun time(period: Int, day: Int? = null): PeriodTime? {
        if (timeMode == TimeMode.PerDay && day != null) {
            val list = dailyPeriodTimes.getOrNull(day) ?: return null
            return list.getOrNull(period)?.takeIf { it.isValid }
        }
        return periodTimes.getOrNull(period)?.takeIf { it.isValid }
    }

    /** 这张表是否填过任何时间（提醒可用的前提）。 */
    val hasAnyTime: Boolean
        get() = if (timeMode == TimeMode.PerDay) {
            dailyPeriodTimes.any { list -> list.any { it?.isValid == true } }
        } else {
            periodTimes.any { it?.isValid == true }
        }

    /** 某个格子是否填了时间。 */
    fun hasTime(day: Int, period: Int): Boolean =
        period < periodCount(day) && time(period, day) != null

    /** 读某一格科目；空字符串表示「无」。 */
    fun subject(row: Int, day: Int, period: Int): String =
        subjects[metaKey(row, day, period)] ?: ""

    /** 写某一格科目；传空字符串等于清掉。 */
    fun withSubject(value: String, row: Int, day: Int, period: Int): ScheduleTable {
        val key = metaKey(row, day, period)
        val next = subjects.toMutableMap()
        if (value.isBlank()) next.remove(key) else next[key] = value.trim()
        return copy(subjects = next).normalized()
    }

    /** 写某天某节的时间。 */
    fun withTime(value: PeriodTime?, day: Int, period: Int): ScheduleTable =
        if (timeMode == TimeMode.PerDay) {
            val list = (dailyPeriodTimes.getOrNull(day) ?: emptyList()).toMutableList()
            while (list.size <= period) list.add(null)
            list[period] = value
            val days = dailyPeriodTimes.toMutableList()
            while (days.size <= day) days.add(emptyList())
            days[day] = list
            copy(dailyPeriodTimes = days).normalized()
        } else {
            val list = periodTimes.toMutableList()
            while (list.size <= period) list.add(null)
            list[period] = value
            copy(periodTimes = list).normalized()
        }

    /** 设定某天有几节。 */
    fun withPeriodCount(day: Int, count: Int): ScheduleTable {
        val list = periodsPerDay.toMutableList()
        while (list.size < dayCount) list.add(defaultPeriods)
        list[day] = count.coerceIn(minPeriods, maxPeriods)
        return copy(periodsPerDay = list).normalized()
    }

    /**
     * 切到「每天单独」时，把当前统一的时间当作每天的初始值，
     * 免得用户切过去发现全空了还要重填一遍。
     */
    fun seedDailyTimesFromUnified(): ScheduleTable {
        if (timeMode != TimeMode.PerDay) return this
        val days = (0 until dayCount).map { day ->
            val want = periodCount(day)
            List(want) { period -> periodTimes.getOrNull(period) }
        }
        return copy(dailyPeriodTimes = days).normalized()
    }

    /**
     * 把各个数组补齐到合法长度，并清掉越界的科目。
     *
     * 和 iOS 版一样：时间数组补齐到「最多节数」，每天单独模式补齐到 7 个子数组。
     */
    fun normalized(): ScheduleTable {
        val days = (0 until dayCount).map { i ->
            (periodsPerDay.getOrNull(i) ?: defaultPeriods).coerceIn(minPeriods, maxPeriods)
        }
        val maxP = days.maxOrNull() ?: defaultPeriods

        val unified = periodTimes.toMutableList()
        while (unified.size < maxP) unified.add(null)

        val perDay = (0 until dayCount).map { day ->
            val want = days[day]
            val src = dailyPeriodTimes.getOrNull(day) ?: emptyList()
            List(want) { i -> src.getOrNull(i) }
        }

        // 清掉越界的科目（节数减少后原来的格子不该留着）
        val valid = subjects.filter { (key, _) ->
            val parts = key.split("-")
            if (parts.size != 3) return@filter false
            val (r, d, p) = parts.map { it.toIntOrNull() ?: return@filter false }
            d in 0 until dayCount && p in 0 until days[d] && r >= 0
        }

        return copy(
            periodsPerDay = days,
            periodTimes = unified,
            dailyPeriodTimes = perDay,
            subjects = valid,
        )
    }

    fun encoded(): String = json.encodeToString(serializer(), this)

    companion object {
        const val minPeriods = 1
        const val maxPeriods = 12
        const val defaultPeriods = 8
        const val dayCount = 7

        private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

        /** 空表。 */
        fun empty(): ScheduleTable = ScheduleTable().normalized()

        /** 从 JSON 解出来；坏了就返回空表。 */
        fun decode(text: String?): ScheduleTable {
            if (text.isNullOrBlank()) return empty()
            return runCatching { json.decodeFromString(serializer(), text).normalized() }
                .getOrElse { empty() }
        }
    }
}

/** "排-日-节" 的 key。 */
fun metaKey(row: Int, day: Int, period: Int): String = "$row-$day-$period"

// MARK: - 合并后的一节课

/** 从两张表里合并出来的一节具体课程（带真实日期时间）。 */
data class ScheduledClass(
    val kind: ScheduleKind,
    val subject: String,
    /** 1 基。 */
    val period: Int,
    val start: java.time.LocalDateTime,
    val end: java.time.LocalDateTime,
) {
    val id: String get() = "${kind.name}-$period-${start}"
}

// MARK: - 当前状态

/** 一天之中此刻处于什么状态——首页圆环靠它。 */
sealed interface ClassState {
    /** 今天没课（或两张表都关了）。 */
    data object NoClass : ClassState
    /** 还没到第一节课。 */
    data class BeforeSchool(val next: ScheduledClass) : ClassState
    /** 正在上课。 */
    data class InClass(val current: ScheduledClass, val next: ScheduledClass?) : ClassState
    /** 课间休息。 */
    data class Resting(val justEnded: ScheduledClass, val next: ScheduledClass) : ClassState
    /** 当天的课已经全部上完。 */
    data class Finished(val last: ScheduledClass) : ClassState

    /**
     * 圆环的填充进度。
     *
     * 上课中按本节进度走；课间和完课时环是闭合的（1.0）。
     */
    fun ringProgress(at: java.time.LocalDateTime): Double = when (this) {
        is InClass -> {
            val total = java.time.Duration.between(current.start, current.end).toMinutes().toDouble()
            val done = java.time.Duration.between(current.start, at).toMinutes().toDouble()
            if (total <= 0) 1.0 else (done / total).coerceIn(0.0, 1.0)
        }
        is Resting -> 1.0
        is Finished -> 1.0
        else -> 0.0
    }
}

// MARK: - 圈内显示的内容

/** 首页圆环 / 小组件里显示的东西。 */
data class RingContent(
    val subject: String,
    val caption: String,
    val progress: Double,
    val phase: ClassState,
) {
    val isNoClass: Boolean get() = subject.isEmpty() || subject == "无课"
}

/** 三行倒计时的内容。 */
data class CountdownLines(
    val untilNextStart: Long?,
    val untilCurrentEnd: Long?,
    val nextSubject: String?,
)

// MARK: - 一天的课表计算

/** 把两张表合并成时间轴，并算出此刻的状态。 */
object ClassSchedule {

    /**
     * 把两张表合并成某一天按时间排序的课程列表。
     *
     * 只包含**填了合法时间**的节次——没时间就无法排进时间轴。
     */
    fun classes(
        date: LocalDate,
        tables: Map<ScheduleKind, ScheduleTable>,
        displayWeek: Int,
    ): List<ScheduledClass> {
        val day = SemesterCalculator.dayIndexInWeek(date)
        val out = mutableListOf<ScheduledClass>()

        for (kind in ScheduleKind.entries) {
            val table = tables[kind] ?: continue
            if (!table.enabled) continue
            val row = if (table.rotatesByWeek) maxOf(0, displayWeek - 1) else 0
            val count = table.periodCount(day)

            for (period in 0 until count) {
                val subject = table.subject(row, day, period)
                if (subject.isEmpty()) continue
                val time = table.time(period, day) ?: continue
                val start = date.atStartOfDay().plusMinutes(time.start.toLong())
                val end = date.atStartOfDay().plusMinutes(time.end.toLong())
                out += ScheduledClass(kind, subject, period + 1, start, end)
            }
        }
        return out.sortedBy { it.start }
    }

    /** 此刻处于什么状态。 */
    fun state(at: java.time.LocalDateTime, classes: List<ScheduledClass>): ClassState {
        if (classes.isEmpty()) return ClassState.NoClass

        val current = classes.firstOrNull { !at.isBefore(it.start) && at.isBefore(it.end) }

        if (current != null) {
            val next = classes.firstOrNull { it.start >= current.end }
            return ClassState.InClass(current, next)
        }

        val next = classes.firstOrNull { at.isBefore(it.start) }
        val lastEnded = classes.lastOrNull { !at.isBefore(it.end) }

        return when {
            next != null && lastEnded != null ->
                // 课间：上一节刚下课，下一节还没开始
                ClassState.Resting(lastEnded, next)
            next != null -> ClassState.BeforeSchool(next)
            else -> ClassState.Finished(classes.last())
        }
    }

    /** 某张表某一天的第一个非空科目；没启用或没课返回 null。 */
    fun firstSubject(day: Int, table: ScheduleTable, displayWeek: Int?): String? {
        if (!table.enabled) return null
        val row = if (table.rotatesByWeek) maxOf(0, (displayWeek ?: 1) - 1) else 0
        for (period in 0 until table.periodCount(day)) {
            val s = table.subject(row, day, period)
            if (s.isNotEmpty()) return s
        }
        return null
    }

    /**
     * 圈内该显示什么。
     *
     * 有时间轴就用当前状态；一节时间都没填时退回「当天正课表第一节」。
     * （iOS 版退回晚课表；Android 版没有晚课表这个概念时用正课表兜底。）
     */
    fun ringContent(
        at: java.time.LocalDateTime,
        tables: Map<ScheduleKind, ScheduleTable>,
        displayWeek: Int,
    ): RingContent {
        val classes = classes(at.toLocalDate(), tables, displayWeek)
        val day = SemesterCalculator.dayIndexInWeek(at.toLocalDate())

        if (classes.isEmpty()) {
            val fallback = ScheduleKind.entries
                .mapNotNull { tables[it]?.let { t -> firstSubject(day, t, displayWeek) } }
                .firstOrNull()
            return RingContent(
                subject = fallback ?: "无课",
                caption = if (fallback != null) "当日课程" else "",
                progress = 0.0,
                phase = ClassState.NoClass,
            )
        }

        val st = state(at, classes)
        return when (st) {
            is ClassState.InClass -> RingContent(st.current.subject, "正在上", st.ringProgress(at), st)
            is ClassState.Resting -> RingContent(st.next.subject, "课间 · 下一节", 1.0, st)
            is ClassState.BeforeSchool -> RingContent(st.next.subject, "即将开始", 0.0, st)
            is ClassState.Finished -> RingContent(st.last.subject, "今天的课上完了", 1.0, st)
            ClassState.NoClass -> RingContent("无课", "", 0.0, st)
        }
    }

    /** 三行倒计时。 */
    fun countdownLines(at: java.time.LocalDateTime, classes: List<ScheduledClass>): CountdownLines {
        val st = state(at, classes)
        return when (st) {
            is ClassState.InClass -> CountdownLines(
                untilNextStart = null,
                untilCurrentEnd = java.time.Duration.between(at, st.current.end).toMinutes(),
                nextSubject = st.next?.subject,
            )
            is ClassState.Resting -> CountdownLines(
                untilNextStart = java.time.Duration.between(at, st.next.start).toMinutes(),
                untilCurrentEnd = null,
                nextSubject = st.next.subject,
            )
            is ClassState.BeforeSchool -> CountdownLines(
                untilNextStart = java.time.Duration.between(at, st.next.start).toMinutes(),
                untilCurrentEnd = null,
                nextSubject = st.next.subject,
            )
            else -> CountdownLines(null, null, null)
        }
    }
}
