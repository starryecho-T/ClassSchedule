package com.classschedule.app

import android.app.Application
import com.classschedule.app.data.CourseRepository
import com.classschedule.app.data.db.AppDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/** 应用入口：初始化数据库并在首次启动时预填示例课程。 */
class ScheduleApp : Application() {

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        instance = this
        val repo = CourseRepository(AppDatabase.getDatabase(this).courseDao())
        appScope.launch { repo.seedIfEmpty() }
    }

    companion object {
        lateinit var instance: ScheduleApp
            private set
    }
}
