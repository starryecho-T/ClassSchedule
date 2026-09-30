package com.classschedule.app.model

/**
 * 一门课程的一次固定安排（某个时间段）。
 *
 * 周次采用离散集合表示（借鉴 zfman/TimetableView 的设计），
 * 天然支持「单周 / 双周 / 不连续周次」如 1,3,5..15。
 *
 * @property name        课程名，如「高等数学 A(一)」
 * @property teacher     授课教师
 * @property room        上课地点
 * @property dayOfWeek   星期几，1 = 周一 ... 7 = 周日
 * @property startPeriod 开始节次（从 1 开始，对应吉林大学作息）
 * @property endPeriod   结束节次（包含）
 * @property weeks       上课周次集合，如 [1..16] 或单周 [1,3,...,15]
 * @property colorIndex  颜色标签索引（在 TimetableView 的调色板中取模）
 */
data class Course(
    val name: String,
    val teacher: String,
    val room: String,
    val dayOfWeek: Int,
    val startPeriod: Int,
    val endPeriod: Int,
    val weeks: Set<Int>,
    val colorIndex: Int = 0,
    /** 数据库主键；0 表示尚未入库（内存构造的对象）。 */
    val id: Long = 0,
) {
    /** 该课程在第 [week] 周是否上课。 */
    fun isThisWeek(week: Int): Boolean = week in weeks

    companion object {
        /** 连续周次，如 [allWeeks](1..16)。 */
        fun allWeeks(range: IntRange): Set<Int> = range.toSet()

        /** 单周，如 [oddWeeks](1..15) = 1,3,...,15。 */
        fun oddWeeks(range: IntRange): Set<Int> = range.filter { it % 2 == 1 }.toSet()

        /** 双周，如 [evenWeeks](2..16) = 2,4,...,16。 */
        fun evenWeeks(range: IntRange): Set<Int> = range.filter { it % 2 == 0 }.toSet()
    }
}

