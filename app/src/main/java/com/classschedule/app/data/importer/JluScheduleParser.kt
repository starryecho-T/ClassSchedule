package com.classschedule.app.data.importer

import android.content.Context
import org.json.JSONObject
import java.io.File

/**
 * 将页面采集结果交给严格的列表解析器。真实学校页面仍需样本校准。
 *
 * 抓取方式：ImportActivity 用 WebView 渲染 iedu.jlu.edu.cn「我的课表」，
 * 登录态、验证码全部由用户在真实页面内完成，App 只提取渲染后的 DOM。
 *
 * 仅解释有明确课程名、星期、节次和周次的列表；未知页面绝不猜测时间。
 */
object JluScheduleParser {

    data class Capture(
        val sourceUrl: String,
        val tables: List<ScheduleTable>,
        val cards: List<GridCard>,
        val warnings: List<String>,
        val html: String,
    )

    /** WebView JSON 作为不可信页面输入处理：尺寸和单元格类型必须先通过校验。 */
    fun decodeCapture(payload: String): Capture {
        require(payload.length <= 4 * 1024 * 1024) { "页面过大，请切换到课表列表后重试" }
        val json = JSONObject(payload)
        val rawTables = json.getJSONArray("tables")
        require(rawTables.length() <= 40) { "页面表格过多" }
        var rowCount = 0
        val tables = (0 until rawTables.length()).map { tableIndex ->
            val rows = rawTables.getJSONObject(tableIndex).getJSONArray("rows")
            rowCount += rows.length()
            require(rowCount <= 1000) { "页面行数过多，请先筛选当前学期" }
            ScheduleTable((0 until rows.length()).map { rowIndex ->
                val cells = rows.getJSONArray(rowIndex)
                require(cells.length() <= 40) { "表格列数超出范围" }
                (0 until cells.length()).map { column ->
                    val value = cells.get(column)
                    require(value is String && value.length <= 4000) { "表格字段格式不正确或过长" }
                    value
                }
            })
        }
        val rawWarnings = json.getJSONArray("warnings")
        require(rawWarnings.length() <= 100) { "页面诊断数量异常" }
        val warnings = (0 until rawWarnings.length()).map { rawWarnings.getString(it).take(300) }
        val cards = decodeCards(json.optJSONArray("cards"))
        val html = json.getString("html")
        require(html.length <= 2 * 1024 * 1024) { "页面快照过大" }
        return Capture(json.optString("url").take(2048), tables, cards, warnings, html)
    }

    /** 网格课表卡片（td[data-role="item"]）作为不可信输入处理：数量与字段必须先过校验。 */
    private fun decodeCards(rawCards: org.json.JSONArray?): List<GridCard> {
        if (rawCards == null) return emptyList()
        require(rawCards.length() <= 300) { "课程卡片数量异常" }
        return (0 until rawCards.length()).map { index ->
            val item = rawCards.getJSONObject(index)
            fun unit(name: String): Int {
                val value = item.optInt(name, 0)
                require(value in 0..12) { "课程卡片 $name 属性异常" }
                return value
            }
            fun text(name: String, max: Int): String {
                val value = item.optString(name, "")
                require(value.length <= max) { "课程卡片 $name 过长" }
                return value
            }
            GridCard(
                week = unit("week"),
                beginUnit = unit("beginUnit"),
                endUnit = unit("endUnit"),
                name = text("name", 200),
                teacher = text("teacher", 200),
                time = text("time", 400),
            )
        }
    }

    fun parse(capture: Capture, term: com.classschedule.app.data.TermConfig = com.classschedule.app.data.Terms.DEFAULT): ScheduleParseResult {
        val result = if (capture.cards.isNotEmpty()) {
            GridScheduleParser.parse(capture.cards, term)
        } else {
            ScheduleTableParser.parse(capture.tables)
        }
        return result.copy(issues = (capture.warnings + result.issues).distinct())
    }

    /** 保存页面快照，便于离线分析页面结构、补充解析规则。 */
    fun dump(context: Context, html: String): File {
        val dir = File(context.cacheDir, "import").apply {
            check(isDirectory || mkdirs()) { "Cannot create snapshot directory" }
        }
        // 独立文件避免选择导出位置期间重新抓取导致内容被替换。
        return File.createTempFile("schedule_snapshot_", ".html", dir).apply { writeText(html) }
    }
}
