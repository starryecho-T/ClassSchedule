package com.classschedule.app

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.classschedule.app.data.TermCalendar
import com.classschedule.app.model.Course
import com.classschedule.app.reminder.ReminderReceiver
import com.classschedule.app.ui.CourseDetailSheet
import com.classschedule.app.ui.EditCourseActivity
import com.classschedule.app.ui.ImportActivity
import com.classschedule.app.ui.MainViewModel
import com.classschedule.app.ui.ScheduleOverviewActivity
import com.classschedule.app.ui.TimetableView
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.button.MaterialButton
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.launch

/**
 * 主页面：顶栏 + 周次切换条（M3 起支持表头具体日期与周次切换），
 * 内容区为周视图课表，数据来自 Room 自动刷新。
 */
class MainActivity : AppCompatActivity() {

    private val viewModel: MainViewModel by viewModels { MainViewModel.Factory }

    /** M5-2：通知权限请求结果 —— 授权后立即重算提醒闹钟。 */
    private val requestPermissionLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        val message = if (granted) R.string.reminder_granted else R.string.reminder_denied
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
        if (granted) ReminderReceiver.reschedule(this)
    }

    /** 当前正在查看的周次（1..总周数），默认定位到本周。 */
    private var selectedWeek: Int = TermCalendar.currentWeek().coerceIn(1, TermCalendar.TOTAL_WEEKS)

    /** Room 里最新的全量课程（切换周次时无需等待 Flow 重发）。 */
    private var latestCourses: List<Course> = emptyList()

    private lateinit var timetable: TimetableView
    private lateinit var weekLabel: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        selectedWeek = savedInstanceState?.getInt(STATE_SELECTED_WEEK, selectedWeek)
            ?.coerceIn(1, TermCalendar.TOTAL_WEEKS) ?: selectedWeek
        setContentView(R.layout.activity_main)

        val toolbar = findViewById<MaterialToolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)

        // M5-2：Android 13+ 首次进入请求通知权限（上课提醒依赖）；只请求一次。
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }

        timetable = findViewById(R.id.timetable)
        // 点击课程块 -> 详情弹窗（不再直接进编辑页，借鉴 thu-info 交互）
        timetable.onCourseClick = { course -> showCourseDetail(course) }
        // 长按空白格 -> 预填星期与节次，快速加课
        timetable.onEmptyCellLongClick = { day, period ->
            startActivity(
                EditCourseActivity.intent(this, prefillDay = day, prefillStartPeriod = period)
            )
        }
        // 横滑切换周次（借鉴 thu-info 的手势交互）
        timetable.onWeekSwipe = { delta -> switchWeek(selectedWeek + delta) }

        findViewById<MaterialButton>(R.id.week_prev).setOnClickListener {
            switchWeek(selectedWeek - 1)
        }
        findViewById<MaterialButton>(R.id.week_next).setOnClickListener {
            switchWeek(selectedWeek + 1)
        }
        findViewById<MaterialButton>(R.id.week_today).setOnClickListener {
            switchWeek(TermCalendar.currentWeek())
        }
        weekLabel = findViewById(R.id.week_label)

        findViewById<FloatingActionButton>(R.id.fab_add).setOnClickListener {
            startActivity(EditCourseActivity.intent(this))
        }

        // 课程数据变化时自动重绘当前查看的周
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.courses.collect { courses ->
                    latestCourses = courses
                    redraw()
                }
            }
        }
        updateWeekUi()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putInt(STATE_SELECTED_WEEK, selectedWeek)
        super.onSaveInstanceState(outState)
    }

    /** 顶栏菜单：日程概览、教务导入和隐藏课程管理。 */
    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_main, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_overview -> {
                startActivity(Intent(this, ScheduleOverviewActivity::class.java))
                true
            }
            R.id.action_import -> {
                startActivity(Intent(this, ImportActivity::class.java))
                true
            }
            R.id.action_hidden_courses -> {
                showHiddenCourses()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    /** 全部隐藏的课程也保留管理入口，点击后可在原详情页恢复。 */
    private fun showHiddenCourses() {
        val hiddenCourses = latestCourses.filter { it.hiddenWeeks.isNotEmpty() }
            .sortedWith(compareBy(Course::dayOfWeek, Course::startPeriod, Course::endPeriod, Course::name, Course::id))
        val dialog = MaterialAlertDialogBuilder(this)
            .setTitle(R.string.hidden_courses_title)
            .setNegativeButton(android.R.string.cancel, null)
        if (hiddenCourses.isEmpty()) {
            dialog.setMessage(R.string.hidden_courses_empty).show()
            return
        }
        val adapter = object : ArrayAdapter<Course>(this, android.R.layout.simple_list_item_2, hiddenCourses) {
            override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
                val view = convertView ?: layoutInflater.inflate(android.R.layout.simple_list_item_2, parent, false)
                val course = hiddenCourses[position]
                view.findViewById<TextView>(android.R.id.text1).apply {
                    text = course.name
                    setSingleLine(false)
                    maxLines = 2
                }
                view.findViewById<TextView>(android.R.id.text2).apply {
                    text = getString(
                        R.string.hidden_course_details,
                        "一二三四五六日".getOrNull(course.dayOfWeek - 1)?.toString().orEmpty(),
                        course.startPeriod,
                        course.endPeriod,
                        course.hiddenWeeks.sorted().joinToString("、"),
                        course.teacher.ifBlank { getString(R.string.sheet_empty_hint) },
                        course.room.ifBlank { getString(R.string.sheet_empty_hint) },
                    )
                    setSingleLine(false)
                    maxLines = Int.MAX_VALUE
                }
                return view
            }
        }
        dialog.setAdapter(adapter) { _, position -> showCourseDetail(hiddenCourses[position]) }.show()
    }

    /** 课程详情底部弹窗：查看信息 + 编辑 / 删除 / 隐藏（M3）。 */
    private fun showCourseDetail(course: Course) {
        CourseDetailSheet(
            context = this,
            course = course,
            selectedWeek = selectedWeek,
            onEdit = { startActivity(EditCourseActivity.intent(this, course.id)) },
            onDelete = { viewModel.deleteCourse(course.id) },
            onHideWeeks = { weeks -> viewModel.hideCourseWeeks(course, weeks) },
            onRestore = { viewModel.restoreCourse(course) },
        ).open()
    }

    /** 切换到 [week]（自动限制在 1..总周数内）并重绘。 */
    private fun switchWeek(week: Int) {
        val target = week.coerceIn(1, TermCalendar.TOTAL_WEEKS)
        if (target == selectedWeek) return
        selectedWeek = target
        redraw()
        updateWeekUi()
    }

    private fun redraw() {
        timetable.setup(
            latestCourses,
            selectedWeek,
            TermCalendar.weekDates(selectedWeek),
            TermCalendar.isCurrentWeek(selectedWeek),
        )
    }

    /** 更新顶栏标题与周次切换条文案。 */
    private fun updateWeekUi() {
        supportActionBar?.apply {
            title = getString(R.string.timetable_title, selectedWeek)
            subtitle = getString(R.string.timetable_subtitle)
        }
        weekLabel.text = if (TermCalendar.isCurrentWeek(selectedWeek)) {
            getString(R.string.week_label_current, selectedWeek)
        } else {
            getString(R.string.week_label_fmt, selectedWeek)
        }
    }

    companion object {
        private const val STATE_SELECTED_WEEK = "selected_week"
    }
}


