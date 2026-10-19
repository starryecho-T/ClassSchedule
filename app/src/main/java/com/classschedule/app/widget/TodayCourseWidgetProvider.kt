package com.classschedule.app.widget

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.widget.RemoteViews
import com.classschedule.app.R
import com.classschedule.app.data.TodayPlan
import com.classschedule.app.data.CourseRepository
import com.classschedule.app.data.db.AppDatabase
import com.classschedule.app.model.Course
import com.classschedule.app.ui.ThemePreferences
import android.content.res.Configuration
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.CoroutineExceptionHandler
import android.util.Log
import java.time.LocalDate
import java.time.LocalTime

/**
 * 今日课程桌面小组件（M5-1）。
 *
 * 更新策略（电池友好，不用常驻服务）：
 * - 课程数据变化（Room flow）时刷新；
 * - 跨天时刷新（日变更闹钟）；
 * - 半点触发一次时间状态刷新（进行中/已结束标记）。
 */
class TodayCourseWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        val repo = CourseRepository(context, AppDatabase.getDatabase(context).courseDao())
        widgetScope.launch {
            val courses = repo.observeAllOnce()
            appWidgetIds.forEach { id -> appWidgetManager.updateAppWidget(id, buildViews(context, courses)) }
        }
    }

    companion object {
        private val widgetScope = CoroutineScope(SupervisorJob() + Dispatchers.IO +
            CoroutineExceptionHandler { _, error -> Log.e("TodayWidget", "Widget refresh failed", error) })

        /** 立即刷新所有已添加的小组件（课程变更、日变更时调用）。 */
        fun refreshAll(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(ComponentName(context, TodayCourseWidgetProvider::class.java))
            if (ids.isEmpty()) return
            TodayCourseWidgetProvider().onUpdate(context, manager, ids)
        }

        /** 渲染小组件内容。课程超过可视行数时截断并提示剩余条数。 */
        fun buildViews(context: Context, courses: List<Course>, date: LocalDate = LocalDate.now(), now: LocalTime = LocalTime.now()): RemoteViews {
            val views = RemoteViews(context.packageName, R.layout.widget_today)
            val themed = ThemePreferences.widgetContext(context)
            val dark = themed.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES
            views.setInt(R.id.widget_root, "setBackgroundResource", if (dark) R.drawable.widget_bg_dark else R.drawable.widget_bg_light)
            views.setTextColor(R.id.widget_header, themed.getColor(R.color.widget_header))
            views.setTextColor(R.id.widget_body, themed.getColor(R.color.widget_text))
            val entries = TodayPlan.entriesFor(courses, date)
            views.setTextViewText(R.id.widget_header, TodayPlan.header(date))
            if (entries.isEmpty()) {
                views.setTextViewText(R.id.widget_body, "今天没有课程\n好好休息吧 🎉")
                return views
            }
            val maxRows = 6
            val shown = entries.take(maxRows)
            val builder = StringBuilder()
            shown.forEach { entry ->
                val status = TodayPlan.statusOf(entry, now)
                val mark = when (status) {
                    TodayPlan.Status.ONGOING -> "▶ "
                    TodayPlan.Status.FINISHED -> "✓ "
                    TodayPlan.Status.UPCOMING -> "  "
                }
                builder.append(mark)
                    .append(entry.startTime.toString())
                    .append("  ")
                    .append(entry.course.name)
                    .append(entry.course.room.takeIf { it.isNotBlank() }?.let { " · $it" } ?: "")
                    .append('\n')
            }
            if (entries.size > maxRows) {
                builder.append("…还有 ").append(entries.size - maxRows).append(" 节课")
            } else {
                // 去掉末尾换行
                builder.setLength(builder.length - 1)
            }
            views.setTextViewText(R.id.widget_body, builder.toString())
            return views
        }
    }
}
