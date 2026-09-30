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

/** 主页 ViewModel：以响应式流暴露课程数据。 */
class MainViewModel(private val repo: CourseRepository) : ViewModel() {

    val courses: StateFlow<List<Course>> = repo.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    suspend fun getCourse(id: Long): Course? = repo.getById(id)

    companion object {
        val Factory = viewModelFactory {
            initializer {
                val dao = AppDatabase.getDatabase(ScheduleApp.instance).courseDao()
                MainViewModel(CourseRepository(dao))
            }
        }
    }
}
