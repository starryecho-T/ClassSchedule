package com.classschedule.app.data

import com.classschedule.app.model.Course
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.format.DateTimeFormatter

/**
 * 「今天上什么课」的纯逻辑核心，供桌面小组件与提醒通知共用。
 *
 * 不持有 Android 依赖，便于 JVM 单元测试：
 * 输入课程列表 + 当前时间，输出排序后的今日安排与状态标记。
 */
object TodayPlan {

    private val timeParser: DateTimeFormatter = DateTimeFormatter.ofPattern("H:m")

    /** 今日的一条课程安排（含具体上下课时间）。 */
    data class Entry(
        val course: Course,
        val startTime: LocalTime,
        val endTime: LocalTime,
    )

    /** 一条安排在时间轴上的状态。 */
    enum class Status { UPCOMING, ONGOING, FINISHED }

    /** 距提醒阈值（分钟）：开始前 20 分钟内视为「即将上课」。 */
    const val REMIND_MINUTES: Long = 20

    /**
     * 计算某天的课程安排，按开始时间排序。
     * 周次在学期范围外时返回空列表（放假/学期未开始）；
     * 节次超出作息表范围（异常数据）的课程跳过，不崩溃。
     */
    fun entriesFor(term: TermConfig, courses: List<Course>, date: LocalDate): List<Entry> {
        val week = TermCalendar.currentWeek(term, date)
        if (week < 1 || week > term.totalWeeks) return emptyList()
        val day = date.dayOfWeek.value
        return courses.asSequence()
            .filter { it.dayOfWeek == day && it.isThisWeek(week) }
            .mapNotNull { course ->
                val start = JluTimeTable.PERIODS.firstOrNull { it.index == course.startPeriod }
                val end = JluTimeTable.PERIODS.firstOrNull { it.index == course.endPeriod }
                if (start == null || end == null) return@mapNotNull null
                Entry(course, LocalTime.parse(start.start, timeParser), LocalTime.parse(end.end, timeParser))
            }
            .sortedWith(compareBy({ it.startTime }, { it.endTime }, { it.course.name }))
            .toList()
    }

    /** 判断某条安排在 [now] 时刻的状态。 */
    fun statusOf(entry: Entry, now: LocalTime): Status = when {
        now < entry.startTime -> Status.UPCOMING
        now <= entry.endTime -> Status.ONGOING
        else -> Status.FINISHED
    }

    /** 下一条未开始的课；全部开始过则返回 null。 */
    fun nextEntry(entries: List<Entry>, now: LocalTime): Entry? =
        entries.firstOrNull { statusOf(it, now) == Status.UPCOMING }

    /** 下一条“需要提醒”的课：未开始且距开始不足 [REMIND_MINUTES] 分钟。 */
    fun nextReminder(entries: List<Entry>, now: LocalDateTime): Entry? {
        val upcoming = nextEntry(entries, now.toLocalTime()) ?: return null
        val remindAt = LocalDateTime.of(now.toLocalDate(), upcoming.startTime).minusMinutes(REMIND_MINUTES)
        return if (!now.isBefore(remindAt)) upcoming else null
    }

    /** 小组件头部文案：「第 X 周 周X · MM月dd日」。 */
    fun header(term: TermConfig, date: LocalDate): String {
        val week = TermCalendar.currentWeek(term, date).coerceIn(1, term.totalWeeks)
        val dayNames = listOf("", "周一", "周二", "周三", "周四", "周五", "周六", "周日")
        return "第${week}周 ${dayNames[date.dayOfWeek.value]} · ${date.monthValue}月${date.dayOfMonth}日"
    }
}
