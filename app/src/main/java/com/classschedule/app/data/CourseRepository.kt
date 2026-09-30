package com.classschedule.app.data

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
class CourseRepository(private val dao: CourseDao) {

    /** 观察全部课程，任何增删改自动触发新数据。 */
    fun observeAll(): Flow<List<Course>> =
        dao.observeAll().map { list -> list.map { it.toDomain() } }

    suspend fun getById(id: Long): Course? = dao.getById(id)?.toDomain()

    /** 有 id 更新、无 id 插入。 */
    suspend fun upsert(course: Course): Long = dao.upsert(course.toEntity())

    suspend fun delete(course: Course) = dao.delete(course.toEntity())

    suspend fun deleteById(id: Long) = dao.deleteById(id)

    /**
     * 首次启动预填真实课表；老版本升级时，若库里仍是未被用户改动过的旧示例课程，
     * 自动替换为真实课表（用户一旦增删改过任何课程，则视为已接手，不再覆盖）。
     */
    suspend fun seedIfEmpty(sample: List<Course> = SampleData.COURSES) {
        val existing = dao.getAllOnce()
        if (existing.isEmpty()) {
            dao.insertAll(sample.map { it.toEntity() })
            return
        }
        val untouchedLegacy = existing.all { it.toDomain().name in SampleData.LEGACY_SAMPLE_NAMES }
        if (untouchedLegacy) {
            dao.deleteAll()
            dao.insertAll(sample.map { it.toEntity() })
        }
    }
}
