package com.classschedule.app.ui

import android.content.Context
import android.content.Intent
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.util.TypedValue
import android.view.View
import android.widget.LinearLayout
import android.widget.NumberPicker
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.classschedule.app.R
import com.classschedule.app.data.CourseRepository
import com.classschedule.app.data.JluTimeTable
import com.classschedule.app.data.db.AppDatabase
import com.classschedule.app.model.Course
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.button.MaterialButton
import com.google.android.material.button.MaterialButtonToggleGroup
import com.google.android.material.textfield.TextInputEditText
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

/**
 * 课程添加 / 编辑页（M2）。
 * 交互流程参考 thu-info-app 的自定义课程表单：名称 + 星期 + 节次 + 周次(含单双周) + 颜色。
 */
class EditCourseActivity : AppCompatActivity() {

    private val repo by lazy { CourseRepository(this, AppDatabase.getDatabase(this).courseDao()) }

    private var courseId = 0L
    private var selectedDay = 0        // 1..7，0 = 未选
    private var selectedColor = 0
    private var isLoading = false
    private var isSaving = false
    private var originalWeeks: Set<Int> = emptySet()
    private var originalWeekSelection: CourseWeekSelection? = null
    /** 编辑模式下保留既有的隐藏周次（隐藏状态不应因编辑信息而丢失）。 */
    private var hiddenWeeks: Set<Int> = emptySet()
    private val dayButtons = mutableListOf<MaterialButton>()
    private val colorViews = mutableListOf<View>()

