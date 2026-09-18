package com.zhoumu.android.data

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale

/**
 * 学期状态：要么还没开学，要么已经在学期里。
 *
 * 对应 iOS 版的 `SemesterPhase`。
 */
sealed interface SemesterPhase {
    /** 还没到开学日期，[daysUntilStart] 是还有几天。 */
    data class NotStarted(val daysUntilStart: Int) : SemesterPhase
    /** 已经在学期里。 */
    data class InSession(val info: SessionInfo) : SemesterPhase
}

/**
 * 开学之后的周目信息。
 *
 * 对应 iOS 版的 `SessionInfo`。
 */
data class SessionInfo(
    /** 从开学第一天算起的自然周序号（不做循环）。 */
    val rawWeek: Int,
    /** 真正展示的周目：开启循环时按 [cycleWeeks] 取模后的结果。 */
    val displayWeek: Int,
    /** 本周是第几天，1...7。 */
    val dayInWeek: Int,
    /** 从开学第一天到今天经过的天数，开学当天为 0。 */
    val daysSinceStart: Int,
    /** 生效的循环长度（周）。 */
    val cycleWeeks: Int,
    /** 是否开启循环。 */
    val cyclingEnabled: Boolean,
) {
    /** 距离下一个周目还有几天。 */
    val daysUntilNextWeek: Int get() = 7 - dayInWeek

    /** 本期周目在环上的进度，1/7...7/7。 */
    val weekProgress: Double get() = dayInWeek / 7.0

    /** 是否处于真正的循环中（开启循环且循环长度大于 1）。 */
    val isCycling: Boolean get() = cyclingEnabled && cycleWeeks > 1

    /** 课表用的排索引。 */
    val scheduleRowIndex: Int get() = if (cyclingEnabled) displayWeek - 1 else 0
}

/**
 * 周目计算：纯函数，方便单独测试。
 *
 * 从 iOS 版 `SemesterCalculator` 逐行移植，算法完全一致：
 * ```
 * 距开学天数 = 今天 - 开学日期（按自然日）
 * 开学第 rawWeek 周 = 距开学天数 / 7 + 1
 * 显示周目 = (rawWeek - 1) % 循环周数 + 1   （开启循环）
 *          = rawWeek                        （关闭循环）
 * ```
 */
object SemesterCalculator {

    /** 循环周数的可选范围。 */
    val cycleWeeksRange = 1..20

    /** 课表的列名（周一至周日）。 */
    val weekdayColumnNames = listOf("一", "二", "三", "四", "五", "六", "日")

    /** 星期几的中文短名。 */
    val weekdayShortNames = listOf("周一", "周二", "周三", "周四", "周五", "周六", "周日")

    /** 星期几的 0 基索引：0 = 周一 … 6 = 周日。 */
    fun dayIndexInWeek(date: LocalDate = LocalDate.now()): Int =
        (date.dayOfWeek.value + 6) % 7   // DayOfWeek 里 1 = 周一

    /**
     * 算某一天处于什么状态。
     *
     * 用自然日之间的天数差，跨夏令时也不会算错。
     */
    fun phase(
        startDate: LocalDate,
        cycleWeeks: Int,
        cyclingEnabled: Boolean,
        referenceDate: LocalDate = LocalDate.now(),
    ): SemesterPhase {
        val days = ChronoUnit.DAYS.between(startDate, referenceDate).toInt()

        if (days < 0) return SemesterPhase.NotStarted(-days)

        val rawWeek = days / 7 + 1
        val cycle = cycleWeeks.coerceIn(cycleWeeksRange.first, cycleWeeksRange.last)
        val displayWeek = if (cyclingEnabled) (rawWeek - 1) % cycle + 1 else rawWeek
        val dayInWeek = days % 7 + 1

        return SemesterPhase.InSession(
            SessionInfo(
                rawWeek = rawWeek,
                displayWeek = displayWeek,
                dayInWeek = dayInWeek,
                daysSinceStart = days,
                cycleWeeks = cycle,
                cyclingEnabled = cyclingEnabled,
            )
        )
    }
}

/** 日期文案，对应 iOS 版的 `DateText`。 */
object DateText {
    private val full = DateTimeFormatter.ofPattern("yyyy年M月d日 EEEE", Locale.CHINA)
    private val short = DateTimeFormatter.ofPattern("yyyy年M月d日", Locale.CHINA)
    private val weekday = DateTimeFormatter.ofPattern("EEEE", Locale.CHINA)

    /** 2026年3月18日 星期三 */
    fun full(date: LocalDate): String = date.format(full)

    /** 2026年3月18日 */
    fun short(date: LocalDate): String = date.format(short)

    /** 星期三 */
    fun weekday(date: LocalDate): String = date.format(weekday)

    /** 把「从 0:00 起的分钟数」格式化成 8:05 这样的时刻。 */
    fun timeOfDay(minutes: Int): String = "%d:%02d".format(minutes / 60, minutes % 60)

    /** 把分钟数说成「1 小时 20 分」。 */
    fun human(minutes: Int): String {
        if (minutes <= 0) return "0 分钟"
        val h = minutes / 60
        val m = minutes % 60
        return when {
            h == 0 -> "$m 分钟"
            m == 0 -> "$h 小时"
            else -> "$h 小时 $m 分"
        }
    }
}
