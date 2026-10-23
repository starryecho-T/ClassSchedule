package com.classschedule.app.data.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import com.classschedule.app.data.Terms
import com.classschedule.app.model.Course

/**
 * 课程表的数据库实体。周次集合以 CSV 字符串落库（如 "1,2,3,..."）。
 *
 * [termId] 把课程归到具体学期（多学期切换后各学期课表互不影响）；
 * 迁移默认值与 [Terms.DEFAULT].id 保持一致，旧版本课程全部归入第一学期。
 */
@Entity(tableName = "courses")
data class CourseEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "name") val name: String,
    @ColumnInfo(name = "teacher") val teacher: String,
    @ColumnInfo(name = "room") val room: String,
    @ColumnInfo(name = "day_of_week") val dayOfWeek: Int,
    @ColumnInfo(name = "start_period") val startPeriod: Int,
    @ColumnInfo(name = "end_period") val endPeriod: Int,
    @ColumnInfo(name = "weeks_csv") val weeksCsv: String,
    @ColumnInfo(name = "color_index") val colorIndex: Int,
    @ColumnInfo(name = "hidden_weeks_csv", defaultValue = "") val hiddenWeeksCsv: String = "",
    @ColumnInfo(name = "term_id", defaultValue = "2026-2027-1") val termId: String = Terms.DEFAULT.id,
)

/** 领域对象 <-> 实体 转换（课程归属的学期由仓库层在写入时指定）。 */
fun Course.toEntity(termId: String): CourseEntity = CourseEntity(
    id = id,
    name = name,
    teacher = teacher,
    room = room,
    dayOfWeek = dayOfWeek,
    startPeriod = startPeriod,
    endPeriod = endPeriod,
    weeksCsv = weeks.joinToString(",") { it.toString() },
    colorIndex = colorIndex,
    hiddenWeeksCsv = hiddenWeeks.joinToString(",") { it.toString() },
    termId = termId,
)

fun CourseEntity.toDomain(): Course = Course(
    id = id,
    name = name,
    teacher = teacher,
    room = room,
    dayOfWeek = dayOfWeek,
    startPeriod = startPeriod,
    endPeriod = endPeriod,
    weeks = weeksCsv.split(",").mapNotNull { it.trim().toIntOrNull() }.toSet(),
    colorIndex = colorIndex,
    hiddenWeeks = hiddenWeeksCsv.split(",").mapNotNull { it.trim().toIntOrNull() }.toSet(),
)
