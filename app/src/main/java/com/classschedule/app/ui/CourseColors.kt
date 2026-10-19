package com.classschedule.app.ui
import android.content.Context
import com.classschedule.app.R
/** Stable index order; resources provide matching light and dark variants. */
object CourseColors {
    fun palette(context: Context): IntArray = context.resources.getIntArray(R.array.course_palette)
    fun color(context: Context, index: Int): Int {
        val colors = palette(context)
        return colors[Math.floorMod(index, colors.size)]
    }
}
