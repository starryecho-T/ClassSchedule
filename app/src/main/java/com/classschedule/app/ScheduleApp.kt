package com.classschedule.app

import android.app.Application
import android.content.res.Configuration
import android.os.Process
import com.classschedule.app.data.CourseRepository
import com.classschedule.app.data.db.AppDatabase
import com.classschedule.app.reminder.ReminderReceiver
import com.classschedule.app.widget.TodayCourseWidgetProvider
import com.classschedule.app.ui.ThemePreferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.io.File
import java.time.LocalDateTime

/** 应用入口：初始化数据库并在首次启动时预填示例课程。 */
class ScheduleApp : Application() {

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        instance = this
        installCrashGuard()
        ThemePreferences.applySaved(this)
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

    /**
     * 全局崩溃兜底：未捕获异常写入本地文件（下次启动可回传排查），
     * 记录后交还系统崩溃处理；此记录器不能替代根因修复。
     * 兜底只做记录与退出，不吞掉异常后继续运行（那会导致更隐蔽的坏状态）。
     */
    private fun installCrashGuard() {
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            runCatching {
                val dir = File(filesDir, "crash").apply { mkdirs() }
                val file = File(dir, "crash-${System.currentTimeMillis()}.txt")
                file.writeText(
                    buildString {
                        appendLine("time: ${LocalDateTime.now()}")
                        appendLine("thread: ${thread.name}")
                        appendLine(throwable.stackTraceToString())
                    }
                )
            }
            // 交还系统默认处理（结束进程）。如需彻底静默可改为 Process.killProcess，
            // 但保留默认链路更稳妥（ANR/系统日志仍可定位）。
            previous?.uncaughtException(thread, throwable) ?: run {
                Process.killProcess(Process.myPid())
                Runtime.getRuntime().exit(10)
            }
        }
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        TodayCourseWidgetProvider.refreshAll(this)
    }

    companion object {
        lateinit var instance: ScheduleApp
            private set
    }
}
