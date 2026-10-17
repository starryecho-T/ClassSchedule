package com.classschedule.app.data

import android.content.Context
import com.classschedule.app.data.db.CourseDao
import com.classschedule.app.data.db.toDomain
import com.classschedule.app.data.db.toEntity
import com.classschedule.app.model.Course
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

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

    /** 一次性读取全部课程（小组件刷新等不需要持续观察的场景）。 */
    suspend fun observeAllOnce(): List<Course> = observeAll().first()

    suspend fun getById(id: Long): Course? = dao.getById(id)?.toDomain()

    /** 有 id 更新、无 id 插入。 */
    suspend fun upsert(course: Course): Long = mutationMutex.withLock {
        markInitialized()
        dao.upsert(course.toEntity())
    }

    suspend fun delete(course: Course) = mutationMutex.withLock {
        // 先记住用户已接管课表，删除最后一门课后也不能重新填充示例。
        markInitialized()
        dao.delete(course.toEntity())
    }

    suspend fun deleteById(id: Long) = mutationMutex.withLock {
        markInitialized()
        dao.deleteById(id)
    }

    /**
     * 仅首次使用且数据库为空时预填内置课表。
     * 兼容旧版本的 seeded_vN 标记；升级内置数据不会覆盖用户课程，
     * 用户主动删空后也不会重新填充。空库检查和插入由 DAO 在同一事务内完成。
     */
    suspend fun seedIfEmpty(sample: List<Course> = SampleData.COURSES) = mutationMutex.withLock {
        if (SeedPolicy.hasInitialized(seedPrefs.all)) return@withLock
        if (sample.isEmpty()) return@withLock
        dao.insertIfEmpty(sample.map { it.toEntity() })
        // 填充成功后才标记，插入失败仍可重试；已有课程也视为完成初始化。
        // 一旦 DAO 已返回，协程取消也应允许这次状态持久化完成。
        withContext(NonCancellable) { markInitialized() }
    }

    /** 教务导入：原子替换，解析出空列表时保留已有课程并报告错误。 */
    suspend fun replaceAll(courses: List<Course>) = mutationMutex.withLock {
        require(courses.isNotEmpty()) { "导入课表为空，未修改现有课程" }
        markInitialized()
        dao.replaceAll(courses.map { it.toEntity() })
    }

    /**
     * 教务导入默认追加并跳过重复安排；全量替换必须由 UI 明确确认。
     * 先校验整批再记录初始化状态，返回实际新增数（替换时为导入数）。
     */
    suspend fun importCourses(
        courses: List<Course>,
        replaceExisting: Boolean = false,
    ): Int = mutationMutex.withLock {
        val prepared = ImportPolicy.prepare(courses.map { it.toEntity() })
        markInitialized()
        dao.importCourses(prepared, replaceExisting)
    }

    private suspend fun markInitialized() {
        if (seedPrefs.getBoolean(SeedPolicy.INITIALIZED_KEY, false)) return
        withContext(Dispatchers.IO) {
            // 用户操作先持久化状态，避免数据库已提交后再因状态写盘失败而误报。
            check(seedPrefs.edit().putBoolean(SeedPolicy.INITIALIZED_KEY, true).commit()) {
                "无法保存课表初始化状态，请稍后重试"
            }
        }
    }

    companion object {
        // Activity 和 Application 各自持有仓库实例，共享锁才能保护启动初始化。
        private val mutationMutex = Mutex()
    }
}
