package com.classschedule.app.data.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import com.classschedule.app.data.ImportPolicy
import com.classschedule.app.data.backup.BackupRestorePolicy
import com.classschedule.app.data.backup.RestoreResult
import com.classschedule.app.data.backup.StaleBackupPreviewException
import com.classschedule.app.data.TermConfig
import kotlinx.coroutines.flow.Flow

/**
 * 课程表数据访问对象。
 *
 * 读取与整批写入都以学期为界（[TermConfig.id]）：切换学期后各学期课表互不可见，
 * 导入 / 恢复替换只影响目标学期，其他学期数据保持原样。
 */
@Dao
interface CourseDao {

    @Query("SELECT * FROM courses WHERE term_id = :termId ORDER BY day_of_week, start_period")
    fun observeAll(termId: String): Flow<List<CourseEntity>>

    @Query("SELECT * FROM courses WHERE id = :id LIMIT 1")
    suspend fun getById(id: Long): CourseEntity?

    @Query("SELECT COUNT(*) FROM courses")
    suspend fun count(): Int

    @Query("SELECT * FROM courses WHERE term_id = :termId ORDER BY day_of_week, start_period")
    suspend fun getAllOnce(termId: String): List<CourseEntity>

    @Query("DELETE FROM courses WHERE term_id = :termId")
    suspend fun deleteTerm(termId: String)

    /** 有 id 更新、无 id 插入。 */
    @Upsert
    suspend fun upsert(course: CourseEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(courses: List<CourseEntity>)

    /** 同一事务内检查和填充，已有任意课程时不修改数据库。 */
    @Transaction
    suspend fun insertIfEmpty(courses: List<CourseEntity>): Boolean {
        if (courses.isEmpty() || count() != 0) return false
        insertAll(courses)
        return true
    }

    /** 用 [courses] 替换 [termId] 学期的全部课程，其他学期不动。 */
    @Transaction
    suspend fun replaceAll(termId: String, courses: List<CourseEntity>) {
        require(courses.isNotEmpty()) { "导入课表为空，未修改现有课程" }
        deleteTerm(termId)
        insertAll(courses)
    }

    /** 插入失败时 Room 回滚删除；空导入不得清空用户课表。 */
    @Transaction
    suspend fun importCourses(
        term: TermConfig,
        courses: List<CourseEntity>,
        replaceExisting: Boolean,
    ): Int {
        val prepared = ImportPolicy.prepare(courses, term.totalWeeks)
        val existing = getAllOnce(term.id)
        val incoming = if (replaceExisting) prepared else ImportPolicy.additions(existing, prepared)
        if (replaceExisting) deleteTerm(term.id)
        if (incoming.isNotEmpty()) insertAll(incoming)
        return incoming.size
    }

    @Delete
    suspend fun delete(course: CourseEntity)

    /** Restore is distinct from academic import: preserve hidden weeks and duplicate rows. */
    @Transaction
    suspend fun restoreBackup(
        term: TermConfig,
        rows: List<CourseEntity>,
        replace: Boolean,
        expected: List<CourseEntity>,
    ): RestoreResult {
        val prepared = BackupRestorePolicy.prepare(rows, term)
        val current = getAllOnce(term.id)
        if (replace && !BackupRestorePolicy.sameSnapshot(current, expected)) throw StaleBackupPreviewException()
        val incoming = if (replace) prepared else BackupRestorePolicy.additions(current, prepared)
        if (replace) deleteTerm(term.id)
        if (incoming.isNotEmpty()) insertAll(incoming)
        return RestoreResult(incoming.size, if (replace) 0 else prepared.size - incoming.size, replace)
    }

    @Query("DELETE FROM courses WHERE id = :id")
    suspend fun deleteById(id: Long)
}
