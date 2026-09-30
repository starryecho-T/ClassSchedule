package com.classschedule.app.ui

import android.content.Context
import android.view.View
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import com.classschedule.app.R
import com.classschedule.app.data.JluTimeTable
import com.classschedule.app.model.Course
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.button.MaterialButton

/**
 * 课程详情底部弹窗（M3）：点击课程块先看详情，再决定编辑 / 删除 / 隐藏。
 *
 * 隐藏三档语义借鉴 thu-info-app 的设计，映射为「仅本周这一次」与「全部周次」
 * 两个动作（集合求并实现），隐藏后可通过「恢复显示」一键清空隐藏记录。
 */
class CourseDetailSheet(
    context: Context,
    private val course: Course,
    /** 当前查看的周次：「仅隐藏本次」以它为准。 */
    private val selectedWeek: Int,
    private val onEdit: () -> Unit,
    private val onDelete: () -> Unit,
    private val onHideWeeks: (Set<Int>) -> Unit,
    private val onRestore: () -> Unit,
) : BottomSheetDialog(context) {

    /** 绑定数据并展示。 */
    fun open() {
        setContentView(R.layout.sheet_course_detail)
        bindViews()
        show()
    }

    private fun bindViews() {
        val color = CourseColors.PALETTE[course.colorIndex % CourseColors.PALETTE.size]
        val empty = context.getString(R.string.sheet_empty_hint)

        findViewById<View>(R.id.sheet_color_bar)!!.setBackgroundColor(color)
        findViewById<TextView>(R.id.sheet_course_name)!!.text = course.name

        val first = JluTimeTable.PERIODS[course.startPeriod - 1]
        val last = JluTimeTable.PERIODS[course.endPeriod - 1]
        val dayName = "一二三四五六日"[course.dayOfWeek - 1]
        findViewById<TextView>(R.id.sheet_time)!!.text =
            "周$dayName · 第${course.startPeriod}-${course.endPeriod}节 · ${first.start}~${last.end}"

        findViewById<TextView>(R.id.sheet_teacher)!!.text =
            context.getString(R.string.sheet_teacher_fmt, course.teacher.ifEmpty { empty })
        findViewById<TextView>(R.id.sheet_room)!!.text =
            context.getString(R.string.sheet_room_fmt, course.room.ifEmpty { empty })
        findViewById<TextView>(R.id.sheet_weeks)!!.text =
            context.getString(R.string.sheet_weeks_fmt, Course.weeksText(course.weeks))

        val hiddenNote = findViewById<TextView>(R.id.sheet_hidden_note)!!
        val restoreBtn = findViewById<MaterialButton>(R.id.btn_restore)!!
        if (course.hiddenWeeks.isNotEmpty()) {
            hiddenNote.visibility = View.VISIBLE
            restoreBtn.visibility = View.VISIBLE
            hiddenNote.text =
                context.getString(R.string.sheet_hidden_note, course.hiddenWeeks.size)
        }

        findViewById<MaterialButton>(R.id.btn_edit)!!.setOnClickListener {
            dismiss(); onEdit()
        }
        findViewById<MaterialButton>(R.id.btn_delete)!!.setOnClickListener {
            AlertDialog.Builder(context)
                .setMessage(R.string.delete_confirm)
                .setPositiveButton(R.string.action_delete) { _, _ -> dismiss(); onDelete() }
                .setNegativeButton(android.R.string.cancel, null)
                .show()
        }
        findViewById<MaterialButton>(R.id.btn_hide_once)!!.setOnClickListener {
            dismiss(); onHideWeeks(setOf(selectedWeek))
        }
        findViewById<MaterialButton>(R.id.btn_hide_all)!!.setOnClickListener {
            dismiss(); onHideWeeks(course.weeks)
        }
        restoreBtn.setOnClickListener {
            dismiss(); onRestore()
        }
    }
}
