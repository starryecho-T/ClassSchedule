package com.classschedule.app.data

import android.content.Context
import com.classschedule.app.data.db.CourseDao
import com.classschedule.app.data.db.CourseEntity
import com.classschedule.app.data.backup.BackupRestorePolicy
import com.classschedule.app.data.backup.RestoreResult
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
 *
 * 课程按学期（[TermConfig.id]）隔离：读取、导入、替换、备份恢复都只作用于目标学期，
 * 切换学期后各学期课表互不影响。写入时未显式指定学期的课程归入当前学期。
 */
class CourseRepository(
    private val context: Context,
    private val dao: CourseDao,
) {

    private val seedPrefs = context.getSharedPreferences("seed", Context.MODE_PRIVATE)

    /** 观察指定学期的全部课程，任何增删改自动触发新数据。 */
    fun observeAll(termId: String): Flow<List<Course>> =
        dao.observeAll(termId).map { list -> list.map { it.toDomain() } }

    /** 一次性读取指定学期的全部课程（小组件刷新等不需要持续观察的场景）。 */
    suspend fun observeAllOnce(termId: String): List<Course> = observeAll(termId).first()

    /** 当前学期（提醒、小组件等无 ViewModel 链路经由本仓库取学期）。 */
    fun activeTerm(): TermConfig = TermPreferences.activeTerm(context)

    suspend fun getById(id: Long): Course? = dao.getById(id)?.toDomain()

    /** 有 id 更新（保留原学期归属）、无 id 插入（归入当前学期）。 */
    suspend fun upsert(course: Course): Long = mutationMutex.withLock {
        markInitialized()
        val termId = dao.getById(course.id)?.termId ?: activeTerm().id
        dao.upsert(course.toEntity(termId))
    }

    suspend fun delete(course: Course) = mutationMutex.withLock {
        // 先记住用户已接管课表，删除最后一门课后也不能重新填充示例。
        markInitialized()
        dao.delete(course.toEntity(activeTerm().id))
    }

    suspend fun deleteById(id: Long) = mutationMutex.withLock {
        markInitialized()
        dao.deleteById(id)
    }

    /**
     * 仅首次使用且数据库为空时预填内置课表（默认学期）。
     * 兼容旧版本的 seeded_vN 标记；升级内置数据不会覆盖用户课程，
     * 用户主动删空后也不会重新填充。空库检查和插入由 DAO 在同一事务内完成。
     */
    suspend fun seedIfEmpty(sample: List<Course> = SampleData.COURSES) = mutationMutex.withLock {
        if (SeedPolicy.hasInitialized(seedPrefs.all)) return@withLock
        if (sample.isEmpty()) return@withLock
        dao.insertIfEmpty(sample.map { it.toEntity(Terms.DEFAULT.id) })
        // 填充成功后才标记，插入失败仍可重试；已有课程也视为完成初始化。
        // 一旦 DAO 已返回，协程取消也应允许这次状态持久化完成。
        withContext(NonCancellable) { markInitialized() }
    }

    /** 用 [courses] 替换 [term] 学期的全部课程，其他学期不动。 */
    suspend fun replaceAll(courses: List<Course>, term: TermConfig = activeTerm()) = mutationMutex.withLock {
        require(courses.isNotEmpty()) { "导入课表为空，未修改现有课程" }
        markInitialized()
        dao.replaceAll(term.id, courses.map { it.toEntity(term.id) })
    }

    /**
     * 教务导入默认追加并跳过重复安排；全量替换必须由 UI 明确确认。
     * 先校验整批再记录初始化状态，返回实际新增数（替换时为导入数）。
     * 课程一律归入目标学期 [term]（默认当前学期），重复判定也在学期内进行。
     */
    suspend fun importCourses(
        courses: List<Course>,
        replaceExisting: Boolean = false,
        term: TermConfig = activeTerm(),
    ): Int = mutationMutex.withLock {
        require(courses.isNotEmpty()) { "导入课表为空，未修改现有课程" }
        val prepared = courses.map { it.toEntity(term.id) }
        markInitialized()
        dao.importCourses(term, prepared, replaceExisting)
    }

    private suspend fun markInitialized() {
        if (seedPrefs.getBoolean(SeedPolicy.INITIALIZED_KEY, false)) return
        withContext(Dispatchers.IO) {
            check(seedPrefs.edit().putBoolean(SeedPolicy.INITIALIZED_KEY, true).commit()) {
                "无法保存课表初始化状态，请稍后重试"
            }
        }
    }

    /** 指定学期的备份快照（备份是按学期的快照，term 块也写入该学期）。 */
    suspend fun backupSnapshot(term: TermConfig = activeTerm()): List<CourseEntity> =
        mutationMutex.withLock { dao.getAllOnce(term.id) }

    suspend fun restoreBackup(
        courses: List<Course>,
        replace: Boolean,
        expected: List<CourseEntity>,
        term: TermConfig = activeTerm(),
    ): RestoreResult = mutationMutex.withLock {
        // 备份协议 v1 的课程不含学期信息：整份备份恢复到当前学期。
        val rows = BackupRestorePolicy.prepare(courses.map { it.toEntity(term.id) }, term)
        markInitialized()
        dao.restoreBackup(term, rows, replace, expected)
    }

    companion object {
        // Activity 和 Application 各自持有仓库实例，共享锁才能保护启动初始化。
        private val mutationMutex = Mutex()
    }
}
