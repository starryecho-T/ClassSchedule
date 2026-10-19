package com.classschedule.app.data.backup

import com.classschedule.app.data.ImportPolicy
import com.classschedule.app.data.db.CourseEntity
import com.classschedule.app.data.db.toDomain
import com.classschedule.app.model.Course

data class RestoreResult(val inserted: Int, val skipped: Int, val replaced: Boolean)
class StaleBackupPreviewException : IllegalStateException("课表已变化，请重新预览并确认替换")

object BackupRestorePolicy {
    fun prepare(rows: List<CourseEntity>): List<CourseEntity> {
        require(rows.isNotEmpty()) { "备份课表为空，未修改现有课程" }
        validatedCourses(rows)
        // Keep all text, hidden state and duplicates. Only database identities are regenerated.
        return rows.map { it.copy(id = 0) }
    }
    fun additions(existing: List<CourseEntity>, incoming: List<CourseEntity>): List<CourseEntity> =
        ImportPolicy.additions(existing, incoming)
    fun sameSnapshot(a: List<CourseEntity>, b: List<CourseEntity>) = a.sortedBy { it.id } == b.sortedBy { it.id }

    /** Reject corrupt persisted CSV instead of silently dropping invalid tokens on export. */
    fun validatedCourses(rows: List<CourseEntity>): List<Course> {
        fun parse(csv: String, allowEmpty: Boolean): Set<Int> {
            if (csv.isEmpty() && allowEmpty) return emptySet()
            return csv.split(",").map { token ->
                token.trim().toIntOrNull() ?: throw IllegalArgumentException("存量课程周次损坏，未导出或恢复")
            }.toSet()
        }
        return rows.map { row ->
            row.toDomain().copy(weeks = parse(row.weeksCsv, false), hiddenWeeks = parse(row.hiddenWeeksCsv, true))
        }.also(BackupCodec::validateCourses)
    }
}
