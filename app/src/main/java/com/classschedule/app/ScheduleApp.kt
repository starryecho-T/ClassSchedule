package com.classschedule.app

import android.app.Application
import com.classschedule.app.data.CourseRepository
import com.classschedule.app.data.db.AppDatabase
import com.classschedule.app.reminder.ReminderReceiver
import com.classschedule.app.widget.TodayCourseWidgetProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/** 应用入口：初始化数据库并在首次启动时预填示例课程。 */
class ScheduleApp : Application() {

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        instance = this
        val repo = CourseRepository(this, AppDatabase.getDatabase(this).courseDao())
        appScope.launch { repo.seedIfEmpty() }
        // M5：课程变化时刷新小组件并重算上课提醒（collectLatest 去抖：连续变更只处理最终态）。
        appScope.launch {
            repo.observeAll().collectLatest {
                TodayCourseWidgetProvider.refreshAll(this@ScheduleApp)
                ReminderReceiver.reschedule(this@ScheduleApp)
            }
        }
    }

    companion object {
        lateinit var instance: ScheduleApp
            private set
    }
}
