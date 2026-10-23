package com.classschedule.app.ui

import android.widget.Toast
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.classschedule.app.ScheduleApp
import com.classschedule.app.data.CourseRepository
import com.classschedule.app.data.TermConfig
import com.classschedule.app.data.TermPreferences
import com.classschedule.app.data.db.AppDatabase
import com.classschedule.app.model.Course
import com.classschedule.app.reminder.ReminderReceiver
import com.classschedule.app.widget.TodayCourseWidgetProvider
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** 主页 ViewModel：以响应式流暴露当前学期与该学期的课程数据。 */
class MainViewModel(private val repo: CourseRepository) : ViewModel() {
    var startupUpdateChecked = false
    private val mutationMutex = Mutex()
    private val appContext get() = ScheduleApp.instance

    /** 当前学期：切换后课程流、周次、提醒与小组件全部随之重算。 */
    private val mutableTerm = MutableStateFlow(TermPreferences.activeTerm(appContext))
    val term: StateFlow<TermConfig> = mutableTerm.asStateFlow()

    @OptIn(ExperimentalCoroutinesApi::class)
    val courseState: StateFlow<List<Course>?> = mutableTerm
        .flatMapLatest { repo.observeAll(it.id) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)
    @OptIn(ExperimentalCoroutinesApi::class)
    val courses: StateFlow<List<Course>> = courseState.filterNotNull()
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    /** 切换学期：持久化偏好，提醒与小组件立即按新学期重算（UI 经 [term] 流自动刷新）。 */
    fun selectTerm(target: TermConfig) {
        if (target.id == mutableTerm.value.id) return
        viewModelScope.launch {
            mutationMutex.withLock {
                TermPreferences.select(appContext, target)
                mutableTerm.value = target
            }
            ReminderReceiver.reschedule(appContext)
            TodayCourseWidgetProvider.refreshAll(appContext)
            Toast.makeText(
                appContext,
                appContext.getString(com.classschedule.app.R.string.term_switched_toast, target.name),
                Toast.LENGTH_SHORT,
            ).show()
        }
    }

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
