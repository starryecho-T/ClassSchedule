package com.classschedule.app.data.importer

import android.content.Context
import com.classschedule.app.model.Course
import java.io.File

/**
 * 教务课表页面解析器（M4）。
 *
 * 抓取方式：ImportActivity 用 WebView 渲染 ieda.jlu.edu.cn「我的课表」，
 * 登录态、验证码全部由用户在真实页面内完成，App 只提取渲染后的 DOM。
 *
 * v1 策略：页面结构未知，[parse] 暂返回空表，同时把原始 HTML 落盘快照；
 * 拿到真实快照后在此实现选择器解析（dayOfWeek/startPeriod/weeks -> Course）。
 */
object JluScheduleParser {

    /** 从「我的课表」页面 HTML 解析课程安排列表；无法识别时返回空表。 */
    fun parse(html: String): List<Course> {
        // TODO(M4-v2): 根据真实页面快照实现解析（表格 / 内嵌 JSON 均可能，先 dump 定结构）
        return emptyList()
    }

    /** 保存页面快照，便于离线分析页面结构、补充解析规则。 */
    fun dump(context: Context, html: String): File {
        val dir = File(context.filesDir, "import").apply { mkdirs() }
        return File(dir, "schedule_snapshot.html").apply { writeText(html) }
    }
}
