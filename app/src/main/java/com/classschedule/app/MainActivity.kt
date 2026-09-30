package com.classschedule.app

import android.content.Intent
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.widget.TextView
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.classschedule.app.data.TermCalendar
import com.classschedule.app.model.Course
import com.classschedule.app.ui.CourseDetailSheet
import com.classschedule.app.ui.EditCourseActivity
import com.classschedule.app.ui.ImportActivity
import com.classschedule.app.ui.MainViewModel
import com.classschedule.app.ui.ScheduleOverviewActivity
import com.classschedule.app.ui.TimetableView
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.button.MaterialButton
import com.google.android.material.floatingactionbutton.FloatingActionButton
import kotlinx.coroutines.launch

/**
 * 主页面：顶栏 + 周次切换条（M3 起支持表头具体日期与周次切换），
 * 内容区为周视图课表，数据来自 Room 自动刷新。
 */
class MainActivity : AppCompatActivity() {

    private val viewModel: MainViewModel by viewModels { MainViewModel.Factory }

    /** 当前正在查看的周次（1..总周数），默认定位到本周。 */
    private var selectedWeek: Int = TermCalendar.currentWeek().coerceIn(1, TermCalendar.TOTAL_WEEKS)

    /** Room 里最新的全量课程（切换周次时无需等待 Flow 重发）。 */
    private var latestCourses: List<Course> = emptyList()

    private lateinit var timetable: TimetableView
    private lateinit var weekLabel: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val toolbar = findViewById<MaterialToolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)

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

    /** 顶栏菜单：日程概览入口（M4）。 */
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
            else -> super.onOptionsItemSelected(item)
        }
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
}


