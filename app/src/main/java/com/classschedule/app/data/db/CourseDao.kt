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
import kotlinx.coroutines.flow.Flow

/** 课程表数据访问对象。 */
@Dao
interface CourseDao {

    @Query("SELECT * FROM courses ORDER BY day_of_week, start_period")
    fun observeAll(): Flow<List<CourseEntity>>

    @Query("SELECT * FROM courses WHERE id = :id LIMIT 1")
    suspend fun getById(id: Long): CourseEntity?

    @Query("SELECT COUNT(*) FROM courses")
    suspend fun count(): Int

    @Query("SELECT * FROM courses ORDER BY day_of_week, start_period")
    suspend fun getAllOnce(): List<CourseEntity>

    @Query("DELETE FROM courses")
    suspend fun deleteAll()

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

    /** 插入失败时 Room 回滚删除；空导入不得清空用户课表。 */
    @Transaction
    suspend fun replaceAll(courses: List<CourseEntity>) {
        require(courses.isNotEmpty()) { "导入课表为空，未修改现有课程" }
        deleteAll()
        insertAll(courses)
    }

    /** 查询、去重和写入共用事务，避免并发导入插入重复安排。 */
    @Transaction
    suspend fun importCourses(
        courses: List<CourseEntity>,
        replaceExisting: Boolean,
    ): Int {
        val prepared = ImportPolicy.prepare(courses)
        val incoming = if (replaceExisting) prepared
            else ImportPolicy.additions(getAllOnce(), prepared)
        if (replaceExisting) deleteAll()
        if (incoming.isNotEmpty()) insertAll(incoming)
        return incoming.size
    }

    @Delete
    suspend fun delete(course: CourseEntity)

    /** Restore is distinct from academic import: preserve hidden weeks and duplicate rows. */
    @Transaction
    suspend fun restoreBackup(
        rows: List<CourseEntity>,
        replace: Boolean,
        expected: List<CourseEntity>,
    ): RestoreResult {
        val prepared = BackupRestorePolicy.prepare(rows)
        val current = getAllOnce()
        if (replace && !BackupRestorePolicy.sameSnapshot(current, expected)) throw StaleBackupPreviewException()
        val incoming = if (replace) prepared else BackupRestorePolicy.additions(current, prepared)
        if (replace) deleteAll()
        if (incoming.isNotEmpty()) insertAll(incoming)
        return RestoreResult(incoming.size, if (replace) 0 else prepared.size - incoming.size, replace)
    }

    @Query("DELETE FROM courses WHERE id = :id")
    suspend fun deleteById(id: Long)
}
