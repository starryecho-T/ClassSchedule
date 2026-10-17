package com.classschedule.app.data.importer

import com.classschedule.app.model.Course

/** Text cells after the page collector has expanded HTML row/column spans. */
data class ScheduleTable(val rows: List<List<String>>)

data class ScheduleParseResult(
    val courses: List<Course>,
    val issues: List<String>,
    val recognizedTables: Int,
) {
    /** Never replace an existing timetable with an empty or partially parsed result. */
    val canImport: Boolean get() = courses.isNotEmpty() && issues.isEmpty()
}

/**
 * Parses explicitly labelled course-list tables, without inferring dates from position.
 * Unknown tables are ignored; an invalid row in a recognized table blocks the whole import.
 * This is deliberately independent of Android, HTML, and JSON so page fixtures can test it.
 */
object ScheduleTableParser {
    private enum class Field { NAME, TEACHER, ROOM, DAY, PERIOD, WEEK, TIME }

    private data class Header(val columns: Map<Field, Int>, val duplicates: Set<Field>)
    private data class Timing(val day: Int, val periods: IntRange, val weeks: Set<Int>, val room: String = "")
    private class InvalidRow(message: String) : IllegalArgumentException(message)

    private const val NUMBERS = "[0-9]+(?:-[0-9]+)?(?:,[0-9]+(?:-[0-9]+)?)*"
    private val numbersPattern = Regex(NUMBERS)
    private val whitespace = Regex("""[\s\u00a0\u3000]+""")
    private val headers = mapOf(
        "课程名称" to Field.NAME, "课程名" to Field.NAME,
        "授课教师" to Field.TEACHER, "教师" to Field.TEACHER, "任课教师" to Field.TEACHER,
        "上课地点" to Field.ROOM, "教学地点" to Field.ROOM, "教室" to Field.ROOM,
        "星期" to Field.DAY, "星期几" to Field.DAY, "上课星期" to Field.DAY,
        "节次" to Field.PERIOD, "上课节次" to Field.PERIOD,
        "周次" to Field.WEEK, "上课周次" to Field.WEEK,
        "上课时间" to Field.TIME, "时间地点" to Field.TIME,
    )

    fun parse(
        tables: List<ScheduleTable>,
        maxWeek: Int = 17,
        maxPeriod: Int = 12,
    ): ScheduleParseResult {
        if (maxWeek < 1 || maxPeriod < 1) {
            return ScheduleParseResult(emptyList(), listOf("学期周数和每天节数必须大于零。"), 0)
        }
        val courses = linkedSetOf<Course>()
        val issues = mutableListOf<String>()
        var recognizedTables = 0
        tables.forEachIndexed { tableIndex, table ->
            val firstHeader = table.rows.indexOfFirst { header(it) != null }
            if (firstHeader < 0) return@forEachIndexed
            recognizedTables++
            var currentHeader: Header? = null
            table.rows.drop(firstHeader).forEachIndexed rowLoop@ { offset, row ->
                val location = "表${tableIndex + 1}第${firstHeader + offset + 1}行"
                val nextHeader = header(row)
                if (nextHeader != null) {
                    currentHeader = nextHeader
                    headerIssue(nextHeader)?.let { issues += "$location：$it" }
                } else if (row.any { display(it).isNotEmpty() }) {
                    val activeHeader = currentHeader ?: return@rowLoop
                    if (headerIssue(activeHeader) == null && !isSummary(row, activeHeader)) {
                        try {
                            courses += parseRow(row, activeHeader, maxWeek, maxPeriod)
                        } catch (error: InvalidRow) {
                            issues += "$location：${error.message}"
                        }
                    }
                }
            }
        }
        return ScheduleParseResult(courses.toList(), issues.distinct(), recognizedTables)
    }

    private fun header(row: List<String>): Header? {
        val columns = linkedMapOf<Field, Int>()
        val duplicates = mutableSetOf<Field>()
        row.forEachIndexed { index, text ->
            headers[compact(text).removeSuffix(":")]?.let { field ->
                if (columns.put(field, index) != null) duplicates += field
            }
        }
        if (Field.NAME !in columns || listOf(Field.DAY, Field.PERIOD, Field.WEEK, Field.TIME).none { it in columns }) {
            return null
        }
        return Header(columns, duplicates)
    }

    private fun headerIssue(header: Header): String? = when {
        header.duplicates.isNotEmpty() -> "存在重复的课程或时间字段表头，无法确定对应列。"
        Field.TIME !in header.columns && listOf(Field.DAY, Field.PERIOD, Field.WEEK).any { it !in header.columns } ->
            "缺少星期、节次或周次列，不能推测缺失时间。"
        else -> null
    }

