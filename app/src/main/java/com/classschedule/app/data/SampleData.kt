package com.classschedule.app.data

import com.classschedule.app.model.Course

/**
 * M1 阶段的静态示例数据（吉林大学风格）。
 * M2 阶段将替换为 Room 数据库 + 用户自建课程。
 */
object SampleData {

    /** 当前显示的周次（示例）。 */
    const val CURRENT_WEEK: Int = 3

    val COURSES: List<Course> = listOf(
        // ---------- 周一 ----------
        Course("高等数学 A(一)", "王建国", "李四光楼 A102", 1, 1, 2, Course.allWeeks(1..16), 0),
        Course("数据结构", "刘洋", "计算机大楼 A405", 1, 3, 4, Course.allWeeks(1..16), 4),
        Course("大学物理", "陈晨", "唐敖庆楼 B201", 1, 6, 7, Course.allWeeks(3..16), 2),
        // ---------- 周二 ----------
        Course("大学英语(二)", "Sarah Smith", "外语楼 306", 2, 1, 2, Course.allWeeks(1..16), 5),
        Course("中国近现代史纲要", "赵明", "经信教学楼 F104", 2, 3, 4, Course.allWeeks(1..11), 7),
        Course("体育(二)", "李强", "南区体育馆 2 层", 2, 6, 7, Course.oddWeeks(1..15), 6),
        // ---------- 周三 ----------
        Course("线性代数", "孙丽", "李四光楼 A203", 3, 1, 2, Course.allWeeks(1..16), 1),
        Course("离散数学", "周杰", "计算机大楼 A305", 3, 3, 4, Course.allWeeks(1..16), 9),
        Course("数据结构实验", "刘洋", "计算机大楼 B505 实验室", 3, 5, 8, Course.evenWeeks(2..16), 3),
        // ---------- 周四 ----------
        Course("大学英语(二)", "Sarah Smith", "外语楼 306", 4, 1, 2, Course.allWeeks(1..16), 5),
        Course("形势与政策", "赵明", "逸夫楼 202", 4, 3, 4, Course.allWeeks(1..8), 8),
        Course("大学物理实验", "陈晨", "基础科学实验馆 201", 4, 9, 10, Course.allWeeks(5..14), 2),
        // ---------- 周五 ----------
        Course("高等数学 A(一)", "王建国", "李四光楼 A102", 5, 1, 2, Course.allWeeks(1..16), 0),
        Course("线性代数", "孙丽", "李四光楼 A203", 5, 3, 4, Course.allWeeks(1..16), 1),
        Course("中华传统文化(公选)", "吴芳", "经信教学楼 F201", 5, 9, 10, Course.allWeeks(1..16), 7),
        // 周六、周日暂无课
    )
}
