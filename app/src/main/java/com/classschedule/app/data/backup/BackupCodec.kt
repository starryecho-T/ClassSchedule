package com.classschedule.app.data.backup

import com.classschedule.app.data.TermCalendar
import com.classschedule.app.data.JluTimeTable
import com.classschedule.app.model.Course
import com.google.gson.GsonBuilder
import com.google.gson.stream.JsonReader
import com.google.gson.stream.JsonToken
import java.io.InputStream
import java.io.StringReader
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction
import java.time.Instant

data class BackupDocument(val appVersion: String, val exportedAt: String, val courses: List<Course>)

/** Strict, bounded JSON independent of Android's stubbed JSON APIs. */
object BackupCodec {
    const val MAX_BYTES = 5 * 1024 * 1024
    const val MAX_COURSES = 10_000
    const val MAX_TEXT = 4096
    private data class NumberToken(val text: String)

    fun read(input: InputStream): BackupDocument {
        val output = java.io.ByteArrayOutputStream()
        val buffer = ByteArray(8192)
        while (true) {
            val count = input.read(buffer)
            if (count < 0) break
            require(output.size() + count <= MAX_BYTES) { "备份文件不能超过 5 MiB" }
            output.write(buffer, 0, count)
        }
        val decoder = Charsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT)
        return decode(decoder.decode(ByteBuffer.wrap(output.toByteArray())).toString())
    }

    fun decode(text: String): BackupDocument {
        require(text.toByteArray(Charsets.UTF_8).size <= MAX_BYTES) { "备份文件不能超过 5 MiB" }
        val root = JsonReader(StringReader(text)).use { reader ->
            reader.isLenient = false
            val value = readValue(reader, 0).obj("备份")
            require(reader.peek() == JsonToken.END_DOCUMENT) { "JSON 末尾有多余内容" }
            value
        }
        require(root.string("format") == "classschedule-backup") { "不是本应用的备份文件" }
        require(root.integer("version") == 1) { "不支持的备份协议版本" }
        val term = root["term"].obj("term")
        require(term.string("startDate") == TermCalendar.TERM_START.toString() &&
            term.integer("totalWeeks") == TermCalendar.TOTAL_WEEKS &&
            term.integer("periodsPerDay") == JluTimeTable.PERIODS.size) { "备份学期与当前学期不一致，未修改课表" }
        val version = root.string("appVersion")
        require(version.isNotBlank()) { "缺少应用版本" }
        val exported = root.string("exportedAt")
        require(runCatching { Instant.parse(exported) }.isSuccess) { "导出时间格式无效" }
        val rows = root["courses"] as? List<*> ?: errorArgument("courses 必须是数组")
        require(rows.size <= MAX_COURSES) { "课程条数超过 10000" }
        val courses = rows.mapIndexed { index, value ->
            try {
                val row = value.obj("课程")
                Course(id = 0, name = row.string("name"), teacher = row.string("teacher"), room = row.string("room"),
                    dayOfWeek = row.integer("dayOfWeek"), startPeriod = row.integer("startPeriod"),
                    endPeriod = row.integer("endPeriod"), weeks = row.weeks("weeks"),
                    hiddenWeeks = row.weeks("hiddenWeeks"), colorIndex = row.integer("colorIndex"))
                    .also { validateCourse(it) }
            } catch (e: IllegalArgumentException) {
                throw IllegalArgumentException("第${index + 1}条课程：${e.message}", e)
            }
        }
        return BackupDocument(version, exported, courses)
    }

    fun encode(courses: List<Course>, appVersion: String, instant: Instant = Instant.now()): String {
        validateCourses(courses)
        require(appVersion.isNotBlank() && appVersion.length <= MAX_TEXT)
        val root = linkedMapOf(
            "format" to "classschedule-backup", "version" to 1, "appVersion" to appVersion,
            "exportedAt" to instant.toString(),
            "term" to linkedMapOf("startDate" to TermCalendar.TERM_START.toString(),
                "totalWeeks" to TermCalendar.TOTAL_WEEKS, "periodsPerDay" to JluTimeTable.PERIODS.size),
            "courses" to courses.map { c -> linkedMapOf(
                "name" to c.name, "teacher" to c.teacher, "room" to c.room, "dayOfWeek" to c.dayOfWeek,
                "startPeriod" to c.startPeriod, "endPeriod" to c.endPeriod,
                "weeks" to c.weeks.sorted(), "hiddenWeeks" to c.hiddenWeeks.sorted(), "colorIndex" to c.colorIndex) })
        val result = GsonBuilder().setPrettyPrinting().create().toJson(root)
        require(result.toByteArray(Charsets.UTF_8).size <= MAX_BYTES) { "备份文件超过 5 MiB，未导出" }
        return result
    }

    fun validateCourses(courses: List<Course>) {
        require(courses.size <= MAX_COURSES) { "课程条数超过 10000" }
        courses.forEachIndexed { index, course ->
            try { validateCourse(course) } catch (e: IllegalArgumentException) {
                throw IllegalArgumentException("第${index + 1}条课程：${e.message}", e)
            }
        }
    }
    private fun validateCourse(c: Course) {
        require(c.name.isNotBlank()) { "课程名称不能为空" }
        require(listOf(c.name, c.teacher, c.room).all { it.length <= MAX_TEXT }) { "文本字段超过 4096 字符" }
        require(c.dayOfWeek in 1..7) { "星期必须为 1 至 7" }
        require(c.startPeriod in 1..JluTimeTable.PERIODS.size && c.endPeriod in c.startPeriod..JluTimeTable.PERIODS.size) { "节次范围无效" }
        require(c.weeks.isNotEmpty() && c.weeks.all { it in 1..TermCalendar.TOTAL_WEEKS }) { "周次范围无效" }
        require(c.weeks.containsAll(c.hiddenWeeks)) { "隐藏周次必须属于课程周次" }
        require(c.colorIndex >= 0) { "颜色索引不能为负数" }
    }

    private fun readValue(reader: JsonReader, depth: Int): Any? {
        require(depth <= 16) { "JSON 嵌套层数过多" }
        return when (reader.peek()) {
            JsonToken.BEGIN_OBJECT -> {
                reader.beginObject()
                val values = linkedMapOf<String, Any?>()
                while (reader.hasNext()) {
                    val key = reader.nextName()
                    require(key.length <= MAX_TEXT && values.size < 1000 && !values.containsKey(key)) { "JSON 字段重复或过多" }
                    values[key] = readValue(reader, depth + 1)
                }
                reader.endObject(); values
            }
            JsonToken.BEGIN_ARRAY -> {
                reader.beginArray()
                val values = mutableListOf<Any?>()
                while (reader.hasNext()) {
                    require(values.size < MAX_COURSES) { "数组条数超过 10000" }
                    values.add(readValue(reader, depth + 1))
                }
                reader.endArray(); values
            }
            JsonToken.STRING -> reader.nextString().also { require(it.length <= MAX_TEXT) { "文本字段超过 4096 字符" } }
            JsonToken.NUMBER -> NumberToken(reader.nextString())
            JsonToken.BOOLEAN -> reader.nextBoolean()
            JsonToken.NULL -> { reader.nextNull(); null }
            else -> errorArgument("JSON 内容不完整")
        }
    }
    @Suppress("UNCHECKED_CAST")
    private fun Any?.obj(label: String) = this as? Map<String, Any?> ?: errorArgument("$label 必须是对象")
    private fun Map<String, Any?>.string(key: String) = this[key] as? String ?: errorArgument("$key 缺失或不是字符串")
    private fun integerValue(value: Any?, key: String): Int {
        val raw = (value as? NumberToken)?.text ?: errorArgument("$key 必须是整数")
        require(raw.matches(Regex("-?(0|[1-9][0-9]*)"))) { "$key 必须是整数" }
        return raw.toIntOrNull() ?: errorArgument("$key 整数越界")
    }
    private fun Map<String, Any?>.integer(key: String) = integerValue(this[key], key)
    private fun Map<String, Any?>.weeks(key: String): Set<Int> {
        val values = this[key] as? List<*> ?: errorArgument("$key 必须是整数数组")
        return values.map { integerValue(it, key) }.toSortedSet()
    }
    private fun errorArgument(message: String): Nothing = throw IllegalArgumentException(message)
}
