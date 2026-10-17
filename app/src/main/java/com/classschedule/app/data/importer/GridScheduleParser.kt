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
        // 实验课等按具体日期排课：卡片名是「X月X日」且时间无周次时，
        // 用格子位置（星期 + 当前学期校历）反推周次，日期并入课程名保留信息。
        val dateName = dateOnlyName.find(card.name)?.groupValues
        if (weeks.isEmpty()) {
            if (dateName == null || card.week == 0) {
                throw InvalidCard("$location：缺少上课周次（“${card.time.take(40)}”）。")
            }
            val derived = deriveWeekFromDate(dateName)
            if (derived == null || derived != card.week) {
                throw InvalidCard("$location：日期「${dateName[1]}月${dateName[2]}日」与卡片位置（星期${card.week}）不符。")
            }
            weeks += derived.toString()
        }
        val dayText = if (cursor < parts.size && daySegment.matches(parts[cursor])) parts[cursor++] else ""
        // 日期排课的时间文本可能不含星期：星期由格子位置或日期给出。
        val effectiveDay = dayText.ifEmpty {
            card.week.takeIf { it in 1..7 }?.let { "星期$it" } ?: throw InvalidCard("$location：缺少上课星期（“${card.time.take(40)}”）。")
        }
        val periodText = if (cursor < parts.size && periodSegment.matches(parts[cursor])) parts[cursor++] else ""
        if (periodText.isEmpty()) throw InvalidCard("$location：缺少上课节次（“${card.time.take(40)}”）。")
        val room = parts.drop(cursor).filter { it.isNotEmpty() }.joinToString(",")
        if (daySegment.containsMatchIn(room) || periodSegment.containsMatchIn(room)) {
            throw InvalidCard("$location：含有多组上课时间，无法确定唯一安排。")
        }
        verifyAttributes(card, effectiveDay, periodText, location)
        val displayName = if (dateName == null) {
            card.name.replaceFirst(classNumberSuffix, "").ifBlank { card.name }
        } else {
            // 去掉「X月X日」后若还有残留（如「机能实验10月13日」）保留原名；纯日期名补上学期上下文。
            val residue = card.name.replace(dateOnlyName, "").trim()
            if (residue.isBlank()) "课程(${dateName[1]}月${dateName[2]}日)" else "${residue}(${dateName[1]}月${dateName[2]}日)"
        }
        return listOf(
            displayName,
            card.teacher,
            effectiveDay,
            periodText.let(digits::findAll).joinToString("-") { it.value },
            // 下游 NUMBERS 正则只接受一个可选的尾部“周”，离散段落必须剥掉各自的“周”再拼接。
            weeks.joinToString(",") { it.removePrefix("第").removeSuffix("周") },
            room,
        )
    }

    /** 「10月13日」这类纯日期课程名（实验课按具体日期排课）。 */
    private val dateOnlyName = Regex("""^\s*(?:实验|课程)?\s*(\d{1,2})月(\d{1,2})日\s*$""")

    /** 用学期开学日把日期换算成周次；不在学期内或参数异常返回 null。 */
    private fun deriveWeekFromDate(groups: MatchGroupCollection): Int? {
        val month = groups[1]?.value?.toIntOrNull() ?: return null
        val day = groups[2]?.value?.toIntOrNull() ?: return null
        val start = com.classschedule.app.data.TermCalendar.TERM_START
        val date = runCatching { java.time.LocalDate.of(start.year, month, day) }.getOrNull()
            // 跨年学期：秋季月份小于春季时按下一年解释。
            ?: runCatching { java.time.LocalDate.of(start.year + 1, month, day) }.getOrNull()
            ?: return null
        val week = com.classschedule.app.data.TermCalendar.currentWeek(date)
        return week.takeIf { it in 1..com.classschedule.app.data.TermCalendar.TOTAL_WEEKS }
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
