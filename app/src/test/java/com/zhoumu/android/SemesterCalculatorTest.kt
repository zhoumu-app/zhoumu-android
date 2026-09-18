package com.zhoumu.android

import com.zhoumu.android.data.*
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime

/**
 * 核心逻辑的单元测试。
 *
 * 和 iOS 版 `Tools/CalculatorCheck` 里的断言一一对应，
 * 保证两个平台算出来的东西完全一样。
 */
class SemesterCalculatorTest {

    private fun d(y: Int, m: Int, day: Int) = LocalDate.of(y, m, day)
    private fun t(y: Int, m: Int, day: Int, h: Int, mi: Int) = LocalDateTime.of(y, m, day, h, mi)

    // MARK: 周目取模

    @Test
    fun `循环取模和 iOS 版一致`() {
        val start = d(2026, 2, 23)
        val cases = listOf(0 to 1, 21 to 1, 28 to 2, 35 to 3, 42 to 1)
        for ((offset, expect) in cases) {
            val p = SemesterCalculator.phase(start, 3, true, start.plusDays(offset.toLong()))
            val info = (p as SemesterPhase.InSession).info
            assertEquals("距开学 $offset 天应显示第 $expect 周", expect, info.displayWeek)
        }
    }

    @Test
    fun `未开学返回剩余天数`() {
        val p = SemesterCalculator.phase(d(2026, 9, 1), 3, true, d(2026, 8, 25))
        assertEquals(7, (p as SemesterPhase.NotStarted).daysUntilStart)
    }

    @Test
    fun `关闭循环直接显示开学第几周`() {
        val p = SemesterCalculator.phase(d(2026, 2, 23), 3, false, d(2026, 3, 16))
        assertEquals(4, (p as SemesterPhase.InSession).info.displayWeek)
    }

    @Test
    fun `循环周数会被夹到合法范围`() {
        val p = SemesterCalculator.phase(d(2026, 2, 23), 99, true, d(2026, 2, 23))
        assertEquals(20, (p as SemesterPhase.InSession).info.cycleWeeks)
    }

    // MARK: 时间轴与状态机

    private fun tableWithTwoClasses(): ScheduleTable {
        var t = ScheduleTable(
            enabled = true, rotatesByWeek = false,
            periodsPerDay = listOf(2, 0, 0, 0, 0, 0, 0),
            periodTimes = listOf(PeriodTime(8 * 60, 8 * 60 + 45), PeriodTime(9 * 60, 9 * 60 + 45)),
        )
        t = t.withSubject("数学", 0, 0, 0)
        t = t.withSubject("语文", 0, 0, 1)
        return t
    }

    @Test
    fun `两张表的课能合并并按时间排序`() {
        val regular = tableWithTwoClasses()
        var evening = ScheduleTable(
            enabled = true, rotatesByWeek = false,
            periodsPerDay = listOf(1, 0, 0, 0, 0, 0, 0),
            periodTimes = listOf(PeriodTime(19 * 60, 20 * 60)),
        )
        evening = evening.withSubject("晚自习", 0, 0, 0)

        val classes = ClassSchedule.classes(
            d(2026, 9, 14),
            mapOf(ScheduleKind.Regular to regular, ScheduleKind.Evening to evening),
            1,
        )
        assertEquals(3, classes.size)
        assertEquals("数学", classes[0].subject)
        assertEquals("语文", classes[1].subject)
        assertEquals("晚自习", classes[2].subject)
    }

    @Test
    fun `状态机认得上课时和课间`() {
        val classes = ClassSchedule.classes(
            d(2026, 9, 14), mapOf(ScheduleKind.Regular to tableWithTwoClasses()), 1)

        val inClass = ClassSchedule.state(t(2026, 9, 14, 8, 20), classes)
        assertTrue(inClass is ClassState.InClass)
        assertEquals("数学", (inClass as ClassState.InClass).current.subject)
        assertEquals("语文", inClass.next?.subject)

        val resting = ClassSchedule.state(t(2026, 9, 14, 8, 50), classes)
        assertTrue(resting is ClassState.Resting)
        assertEquals(1.0, resting.ringProgress(t(2026, 9, 14, 8, 50)), 0.0001)

        val finished = ClassSchedule.state(t(2026, 9, 14, 22, 0), classes)
        assertTrue(finished is ClassState.Finished)
    }

    @Test
    fun `圆环进度按本节走的分钟数算`() {
        val classes = ClassSchedule.classes(
            d(2026, 9, 14), mapOf(ScheduleKind.Regular to tableWithTwoClasses()), 1)
        val st = ClassSchedule.state(t(2026, 9, 14, 8, 20), classes)
        assertEquals(20.0 / 45.0, st.ringProgress(t(2026, 9, 14, 8, 20)), 0.0001)
    }

    // MARK: 每天单独的时间

    @Test
    fun `默认是统一模式`() {
        val t = ScheduleTable(periodsPerDay = listOf(2, 2, 2, 2, 2, 1, 1))
            .let { it.copy(periodTimes = listOf(PeriodTime(480, 525), PeriodTime(540, 585))) }
            .normalized()
        assertEquals(TimeMode.Unified, t.timeMode)
        assertEquals(480, t.time(0, day = 3)?.start)
    }

