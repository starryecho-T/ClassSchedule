package com.classschedule.app.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CourseTest {
    @Test
    fun emptyAndSingleWeeksHaveClearLabels() {
        assertEquals("", Course.weeksText(emptySet()))
        assertEquals("第5周", Course.weeksText(setOf(5)))
        assertEquals("第6周", Course.weeksText(setOf(6)))
    }

    @Test
    fun continuousWeeksUseRangeLabel() {
        assertEquals("1-16周", Course.weeksText(Course.allWeeks(1..16)))
        assertEquals("4-5周", Course.weeksText(setOf(5, 4)))
    }

    @Test
    fun completeOddWeeksAreRecognized() {
        assertEquals("1-15周单周", Course.weeksText(Course.oddWeeks(1..16)))
        assertEquals("3-7周单周", Course.weeksText(setOf(7, 3, 5)))
    }

    @Test
    fun completeEvenWeeksAreRecognized() {
        assertEquals("2-16周双周", Course.weeksText(Course.evenWeeks(1..16)))
        assertEquals("4-8周双周", Course.weeksText(setOf(8, 4, 6)))
    }

    @Test
    fun gapsInOddOrEvenWeeksAreShownExplicitly() {
        assertEquals("1、3、7周", Course.weeksText(setOf(1, 3, 7)))
        assertEquals("2、6、8周", Course.weeksText(setOf(2, 6, 8)))
    }

    @Test
    fun discontinuousWeeksCompressOnlyConsecutiveRuns() {
        assertEquals("1-3、5、8-9周", Course.weeksText(setOf(9, 1, 8, 2, 5, 3)))
    }

    @Test
    fun hidingWeeksDoesNotChangeScheduledWeeks() {
        val course = Course("数学", "教师", "教室", 1, 1, 2, setOf(1, 2, 3), hiddenWeeks = setOf(2))
        assertTrue(course.isThisWeek(1))
        assertFalse(course.isThisWeek(2))
        assertTrue(course.isThisWeek(3))
        assertFalse(course.isThisWeek(4))
        assertEquals("1-3周", Course.weeksText(course.weeks))
    }
}
