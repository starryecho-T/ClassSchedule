package com.classschedule.app.ui

import android.content.Context
import android.content.res.Configuration
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.text.TextUtils
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import com.classschedule.app.data.JluTimeTable
import com.classschedule.app.model.Course
import java.time.LocalDate
import java.util.Calendar
import kotlin.math.abs
import kotlin.math.max

/**
 * 周视图课表控件：左侧节次时间列 + 周一~周日七列 + 课程色块。
 *
 * 通过 [setup] 注入课程数据后自动重绘。
 */
class TimetableView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : View(context, attrs, defStyleAttr) {

    // ---------- 尺寸 ----------
    private val density = resources.displayMetrics.density
    private val scaledDensity = resources.displayMetrics.scaledDensity
    private fun dp(v: Float) = v * density + 0.5f
    private fun sp(v: Float) = v * scaledDensity + 0.5f

    private val timeColumnWidth = dp(44f)
    private val headerHeight = dp(40f)
    private val cellHeight = dp(64f)
    private val blockMargin = dp(2f)
    private val blockRadius = dp(6f)

    // ---------- 颜色（深色模式自适应） ----------
    private val isNight =
        (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
            Configuration.UI_MODE_NIGHT_YES

    private val gridColor = if (isNight) 0x30FFFFFF else 0x15000000
    private val labelColor = if (isNight) 0xFFB0BEC5.toInt() else 0xFF78909C.toInt()
    private val todayColumnColor = if (isNight) 0x14FFFFFF else 0x0D1E88E5
    private val todayPillColor = 0xFF1E88E5.toInt()

    /** 课程调色板：与编辑页共享（见 [CourseColors]）。 */
    private val palette = CourseColors.PALETTE

    private val dayNames = arrayOf("一", "二", "三", "四", "五", "六", "日")

    // ---------- 数据 ----------
    private var courses: List<Course> = emptyList()

    /** 当前周的 7 个具体日期（用于表头显示），size == 7 时才绘制。 */
    private var weekDates: List<LocalDate> = emptyList()

    /** 是否高亮“今天”列（仅当查看的是本周时为 true）。 */
    private var highlightToday: Boolean = false

    /** 课程块被点击时的回调（用于进入编辑页）。 */
    var onCourseClick: ((Course) -> Unit)? = null

    /** 横向滑动切换周次：-1 = 上一周，+1 = 下一周。 */
    var onWeekSwipe: ((Int) -> Unit)? = null

    // ---------- 画笔 ----------
    private val gridPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = dp(1f)
        color = gridColor
    }
    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }

    private val periodNumPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        textSize = sp(10f)
        isFakeBoldText = true
        color = labelColor
    }
    private val periodTimePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        textSize = sp(7.5f)
        color = labelColor
    }
    private val dayPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        textSize = sp(12f)
        isFakeBoldText = true
    }
    private val datePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        textSize = sp(9f)
    }
    private val courseNamePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = sp(10f)
        isFakeBoldText = true
    }
    private val courseInfoPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = sp(8f)
    }

    /**
     * 注入课程数据与所查看的周次，控件只绘制该周课程。
     *
     * @param selectedWeek  当前查看的周次
     * @param dates         该周的 7 个具体日期（表头显示），空列表则只画星期
     * @param isCurrentWeek 该周是否为本周（控制“今天”列高亮）
     */
    fun setup(
        courses: List<Course>,
        selectedWeek: Int,
        dates: List<LocalDate> = emptyList(),
        isCurrentWeek: Boolean = false,
    ) {
        this.courses = courses.filter { it.isThisWeek(selectedWeek) }
        this.weekDates = dates
        this.highlightToday = isCurrentWeek
        requestLayout()
        invalidate()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val width = when (MeasureSpec.getMode(widthMeasureSpec)) {
            MeasureSpec.UNSPECIFIED -> resources.displayMetrics.widthPixels
            else -> MeasureSpec.getSize(widthMeasureSpec)
        }
        val neededHeight = headerHeight + JluTimeTable.PERIODS.size * cellHeight
        setMeasuredDimension(width, resolveSize(neededHeight.toInt(), heightMeasureSpec))
    }

    /** 今天是周几（1=周一 ... 7=周日）。 */
    private fun todayColumn(): Int {
        val dow = Calendar.getInstance().get(Calendar.DAY_OF_WEEK) // 1=周日 ... 7=周六
        return if (dow == Calendar.SUNDAY) 7 else dow - 1
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val periods = JluTimeTable.PERIODS
        val rows = periods.size
        val colWidth = (width - timeColumnWidth) / 7f
        val todayCol = todayColumn()

        // 1. 今日列背景（仅查看本周时）
        if (highlightToday && todayCol in 1..7) {
            fillPaint.color = todayColumnColor
            val left = timeColumnWidth + (todayCol - 1) * colWidth
            canvas.drawRect(left, 0f, left + colWidth, height.toFloat(), fillPaint)
        }

        // 2. 网格线
        var y = headerHeight
        repeat(rows + 1) {
            canvas.drawLine(0f, y, width.toFloat(), y, gridPaint)
            y += cellHeight
        }
        canvas.drawLine(timeColumnWidth, 0f, timeColumnWidth, height.toFloat(), gridPaint)
        for (c in 1..7) {
            val x = timeColumnWidth + c * colWidth
            canvas.drawLine(x, 0f, x, height.toFloat(), gridPaint)
        }

        // 3. 表头：周一~周日 + 该周具体日期（两行）
        val dayBaseY = dp(16f) - (dayPaint.descent() + dayPaint.ascent()) / 2f
        val dateBaseY = dp(31f) - (datePaint.descent() + datePaint.ascent()) / 2f
        for (day in 1..7) {
            val cx = timeColumnWidth + (day - 1) * colWidth + colWidth / 2f
            val isToday = highlightToday && day == todayCol
            if (isToday) {
                fillPaint.color = todayPillColor
                val pillW = colWidth - dp(12f)
                val pillH = headerHeight - dp(8f)
                canvas.drawRoundRect(
                    RectF(cx - pillW / 2f, dp(4f), cx + pillW / 2f, dp(4f) + pillH),
                    dp(10f), dp(10f), fillPaint
                )
                dayPaint.color = 0xFFFFFFFF.toInt()
                datePaint.color = 0xE6FFFFFF.toInt()
            } else {
                dayPaint.color = labelColor
                datePaint.color = labelColor
            }
            canvas.drawText("周" + dayNames[day - 1], cx, dayBaseY, dayPaint)
            if (weekDates.size == 7) {
                val d = weekDates[day - 1]
                canvas.drawText("${d.monthValue}/${d.dayOfMonth}", cx, dateBaseY, datePaint)
            }
        }

        // 4. 左侧节次 + 上下课时间
        periods.forEachIndexed { index, period ->
            val top = headerHeight + index * cellHeight
            val cx = timeColumnWidth / 2f
            canvas.drawText(period.index.toString(), cx, top + dp(19f), periodNumPaint)
            canvas.drawText(period.start, cx, top + dp(33f), periodTimePaint)
            canvas.drawText(period.end, cx, top + dp(44f), periodTimePaint)
        }

        // 5. 课程色块
        courses.forEach { course ->
            val color = palette[course.colorIndex % palette.size]
            val left = timeColumnWidth + (course.dayOfWeek - 1) * colWidth + blockMargin
            val right = timeColumnWidth + course.dayOfWeek * colWidth - blockMargin
            val top = headerHeight + (course.startPeriod - 1) * cellHeight + blockMargin
            val bottom = headerHeight + course.endPeriod * cellHeight - blockMargin
            if (right <= left || bottom <= top) return@forEach

            // 半透明底色圆角块
            fillPaint.color = (color and 0x00FFFFFF) or 0x2E000000
            canvas.drawRoundRect(RectF(left, top, right, bottom), blockRadius, blockRadius, fillPaint)

            val contentWidth = (right - left - dp(6f)).toInt()
            if (contentWidth <= 0) return@forEach
            val blockHeight = bottom - top

            courseNamePaint.color = color
            val nameLayout = StaticLayout.Builder
                .obtain(course.name, 0, course.name.length, courseNamePaint, contentWidth)
                .setAlignment(Layout.Alignment.ALIGN_CENTER)
                .setLineSpacing(0f, 1.1f)
                .setIncludePad(false)
                .setMaxLines(if (blockHeight < dp(48f)) 2 else 3)
                .setEllipsize(TextUtils.TruncateAt.END)
                .build()

            courseInfoPaint.color = color
            val infoText = "@${course.room}\n${weeksText(course.weeks)}"
            val infoLayout = StaticLayout.Builder
                .obtain(infoText, 0, infoText.length, courseInfoPaint, contentWidth)
                .setAlignment(Layout.Alignment.ALIGN_CENTER)
                .setLineSpacing(0f, 1.2f)
                .setIncludePad(false)
                .build()

            val totalH = nameLayout.height + dp(4f) + infoLayout.height
            val textTop = max(top + dp(3f), top + (blockHeight - totalH) / 2f)
            canvas.save()
            canvas.translate(left + dp(3f), textTop)
            nameLayout.draw(canvas)
            canvas.translate(0f, nameLayout.height + dp(4f))
            infoLayout.draw(canvas)
            canvas.restore()
        }
    }

    // ---------- 触摸：点击课程块 / 横滑切换周次 ----------
    private var downX = 0f
    private var downY = 0f

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downX = event.x
                downY = event.y
            }
            MotionEvent.ACTION_UP -> {
                val dx = event.x - downX
                val dy = event.y - downY
                if (abs(dx) > dp(60f) && abs(dx) > abs(dy) * 2f) {
                    // 左滑 -> 下一周，右滑 -> 上一周
                    onWeekSwipe?.invoke(if (dx < 0) 1 else -1)
                } else {
                    hitCourse(event.x, event.y)?.let { onCourseClick?.invoke(it) }
                }
            }
        }
        return true
    }

    /** 命中测试：根据 (x, y) 反算星期与节次，返回该位置的课程。 */
    private fun hitCourse(x: Float, y: Float): Course? {
        if (y < headerHeight || x < timeColumnWidth) return null
        val colWidth = (width - timeColumnWidth) / 7f
        val day = ((x - timeColumnWidth) / colWidth).toInt() + 1
        val period = ((y - headerHeight) / cellHeight).toInt() + 1
        if (day !in 1..7 || period !in 1..JluTimeTable.PERIODS.size) return null
        return courses.lastOrNull {
            it.dayOfWeek == day && period in it.startPeriod..it.endPeriod
        }
    }

    private fun weeksText(weeks: Set<Int>): String {
        if (weeks.isEmpty()) return ""
        val sorted = weeks.sorted()
        val first = sorted.first()
        val last = sorted.last()
        val contiguous = sorted == (first..last).toList()
        return when {
            weeks.size == 1 -> "第${first}周"
            contiguous && sorted.all { it % 2 == 1 } -> "${first}-${last}周单周"
            contiguous && sorted.all { it % 2 == 0 } -> "${first}-${last}周双周"
            contiguous -> "${first}-${last}周"
            else -> "${first}-${last}周(部分)"
        }
    }
}
