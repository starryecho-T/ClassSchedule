package com.classschedule.app.ui

/** 表单的范围/单双周只在用户改变选择时替换原有离散周次。 */
internal data class CourseWeekSelection(
    val fromWeek: Int,
    val toWeek: Int,
    val mode: Mode,
) {
    enum class Mode { ALL, ODD, EVEN }

    fun generatedWeeks(): Set<Int> = (fromWeek..toWeek).filter { week ->
        when (mode) {
            Mode.ALL -> true
            Mode.ODD -> week % 2 == 1
            Mode.EVEN -> week % 2 == 0
        }
    }.toSet()

    fun weeksForSave(originalWeeks: Set<Int>, originalSelection: CourseWeekSelection?): Set<Int> =
        if (this == originalSelection) originalWeeks.toSet() else generatedWeeks()

    companion object {
        fun fromWeeks(weeks: Set<Int>): CourseWeekSelection? {
            if (weeks.isEmpty()) return null
            return CourseWeekSelection(
                fromWeek = weeks.min(),
                toWeek = weeks.max(),
                mode = when {
                    weeks.size > 1 && weeks.all { it % 2 == 1 } -> Mode.ODD
                    weeks.size > 1 && weeks.all { it % 2 == 0 } -> Mode.EVEN
                    else -> Mode.ALL
                },
            )
        }
    }
}
