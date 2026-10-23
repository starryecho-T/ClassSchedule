package com.classschedule.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.time.LocalDate

/**
 * 校历推算测试。内置学期来自吉林大学 2026-2027 学年教学日历：
 * 第一学期第 1 周 = 2026-09-07（周一），第二学期第 1 周 = 2027-03-01（周一），均 17 教学周。
 */
class TermCalendarTest {
    private val term = Terms.FALL_2026_2027

    @Test
    fun everyDayInFirstWeekIsWeekOne() {
        for (day in 0L..6L) {
            assertEquals(1, TermCalendar.currentWeek(term, term.startDate.plusDays(day)))
        }
    }

    @Test
    fun weekChangesOnMonday() {
        assertEquals(1, TermCalendar.currentWeek(term, term.startDate.plusDays(6)))
        assertEquals(2, TermCalendar.currentWeek(term, term.startDate.plusDays(7)))
    }

    @Test
    fun everyDayImmediatelyBeforeTermIsWeekZero() {
        for (daysBefore in 1L..7L) {
            assertEquals(0, TermCalendar.currentWeek(term, term.startDate.minusDays(daysBefore)))
        }
    }

    @Test
    fun earlierWeeksUseFloorDivision() {
        assertEquals(-1, TermCalendar.currentWeek(term, term.startDate.minusDays(8)))
        assertEquals(-1, TermCalendar.currentWeek(term, term.startDate.minusDays(14)))
        assertEquals(-2, TermCalendar.currentWeek(term, term.startDate.minusDays(15)))
    }

    @Test
    fun weekDatesAndCurrentWeekAgreeAcrossTermBoundaries() {
        for (week in -2..term.totalWeeks + 2) {
            val dates = TermCalendar.weekDates(term, week)
            assertEquals(7, dates.size)
            assertEquals(1, dates.first().dayOfWeek.value)
            dates.forEach { assertEquals(week, TermCalendar.currentWeek(term, it)) }
        }
    }

    @Test
    fun springTermStartsFromCalendar() {
        val spring = Terms.SPRING_2026_2027
        assertEquals(LocalDate.of(2027, 3, 1), spring.startDate)
        assertEquals(1, TermCalendar.currentWeek(spring, LocalDate.of(2027, 3, 7)))
        assertEquals(2, TermCalendar.currentWeek(spring, LocalDate.of(2027, 3, 8)))
        assertEquals(LocalDate.of(2027, 3, 1), TermCalendar.weekDates(spring, 1).first())
        // 两学期互不干扰：同一日期的周次编号各自独立
        assertEquals(LocalDate.of(2027, 3, 8), TermCalendar.weekDates(spring, 2).first())
    }

    @Test
    fun builtInTermsMatchCalendar() {
        assertEquals(LocalDate.of(2026, 9, 7), Terms.FALL_2026_2027.startDate)
        assertEquals(LocalDate.of(2027, 3, 1), Terms.SPRING_2026_2027.startDate)
        assertTrue(Terms.available.all { it.totalWeeks == 17 })
        assertEquals(listOf(Terms.FALL_2026_2027.id, Terms.SPRING_2026_2027.id), Terms.available.map { it.id })
        assertEquals(Terms.FALL_2026_2027, Terms.DEFAULT)
        // 未知 id（含 null）回退默认学期，已知 id 精确匹配
        assertEquals(Terms.FALL_2026_2027, Terms.byId(null))
        assertEquals(Terms.FALL_2026_2027, Terms.byId("does-not-exist"))
        assertEquals(Terms.SPRING_2026_2027, Terms.byId("2026-2027-2"))
    }

    @Test
    fun defaultWeekClampsToNearestBoundary() {
        assertEquals(1, TermCalendar.defaultWeek(term, LocalDate.of(2026, 8, 31)))
        assertEquals(term.totalWeeks, TermCalendar.defaultWeek(term, LocalDate.of(2027, 1, 20)))
        assertEquals(6, TermCalendar.defaultWeek(term, LocalDate.of(2026, 10, 17)))
    }

    @Test
    fun termConfigRejectsOutOfRangeValues() {
        for (badWeeks in listOf(0, 31, -1)) {
            try { TermConfig("t", "测试学期", LocalDate.of(2026, 9, 7), badWeeks); fail("周数 $badWeeks 被接受") }
            catch (_: IllegalArgumentException) {}
        }
        for (badDate in listOf(LocalDate.of(2019, 12, 31), LocalDate.of(2036, 1, 1))) {
            try { TermConfig("t", "测试学期", badDate, 17); fail("日期 $badDate 被接受") }
            catch (_: IllegalArgumentException) {}
        }
        try { TermConfig(" ", "测试学期", LocalDate.of(2026, 9, 7), 17); fail("空 id 被接受") }
        catch (_: IllegalArgumentException) {}
        // 边界内合法
        TermConfig("t", "测试学期", LocalDate.of(2020, 1, 1), 1)
        TermConfig("t", "测试学期", LocalDate.of(2035, 12, 31), 30)
    }
}
