package com.classschedule.app.data.importer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ScheduleTableParserTest {
    private val standardHeader = listOf("课程名称", "授课教师", "上课地点", "星期", "节次", "周次")

    private fun row(
        name: String = "高等数学",
        day: String = "星期一",
        periods: String = "第1-2节",
        weeks: String = "1-16周",
        teacher: String = "张老师",
        room: String = "经信楼101",
    ) = listOf(name, teacher, room, day, periods, weeks)

    private fun parse(vararg rows: List<String>): ScheduleParseResult =
        ScheduleTableParser.parse(listOf(ScheduleTable(listOf(standardHeader) + rows)))

    private fun combined(time: String, room: String? = null): ScheduleParseResult {
        val headers = listOf("课程名", "时间地点") + listOfNotNull(room?.let { "教室" })
        val values = listOf("高等数学", time) + listOfNotNull(room)
        return ScheduleTableParser.parse(listOf(ScheduleTable(listOf(headers, values))))
    }

    private fun assertBlocked(result: ScheduleParseResult) {
        assertFalse(result.canImport)
        assertTrue("Expected a blocking issue", result.issues.isNotEmpty())
    }

    @Test fun parsesExplicitColumnsAndKeepsDatabaseFieldsUnset() {
        val result = parse(row())
        assertTrue(result.canImport)
        assertEquals(1, result.recognizedTables)
        val course = result.courses.single()
        assertEquals("高等数学", course.name)
        assertEquals("张老师", course.teacher)
        assertEquals("经信楼101", course.room)
        assertEquals(1, course.dayOfWeek)
        assertEquals(1, course.startPeriod)
        assertEquals(2, course.endPeriod)
        assertEquals((1..16).toSet(), course.weeks)
        assertEquals(0L, course.id)
        assertTrue(course.hiddenWeeks.isEmpty())
    }

    @Test fun acceptsHeaderAliasesAndSequenceColumn() {
        val header = listOf("序号", "课程名", "任课教师", "教学地点", "上课星期", "上课节次", "上课周次")
        val result = ScheduleTableParser.parse(listOf(ScheduleTable(listOf(header, listOf("1") + row()))))
        assertTrue(result.canImport)
        assertEquals("张老师", result.courses.single().teacher)
    }

    @Test fun acceptsOptionalTeacherAndRoom() {
        val result = ScheduleTableParser.parse(listOf(ScheduleTable(listOf(
            listOf("课程名", "星期几", "节次", "周次"),
            listOf("数学", "一", "1", "1"),
        ))))
        assertTrue(result.canImport)
        assertEquals("", result.courses.single().teacher)
        assertEquals("", result.courses.single().room)
    }

    @Test fun normalizesFullWidthNumbersPunctuationAndRangeSeparators() {
        val result = parse(row(day = "星期７", periods = "第０１～０２节", weeks = "第１－８，１０—１６周"))
        assertTrue(result.canImport)
        assertEquals(7, result.courses.single().dayOfWeek)
        assertEquals((1..8).toSet() + (10..16).toSet(), result.courses.single().weeks)
    }

    @Test fun acceptsEachSundaySpelling() {
        listOf("7", "星期7", "星期日", "星期天", "周日", "周天", "日", "七").forEach { value ->
            val result = parse(row(day = value))
            assertTrue("Sunday spelling: $value", result.canImport)
            assertEquals(7, result.courses.single().dayOfWeek)
        }
    }

    @Test fun acceptsDiscreteWeeksAndDisjointRanges() {
        assertEquals(setOf(1, 3, 5), parse(row(weeks = "1、3，5周")).courses.single().weeks)
        assertEquals((1..8).toSet() + (10..16).toSet(), parse(row(weeks = "1-8,10-16周")).courses.single().weeks)
    }

    @Test fun acceptsOddWeekSuffixVariants() {
        listOf("1-16周(单)", "1-16(单)周", "第1-16周（单周）", "1-16周单周").forEach { weeks ->
            val result = parse(row(weeks = weeks))
            assertTrue(weeks, result.canImport)
            assertEquals((1..16).filter { it % 2 == 1 }.toSet(), result.courses.single().weeks)
        }
    }

    @Test fun acceptsEvenWeekSuffixVariants() {
        listOf("1-16周(双)", "1-16(双)周", "第1-16周（双周）", "1-16周双周").forEach { weeks ->
            val result = parse(row(weeks = weeks))
            assertTrue(weeks, result.canImport)
            assertEquals((1..16).filter { it % 2 == 0 }.toSet(), result.courses.single().weeks)
        }
    }

    @Test fun acceptsContiguousPeriodList() {
        val course = parse(row(periods = "01,02节")).courses.single()
        assertEquals(1, course.startPeriod)
        assertEquals(2, course.endPeriod)
    }

    @Test fun rejectsNonContiguousPeriodList() {
        assertBlocked(parse(row(periods = "1,3节")))
    }

    @Test fun rejectsMissingCourseName() {
        assertBlocked(parse(row(name = " ")))
    }

    @Test fun rejectsMissingDayPeriodOrWeekWithoutDefaults() {
        assertBlocked(parse(row(day = "")))
        assertBlocked(parse(row(periods = "")))
        assertBlocked(parse(row(weeks = "")))
    }

    @Test fun rejectsZeroAndOutOfRangeWeekInsteadOfTruncating() {
        listOf("0-16周", "1-18周", "18周", "1,18周").forEach { assertBlocked(parse(row(weeks = it))) }
    }

    @Test fun rejectsZeroAndOutOfRangePeriod() {
        listOf("0-2节", "11-13节", "13节").forEach { assertBlocked(parse(row(periods = it))) }
    }

    @Test fun honorsExplicitTermAndPeriodLimits() {
        val result = ScheduleTableParser.parse(listOf(ScheduleTable(listOf(standardHeader, row(weeks = "18-20周", periods = "13-14节")))), maxWeek = 20, maxPeriod = 14)
        assertTrue(result.canImport)
        assertEquals(setOf(18, 19, 20), result.courses.single().weeks)
    }

    @Test fun rejectsInvalidConfiguration() {
        assertBlocked(ScheduleTableParser.parse(emptyList(), maxWeek = 0))
        assertBlocked(ScheduleTableParser.parse(emptyList(), maxPeriod = -1))
    }

    @Test fun rejectsReversedWeekAndPeriodRanges() {
        assertBlocked(parse(row(weeks = "16-1周")))
        assertBlocked(parse(row(periods = "2-1节")))
    }

    @Test fun rejectsEmptyParitySelection() {
        assertBlocked(parse(row(weeks = "2周(单)")))
        assertBlocked(parse(row(weeks = "1周(双)")))
    }

    @Test fun rejectsUnrecognizedSuffixesAndMixedWeekContent() {
        listOf("1-16周待定", "1-8周,另行通知", "1-8周,10-16周", "1-16周(单)(双)", "约1-16周").forEach {
            assertBlocked(parse(row(weeks = it)))
        }
    }

    @Test fun rejectsWeekAndPeriodColumnsAccidentallySwapped() {
        assertBlocked(parse(row(periods = "1-2周", weeks = "1-2节")))
    }

    @Test fun rejectsUnknownOrMultipleDays() {
        listOf("0", "8", "周一,周三", "周一至周三", "星期一备注", "每天").forEach { assertBlocked(parse(row(day = it))) }
    }

    @Test fun rejectsWhitespaceSeparatedNumbersInsteadOfJoiningThem() {
        assertBlocked(parse(row(weeks = "1 7周")))
        assertBlocked(parse(row(weeks = "1\n7周")))
        assertBlocked(parse(row(periods = "1 2节")))
    }

    @Test fun rejectsOverflowWithoutThrowing() {
        assertBlocked(parse(row(weeks = "999999999999999999999999周")))
    }

    @Test fun ignoresUnrelatedTablesAndDoesNotGuessGridPositions() {
        val result = ScheduleTableParser.parse(listOf(
            ScheduleTable(listOf(listOf("姓名", "学号"), listOf("学生", "123"))),
            ScheduleTable(listOf(listOf("节次", "星期一", "星期二"), listOf("1-2", "高等数学", "英语"))),
        ))
        assertEquals(0, result.recognizedTables)
        assertTrue(result.courses.isEmpty())
        assertFalse(result.canImport)
    }

    @Test fun rejectsIncompleteRecognizedHeaders() {
        val result = ScheduleTableParser.parse(listOf(ScheduleTable(listOf(
            listOf("课程名称", "星期", "节次"), listOf("数学", "星期一", "1-2节"),
        ))))
        assertEquals(1, result.recognizedTables)
        assertBlocked(result)
    }

    @Test fun rejectsDuplicateRecognizedHeaders() {
        val result = ScheduleTableParser.parse(listOf(ScheduleTable(listOf(
            standardHeader + "上课周次", row() + "1-16周",
        ))))
        assertBlocked(result)
    }

    @Test fun skipsRepeatedHeadersBlankRowsAndSummaryRows() {
        val result = parse(row(), emptyList(), standardHeader, List(6) { " " }, listOf("合计", "1"))
        assertTrue(result.canImport)
        assertEquals(1, result.courses.size)
    }

    @Test fun recognizesHeaderAfterTitleRows() {
        val result = ScheduleTableParser.parse(listOf(ScheduleTable(listOf(listOf("学生个人课表"), listOf("2026学年秋季"), standardHeader, row()))))
        assertTrue(result.canImport)
    }

    @Test fun deduplicatesSameScheduleAcrossTablesButKeepsDifferentSessions() {
        val result = ScheduleTableParser.parse(listOf(
            ScheduleTable(listOf(standardHeader, row(), row(), row(day = "星期三"))),
            ScheduleTable(listOf(standardHeader, row())),
        ))
        assertTrue(result.canImport)
        assertEquals(2, result.courses.size)
        assertEquals(2, result.recognizedTables)
    }

    @Test fun colorIsStableAndInPaletteRange() {
        val result = parse(row(name = "数学"), row(name = "英语"), row(name = "数学", day = "星期三"))
        assertTrue(result.courses.all { it.colorIndex in 0..7 })
        assertEquals(result.courses[0].colorIndex, result.courses[2].colorIndex)
    }

    @Test fun oneInvalidRowBlocksBatchEvenWhenOtherRowsParse() {
        val result = parse(row(), row(name = "英语", weeks = "1-18周"))
        assertEquals(1, result.courses.size)
        assertBlocked(result)
        assertTrue(result.issues.single().startsWith("表1第3行"))
    }

    @Test fun parsesOneExplicitMergedTimingInEitherOrder() {
        listOf("1-16周 星期一 第1-2节", "星期一，第1-2节；第1-16周", "周次：1-16；星期：一；节次：1-2").forEach { value ->
            val result = combined(value)
            assertTrue("$value: ${result.issues}", result.canImport)
            assertEquals((1..16).toSet(), result.courses.single().weeks)
        }
    }

    @Test fun parsesMergedOddWeeksAndExplicitRoom() {
        val result = combined("1-16周（单） 星期一 第1-2节 教室：经信楼101")
        assertTrue(result.issues.toString(), result.canImport)
        assertEquals("经信楼101", result.courses.single().room)
        assertEquals((1..16).filter { it % 2 == 1 }.toSet(), result.courses.single().weeks)
    }

    @Test fun rejectsMultipleMergedSessionsAndUnpairedLocations() {
        assertBlocked(combined("1-8周 星期一 第1-2节；9-16周 星期三 第3-4节"))
        assertBlocked(combined("1-16周 星期一、星期三 第1-2节"))
        assertBlocked(combined("1-16周 星期一 第1-2节 教室：101；教室：202"))
    }

    @Test fun rejectsMergedTimeWithUnlabelledRoomOrUnknownText() {
        assertBlocked(combined("1-16周 星期一 第1-2节 经信楼101"))
        assertBlocked(combined("1-16周 星期一 第1-2节 待定"))
        assertBlocked(combined("1-16周 星期一"))
    }

    @Test fun rejectsConflictingRoomColumns() {
        assertBlocked(combined("1-16周 星期一 第1-2节 教室：101", room = "202"))
    }

    @Test fun rejectsConflictingSplitAndMergedTime() {
        val result = ScheduleTableParser.parse(listOf(ScheduleTable(listOf(
            standardHeader + "上课时间", row(day = "星期二") + "1-16周 星期一 第1-2节",
        ))))
        assertBlocked(result)
    }

    @Test fun acceptsMatchingSplitAndMergedTime() {
        val result = ScheduleTableParser.parse(listOf(ScheduleTable(listOf(
            standardHeader + "上课时间", row() + "1-16周 星期一 第1-2节",
        ))))
        assertTrue(result.issues.toString(), result.canImport)
    }

    @Test fun emptyRecognizedTableCannotReplaceExistingCourses() {
        val result = parse()
        assertEquals(1, result.recognizedTables)
        assertTrue(result.issues.isEmpty())
        assertFalse(result.canImport)
    }
}
