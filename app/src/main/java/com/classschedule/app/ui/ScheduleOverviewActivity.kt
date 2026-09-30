package com.classschedule.app.ui

import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.classschedule.app.R
import com.classschedule.app.data.JluTimeTable
import com.classschedule.app.data.TermCalendar
import com.classschedule.app.model.Course
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.button.MaterialButton
import com.google.android.material.color.MaterialColors
import java.time.LocalDate
import kotlinx.coroutines.launch

/**
 * 日程概览（M4）：按天浏览课程（借鉴 thu-info 的日程列表视图）。
 *
 * 顶部为周内 7 天日期条（默认选中今天），下方按节次排序列出选中日的课程卡片；
 * 周切换与主界面周视图一致，数据经 Room Flow 自动刷新，点击卡片复用课程详情弹窗。
 */
class ScheduleOverviewActivity : AppCompatActivity() {

    private val viewModel: MainViewModel by viewModels { MainViewModel.Factory }

    /** 当前查看的周次，默认定位到本周。 */
    private var selectedWeek: Int = TermCalendar.currentWeek().coerceIn(1, TermCalendar.TOTAL_WEEKS)

    /** 当前查看的星期（1..7），默认今天。 */
    private var selectedDay: Int = LocalDate.now().dayOfWeek.value.coerceIn(1, 7)

    private var latestCourses: List<Course> = emptyList()

    private lateinit var toolbar: MaterialToolbar
    private lateinit var weekLabel: TextView
    private lateinit var dayBar: LinearLayout
    private lateinit var courseList: LinearLayout
    private lateinit var emptyHint: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_schedule_overview)

        toolbar = findViewById(R.id.overview_toolbar)
        toolbar.setNavigationOnClickListener { finish() }

        findViewById<MaterialButton>(R.id.overview_prev).setOnClickListener {
            switchWeek(selectedWeek - 1)
        }
        findViewById<MaterialButton>(R.id.overview_next).setOnClickListener {
            switchWeek(selectedWeek + 1)
        }
        findViewById<MaterialButton>(R.id.overview_today).setOnClickListener {
            selectedWeek = TermCalendar.currentWeek().coerceIn(1, TermCalendar.TOTAL_WEEKS)
            selectedDay = LocalDate.now().dayOfWeek.value
            rerender()
        }
        weekLabel = findViewById(R.id.overview_week_label)
        dayBar = findViewById(R.id.overview_day_bar)
        courseList = findViewById(R.id.overview_course_list)
        emptyHint = findViewById(R.id.overview_empty)

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.courses.collect { courses ->
                    latestCourses = courses
                    rerender()
                }
            }
        }
    }

    /** 切换周次（自动限制在 1..总周数内）并重绘。 */
    private fun switchWeek(week: Int) {
        selectedWeek = week.coerceIn(1, TermCalendar.TOTAL_WEEKS)
        rerender()
    }

    private fun rerender() {
        val dates = TermCalendar.weekDates(selectedWeek)
        val date = dates[selectedDay - 1]
        toolbar.subtitle = getString(
            R.string.overview_subtitle_fmt,
            selectedWeek, date.monthValue, date.dayOfMonth, DAY_NAMES[selectedDay - 1],
        )
        weekLabel.text = if (TermCalendar.isCurrentWeek(selectedWeek)) {
            getString(R.string.week_label_current, selectedWeek)
        } else {
            getString(R.string.week_label_fmt, selectedWeek)
        }
        renderDayBar(dates)
        renderCourseList()
    }

    /** 生成周内 7 天日期条：选中日填充主色、今天以主色数字强调。 */
    private fun renderDayBar(dates: List<LocalDate>) {
        dayBar.removeAllViews()
        val today = LocalDate.now()
        val primary = MaterialColors.getColor(dayBar, com.google.android.material.R.attr.colorPrimary)
        val onPrimary = MaterialColors.getColor(dayBar, com.google.android.material.R.attr.colorOnPrimary)
        val onSurface = MaterialColors.getColor(dayBar, com.google.android.material.R.attr.colorOnSurface)
        val density = resources.displayMetrics.density

        dates.forEachIndexed { index, date ->
            val cell = layoutInflater.inflate(R.layout.item_day_chip, dayBar, false)
            val lp = cell.layoutParams as LinearLayout.LayoutParams
            lp.width = 0
            lp.weight = 1f
            lp.marginStart = (density * 3).toInt()
            lp.marginEnd = (density * 3).toInt()
            cell.layoutParams = lp

            val dayChar = cell.findViewById<TextView>(R.id.chip_day)
            val dayNum = cell.findViewById<TextView>(R.id.chip_date)
            dayChar.text = DAY_NAMES[index]
            dayNum.text = date.dayOfMonth.toString()

            val selected = (index + 1) == selectedDay
            val isToday = date == today
            cell.background = GradientDrawable().apply {
                cornerRadius = density * 18
                setColor(if (selected) primary else android.graphics.Color.TRANSPARENT)
            }
            dayChar.setTextColor(if (selected) onPrimary else onSurface)
            dayChar.typeface = Typeface.create(Typeface.DEFAULT, if (selected) Typeface.BOLD else Typeface.NORMAL)
            dayNum.setTextColor(when {
                selected -> onPrimary
                isToday -> primary
                else -> onSurface
            })
            dayNum.typeface = Typeface.create(Typeface.DEFAULT, if (selected || isToday) Typeface.BOLD else Typeface.NORMAL)

            cell.setOnClickListener {
                selectedDay = index + 1
                rerender()
            }
            dayBar.addView(cell)
        }
    }

    /** 按节次排序列出选中日的课程卡片。 */
    private fun renderCourseList() {
        courseList.removeAllViews()
        val dayCourses = latestCourses
            .filter { it.dayOfWeek == selectedDay && it.isThisWeek(selectedWeek) }
            .sortedWith(compareBy({ it.startPeriod }, { it.endPeriod }))

        emptyHint.visibility = if (dayCourses.isEmpty()) View.VISIBLE else View.GONE

        dayCourses.forEach { course ->
            val item = layoutInflater.inflate(R.layout.item_day_course, courseList, false)
            val color = CourseColors.PALETTE[course.colorIndex % CourseColors.PALETTE.size]
            item.findViewById<View>(R.id.item_color_bar).setBackgroundColor(color)
            item.findViewById<TextView>(R.id.item_course_name).text = course.name
            item.findViewById<TextView>(R.id.item_weeks).text = Course.weeksText(course.weeks)

            val first = JluTimeTable.PERIODS[course.startPeriod - 1]
            val last = JluTimeTable.PERIODS[course.endPeriod - 1]
            item.findViewById<TextView>(R.id.item_time).text = getString(
                R.string.overview_time_fmt, first.start, last.end, course.startPeriod, course.endPeriod,
            )

            val teacherRoom = listOf(course.teacher, course.room)
                .filter { it.isNotBlank() }
                .joinToString(" · ")
            item.findViewById<TextView>(R.id.item_teacher_room).text =
                teacherRoom.ifEmpty { getString(R.string.sheet_empty_hint) }

            item.setOnClickListener { showDetail(course) }
            courseList.addView(item)
        }
    }

    /** 复用主界面的课程详情弹窗（编辑 / 删除 / 隐藏）。 */
    private fun showDetail(course: Course) {
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

    private companion object {
        val DAY_NAMES = listOf("一", "二", "三", "四", "五", "六", "七")
    }
}
