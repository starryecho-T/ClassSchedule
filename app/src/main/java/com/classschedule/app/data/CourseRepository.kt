package com.classschedule.app.data

import android.content.Context
import com.classschedule.app.data.db.CourseDao
import com.classschedule.app.data.db.toDomain
import com.classschedule.app.data.db.toEntity
import com.classschedule.app.model.Course
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * 课程数据仓库：隔离 UI 与数据库实现（分层思想参考 thu-info-app 的 lib/App 解耦设计）。
 * M4 接入教务导入时，在此层之上新增独立解析模块即可，UI 无需改动。
 */
class CourseRepository(
    context: Context,
    private val dao: CourseDao,
) {

    private val seedPrefs = context.getSharedPreferences("seed", Context.MODE_PRIVATE)

    /** 观察全部课程，任何增删改自动触发新数据。 */
    fun observeAll(): Flow<List<Course>> =
        dao.observeAll().map { list -> list.map { it.toDomain() } }

    suspend fun getById(id: Long): Course? = dao.getById(id)?.toDomain()

    /** 有 id 更新、无 id 插入。 */
    suspend fun upsert(course: Course): Long = dao.upsert(course.toEntity())

    suspend fun delete(course: Course) = dao.delete(course.toEntity())

    suspend fun deleteById(id: Long) = dao.deleteById(id)

    /**
     * 预填真实课表。按 [SEED_VERSION] 用 SharedPreferences 记忆：每个数据版本只在
     * 升级后首次启动时执行一次全量替换（覆盖旧示例/测试数据），此后用户的任何
     * 增删改都不会再被覆盖；未来更新内置课表时递增 [SEED_VERSION] 即可再刷一次。
     */
    suspend fun seedIfEmpty(sample: List<Course> = SampleData.COURSES) {
        val flag = "seeded_v$SEED_VERSION"
        if (seedPrefs.getBoolean(flag, false)) return
        dao.deleteAll()
        dao.insertAll(sample.map { it.toEntity() })
        seedPrefs.edit().putBoolean(flag, true).apply()
    }

    companion object {
        /** 内置课表数据版本（v2：思政实践/马原晚课 9-11 节，实际不上第 12 节）。 */
        private const val SEED_VERSION = 2
    }
}
