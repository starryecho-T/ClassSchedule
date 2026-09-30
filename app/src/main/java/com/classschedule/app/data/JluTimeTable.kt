package com.classschedule.app.data

/**
 * 吉林大学作息时间表（节次 -> 上下课时间）。
 *
 * 默认采用「上午 4 节 + 下午 4 节 + 晚上 4 节」共 12 节的安排。
 * 如果与你所在校区 / 年级的实际作息不符，直接修改下方 [PERIODS] 即可，
 * 周视图会自动按新的节次数与时间渲染。
 */
object JluTimeTable {

    /** 一个节次：序号、上课时间、下课时间。 */
    data class Period(val index: Int, val start: String, val end: String)

    val PERIODS: List<Period> = listOf(
        // 上午
        Period(1, "08:00", "08:45"),
        Period(2, "08:50", "09:35"),
        Period(3, "09:50", "10:35"),
        Period(4, "10:40", "11:25"),
        // 下午
        Period(5, "13:30", "14:15"),
        Period(6, "14:20", "15:05"),
        Period(7, "15:20", "16:05"),
        Period(8, "16:10", "16:55"),
        // 晚上
        Period(9, "18:00", "18:45"),
        Period(10, "18:50", "19:35"),
        Period(11, "19:45", "20:30"),
        Period(12, "20:35", "21:20"),
    )

    /** 学期总周数（吉林大学本科一般 16~18 周，考试周另计）。 */
    const val TOTAL_WEEKS: Int = 16
}
