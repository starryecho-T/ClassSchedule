package com.classschedule.app.data

import com.classschedule.app.model.Course

/**
 * 真实课表数据（2026-2027 学年第一学期 · 25 级地球物理学），
 * 整理自教务系统「我的课表」截图，覆盖第 4-17 周。
 *
 * 同一门课在不同天/节次的记录使用同一颜色索引，便于在周视图中识别。
 * M3 起周次由 [TermCalendar] 按日期计算，不再使用硬编码值。
 */
object SampleData {

    val COURSES: List<Course> = listOf(
        // ---------- 周一 ----------
        Course("概率论与数理统计B", "李亚军", "李四光楼-206", 1, 1, 2, Course.allWeeks(4..17), 0),
        Course("地理信息科学概论", "张艳红", "李四光楼-313", 1, 3, 4, Course.allWeeks(4..8), 3),
        Course("自然地理学", "张艳红", "李四光楼-313", 1, 3, 4, Course.allWeeks(9..16), 4),
        Course("MATLAB程序设计", "丁继红", "第二阶梯教室", 1, 5, 6, Course.allWeeks(4..13), 2),
        Course("地图学", "王明常", "李四光楼-308", 1, 7, 8, Course.allWeeks(4..12), 6),
        Course("思想政治理论课实践教学", "王笑严", "线上教学", 1, 9, 11, Course.allWeeks(4..9), 5),
        // ---------- 周二 ----------
        Course("面向对象程序设计", "路兴昌", "李四光楼-308", 2, 1, 2, Course.allWeeks(4..12), 7),
        Course("地理信息系统原理A", "路兴昌", "李四光楼-312", 2, 3, 4, Course.allWeeks(4..15), 8),
        Course("大学物理B", "郭欣", "李四光楼-206", 2, 5, 6, Course.allWeeks(4..16), 1),
        Course("体育Ⅲ", "夏忠岩", "体育场", 2, 7, 8, Course.allWeeks(4..17), 9),
        Course("马克思主义基本原理", "王思然", "李四光楼-107", 2, 9, 11, Course.allWeeks(4..10), 5),
        // ---------- 周三 ----------
        Course("中国文化的英文表达Ⅱ", "于晓辉", "敬信楼D305", 3, 1, 2, Course.allWeeks(4..17), 3),
        Course("人文地理学", "张萍", "李四光楼-312", 3, 3, 4, Course.allWeeks(4..16), 9),
        Course("MATLAB程序设计", "丁继红", "第二阶梯教室", 3, 5, 6, Course.allWeeks(4..13), 2),
        Course("自然地理学", "张艳红", "李四光楼-313", 3, 7, 8, Course.allWeeks(4..16), 4),
        // ---------- 周四 ----------
        Course("岩石学B", "张晋瑞（前期刘娜）", "第二阶梯教室", 4, 1, 2, Course.allWeeks(4..17), 6),
        Course("面向对象程序设计", "路兴昌", "李四光楼-308", 4, 3, 4, Course.allWeeks(4..12), 7),
        Course("人文地理学", "张萍", "李四光楼-308", 4, 3, 4, Course.allWeeks(13..16), 9),
        Course("地理信息系统原理A", "路兴昌", "李四光楼-312", 4, 5, 6, Course.allWeeks(4..15), 8),
        Course("大学物理B", "郭欣", "李四光楼-206", 4, 7, 8, Course.allWeeks(4..16), 1),
        Course("地图学", "王明常", "李四光楼-210", 4, 9, 10, Course.allWeeks(4..12), 6),
        // ---------- 周五 ----------
        Course("概率论与数理统计B", "李亚军", "李四光楼-206", 5, 1, 2, Course.allWeeks(4..17), 0),
        Course("人文地理学", "张萍", "李四光楼-208", 5, 3, 4, Course.allWeeks(13..16), 9),
        Course("岩石学B", "张晋瑞（前期刘娜）", "第二阶梯教室", 5, 5, 6, Course.allWeeks(4..17), 6),
        // ---------- 周六 ----------
        Course("大学物理实验B", "于国伟", "李四光楼", 6, 1, 4, Course.allWeeks(4..15), 1),
        Course("形势与政策Ⅲ", "黄丽娜", "李四光楼-109", 6, 5, 6, Course.allWeeks(4..4), 5),
        // 周日全天无课
    )
}
