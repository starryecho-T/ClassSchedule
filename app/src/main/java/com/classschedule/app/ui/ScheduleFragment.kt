package com.classschedule.app.ui
import android.os.Bundle
import android.view.View
import android.widget.TextView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.classschedule.app.R
import com.classschedule.app.data.TermCalendar
import com.classschedule.app.model.Course
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.button.MaterialButton
import com.google.android.material.floatingactionbutton.FloatingActionButton
import kotlinx.coroutines.launch
class ScheduleFragment : CourseFragment(R.layout.fragment_schedule) {
    /** 当前正在查看的周次（1..总周数），默认定位到本周。 */
    private var selectedWeek: Int = TermCalendar.currentWeek().coerceIn(1, TermCalendar.TOTAL_WEEKS)

    /** Room 里最新的全量课程（切换周次时无需等待 Flow 重发）。 */
    private var latestCourses: List<Course> = emptyList()
    private var savedScroll = -1

    private val timetable: TimetableView get() = requireView().findViewById(R.id.timetable)
    private val weekLabel: TextView get() = requireView().findViewById(R.id.week_label)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        selectedWeek = savedInstanceState?.getInt(STATE_SELECTED_WEEK, selectedWeek)
            ?.coerceIn(1, TermCalendar.TOTAL_WEEKS) ?: selectedWeek
        savedScroll = savedInstanceState?.getInt("scroll", savedScroll) ?: savedScroll
        // 点击课程块 -> 详情弹窗（不再直接进编辑页，借鉴 thu-info 交互）
        timetable.onCourseClick = { course -> showCourseDetail(course, selectedWeek) }
        // 长按空白格 -> 预填星期与节次，快速加课
        timetable.onEmptyCellLongClick = { day, period ->
            startActivity(
                EditCourseActivity.intent(requireContext(), prefillDay = day, prefillStartPeriod = period)
            )
        }
        // 横滑切换周次（借鉴 thu-info 的手势交互）
        timetable.onWeekSwipe = { delta -> switchWeek(selectedWeek + delta) }

        view.findViewById<MaterialButton>(R.id.week_prev).setOnClickListener {
            switchWeek(selectedWeek - 1)
        }
        view.findViewById<MaterialButton>(R.id.week_next).setOnClickListener {
            switchWeek(selectedWeek + 1)
        }
        view.findViewById<MaterialButton>(R.id.week_today).setOnClickListener {
            switchWeek(TermCalendar.currentWeek())
        }

        view.findViewById<FloatingActionButton>(R.id.fab_add).setOnClickListener {
            startActivity(EditCourseActivity.intent(requireContext()))
        }

        // 课程数据变化时自动重绘当前查看的周
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.RESUMED) {
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
        outState.putInt("scroll", view?.findViewById<androidx.core.widget.NestedScrollView>(R.id.schedule_scroll)?.scrollY ?: savedScroll)
        super.onSaveInstanceState(outState)
    }
    override fun onDestroyView() {
        timetable.onCourseClick = null
        timetable.onWeekSwipe = null
        timetable.onEmptyCellLongClick = null
        super.onDestroyView()
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
        val scroll = requireView().findViewById<androidx.core.widget.NestedScrollView>(R.id.schedule_scroll)
        val position = if (savedScroll >= 0) savedScroll else scroll.scrollY
        timetable.setup(
            latestCourses,
            selectedWeek,
            TermCalendar.weekDates(selectedWeek),
            TermCalendar.isCurrentWeek(selectedWeek),
        )
        scroll.post { scroll.scrollTo(0, position) }
        savedScroll = -1
    }

    /** 更新顶栏标题与周次切换条文案。 */
    private fun updateWeekUi() {
        requireView().findViewById<MaterialToolbar>(R.id.toolbar).apply {
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
