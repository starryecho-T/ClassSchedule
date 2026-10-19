package com.classschedule.app.reminder

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import android.net.Uri
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.classschedule.app.MainActivity
import com.classschedule.app.R
import com.classschedule.app.data.CourseRepository
import com.classschedule.app.data.TodayPlan
import com.classschedule.app.data.db.AppDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import com.classschedule.app.widget.TodayCourseWidgetProvider
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

/**
 * 上课前提醒（M5-2）。
 *
 * 调度策略：每天计算当日需提醒的课，为每节课注册一个精确闹钟（开始前
 * [TodayPlan.REMIND_MINUTES] 分钟）。课程变化、跨天、开机后重算。
 * 用户未授权 POST_NOTIFICATIONS 时静默跳过，不注册闹钟。
 */
class ReminderReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val result = goAsync()
        val app = context.applicationContext
        reminderScope.launch {
            try {
                schedulingMutex.withLock {
                    if (intent.action == ACTION_SCHEDULE && intent.getBooleanExtra(EXTRA_NOTIFY, false)) {
                        showNotification(app, intent.getLongExtra(EXTRA_COURSE_ID, 0L))
                    }
                    scheduleFor(app)
                    TodayCourseWidgetProvider.refreshAll(app)
                }
            } finally { result.finish() }
        }
    }

    /** 计算今日剩余提醒并注册闹钟。 */
    private suspend fun scheduleFor(context: Context) {
        val repo = CourseRepository(context, AppDatabase.getDatabase(context).courseDao())
        run {
            val courses = repo.observeAll().first()
            val now = LocalDateTime.now()
            val entries = if (hasNotificationPermission(context)) TodayPlan.entriesFor(courses, now.toLocalDate()) else emptyList()
            val alarm = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            // 先清掉旧课程维度的闹钟：重算必然覆盖更新的集合。
            cancelCourseAlarms(context, alarm)
            // Retire v0.8 PendingIntent identities on upgrade without reusing truncated IDs.
            courses.forEach { course ->
                val old = PendingIntent.getBroadcast(context, course.id.toInt(),
                    Intent(context, ReminderReceiver::class.java).setAction(ACTION_SCHEDULE),
                    PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE)
                old?.let { alarm.cancel(it); it.cancel() }
            }
            check(context.getSharedPreferences("reminder_alarms", Context.MODE_PRIVATE).edit()
                .putStringSet("ids", entries.map { it.course.id.toString() }.toSet()).commit())
            entries.forEach { entry ->
                val remindAt = LocalDateTime.of(now.toLocalDate(), entry.startTime)
                    .minusMinutes(TodayPlan.REMIND_MINUTES)
                if (!remindAt.isBefore(now)) {
                    val pending = pendingFor(context, entry.course.id, notify = true)
                    val triggerAtMillis = remindAt.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
                    AlarmPolicy.schedule(
                        canExact = Build.VERSION.SDK_INT < 31 || alarm.canScheduleExactAlarms(),
                        exact = { alarm.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pending) },
                        approximate = { alarm.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pending) },
                    )
                }
            }
            val tomorrow = now.toLocalDate().plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
            alarm.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, tomorrow, pendingFor(context, 0, notify = false))
        }
    }

    /** 弹出提醒并安排下一条（同一次也可能是本日最后一条）。 */
    private suspend fun showNotification(context: Context, courseId: Long) {
        if (!hasNotificationPermission(context)) return
        ensureChannel(context)
        val repo = CourseRepository(context, AppDatabase.getDatabase(context).courseDao())
        run {
            val course = repo.getById(courseId) ?: return
            val now = LocalDateTime.now()
            val entry = TodayPlan.entriesFor(listOf(course), now.toLocalDate()).singleOrNull() ?: return
            val remindAt = LocalDateTime.of(now.toLocalDate(), entry.startTime).minusMinutes(TodayPlan.REMIND_MINUTES)
            if (now.isBefore(remindAt) || !now.toLocalTime().isBefore(entry.startTime)) return
            // 节次超出作息表（异常数据）时静默跳过该条提醒，不崩溃。
            val start = com.classschedule.app.data.JluTimeTable.PERIODS
                .firstOrNull { it.index == course.startPeriod }?.start ?: return
            val open = PendingIntent.getActivity(
                context, courseId.toInt(),
                Intent(context, MainActivity::class.java).setFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            val text = buildString {
                append(start).append(" 开始 · 第").append(course.startPeriod).append('-').append(course.endPeriod).append("节")
                if (course.room.isNotBlank()) append(" · ").append(course.room)
                if (course.teacher.isNotBlank()) append(" · ").append(course.teacher)
            }
            val notification = NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
                .setContentTitle(context.getString(R.string.reminder_title_prefix) + "：" + course.name)
                .setContentText(text)
                .setAutoCancel(true)
                .setContentIntent(open)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .build()
            if (hasNotificationPermission(context)) {
                try { manager(context).notify(courseId.toInt(), notification) }
                catch (error: SecurityException) { Log.w("ClassReminder", "Notification permission changed", error) }
            }
        }
    }

    private fun cancelCourseAlarms(context: Context, alarm: AlarmManager) {
        context.getSharedPreferences("reminder_alarms", Context.MODE_PRIVATE)
            .getStringSet("ids", emptySet()).orEmpty().toSet().forEach { id ->
                id.toLongOrNull()?.let { alarm.cancel(pendingFor(context, it, true)) }
            }
    }

    private fun pendingFor(context: Context, courseId: Long, notify: Boolean): PendingIntent {
        val intent = Intent(context, ReminderReceiver::class.java).setAction(ACTION_SCHEDULE)
            .setData(Uri.parse("classschedule://reminder/" + if (notify) courseId.toString() else "refresh"))
            .putExtra(EXTRA_COURSE_ID, courseId)
            .putExtra(EXTRA_NOTIFY, notify)
        return PendingIntent.getBroadcast(
            context, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                context.getString(R.string.reminder_channel_name),
                NotificationManager.IMPORTANCE_HIGH,
            ).apply { description = context.getString(R.string.reminder_channel_description) }
            manager(context).createNotificationChannel(channel)
        }
    }

    private fun manager(context: Context) = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    private fun hasNotificationPermission(context: Context): Boolean =
        Build.VERSION.SDK_INT < 33 || ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    companion object {
        const val CHANNEL_ID = "class_reminder"
        const val ACTION_SCHEDULE = "com.classschedule.app.REMINDER_SCHEDULE"
        const val EXTRA_COURSE_ID = "course_id"
        const val EXTRA_NOTIFY = "notify"
        private val schedulingMutex = Mutex()
        val reminderScope = CoroutineScope(SupervisorJob() + Dispatchers.IO +
            CoroutineExceptionHandler { _, error -> Log.e("ClassReminder", "Reminder refresh failed", error) })

        /** 对外入口：课程变更 / 页面进入时重算提醒。 */
        fun reschedule(context: Context) {
            context.sendBroadcast(
                Intent(context, ReminderReceiver::class.java).setAction(ACTION_SCHEDULE).putExtra(EXTRA_NOTIFY, false),
            )
        }
    }
}
