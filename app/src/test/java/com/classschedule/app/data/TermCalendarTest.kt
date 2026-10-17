package com.classschedule.app.data

import org.junit.Assert.assertEquals
import org.junit.Test

class TermCalendarTest {
    @Test
    fun everyDayInFirstWeekIsWeekOne() {
        for (day in 0L..6L) {
            assertEquals(1, TermCalendar.currentWeek(TermCalendar.TERM_START.plusDays(day)))
        }
    }

    @Test
    fun weekChangesOnMonday() {
        assertEquals(1, TermCalendar.currentWeek(TermCalendar.TERM_START.plusDays(6)))
        assertEquals(2, TermCalendar.currentWeek(TermCalendar.TERM_START.plusDays(7)))
    }

    @Test
    fun everyDayImmediatelyBeforeTermIsWeekZero() {
        for (daysBefore in 1L..7L) {
            assertEquals(0, TermCalendar.currentWeek(TermCalendar.TERM_START.minusDays(daysBefore)))
        }
    }

    @Test
    fun earlierWeeksUseFloorDivision() {
        assertEquals(-1, TermCalendar.currentWeek(TermCalendar.TERM_START.minusDays(8)))
        assertEquals(-1, TermCalendar.currentWeek(TermCalendar.TERM_START.minusDays(14)))
        assertEquals(-2, TermCalendar.currentWeek(TermCalendar.TERM_START.minusDays(15)))
    }

    @Test
    fun weekDatesAndCurrentWeekAgreeAcrossTermBoundaries() {
        for (week in -2..TermCalendar.TOTAL_WEEKS + 2) {
            val dates = TermCalendar.weekDates(week)
            assertEquals(7, dates.size)
            assertEquals(1, dates.first().dayOfWeek.value)
            dates.forEach { assertEquals(week, TermCalendar.currentWeek(it)) }
        }
    }
}
