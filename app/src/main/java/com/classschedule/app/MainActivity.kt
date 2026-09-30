package com.classschedule.app

import android.os.Bundle
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.classschedule.app.data.SampleData
import com.classschedule.app.ui.EditCourseActivity
import com.classschedule.app.ui.MainViewModel
import com.classschedule.app.ui.TimetableView
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.floatingactionbutton.FloatingActionButton
import kotlinx.coroutines.launch

/**
 * 主页面：顶栏显示周次 / 学期信息，内容区为周视图课表（M2 起数据来自 Room，自动刷新）。
 */
class MainActivity : AppCompatActivity() {

    private val viewModel: MainViewModel by viewModels { MainViewModel.Factory }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val toolbar = findViewById<MaterialToolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.apply {
            title = getString(R.string.timetable_title, SampleData.CURRENT_WEEK)
            subtitle = getString(R.string.timetable_subtitle)
        }

        val timetable = findViewById<TimetableView>(R.id.timetable)
        timetable.onCourseClick = { course ->
            startActivity(EditCourseActivity.intent(this, course.id))
        }

        findViewById<FloatingActionButton>(R.id.fab_add).setOnClickListener {
            startActivity(EditCourseActivity.intent(this))
        }

        // 课程数据变化时自动重绘周视图
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.courses.collect { courses ->
                    timetable.setup(courses, SampleData.CURRENT_WEEK)
                }
            }
        }
    }
}

