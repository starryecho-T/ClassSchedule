package com.classschedule.app.ui
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import com.classschedule.app.R
import com.classschedule.app.data.TermCalendar
import com.classschedule.app.model.Course
import com.google.android.material.dialog.MaterialAlertDialogBuilder
abstract class CourseFragment(layout: Int) : Fragment(layout) {
    protected val viewModel: MainViewModel
        get() = ViewModelProvider(requireActivity(), MainViewModel.Factory)[MainViewModel::class.java]
    /** 全部隐藏的课程也保留管理入口，点击后可在原详情页恢复。 */
    protected fun showHiddenCourses() {
        val hiddenCourses = viewModel.courses.value.filter { it.hiddenWeeks.isNotEmpty() }
            .sortedWith(compareBy(Course::dayOfWeek, Course::startPeriod, Course::endPeriod, Course::name, Course::id))
        val dialog = MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.hidden_courses_title)
            .setNegativeButton(android.R.string.cancel, null)
        if (hiddenCourses.isEmpty()) {
            dialog.setMessage(R.string.hidden_courses_empty).show()
            return
        }
        val adapter = object : ArrayAdapter<Course>(requireContext(), android.R.layout.simple_list_item_2, hiddenCourses) {
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
        dialog.setAdapter(adapter) { _, position -> showCourseDetail(hiddenCourses[position], TermCalendar.currentWeek().coerceIn(1, TermCalendar.TOTAL_WEEKS)) }.show()
    }

    /** 课程详情底部弹窗：查看信息 + 编辑 / 删除 / 隐藏（M3）。 */
    protected fun showCourseDetail(course: Course, selectedWeek: Int) {
        CourseDetailSheet(
            context = requireContext(),
            course = course,
            selectedWeek = selectedWeek,
            onEdit = { startActivity(EditCourseActivity.intent(requireContext(), course.id)) },
            onDelete = { viewModel.deleteCourse(course.id) },
            onHideWeeks = { weeks -> viewModel.hideCourseWeeks(course, weeks) },
            onRestore = { viewModel.restoreCourse(course) },
        ).open()
    }

}
