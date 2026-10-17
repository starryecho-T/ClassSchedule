package com.classschedule.app.data

import com.classschedule.app.data.db.CourseEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class ImportPolicyTest {
    @Test
    fun prepareTrimsTextAndClearsImportedIdentityAndHiddenState() {
        val incoming = course().copy(
            id = 42, name = "  高等数学  ", teacher = " 教师 ", room = " 教室 ",
            weeksCsv = "3, 1,2,1", hiddenWeeksCsv = "1,2,3",
        )

        val prepared = ImportPolicy.prepare(listOf(incoming)).single()

        assertEquals(0L, prepared.id)
        assertEquals("高等数学", prepared.name)
        assertEquals("教师", prepared.teacher)
        assertEquals("教室", prepared.room)
        assertEquals("1,2,3", prepared.weeksCsv)
        assertEquals("", prepared.hiddenWeeksCsv)
        assertEquals(42L, incoming.id)
        assertEquals("1,2,3", incoming.hiddenWeeksCsv)
    }

    @Test
    fun duplicateBatchUsesNaturalKeyAndKeepsFirstColor() {
        val first = course().copy(colorIndex = 2)
        val duplicate = first.copy(id = 9, colorIndex = 5, weeksCsv = "3,2,1",
            name = " 高等数学 ", hiddenWeeksCsv = "1")

        val prepared = ImportPolicy.prepare(listOf(first, duplicate))

        assertEquals(1, prepared.size)
        assertEquals(2, prepared.single().colorIndex)
    }

    @Test
    fun mergeSkipsExistingRegardlessOfColorIdAndHiddenWeeks() {
        val existing = course().copy(id = 8, colorIndex = 6, hiddenWeeksCsv = "1,3")
        val prepared = ImportPolicy.prepare(listOf(course().copy(weeksCsv = "3,1,2")))

        assertTrue(ImportPolicy.additions(listOf(existing), prepared).isEmpty())
        assertEquals("1,3", existing.hiddenWeeksCsv)
        assertEquals(8L, existing.id)
    }

    @Test
    fun mergeTrimsExistingTextAndIgnoresWeekOrder() {
        val existing = course().copy(name = " 高等数学 ", teacher = " 教师 ",
            room = " 教室 ", weeksCsv = "3, 2,1,1")
        assertTrue(ImportPolicy.additions(listOf(existing),
            ImportPolicy.prepare(listOf(course()))).isEmpty())
    }

    @Test
    fun everyScheduleFieldParticipatesInNaturalKey() {
        val original = course()
        val different = listOf(
            original.copy(name = "大学物理"),
            original.copy(teacher = "另一位教师"),
            original.copy(room = "另一教室"),
            original.copy(dayOfWeek = 2),
            original.copy(startPeriod = 2),
            original.copy(endPeriod = 3),
            original.copy(weeksCsv = "1,2"),
        )
        assertEquals(7, ImportPolicy.additions(listOf(original),
            ImportPolicy.prepare(different)).size)
    }

    @Test
    fun additionsDeduplicatesItsInputAsWell() {
        assertEquals(1, ImportPolicy.additions(emptyList(), listOf(course(), course())).size)
    }

    @Test
    fun acceptsScheduleBoundsAndOptionalTeacherAndRoom() {
        val first = course().copy(dayOfWeek = 1, startPeriod = 1, endPeriod = 1,
            weeksCsv = "1", teacher = "", room = "")
        val last = course().copy(dayOfWeek = 7, startPeriod = 12, endPeriod = 12,
            weeksCsv = "17")
        assertEquals(2, ImportPolicy.prepare(listOf(first, last)).size)
    }

    @Test
    fun rejectsEmptyBatch() = expectInvalid(emptyList())

    @Test
    fun rejectsBlankName() = expectInvalid(listOf(course().copy(name = "  \t")))

    @Test
    fun rejectsOutOfRangeDays() {
        for (day in listOf(0, 8)) expectInvalid(listOf(course().copy(dayOfWeek = day)))
    }

    @Test
    fun rejectsInvalidPeriodRanges() {
        for ((start, end) in listOf(0 to 2, 1 to 13, 13 to 13, 3 to 2, 1 to 0)) {
            expectInvalid(listOf(course().copy(startPeriod = start, endPeriod = end)))
        }
    }

    @Test
    fun rejectsEmptyMalformedAndOutOfRangeWeeks() {
        for (weeks in listOf("", " ", "0", "18", "1,,2", "1,x", "1,")) {
            expectInvalid(listOf(course().copy(weeksCsv = weeks)))
        }
    }

    @Test
    fun rejectsNegativeColorIndex() = expectInvalid(listOf(course().copy(colorIndex = -1)))

    @Test
    fun validatesEveryRowBeforeDeduplication() {
        expectInvalid(listOf(course(), course().copy(colorIndex = -1)))
    }

    private fun expectInvalid(courses: List<CourseEntity>) {
        try {
            ImportPolicy.prepare(courses)
            fail("无效导入应该整批被拒绝")
        } catch (_: IllegalArgumentException) {
            // Expected: no partially prepared batch is returned.
        }
    }

    private fun course() = CourseEntity(
        name = "高等数学", teacher = "教师", room = "教室",
        dayOfWeek = 1, startPeriod = 1, endPeriod = 2,
        weeksCsv = "1,2,3", colorIndex = 0,
    )
}
