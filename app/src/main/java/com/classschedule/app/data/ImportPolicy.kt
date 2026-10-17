package com.classschedule.app.data

import com.classschedule.app.data.db.CourseEntity

/** 导入前的纯校验和自然键去重；任何一行有误都拒绝整批。 */
internal object ImportPolicy {
    fun prepare(courses: List<CourseEntity>): List<CourseEntity> {
        require(courses.isNotEmpty()) { "导入课表为空，未修改现有课程" }
        return courses.mapIndexed { index, course ->
            val location = "第${index + 1}条课程安排"
            require(course.name.trim().isNotEmpty()) { "${location}缺少课程名称" }
            require(course.dayOfWeek in 1..7) { "${location}的星期必须为1至7" }
            require(course.startPeriod in 1..12 &&
                course.endPeriod in course.startPeriod..12) {
                "${location}的节次必须为1至12内的连续范围"
            }
            val weekTokens = course.weeksCsv.split(",")
            val weeks = weekTokens.map { it.trim().toIntOrNull() }
            require(weeks.isNotEmpty() && weeks.all { it != null && it in 1..17 }) {
                "${location}的周次不能为空且必须为1至17"
            }
            require(course.colorIndex >= 0) { "${location}的颜色索引无效" }
            course.copy(
                id = 0,
                name = course.name.trim(),
                teacher = course.teacher.trim(),
                room = course.room.trim(),
                weeksCsv = weeks.filterNotNull().toSortedSet().joinToString(","),
                hiddenWeeksCsv = "",
            )
        }.distinctBy(::naturalKey)
    }

    /** 保留数据库中的原行及其隐藏状态，只返回确实需要新增的安排。 */
    fun additions(
        existing: List<CourseEntity>,
        prepared: List<CourseEntity>,
    ): List<CourseEntity> {
        val seen = existing.mapTo(mutableSetOf(), ::naturalKey)
        return prepared.filter { seen.add(naturalKey(it)) }
    }

    private fun naturalKey(course: CourseEntity) = NaturalKey(
        name = course.name.trim(),
        teacher = course.teacher.trim(),
        room = course.room.trim(),
        dayOfWeek = course.dayOfWeek,
        startPeriod = course.startPeriod,
        endPeriod = course.endPeriod,
        // 与领域模型的周次语义一致，不依赖 CSV 顺序。
        weeks = course.weeksCsv.split(",").mapNotNull { it.trim().toIntOrNull() }.toSet(),
    )

    private data class NaturalKey(
        val name: String,
        val teacher: String,
        val room: String,
        val dayOfWeek: Int,
        val startPeriod: Int,
        val endPeriod: Int,
        val weeks: Set<Int>,
    )
}
