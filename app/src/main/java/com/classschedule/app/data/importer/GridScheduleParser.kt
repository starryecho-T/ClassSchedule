package com.classschedule.app.data.importer

/**
 * 吉大教务「我的课表」网格视图中的一个课程卡片。
 *
 * 采集自 iedu.jlu.edu.cn 课表网格 `td[data-role="item"]`：
 * [week]/[beginUnit]/[endUnit] 是页面标注的结构位置（0 表示页面未提供），
 * [time] 是卡片原文，如「1-2周,4周,星期4,第1节-第2节,前卫-李四光楼-第二阶梯（地学优先）」。
 */
data class GridCard(
    val week: Int,
    val beginUnit: Int,
    val endUnit: Int,
    val name: String,
    val teacher: String,
    val time: String,
)

/**
 * 将网格课表卡片转换成带明确表头的课程列表，复用 [ScheduleTableParser] 的既有严格解析。
 *
 * 卡片时间文本必须符合「周次…,星期,节次,地点」的固定段序；卡片属性与文本不一致、
 * 段缺失或含有无法解释的内容时拒绝整批导入，绝不按位置猜测。
 */
object GridScheduleParser {
    private const val MAX_CARDS = 300
    private const val NAME_MAX = 200
    private const val TIME_MAX = 400
    private val classNumberSuffix = Regex("""\s*\[[^\[\]]{1,16}]$""")
    private val weekSegment = Regex("""第?[0-9]{1,2}(?:-[0-9]{1,2})?周""")
    private val daySegment = Regex("""星期[1-7一二三四五六日天]|周[一二三四五六日天]""")
    private val periodSegment = Regex("""第[0-9]{1,2}节(?:-第[0-9]{1,2}节)?""")
    private val digits = Regex("""[0-9]{1,2}""")
    private val chineseDay = mapOf("一" to 1, "二" to 2, "三" to 3, "四" to 4, "五" to 5, "六" to 6, "七" to 7, "日" to 7, "天" to 7)

    private class InvalidCard(message: String) : IllegalArgumentException(message)

    fun parse(cards: List<GridCard>, maxWeek: Int = 17, maxPeriod: Int = 12): ScheduleParseResult {
        if (maxWeek < 1 || maxPeriod < 1) {
            return ScheduleParseResult(emptyList(), listOf("学期周数和每天节数必须大于零。"), 0)
        }
        if (cards.size > MAX_CARDS) {
            return ScheduleParseResult(emptyList(), listOf("课程卡片数量超出安全限制，请重新抓取。"), 0)
        }
        val issues = mutableListOf<String>()
        // 伪表：让 ScheduleTableParser 按显式列解析星期/节次/周次，地点与教师来自卡片。
        val rows = mutableListOf(listOf("课程名称", "教师", "星期", "节次", "周次", "上课地点"))
        cards.forEachIndexed { index, card ->
            try {
                rows += toRow(card, index)
            } catch (error: InvalidCard) {
                issues += error.message ?: "课程${index + 1}无法识别。"
            }
        }
        val result = ScheduleTableParser.parse(listOf(ScheduleTable(rows)), maxWeek, maxPeriod)
        return ScheduleParseResult(result.courses, (issues + result.issues).distinct(), result.recognizedTables)
    }

    private fun toRow(card: GridCard, index: Int): List<String> {
        val location = "课程${index + 1}「${card.name.take(30)}」"
        if (card.name.isBlank()) throw InvalidCard("$location：课程名称为空。")
        if (card.name.length > NAME_MAX || card.teacher.length > NAME_MAX || card.time.length > TIME_MAX) {
            throw InvalidCard("$location：字段长度异常，请重新抓取。")
        }
        val parts = card.time.replace('，', ',').split(',').map { it.trim() }
        var cursor = 0
        val weeks = mutableListOf<String>()
        while (cursor < parts.size && weekSegment.matches(parts[cursor])) {
            weeks += parts[cursor]
            cursor++
        }
        if (weeks.isEmpty()) throw InvalidCard("$location：缺少上课周次（“${card.time.take(40)}”）。")
        val dayText = if (cursor < parts.size && daySegment.matches(parts[cursor])) parts[cursor++] else ""
        if (dayText.isEmpty()) throw InvalidCard("$location：缺少上课星期（“${card.time.take(40)}”）。")
        val periodText = if (cursor < parts.size && periodSegment.matches(parts[cursor])) parts[cursor++] else ""
        if (periodText.isEmpty()) throw InvalidCard("$location：缺少上课节次（“${card.time.take(40)}”）。")
        val room = parts.drop(cursor).filter { it.isNotEmpty() }.joinToString(",")
        if (daySegment.containsMatchIn(room) || periodSegment.containsMatchIn(room)) {
            throw InvalidCard("$location：含有多组上课时间，无法确定唯一安排。")
        }
        verifyAttributes(card, dayText, periodText, location)
        return listOf(
            card.name.replaceFirst(classNumberSuffix, "").ifBlank { card.name },
            card.teacher,
            dayText,
            periodText.let(digits::findAll).joinToString("-") { it.value },
            // 下游 NUMBERS 正则只接受一个可选的尾部“周”，离散段落必须剥掉各自的“周”再拼接。
            weeks.joinToString(",") { it.removePrefix("第").removeSuffix("周") },
            room,
        )
    }

    /**
     * 卡片自带的结构位置与文本必须互相印证；属性缺失（0）时跳过对应校验。
     *
     * 节次校验为“包含”关系：格子的跨度（beginUnit..endUnit，真实页面由 rowspan 推导）
     * 是卡片可占据的最大范围，同一格子里不同卡片可以只占其中一部分（例如 4 节格子
     * 里既有 5-8 节的课也有 5-6 节的课）。文本节次必须完整落在跨度内，否则拒绝。
     */
    private fun verifyAttributes(card: GridCard, dayText: String, periodText: String, location: String) {
        if (card.week != 0) {
            val dayValue = digits.findAll(dayText).map { it.value.toInt() }.singleOrNull()
                ?: chineseDay.keys.firstOrNull(dayText::contains)?.let(chineseDay::getValue)
            if (card.week !in 1..7 || dayValue != card.week) {
                throw InvalidCard("$location：卡片位置（星期${card.week}）与文本“$dayText”不一致。")
            }
        }
        if (card.beginUnit != 0 || card.endUnit != 0) {
            val units = periodText.let(digits::findAll).map { it.value.toInt() }.toList()
            val cellBegin = card.beginUnit
            val cellEnd = if (card.endUnit != 0) card.endUnit else card.beginUnit
            if (units.size !in 1..2 || units.first() < cellBegin || units.last() > cellEnd) {
                throw InvalidCard("$location：文本节次“$periodText”超出卡片位置（第${cellBegin}节-第${cellEnd}节）。")
            }
        }
    }
}
