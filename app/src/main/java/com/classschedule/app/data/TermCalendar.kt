package com.classschedule.app.data

import java.time.LocalDate
import java.time.temporal.ChronoUnit

/**
 * 校历工具：由学期开学日（第 1 周的周一）推算周次与具体日期。
 *
 * 所有函数都以 [TermConfig] 为参数（纯函数，无全局状态）：
 * 周次编号与表头日期跟随传入的学期，切换学期后各调用方自动重算。
 * 内置学期见 [Terms]。
 */
object TermCalendar {

    /** [today] 在 [term] 中是第几周（学期外可能返回 <=0 或 > 总周数，调用方自行 clamp）。 */
    fun currentWeek(term: TermConfig, today: LocalDate = LocalDate.now()): Int =
        Math.floorDiv(ChronoUnit.DAYS.between(term.startDate, today), 7L).toInt() + 1

    /** [term] 第 [week] 周的 7 个日期（下标 0=周一 ... 6=周日）。 */
    fun weekDates(term: TermConfig, week: Int): List<LocalDate> {
        val monday = term.startDate.plusWeeks((week - 1).toLong())
        return (0L..6L).map(monday::plusDays)
    }

    /** [week] 是否为 [term] 的本周。 */
    fun isCurrentWeek(term: TermConfig, week: Int): Boolean = currentWeek(term) == week

    /** 默认定位周：今天在学期内取本周，假期/学期外取最近的边界周。 */
    fun defaultWeek(term: TermConfig, today: LocalDate = LocalDate.now()): Int =
        currentWeek(term, today).coerceIn(1, term.totalWeeks)
}
