package com.classschedule.app.data.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import com.classschedule.app.model.Course

/**
 * 课程表的数据库实体。周次集合以 CSV 字符串落库（如 "1,2,3,..."）。
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
)

/** 领域对象 <-> 实体 转换。 */
fun Course.toEntity(): CourseEntity = CourseEntity(
    id = id,
    name = name,
    teacher = teacher,
    room = room,
    dayOfWeek = dayOfWeek,
    startPeriod = startPeriod,
    endPeriod = endPeriod,
    weeksCsv = weeks.joinToString(",") { it.toString() },
    colorIndex = colorIndex,
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
)
