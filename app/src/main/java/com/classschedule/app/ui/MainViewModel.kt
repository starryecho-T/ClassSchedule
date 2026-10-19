package com.classschedule.app.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.classschedule.app.ScheduleApp
import com.classschedule.app.data.CourseRepository
import com.classschedule.app.data.db.AppDatabase
import com.classschedule.app.model.Course
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** 主页 ViewModel：以响应式流暴露课程数据。 */
class MainViewModel(private val repo: CourseRepository) : ViewModel() {
    var startupUpdateChecked = false
    private val mutationMutex = Mutex()

    val courseState: StateFlow<List<Course>?> = repo.observeAll()
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)
    val courses: StateFlow<List<Course>> = courseState.filterNotNull()
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    suspend fun getCourse(id: Long): Course? = repo.getById(id)

    /** 删除课程（详情弹窗入口）。 */
    fun deleteCourse(id: Long) = viewModelScope.launch {
        mutationMutex.withLock { repo.deleteById(id) }
    }

    /**
     * 隐藏课程：把 [weeks] 并入隐藏集合。
     * 三档语义（借鉴 thu-info）：setOf(当前周) = 仅本次；course.weeks = 全部周次。
     */
    fun hideCourseWeeks(course: Course, weeks: Set<Int>) = viewModelScope.launch {
        mutationMutex.withLock {
            val current = repo.getById(course.id) ?: return@withLock
            repo.upsert(current.copy(hiddenWeeks = (current.hiddenWeeks + weeks).intersect(current.weeks)))
        }
    }

    /** 恢复显示：清空隐藏集合。 */
    fun restoreCourse(course: Course) = viewModelScope.launch {
        mutationMutex.withLock {
            val current = repo.getById(course.id) ?: return@withLock
            repo.upsert(current.copy(hiddenWeeks = emptySet()))
        }
    }

    companion object {
        val Factory = viewModelFactory {
            initializer {
                val dao = AppDatabase.getDatabase(ScheduleApp.instance).courseDao()
                MainViewModel(CourseRepository(ScheduleApp.instance, dao))
            }
        }
    }
}