    private lateinit var etName: TextInputEditText
    private lateinit var etTeacher: TextInputEditText
    private lateinit var etRoom: TextInputEditText
    private lateinit var npStart: NumberPicker
    private lateinit var npEnd: NumberPicker
    private lateinit var npFromWeek: NumberPicker
    private lateinit var npToWeek: NumberPicker
    private lateinit var weekModeGroup: MaterialButtonToggleGroup
    private lateinit var toolbar: MaterialToolbar
    private lateinit var originalWeeksNotice: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_edit_course)
        courseId = intent.getLongExtra(EXTRA_COURSE_ID, 0L)
        isLoading = courseId != 0L

        toolbar = findViewById(R.id.toolbar)
        toolbar.title =
            getString(if (courseId == 0L) R.string.edit_course_new else R.string.edit_course_edit)
        toolbar.setNavigationOnClickListener { finish() }
        toolbar.inflateMenu(R.menu.menu_edit_course)
        toolbar.menu.findItem(R.id.action_delete).isVisible = courseId != 0L
        toolbar.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                R.id.action_save -> { save(); true }
                R.id.action_delete -> { confirmDelete(); true }
                else -> false
            }
        }

        bindViews()
        updateEditorEnabled()
        if (courseId != 0L) loadExisting() else applyPrefill()
    }

    private fun bindViews() {
        etName = findViewById(R.id.et_name)
        etTeacher = findViewById(R.id.et_teacher)
        etRoom = findViewById(R.id.et_room)
        npStart = findViewById(R.id.np_start)
        npEnd = findViewById(R.id.np_end)
        npFromWeek = findViewById(R.id.np_from_week)
        npToWeek = findViewById(R.id.np_to_week)
        weekModeGroup = findViewById(R.id.week_mode_group)

        npStart.config(1, JluTimeTable.PERIODS.size)
        npEnd.config(1, JluTimeTable.PERIODS.size, value = 2)
        npFromWeek.config(1, JluTimeTable.TOTAL_WEEKS)
        npToWeek.config(1, JluTimeTable.TOTAL_WEEKS, value = JluTimeTable.TOTAL_WEEKS)

        buildDayButtons(findViewById(R.id.day_group))
        buildColorRow(findViewById(R.id.color_row))

        originalWeeksNotice = TextView(this).apply {
            setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_BodySmall)
            setPadding(0, dp(8), 0, 0)
            visibility = View.GONE
        }
        val form = weekModeGroup.parent as LinearLayout
        form.addView(originalWeeksNotice, form.indexOfChild(weekModeGroup) + 1)
        npFromWeek.setOnValueChangedListener { _, _, _ -> updateOriginalWeeksNotice() }
        npToWeek.setOnValueChangedListener { _, _, _ -> updateOriginalWeeksNotice() }
        weekModeGroup.addOnButtonCheckedListener { _, _, _ -> updateOriginalWeeksNotice() }
    }

    /** 星期按钮组（一~日）由代码生成，避免 XML 冗长。 */
    private fun buildDayButtons(group: MaterialButtonToggleGroup) {
        dayNames.forEach { name ->
            val btn = MaterialButton(
                this, null,
                com.google.android.material.R.attr.materialButtonOutlinedStyle,
            )
            btn.id = View.generateViewId()
            btn.text = name
            btn.layoutParams =
                LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            group.addView(btn)
            dayButtons.add(btn)
        }
        group.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (isChecked) {
                selectedDay = dayButtons.indexOfFirst { it.id == checkedId } + 1
            }
        }
    }

    /** 色板：圆形色块，选中不透明、其余半透明。 */
    private fun buildColorRow(row: LinearLayout) {
        CourseColors.PALETTE.forEachIndexed { index, color ->
            val view = View(this)
            view.layoutParams = LinearLayout.LayoutParams(dp(40), dp(40)).apply {
                marginEnd = dp(12)
            }
            view.background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(color)
            }
            view.setOnClickListener { selectColor(index) }
            row.addView(view)
            colorViews.add(view)
        }
        selectColor(0)
    }

    private fun selectColor(index: Int) {
        selectedColor = index
        colorViews.forEachIndexed { i, view -> view.alpha = if (i == index) 1f else 0.4f }
    }

    /** 编辑模式：从数据库回填已有课程。 */
    private fun loadExisting() {
        lifecycleScope.launch {
            try {
                val course = repo.getById(courseId)
                if (course == null) {
                    toast(R.string.course_not_found)
                    finish()
                    return@launch
                }
                etName.setText(course.name)
                etTeacher.setText(course.teacher)
                etRoom.setText(course.room)
                hiddenWeeks = course.hiddenWeeks
                originalWeeks = course.weeks
                npStart.value = course.startPeriod
                npEnd.value = course.endPeriod
                selectedDay = course.dayOfWeek
                dayButtons.getOrNull(course.dayOfWeek - 1)?.isChecked = true
                val selection = CourseWeekSelection.fromWeeks(course.weeks)
                if (selection != null) {
                    npFromWeek.value = selection.fromWeek
                    npToWeek.value = selection.toWeek
                    weekModeGroup.check(
                        when (selection.mode) {
                            CourseWeekSelection.Mode.ODD -> R.id.btn_mode_odd
                            CourseWeekSelection.Mode.EVEN -> R.id.btn_mode_even
                            CourseWeekSelection.Mode.ALL -> R.id.btn_mode_all
                        }
                    )
                }
                originalWeekSelection = currentWeekSelection()
                selectColor(course.colorIndex)
                isLoading = false
                updateEditorEnabled()
                updateOriginalWeeksNotice()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                toast(R.string.course_load_failed)
                finish()
            }
        }
    }

    /** 新增模式下的预填：长按课表空白格加课时，带上星期与节次。 */
    private fun applyPrefill() {
        val day = intent.getIntExtra(EXTRA_PREFILL_DAY, 0)
        val period = intent.getIntExtra(EXTRA_PREFILL_PERIOD, 0)
        if (day in 1..7) {
            selectedDay = day
            dayButtons.getOrNull(day - 1)?.isChecked = true
        }
        if (period in 1..JluTimeTable.PERIODS.size) {
            npStart.value = period
            npEnd.value = (period + 1).coerceAtMost(JluTimeTable.PERIODS.size)
        }
    }

    private fun save() {
        if (isLoading) return toast(R.string.course_loading)
        if (isSaving) return
        // 提交 NumberPicker 中仍处于输入状态的数字后再校验。
        currentFocus?.clearFocus()
        val name = etName.text?.toString()?.trim().orEmpty()
        if (name.isEmpty()) return toast(R.string.toast_name_empty)
        if (selectedDay == 0) return toast(R.string.toast_day_required)
        if (npStart.value > npEnd.value) return toast(R.string.toast_period_invalid)
        if (npFromWeek.value > npToWeek.value) return toast(R.string.toast_week_invalid)

        val weeks = currentWeekSelection().weeksForSave(originalWeeks, originalWeekSelection)
        if (weeks.isEmpty()) return toast(R.string.course_week_selection_empty)
        val course = Course(
            name = name,
            teacher = etTeacher.text?.toString()?.trim().orEmpty(),
            room = etRoom.text?.toString()?.trim().orEmpty(),
            dayOfWeek = selectedDay,
            startPeriod = npStart.value,
            endPeriod = npEnd.value,
            weeks = weeks,
            colorIndex = selectedColor,
            hiddenWeeks = hiddenWeeks.intersect(weeks),
            id = courseId,
        )
        isSaving = true
        updateEditorEnabled()
        lifecycleScope.launch {
            try {
                repo.upsert(course)
                finish()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                toast(R.string.course_save_failed)
                isSaving = false
                updateEditorEnabled()
            }
        }
    }

    private fun confirmDelete() {
        if (isLoading || isSaving || courseId == 0L) return
        AlertDialog.Builder(this)
            .setMessage(R.string.delete_confirm)
            .setPositiveButton(R.string.action_delete) { _, _ ->
                if (isLoading || isSaving) return@setPositiveButton
                isSaving = true
                updateEditorEnabled()
                lifecycleScope.launch {
                    try {
                        repo.deleteById(courseId)
                        finish()
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (_: Exception) {
                        toast(R.string.course_delete_failed)
                        isSaving = false
                        updateEditorEnabled()
                    }
                }
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun currentWeekSelection() = CourseWeekSelection(
        fromWeek = npFromWeek.value,
        toWeek = npToWeek.value,
        mode = when (weekModeGroup.checkedButtonId) {
            R.id.btn_mode_odd -> CourseWeekSelection.Mode.ODD
            R.id.btn_mode_even -> CourseWeekSelection.Mode.EVEN
            else -> CourseWeekSelection.Mode.ALL
        },
    )

    private fun updateOriginalWeeksNotice() {
        val selection = currentWeekSelection()
        val preservingIrregularWeeks = originalWeeks.isNotEmpty() &&
            selection == originalWeekSelection && originalWeeks != selection.generatedWeeks()
        originalWeeksNotice.visibility = if (preservingIrregularWeeks) View.VISIBLE else View.GONE
        if (preservingIrregularWeeks) {
            originalWeeksNotice.text = getString(
                R.string.course_original_weeks_notice, originalWeeks.sorted().joinToString("、"),
            )
        }
    }

    private fun updateEditorEnabled() {
        val enabled = !isLoading && !isSaving
        toolbar.menu.findItem(R.id.action_save).isEnabled = enabled
        toolbar.menu.findItem(R.id.action_delete).isEnabled = enabled
        listOf(etName, etTeacher, etRoom, npStart, npEnd, npFromWeek, npToWeek)
            .forEach { it.isEnabled = enabled }
        dayButtons.forEach { it.isEnabled = enabled }
        colorViews.forEach { it.isEnabled = enabled }
        for (index in 0 until weekModeGroup.childCount) {
            weekModeGroup.getChildAt(index).isEnabled = enabled
        }
    }

    private fun toast(res: Int) {
        Toast.makeText(this, getString(res), Toast.LENGTH_SHORT).show()
    }

    private fun dp(v: Int) = TypedValue.applyDimension(
        TypedValue.COMPLEX_UNIT_DIP, v.toFloat(), resources.displayMetrics,
    ).toInt()

    private fun NumberPicker.config(min: Int, max: Int, value: Int = min) {
        minValue = min
        maxValue = max
        this.value = value
        wrapSelectorWheel = false
    }

    companion object {
        private const val EXTRA_COURSE_ID = "extra_course_id"
        private const val EXTRA_PREFILL_DAY = "extra_prefill_day"
        private const val EXTRA_PREFILL_PERIOD = "extra_prefill_period"
        private val dayNames = arrayOf("一", "二", "三", "四", "五", "六", "日")

        /**
         * courseId 传 0（默认）表示新增课程；新增时可预填星期与节次
         * （长按课表空白格快速加课的入口）。
         */
        fun intent(
            context: Context,
            courseId: Long = 0L,
            prefillDay: Int = 0,
            prefillStartPeriod: Int = 0,
        ): Intent = Intent(context, EditCourseActivity::class.java)
            .putExtra(EXTRA_COURSE_ID, courseId)
            .putExtra(EXTRA_PREFILL_DAY, prefillDay)
            .putExtra(EXTRA_PREFILL_PERIOD, prefillStartPeriod)
    }
}

