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
 * @property hiddenWeeks 被隐藏的周次（借鉴 thu-info 的三档隐藏：仅本次/全部周），
 *                      周次在集合中时不绘制，可通过详情弹窗恢复
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
    val hiddenWeeks: Set<Int> = emptySet(),
    /** 数据库主键；0 表示尚未入库（内存构造的对象）。 */
    val id: Long = 0,
) {
    /** 该课程在第 [week] 周是否上课（未被隐藏）。 */
    fun isThisWeek(week: Int): Boolean = week in weeks && week !in hiddenWeeks

    companion object {
        /** 连续周次，如 [allWeeks](1..16)。 */
        fun allWeeks(range: IntRange): Set<Int> = range.toSet()

        /** 单周，如 [oddWeeks](1..15) = 1,3,...,15。 */
        fun oddWeeks(range: IntRange): Set<Int> = range.filter { it % 2 == 1 }.toSet()

        /** 双周，如 [evenWeeks](2..16) = 2,4,...,16。 */
        fun evenWeeks(range: IntRange): Set<Int> = range.filter { it % 2 == 0 }.toSet()

        /** 周次集合的可读文本：如「1-16周」「1-15周单周」「1-16周(部分)」。 */
        fun weeksText(weeks: Set<Int>): String {
            if (weeks.isEmpty()) return ""
            val sorted = weeks.sorted()
            val first = sorted.first()
            val last = sorted.last()
            val contiguous = sorted == (first..last).toList()
            return when {
                weeks.size == 1 -> "第${first}周"
                contiguous && sorted.all { it % 2 == 1 } -> "${first}-${last}周单周"
                contiguous && sorted.all { it % 2 == 0 } -> "${first}-${last}周双周"
                contiguous -> "${first}-${last}周"
                else -> "${first}-${last}周(部分)"
            }
        }
    }
}

