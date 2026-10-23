package com.classschedule.app.data

import com.classschedule.app.model.Course
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

/**
 * TodayPlan 纯逻辑测试。
 *
 * 学期起点内置校历：第一学期 2026-09-07（第 1 周周一）、第二学期 2027-03-01，
 * 因此 2026-10-17（周六）= 第一学期第 6 周，日期与周次的对应关系由此推算。
 */
class TodayPlanTest {

    private val term = Terms.FALL_2026_2027

    private fun course(
        day: Int,
        start: Int,
        end: Int,
        weeks: Set<Int> = (1..17).toSet(),
        hidden: Set<Int> = emptySet(),
        name: String = "测试课",
    ) = Course(name, "教师", "教室", day, start, end, weeks, 0, hidden)

    private val saturday = LocalDate.of(2026, 10, 17)   // 第 6 周周六

    @Test fun sortsEntriesByStartTime() {
        val courses = listOf(
            course(6, 7, 8, name = "下午课"),
            course(6, 1, 2, name = "早课"),
            course(6, 5, 6, name = "午课"),
            course(1, 1, 2, name = "周一课"),
        )
        val entries = TodayPlan.entriesFor(term, courses, saturday)
        assertEquals(listOf("早课", "午课", "下午课"), entries.map { it.course.name })
        assertEquals(LocalTime.of(8, 0), entries.first().startTime)
        assertEquals(LocalTime.of(9, 40), entries.first().endTime)
    }

    @Test fun respectsWeeksAndHiddenWeeks() {
        val courses = listOf(
            course(6, 1, 2, weeks = setOf(1, 2, 3), name = "早期已结束"),
            course(6, 3, 4, weeks = setOf(1, 2, 3), hidden = setOf(6), name = "被隐藏"),
            course(6, 5, 6, weeks = (6..17).toSet(), name = "本周有效"),
        )
        val entries = TodayPlan.entriesFor(term, courses, saturday)
        assertEquals(listOf("本周有效"), entries.map { it.course.name })
    }

    @Test fun outOfTermReturnsEmpty() {
        // 2027-01-02 已超 17 周（学期到 2027-01-03 周日结束）→ 1 月 4 日是学期外
        val beyond = LocalDate.of(2027, 1, 4)
        assertTrue(TodayPlan.entriesFor(term, listOf(course(1, 1, 2)), beyond).isEmpty())
        val before = LocalDate.of(2026, 8, 31)
        assertTrue(TodayPlan.entriesFor(term, listOf(course(1, 1, 2)), before).isEmpty())
    }

    @Test fun springTermComputesWeeksFromItsOwnStart() {
        val spring = Terms.SPRING_2026_2027
        val wednesday = LocalDate.of(2027, 3, 3)   // 第二学期第 1 周周三
        val entries = TodayPlan.entriesFor(spring, listOf(course(3, 1, 2)), wednesday)
        assertEquals(listOf("测试课"), entries.map { it.course.name })
        // 第一学期课表在第二学期开学前：同一份课程数据按学期区分
        assertTrue(TodayPlan.entriesFor(spring, listOf(course(3, 1, 2)), LocalDate.of(2027, 2, 24)).isEmpty())
        assertTrue(TodayPlan.entriesFor(term, listOf(course(3, 1, 2)), wednesday).isEmpty())
    }

    @Test fun statusTransitions() {
        val entry = TodayPlan.entriesFor(term, listOf(course(6, 1, 2)), saturday).single()
        assertEquals(TodayPlan.Status.UPCOMING, TodayPlan.statusOf(entry, LocalTime.of(7, 0)))
        assertEquals(TodayPlan.Status.ONGOING, TodayPlan.statusOf(entry, LocalTime.of(8, 30)))
        // 09:40 下课；边界值：恰好在下课时刻仍算 ONGOING
        assertEquals(TodayPlan.Status.ONGOING, TodayPlan.statusOf(entry, LocalTime.of(9, 40)))
        assertEquals(TodayPlan.Status.FINISHED, TodayPlan.statusOf(entry, LocalTime.of(9, 41)))
    }

    @Test fun nextEntrySkipsStartedCourses() {
        val courses = listOf(course(6, 1, 2, name = "早课"), course(6, 7, 8, name = "晚些的课"))
        val entries = TodayPlan.entriesFor(term, courses, saturday)
        assertEquals("晚些的课", TodayPlan.nextEntry(entries, LocalTime.of(9, 0))?.course?.name)
        assertNull(TodayPlan.nextEntry(entries, LocalTime.of(17, 0)))
    }

    @Test fun reminderFiresOnlyInsideWindow() {
        val courses = listOf(course(6, 5, 6)) // 13:30 开始
        val entries = TodayPlan.entriesFor(term, courses, saturday)
        // 12:00 距开始 90 分钟 → 不提醒
        assertNull(TodayPlan.nextReminder(entries, LocalDateTime.of(saturday, LocalTime.of(12, 0))))
        // 13:15 距开始 15 分钟 → 提醒
        assertEquals(5, TodayPlan.nextReminder(entries, LocalDateTime.of(saturday, LocalTime.of(13, 15)))?.course?.startPeriod)
        // 13:40 已开课 → 不提醒
        assertNull(TodayPlan.nextReminder(entries, LocalDateTime.of(saturday, LocalTime.of(13, 40))))
    }

    @Test fun headerFormatsWeekAndDate() {
        assertEquals("第6周 周六 · 10月17日", TodayPlan.header(term, saturday))
        assertEquals("第1周 周一 · 9月7日", TodayPlan.header(term, LocalDate.of(2026, 9, 7)))
        assertEquals("第1周 周一 · 3月1日", TodayPlan.header(Terms.SPRING_2026_2027, LocalDate.of(2027, 3, 1)))
    }
}
