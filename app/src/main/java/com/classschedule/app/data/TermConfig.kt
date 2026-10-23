package com.classschedule.app.data

import java.time.LocalDate

/**
 * 学期配置：一份校历 = 开学日（第 1 周周一）+ 总周数。
 *
 * 周次编号、日期表头、"今天"高亮、提醒与小组件全部由 [startDate] 推算，
 * 课程数据也按 [id] 在数据库中分学期保存。
 */
data class TermConfig(
    val id: String,
    val name: String,
    val startDate: LocalDate,
    val totalWeeks: Int,
) {
    init {
        require(id.isNotBlank()) { "学期标识不能为空" }
        require(totalWeeks in 1..30) { "学期总周数必须在 1..30 内" }
        require(startDate in ALLOWED_DATE_RANGE) { "开学日期超出允许范围" }
    }

    companion object {
        /** 防误选的保护性边界（与备份协议的学期校验一致）。 */
        val ALLOWED_DATE_RANGE = LocalDate.of(2020, 1, 1)..LocalDate.of(2035, 12, 31)
    }
}

/**
 * 内置学期目录，数据来自吉林大学《2026-2027 学年教学日历》：
 * - 第一学期第 1 周始于 2026-09-07（周一），17 个教学周后为两周期末考试（2027-01-04 起），1 月 18 日放寒假；
 * - 第二学期第 1 周始于 2027-03-01（周一），17 个教学周后考试，7 月 12 日放暑假。
 *
 * 今后新增学年时在此追加一个 [TermConfig] 即可，历史学期数据不受影响。
 */
object Terms {

    /** 2026-2027 学年第一学期（2026 年秋季学期）。 */
    val FALL_2026_2027 = TermConfig(
        id = "2026-2027-1",
        name = "2026-2027 学年第一学期",
        startDate = LocalDate.of(2026, 9, 7),
        totalWeeks = 17,
    )

    /** 2026-2027 学年第二学期（2027 年春季学期）。 */
    val SPRING_2026_2027 = TermConfig(
        id = "2026-2027-2",
        name = "2026-2027 学年第二学期",
        startDate = LocalDate.of(2027, 3, 1),
        totalWeeks = 17,
    )

    /** 可切换的学期列表（按时间先后排列）。 */
    val available: List<TermConfig> = listOf(FALL_2026_2027, SPRING_2026_2027)

    /** 默认学期：也是 0.9.x 之前硬编码校历对应的学期，升级用户零迁移。 */
    val DEFAULT: TermConfig = FALL_2026_2027

    /** 未知 id（偏好残留等）回退到默认学期，保证任何输入都能得到有效配置。 */
    fun byId(id: String?): TermConfig = available.firstOrNull { it.id == id } ?: DEFAULT
}