    private fun isSummary(row: List<String>, header: Header): Boolean {
        val nonEmpty = row.map(::display).filter(String::isNotEmpty)
        val first = nonEmpty.firstOrNull() ?: return true
        if (!Regex("""(?:合计|总计|小计|共计)(?:[:：]?[0-9]+(?:门|条|项|门课程)?)?|共[0-9]+(?:门|条|门课程)""").matches(first)) return false
        if (listOf(Field.DAY, Field.PERIOD, Field.WEEK, Field.TIME).any { cell(row, header, it).isNotBlank() }) return false
        return nonEmpty.drop(1).all { Regex("""[0-9]+(?:\.[0-9]+)?(?:门|条|项|门课程)?""").matches(it) }
    }

    private fun parseRow(row: List<String>, header: Header, maxWeek: Int, maxPeriod: Int): Course {
        val name = display(cell(row, header, Field.NAME))
        if (name.isEmpty()) invalid("课程名称为空。")
        val mergedText = cell(row, header, Field.TIME)
        val timing = if (mergedText.isNotBlank()) {
            parseMergedTime(mergedText, maxWeek, maxPeriod).also { merged ->
                val day = cell(row, header, Field.DAY)
                val period = cell(row, header, Field.PERIOD)
                val week = cell(row, header, Field.WEEK)
                if (day.isNotBlank() && parseDay(day) != merged.day) invalid("星期列与合并时间不一致。")
                if (period.isNotBlank() && parsePeriods(period, maxPeriod) != merged.periods) invalid("节次列与合并时间不一致。")
                if (week.isNotBlank() && parseWeeks(week, maxWeek) != merged.weeks) invalid("周次列与合并时间不一致。")
            }
        } else {
            Timing(
                parseDay(cell(row, header, Field.DAY)),
                parsePeriods(cell(row, header, Field.PERIOD), maxPeriod),
                parseWeeks(cell(row, header, Field.WEEK), maxWeek),
            )
        }
        val room = display(cell(row, header, Field.ROOM))
        if (room.isNotEmpty() && timing.room.isNotEmpty() && room != timing.room) {
            invalid("地点列与合并时间中的地点不一致。")
        }
        return Course(
            name = name,
            teacher = display(cell(row, header, Field.TEACHER)),
            room = room.ifEmpty { timing.room },
            dayOfWeek = timing.day,
            startPeriod = timing.periods.first,
            endPeriod = timing.periods.last,
            weeks = timing.weeks,
            colorIndex = (name.hashCode() and Int.MAX_VALUE) % 8,
            hiddenWeeks = emptySet(),
            id = 0,
        )
    }

    private fun parseDay(raw: String): Int {
        val value = compact(raw).replace(Regex("""^(?:上课星期|星期几|星期):"""), "")
        val match = Regex("""(?:星期|周)?([1-7一二三四五六七日天])""").matchEntire(value)
            ?: invalid("无法完整识别星期“${display(raw)}”。")
        return when (val day = match.groupValues[1]) {
            "一" -> 1; "二" -> 2; "三" -> 3; "四" -> 4; "五" -> 5; "六" -> 6
            "七", "日", "天" -> 7
            else -> day.toInt()
        }
    }

    private fun parsePeriods(raw: String, maxPeriod: Int): IntRange {
        val value = compact(raw).replace(Regex("""^(?:上课节次|节次):"""), "")
            .removePrefix("第").removeSuffix("节")
        val periods = parseNumbers(value, maxPeriod, "节次").sorted()
        if (periods.zipWithNext().any { (left, right) -> right != left + 1 }) {
            invalid("节次不连续，无法确定同一条上课安排。")
        }
        return periods.first()..periods.last()
    }

    private fun parseWeeks(raw: String, maxWeek: Int): Set<Int> {
        val value = compact(raw).replace(Regex("""^(?:上课周次|周次):"""), "")
        val parityPatterns = listOf(
            Regex("""第?(${NUMBERS})(?:周)?\((单|双)(?:周)?\)"""),
            Regex("""第?(${NUMBERS})\((单|双)(?:周)?\)周"""),
            Regex("""第?(${NUMBERS})(?:周)?(单|双)周?"""),
        )
        val parityMatch = parityPatterns.firstNotNullOfOrNull { it.matchEntire(value) }
        if (parityMatch != null) {
            val weeks = parseNumbers(parityMatch.groupValues[1], maxWeek, "周次")
            val parity = if (parityMatch.groupValues[2] == "单") 1 else 0
            return weeks.filter { it % 2 == parity }.toSet().also {
                if (it.isEmpty()) invalid("单双周筛选后没有任何上课周次。")
            }
        }
        val match = Regex("""第?(${NUMBERS})周?""").matchEntire(value)
            ?: invalid("无法完整识别周次“${display(raw)}”。")
        return parseNumbers(match.groupValues[1], maxWeek, "周次")
    }