    @Test
    fun `切成每天单独会用统一的时间打底`() {
        var t = ScheduleTable(periodsPerDay = listOf(2, 2, 2, 2, 2, 1, 1))
            .let { it.copy(periodTimes = listOf(PeriodTime(480, 525), PeriodTime(540, 585))) }
            .normalized()
        t = t.copy(timeMode = TimeMode.PerDay).seedDailyTimesFromUnified()
        assertEquals(480, t.time(0, day = 0)?.start)
        // 周日只有 1 节，所以第 1 节继承了统一时间的第一个
        assertEquals(480, t.time(0, day = 6)?.start)
        assertNull("周日只有 1 节，第 2 节本来就没有", t.time(1, day = 6))
    }

    @Test
    fun `改一天不影响别的天`() {
        var t = ScheduleTable(
            periodsPerDay = listOf(1, 1, 1, 1, 1, 1, 1),
            timeMode = TimeMode.PerDay,
        ).seedDailyTimesFromUnified()
        t = t.withTime(PeriodTime(600, 645), day = 2, period = 0)
        assertEquals(600, t.time(0, day = 2)?.start)
        assertNull(t.time(0, day = 0))
    }

    @Test
    fun `每天单独模式下时间轴按当天取`() {
        var t = ScheduleTable(
            enabled = true, rotatesByWeek = false,
            periodsPerDay = listOf(1, 1, 1, 1, 1, 1, 1),
            timeMode = TimeMode.PerDay,
        ).normalized()
        t = t.withSubject("数学", 0, 0, 0).withSubject("数学", 0, 2, 0)
        t = t.withTime(PeriodTime(480, 525), day = 0, period = 0)
        t = t.withTime(PeriodTime(840, 885), day = 2, period = 0)

        val mon = ClassSchedule.classes(d(2026, 9, 14), mapOf(ScheduleKind.Regular to t), 1)
        val wed = ClassSchedule.classes(d(2026, 9, 16), mapOf(ScheduleKind.Regular to t), 1)
        val tue = ClassSchedule.classes(d(2026, 9, 15), mapOf(ScheduleKind.Regular to t), 1)

        assertEquals(8, mon.first().start.hour)
        assertEquals(14, wed.first().start.hour)
        assertTrue(tue.isEmpty())
    }

    // MARK: 存储编解码

    @Test
    fun `编解码往返不丢东西`() {
        var t = ScheduleTable(periodsPerDay = listOf(3, 3, 3, 3, 3, 1, 1))
        t = t.withSubject("数学", 0, 0, 0).withSubject("语文", 0, 0, 1)
        t = t.withTime(PeriodTime(480, 525), day = 0, period = 0)

        val back = ScheduleTable.decode(t.encoded())
        assertEquals(3, back.periodCount(0))
        assertEquals("数学", back.subject(0, 0, 0))
        assertEquals("语文", back.subject(0, 0, 1))
        assertEquals(480, back.time(0, day = 0)?.start)
    }

    @Test
    fun `坏的或者空的 JSON 返回空表而不是崩溃`() {
        assertEquals(0, ScheduleTable.decode("").subjects.size)
        assertEquals(0, ScheduleTable.decode("{ 这不是 json }").subjects.size)
        assertEquals(ScheduleTable.dayCount, ScheduleTable.decode(null).periodsPerDay.size)
    }

    @Test
    fun `节数减少后越界的科目会被清掉`() {
        var t = ScheduleTable(periodsPerDay = listOf(3, 1, 1, 1, 1, 1, 1))
        t = t.withSubject("第三节课", 0, 0, 2)
        t = t.withPeriodCount(0, 1)
        assertEquals("", t.subject(0, 0, 2))
    }

    // MARK: 没填时间的回退

    @Test
    fun `没填时间时回退到当天第一节`() {
        var t = ScheduleTable(
            enabled = true, rotatesByWeek = false,
            periodsPerDay = listOf(2, 1, 1, 1, 1, 1, 1),
        )
        t = t.withSubject("数学", 0, 0, 0)
        val s = ClassSchedule.firstSubject(0, t, 1)
        assertEquals("数学", s)
    }

    @Test
    fun `关闭的表不参与回退`() {
        var t = ScheduleTable(enabled = false, periodsPerDay = listOf(1, 1, 1, 1, 1, 1, 1))
        t = t.withSubject("数学", 0, 0, 0)
        assertNull(ClassSchedule.firstSubject(0, t, 1))
    }

    @Test
    fun `轮换表按周目取排`() {
        var t = ScheduleTable(rotatesByWeek = true, periodsPerDay = listOf(1, 1, 1, 1, 1, 1, 1))
        t = t.withSubject("历史", 2, 0, 0)
        assertEquals("历史", ClassSchedule.firstSubject(0, t, 3))
    }

    // MARK: 日期工具

    @Test
    fun `星期索引是周一起算的`() {
        assertEquals(0, SemesterCalculator.dayIndexInWeek(d(2026, 9, 14)))  // 周一
        assertEquals(4, SemesterCalculator.dayIndexInWeek(d(2026, 9, 18)))  // 周五
        assertEquals(6, SemesterCalculator.dayIndexInWeek(d(2026, 9, 20)))  // 周日
    }
}
