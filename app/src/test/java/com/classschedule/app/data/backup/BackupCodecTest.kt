package com.classschedule.app.data.backup

import com.classschedule.app.model.Course
import com.classschedule.app.data.TermConfig
import com.classschedule.app.data.Terms
import com.classschedule.app.data.db.toEntity
import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayInputStream
import java.time.Instant
import java.time.LocalDate

class BackupCodecTest {
    private val course = Course(id = 49, name = "  数学 \"甲\" \n", teacher = " 教师 ", room = "教室 & <A>",
        dayOfWeek = 1, startPeriod = 1, endPeriod = 2, weeks = setOf(3, 1, 2), hiddenWeeks = setOf(2), colorIndex = Int.MAX_VALUE)
    private fun json() = BackupCodec.encode(listOf(course), "0.9.0", Instant.parse("2026-10-19T08:00:00Z"))
    private fun reject(text: String) {
        try { BackupCodec.decode(text); fail("Invalid backup accepted") }
        catch (_: Exception) { /* Rejection is required before any database operation. */ }
    }
    @Test fun roundTripKeepsTextHiddenWeeksAndColors() {
        assertEquals(listOf(course.copy(id = 0)), BackupCodec.decode(json()).courses)
    }
    @Test fun duplicateAndFullyHiddenRowsRemainDistinctRecords() {
        val rows = listOf(course, course, course.copy(hiddenWeeks = course.weeks))
        assertEquals(rows.map { it.copy(id = 0) }, BackupCodec.decode(BackupCodec.encode(rows, "0.9.0")).courses)
    }
    @Test fun emptyBackupCanBeExportedButCannotBeRestored() {
        assertTrue(BackupCodec.decode(BackupCodec.encode(emptyList(), "0.9.0")).courses.isEmpty())
        try { BackupRestorePolicy.prepare(emptyList()); fail("Empty restore accepted") } catch (_: IllegalArgumentException) {}
    }
    @Test fun requiredNumericFieldsDoNotCoerceStringsDecimalsOrNull() {
        for (bad in listOf("\"1\"", "1.0", "1e0", "null", "true", "2147483648")) {
            reject(json().replace("\"dayOfWeek\": 1", "\"dayOfWeek\": $bad"))
        }
    }
    @Test fun rejectsUnsupportedFormatVersionOrTerm() {
        reject(json().replace("classschedule-backup", "unrelated"))
        reject(json().replace("\"version\": 1", "\"version\": 2"))
        reject(json().replace("2026-09-07", "2027-02-01"))
        reject(json().replace("\"totalWeeks\": 17", "\"totalWeeks\": 18"))
    }
    @Test fun backupIsScopedToTheTermItWasExportedFrom() {
        val spring = Terms.SPRING_2026_2027
        // 第一学期备份在第二学期恢复被拒（反之亦然）
        try { BackupCodec.decode(json(), spring); fail("跨学期恢复被接受") } catch (_: IllegalArgumentException) {}
        val springJson = BackupCodec.encode(listOf(course), "0.9.1", term = spring)
        assertEquals(listOf(course.copy(id = 0)), BackupCodec.decode(springJson, spring).courses)
        try { BackupCodec.decode(springJson, Terms.FALL_2026_2027); fail("跨学期恢复被接受") } catch (_: IllegalArgumentException) {}
        // 默认参数 = 默认学期（第一学期）
        assertEquals(1, BackupCodec.decode(json()).courses.size)
    }
    @Test fun weekRangeValidationFollowsTheActiveTerm() {
        val short = TermConfig("short", "短学期", LocalDate.of(2026, 9, 7), 5)
        val shortWeekCourse = course.copy(weeks = setOf(5), hiddenWeeks = emptySet())
        try { BackupCodec.encode(listOf(shortWeekCourse.copy(weeks = setOf(6))), "0.9.1", term = short); fail("越界周次被接受") }
        catch (_: IllegalArgumentException) {}
        assertEquals(
            listOf(shortWeekCourse.copy(id = 0)),
            BackupCodec.decode(BackupCodec.encode(listOf(shortWeekCourse), "0.9.1", term = short), short).courses,
        )
        // 默认学期仍按 17 周校验
        try { BackupCodec.encode(listOf(course.copy(weeks = setOf(18))), "0.9.1"); fail("越界周次被接受") }
        catch (_: IllegalArgumentException) {}
    }
    @Test fun rejectsMissingRequiredField() { reject(json().replace("\"hiddenWeeks\"", "\"omitted\"")) }
    @Test fun rejectsTrailingContentMalformedAndDuplicateKeys() {
        reject(json() + "{}")
        reject(json().dropLast(5))
        reject(json().replace("\"version\": 1,", "\"version\": 1, \"version\": 1,"))
        reject("{unquoted:true}")
    }
    @Test fun ignoresUnknownFieldsInSupportedVersion() {
        val withExtra = json().replaceFirst("{", "{\"futureField\": {\"a\": true},")
        assertEquals(1, BackupCodec.decode(withExtra).courses.size)
    }
    @Test fun validatesEveryRecordBeforeEncoding() {
        for (bad in listOf(course.copy(name = " "), course.copy(dayOfWeek = 8),
            course.copy(startPeriod = 0), course.copy(endPeriod = 13), course.copy(weeks = emptySet()),
            course.copy(weeks = setOf(18)), course.copy(hiddenWeeks = setOf(4)), course.copy(colorIndex = -1),
            course.copy(teacher = "a".repeat(4097)))) {
            try { BackupCodec.encode(listOf(course, bad), "0.9.0"); fail("Invalid row accepted") }
            catch (_: IllegalArgumentException) {}
        }
    }
    @Test fun rejectsOversizeAndInvalidUtf8Input() {
        for (bytes in listOf(ByteArray(BackupCodec.MAX_BYTES + 1), byteArrayOf(0xc3.toByte(), 0x28))) {
            try { BackupCodec.read(ByteArrayInputStream(bytes)); fail("Invalid input accepted") }
            catch (_: Exception) {}
        }
    }
    @Test fun readsUtf8Stream() { assertEquals(1, BackupCodec.read(ByteArrayInputStream(json().toByteArray())).courses.size) }
    @Test fun appendPreservesExistingAndFirstIncomingVariant() {
        val existing = course.toEntity(Terms.DEFAULT.id)
        val duplicate = course.copy(colorIndex = 0, hiddenWeeks = emptySet()).toEntity(Terms.DEFAULT.id)
        val another = course.copy(name = "新课程").toEntity(Terms.DEFAULT.id)
        val prepared = BackupRestorePolicy.prepare(listOf(duplicate, another, another.copy(colorIndex = 2)))
        val additions = BackupRestorePolicy.additions(listOf(existing), prepared)
        assertEquals(listOf(another.copy(id = 0)), additions)
        assertTrue(BackupRestorePolicy.additions(listOf(existing) + additions, prepared).isEmpty())
    }
    @Test fun replacementPreservesDuplicatesWithoutTrimming() {
        val result = BackupRestorePolicy.prepare(listOf(course.toEntity(Terms.DEFAULT.id), course.toEntity(Terms.DEFAULT.id)))
        assertEquals(2, result.size)
        assertEquals(course.name, result.first().name)
        assertEquals("2", result.first().hiddenWeeksCsv)
        assertTrue(result.all { it.id == 0L })
    }
    @Test fun detectsConcurrentMutationButNotOrderChange() {
        val a = course.toEntity(Terms.DEFAULT.id); val b = a.copy(id = 50, name = "其他")
        assertTrue(BackupRestorePolicy.sameSnapshot(listOf(a, b), listOf(b, a)))
        assertFalse(BackupRestorePolicy.sameSnapshot(listOf(a), listOf(a.copy(hiddenWeeksCsv = ""))))
    }
    @Test fun corruptPersistedWeekTokensCannotBeSilentlyExported() {
        for (row in listOf(course.toEntity(Terms.DEFAULT.id).copy(weeksCsv = "1,bad,2"), course.toEntity(Terms.DEFAULT.id).copy(hiddenWeeksCsv = "2,bad"))) {
            try { BackupRestorePolicy.validatedCourses(listOf(row)); fail("Corrupt CSV silently accepted") }
            catch (_: IllegalArgumentException) {}
        }
    }
}