    private fun parseNumbers(raw: String, maximum: Int, label: String): Set<Int> {
        if (!numbersPattern.matches(raw)) invalid("无法完整识别$label“$raw”。")
        val values = linkedSetOf<Int>()
        raw.split(',').forEach { segment ->
            val ends = segment.split('-').map { it.toIntOrNull() ?: invalid("$label 数字过大。") }
            val first = ends.first()
            val last = ends.last()
            if (first !in 1..maximum || last !in 1..maximum) invalid("$label 必须在 1–$maximum 范围内，不能截断导入。")
            if (first > last) invalid("$label 范围起点大于终点。")
            values.addAll(first..last)
        }
        return values
    }

    private fun parseMergedTime(raw: String, maxWeek: Int, maxPeriod: Int): Timing {
        var remaining = normalized(raw).trim()
        var room = ""
        val roomLabels = Regex("""(?:上课地点|教学地点|教室|地点)\s*:""").findAll(remaining).toList()
        if (roomLabels.size > 1) invalid("合并时间含多个地点，无法确定时间与地点对应关系。")
        if (roomLabels.size == 1) {
            val label = roomLabels.single()
            val roomText = remaining.substring(label.range.last + 1).trim().trimEnd(';', ',').trim()
            if (roomText.isEmpty() || Regex("""[;|\r\n]|(?:星期|周)[一二三四五六七日天]|星期[1-7]|[0-9](?:周|节)""").containsMatchIn(roomText)) {
                invalid("合并时间中的地点不明确或后面仍有其他上课时间。")
            }
            room = display(roomText)
            remaining = remaining.substring(0, label.range.first)
        }
        remaining = compact(remaining).replace(Regex("""^(?:上课时间|时间地点|时间):"""), "")
        fun take(pattern: Regex, label: String): String {
            val matches = pattern.findAll(remaining).toList()
            if (matches.size != 1) invalid("合并时间必须明确且只包含一组$label，多时段需要分行。")
            val match = matches.single()
            remaining = remaining.replaceRange(match.range, " ")
            return match.value
        }
        val day = take(Regex("""(?:(?:上课星期|星期几|星期):(?:星期|周)?[1-7一二三四五六七日天]|星期[1-7一二三四五六七日天]|周[一二三四五六七日天])"""), "星期")
        val week = take(
            Regex("""(?:(?:上课周次|周次):第?${NUMBERS}(?:周)?(?:\([单双](?:周)?\)|[单双]周?)?(?:周)?|第?${NUMBERS}(?:\([单双](?:周)?\)周|周(?:\([单双](?:周)?\)|[单双]周?)?))"""),
            "周次",
        )
        val period = take(Regex("""(?:(?:上课节次|节次):第?${NUMBERS}(?:节)?|第?${NUMBERS}节)"""), "节次")
        if (remaining.replace(Regex("""[\s,;:|]"""), "").isNotEmpty()) {
            invalid("合并时间含有无法解释的内容，请将星期、节次、周次和地点分列。")
        }
        return Timing(parseDay(day), parsePeriods(period, maxPeriod), parseWeeks(week, maxWeek), room)
    }

    private fun cell(row: List<String>, header: Header, field: Field): String =
        header.columns[field]?.let { row.getOrNull(it) }.orEmpty()

    private fun normalized(raw: String): String = buildString(raw.length) {
        raw.forEach { character ->
            append(when (character) {
                in '０'..'９' -> '0' + (character - '０')
                '，', '、', '﹑' -> ','
                '（' -> '('; '）' -> ')'; '：' -> ':'; '；' -> ';'
                '‐', '‑', '‒', '–', '—', '―', '−', '﹣', '－', '~', '～', '至' -> '-'
                '\u00a0', '\u3000' -> ' '
                else -> character
            })
        }
    }

    private fun compact(raw: String): String {
        // Whitespace may separate two arrangements. Never turn "1 7" into week 17.
        val separatedNumbers = normalized(raw).replace(Regex("""(?<=[0-9])[\s\u00a0\u3000]+(?=[0-9])"""), "?")
        return whitespace.replace(separatedNumbers, "")
    }
    private fun display(raw: String): String = whitespace.replace(raw, " ").trim()
    private fun invalid(message: String): Nothing = throw InvalidRow(message)
}
