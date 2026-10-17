package com.classschedule.app.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CourseWeekSelectionTest {
    @Test
    fun unchangedSelectionPreservesIrregularWeeks() {
        val original = setOf(1, 2, 5, 9)
        val selection = requireNotNull(CourseWeekSelection.fromWeeks(original))
        assertEquals(original, selection.weeksForSave(original, selection))
    }

    @Test
    fun unchangedOddSelectionPreservesMissingOddWeeks() {
        val original = setOf(1, 5, 9)
        val selection = requireNotNull(CourseWeekSelection.fromWeeks(original))
        assertEquals(CourseWeekSelection.Mode.ODD, selection.mode)
        assertEquals(original, selection.weeksForSave(original, selection))
    }

    @Test
    fun changingWeekModeRegeneratesWeeks() {
        val original = setOf(1, 2, 5, 9)
        val initial = requireNotNull(CourseWeekSelection.fromWeeks(original))
        val edited = initial.copy(mode = CourseWeekSelection.Mode.EVEN)
        assertEquals(setOf(2, 4, 6, 8), edited.weeksForSave(original, initial))
    }

    @Test
    fun changingRangeRegeneratesWeeks() {
        val original = setOf(2, 6)
        val initial = requireNotNull(CourseWeekSelection.fromWeeks(original))
        val edited = initial.copy(toWeek = 8)
        assertEquals(setOf(2, 4, 6, 8), edited.weeksForSave(original, initial))
    }

    @Test
    fun newCourseUsesSelectedRange() {
        val selected = CourseWeekSelection(1, 4, CourseWeekSelection.Mode.ALL)
        assertEquals(setOf(1, 2, 3, 4), selected.weeksForSave(emptySet(), null))
    }

    @Test
    fun parityCanProduceAnEmptySelection() {
        assertTrue(CourseWeekSelection(2, 2, CourseWeekSelection.Mode.ODD).generatedWeeks().isEmpty())
        assertTrue(CourseWeekSelection(3, 3, CourseWeekSelection.Mode.EVEN).generatedWeeks().isEmpty())
    }

    @Test
    fun singleWeekIsPreserved() {
        val original = setOf(7)
        val initial = requireNotNull(CourseWeekSelection.fromWeeks(original))
        assertEquals(original, initial.weeksForSave(original, initial))
    }
}
